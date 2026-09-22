package com.chefotech.jadibuti.scheduling

import com.chefotech.jadibuti.data.local.EventDao
import com.chefotech.jadibuti.data.local.EventEntity
import com.chefotech.jadibuti.data.local.MedicineDao
import com.chefotech.jadibuti.data.local.MedicineEntity
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.repo.Outbox
import com.chefotech.jadibuti.data.toDto
import com.chefotech.jadibuti.data.toSchedule
import com.chefotech.jadibuti.domain.DoseAction
import com.chefotech.jadibuti.domain.DoseReconciler
import com.chefotech.jadibuti.domain.EventStatus
import com.chefotech.jadibuti.domain.ExistingDose
import com.chefotech.jadibuti.domain.ScheduleEngine
import com.chefotech.jadibuti.domain.ScheduledDose
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Materialises [ScheduleEngine] output into the local events table for a rolling window
 * (yesterday .. today + 7). Ids are deterministic so re-running is idempotent and never
 * creates duplicate doses; [DoseReconciler] decides what to do when the schedule changes
 * underneath rows that already exist.
 */
@Singleton
class EventGenerator @Inject constructor(
    private val events: EventDao,
    private val medicines: MedicineDao,
    private val session: SessionStore,
    private val outbox: Outbox,
) {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    private fun window(): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return today.minusDays(1) to today.plusDays(7)
    }

    suspend fun ensureWindow(familyId: String? = session.current.activeFamilyId) {
        familyId ?: return
        for (m in medicines.listActiveFamily(familyId)) refreshMedicine(m)
    }

    /** Re-derives the window for one medicine after its schedule changed (or on a fresh sync). */
    suspend fun refreshMedicine(medicine: MedicineEntity) {
        val (from, to) = window()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val expected = if (medicine.deleted || !medicine.active) emptyList() else ScheduleEngine.generate(medicine.toSchedule(), from, to, zone)
        // Deleted rows are included on purpose: a retired dose must be restorable under its own id.
        val existing = events.forMedicineBetweenAll(medicine.id, from.format(fmt), to.format(fmt)).associateBy { it.id }
        val existingDoses = existing.values.map { e ->
            ExistingDose(
                id = e.id, localDate = LocalDate.parse(e.localDate), time = LocalTime.parse(e.time), scheduledAt = Instant.ofEpochMilli(e.scheduledAt),
                doseAmount = e.doseAmount, doseUnit = e.doseUnit,
                status = runCatching { EventStatus.valueOf(e.status) }.getOrDefault(EventStatus.UPCOMING), deleted = e.deleted,
            )
        }

        val toInsert = mutableListOf<EventEntity>()
        for (action in DoseReconciler.reconcile(existingDoses, expected, Instant.ofEpochMilli(now))) {
            when (action) {
                is DoseAction.Insert -> toInsert += newEvent(medicine, action.slot, zone, now)
                is DoseAction.Retime -> {
                    val e = existing.getValue(action.existingId)
                    val pending = e.status !in SETTLED
                    val moved = e.copy(
                        localDate = action.slot.localDate.format(fmt), time = action.slot.time.format(TIME), zoneId = zone.id,
                        scheduledAt = action.slot.scheduledAt.toEpochMilli(),
                        // An acted-on dose keeps the amount that was actually recorded (and deducted from stock).
                        doseAmount = if (pending) action.slot.doseAmount else e.doseAmount,
                        doseUnit = if (pending) action.slot.doseUnit else e.doseUnit,
                        updatedAt = now,
                    )
                    if (moved.copy(updatedAt = e.updatedAt) != e) { events.upsert(moved); outbox.event(moved.toDto()) }
                }
                is DoseAction.Update -> {
                    val e = existing.getValue(action.existingId)
                    val upd = e.copy(doseAmount = action.slot.doseAmount, doseUnit = action.slot.doseUnit, scheduledAt = action.slot.scheduledAt.toEpochMilli(), zoneId = zone.id, updatedAt = now)
                    events.upsert(upd)
                    // A pure instant change (timezone) is local; an amount change is data other devices need.
                    if (action.slot.doseAmount != e.doseAmount || action.slot.doseUnit != e.doseUnit) outbox.event(upd.toDto())
                }
                is DoseAction.Restore -> {
                    val e = existing.getValue(action.existingId)
                    val back = e.copy(deleted = false, doseAmount = action.slot.doseAmount, doseUnit = action.slot.doseUnit, scheduledAt = action.slot.scheduledAt.toEpochMilli(), zoneId = zone.id, updatedAt = now)
                    events.upsert(back)
                    outbox.event(back.toDto())
                }
                is DoseAction.Retire -> {
                    val e = existing.getValue(action.existingId)
                    val gone = e.copy(deleted = true, updatedAt = now)
                    events.upsert(gone)
                    outbox.event(gone.toDto())
                }
            }
        }
        if (toInsert.isNotEmpty()) events.insertIgnore(toInsert)
    }

    private fun newEvent(medicine: MedicineEntity, d: ScheduledDose, zone: ZoneId, now: Long) = EventEntity(
        id = d.id, familyId = medicine.familyId, memberId = d.memberId, medicineId = d.medicineId,
        localDate = d.localDate.format(fmt), time = d.time.format(TIME), zoneId = zone.id, scheduledAt = d.scheduledAt.toEpochMilli(),
        doseAmount = d.doseAmount, doseUnit = d.doseUnit, status = "UPCOMING", actualAt = null, recordedByUserId = null,
        snoozedUntil = null, note = "", statusHistoryJson = "[]", updatedAt = now, version = 0, deleted = false,
    )

    /** After a timezone / clock change: recompute instants of pending doses from their local wall-clock time. */
    suspend fun recomputeZone() {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val pending = events.pendingBetweenAllFamilies(now - 2L * 24 * 3600_000, now + 10L * 24 * 3600_000)
        for (e in pending) {
            val instant = LocalDate.parse(e.localDate).atTime(LocalTime.parse(e.time)).atZone(zone).toInstant().toEpochMilli()
            if (instant != e.scheduledAt || e.zoneId != zone.id) events.upsert(e.copy(scheduledAt = instant, zoneId = zone.id))
        }
    }

    companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val SETTLED = setOf("TAKEN", "SKIPPED", "MISSED")
    }
}
