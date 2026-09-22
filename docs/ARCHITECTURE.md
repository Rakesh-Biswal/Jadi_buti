# Jadi-Buti architecture

## Overview

```
Android app (Kotlin / Compose)                      Backend (Next.js API)          MongoDB Atlas
┌───────────────────────────────────────┐            ┌──────────────────────┐      ┌───────────┐
│ UI (Compose screens + ViewModels)     │            │ /api/v1/auth         │      │ JadiButi  │
│ Repositories  ──► Room (offline data) │  push/pull │ /api/v1/families/... │ ───► │ database  │
│ Outbox        ──► SyncWorker ─────────┼──────────► │ /api/v1/sync/*       │      └───────────┘
│ domain: ScheduleEngine, InventoryMath │            │ /api/v1/.../prescr.  │──► private image store
│ AlarmManager reminders + receivers    │            │ AI extraction (Claude)│
└───────────────────────────────────────┘            └──────────────────────┘
```

## Data model

`User → Family → FamilyMembership (OWNER | CAREGIVER)`, and per family: `FamilyMember → Medicine (schedule embedded) → MedicationEvent`, `InventoryTransaction` (append-only ledger), `Prescription` (+ extraction draft).

All family-scoped documents carry `id` (client-generated UUID), `version`, `updatedAt` (client clock), `serverUpdatedAt` (pull cursor) and `deleted` (tombstone).

## Scheduling engine (`android/domain`)

`ScheduleEngine.generate(schedule, from, to, zone)` turns a medicine's rules (daily, alternate days, specific weekdays, weekly, every-N-days; multiple dose times with different amounts; start/end/ongoing) into `ScheduledDose`s. Times are resolved from local wall-clock time with `java.time` so DST and timezone changes are handled by regeneration.

Event ids are deterministic: `medicineId:yyyy-MM-dd:HH:mm`. Every device and the server therefore agree on identity without coordination. This is what prevents duplicate reminders and duplicate inventory deductions.

Alternate-day and custom-interval rules are anchored on `referenceDate` (defaults to the start date), never on weekday names.

## Events and status

`EventGenerator` materialises a rolling window (yesterday → +7 days) into Room. `StatusPolicy` derives the display status (UPCOMING / DUE / SNOOZED / MISSED) from time; TAKEN / SKIPPED are user decisions (terminal). `MaintenanceService` persists MISSED after the configurable grace period and never overwrites terminal states. Every status change is appended to `statusHistory`.

## Meal-based dashboard

Each dose time may carry an explicit meal (`BREAKFAST`, `LUNCH`, `DINNER`, `BEDTIME`); otherwise `MealSlot.infer(time)` in the domain module maps the clock time to a slot (04:00–10:59 breakfast, 11:00–15:59 lunch, 16:00–20:59 dinner, otherwise bedtime). The Home screen groups today's doses by slot, then by family member, and shows the food instruction (before/after/with food, empty stomach) on every dose. Nothing is hard-coded per medicine; a medicine with three dose times appears in three slots.

## Reminders

`ReminderScheduler` sets AlarmManager alarms (exact when permitted, otherwise inexact with UI guidance) per pending dose: REMIND at the scheduled time (or snooze end), optional FOLLOW_UP after N minutes on a stronger channel, and MISSED at the grace deadline. Alarms carry only the event id; `ReminderReceiver` re-reads the database before acting, so stale alarms are harmless. `BootReceiver` rebuilds everything after reboot, app update, time or timezone change. WorkManager periodic jobs are a safety net.

When a REMIND alarm fires, `ReminderReceiver` gathers every dose due in the same minute and starts `AlarmService`, a foreground service (media-playback type) that posts one alarm notification with a full-screen intent (so `AlarmActivity` appears over the lock screen with big TAKEN / SNOOZE / SKIP buttons per dose), posts one actionable notification per dose, and plays the chosen tone in a loop on the ALARM audio stream with a repeating vibration. Ringing stops when every dose is handled, when the user taps "Stop sound", or after the configured ring time (30 s to 5 min); the per-dose notifications stay until acted on. Reminder channels are silent by design so the sound never plays twice.

Tones: four original looping WAVs in `res/raw` (classic, chime, gentle, urgent), the phone's alarm sound, or a user-picked sound; each can be previewed in Settings. Vibration can be switched off or set to gentle/normal/strong (separately for overdue follow-ups). Because the ALARM stream is used, silent mode does not mute a medication alarm (same as a clock alarm), while Do Not Disturb is respected per the phone's alarm policy.

## Medicine photo scanning

A photo of packaging is uploaded to `POST /families/:id/medicine-scans` (same private storage and model as prescriptions, `kind = MEDICINE_PHOTO`) and read by `extractMedicinePhoto` into name / strength / form / composition with per-field `uncertain` flags. The app shows the draft beside the photo (step 1); the user corrects and explicitly confirms the identity, then configures member, doses, meals, frequency, food instruction, dates and stock (step 2) and confirms again. Only then is the medicine created. Nothing is created from the scan on its own, and no AI credentials exist in the APK.

## Offline-first sync

Every local write goes to Room and to the `outbox`. `SyncWorker` pushes the outbox (`POST /sync/push`), then pulls changes since the family cursor (`GET /sync/pull`). Conflict rules (same code on server and device, `MergePolicy`):

- A terminal status (TAKEN/SKIPPED) is never downgraded by a non-terminal one.
- Between two terminal statuses the later recorded time wins.
- Otherwise higher rank wins, then latest `updatedAt`.
- Ledger entries are immutable; the deduction for a dose is `deduct:<eventId>`, so it can only exist once.

## Inventory

Stock = sum of the ledger. Deduction happens only when a dose is marked TAKEN, by the dose amount configured for that time. Changing TAKEN → SKIPPED appends a `reversal:<eventId>`. Stock is never silently driven negative: if stock is insufficient the recorded deduction is clamped and the user is warned. Low-stock alerts (percent of the last baseline, or absolute quantity) fire once per low-stock episode.

## Prescription AI

Image → authenticated upload (private storage) → `POST /extract` (Claude, structured output with per-field `uncertain` flags) → DRAFT shown next to the original image → user edits → explicit **Confirm & Add** creates medicines. The model is instructed to transcribe only; missing or unclear fields are flagged "Unable to confidently read this field. Please verify." Nothing becomes active without confirmation. Without an API key the server answers 503 and the app offers manual entry.

## Security

- Backend validates every request: JWT access tokens, family membership checked server-side on every family route, owner-only routes for invites and access management.
- Secrets only in `backend/.env`. The APK contains no database or AI credentials.
- Tokens on device in EncryptedSharedPreferences (Android Keystore), excluded from backups.
- Prescription images are served only through an authenticated family-scoped endpoint.
