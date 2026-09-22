package com.chefotech.jadibuti.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chefotech.jadibuti.data.local.EventDao
import com.chefotech.jadibuti.data.prefs.SettingsStore
import com.chefotech.jadibuti.data.repo.EventRepository
import com.chefotech.jadibuti.sync.SyncScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun events(): EventDao
    fun eventRepository(): EventRepository
    fun notifier(): ReminderNotifier
    fun scheduler(): ReminderScheduler
    fun settings(): SettingsStore
    fun syncScheduler(): SyncScheduler
    fun controller(): AlarmController
}

private fun entry(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)

/** Runs suspend work inside a receiver, keeping the process alive until it completes (max ~9s). */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val result = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            withTimeoutOrNull(9_000) { block() }
        } catch (t: Throwable) {
            Log.e("JadiButiReminder", "receiver failed", t)
        } finally {
            result.finish()
        }
    }
}

/**
 * Fires for REMIND / FOLLOW_UP / MISSED alarms. Always re-validates against the database.
 * A REMIND gathers every dose due within the same minute so simultaneous medicines ring
 * together as one alarm (no alarm storm) while staying individually actionable.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) ?: return
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE) ?: return
        val ep = entry(context)
        async {
            val row = ep.events().getWithDetails(eventId) ?: return@async
            val e = row.event
            val terminal = e.status == "TAKEN" || e.status == "SKIPPED" || e.status == "MISSED"
            if (e.deleted || terminal) { ep.notifier().cancel(eventId); return@async }
            val settings = ep.settings().current()
            val now = System.currentTimeMillis()
            when (type) {
                ReminderScheduler.TYPE_REMIND -> {
                    if (e.status == "SNOOZED" && e.snoozedUntil != null && e.snoozedUntil > now + 5_000) return@async // snoozed again meanwhile
                    // Everything due in the same window that has not been announced yet rings with this dose.
                    val batch = ep.events().pendingBetweenAllFamilies(now - 60_000, now + 60_000)
                        .filter { it.id == eventId || (it.notifiedAt == null && (it.snoozedUntil == null || it.snoozedUntil <= now)) }
                        .map { it.id }.distinct()
                    ep.notifier().ensureChannels(settings)
                    for (id in batch) {
                        ep.eventRepository().markDueLocally(id)
                        ep.events().setNotifiedAt(id, now)
                        ep.events().get(id)?.let { ep.scheduler().scheduleAfterNotified(it.copy(notifiedAt = now)) }
                    }
                    AlarmService.start(context, batch, overdue = false)
                }
                ReminderScheduler.TYPE_FOLLOW_UP -> {
                    if (e.status == "SNOOZED" && e.snoozedUntil != null && e.snoozedUntil > now) return@async
                    ep.notifier().ensureChannels(settings)
                    ep.events().setFollowUpNotifiedAt(eventId, now)
                    AlarmService.start(context, listOf(eventId), overdue = true)
                }
                ReminderScheduler.TYPE_MISSED -> {
                    if (e.status == "SNOOZED" && e.snoozedUntil != null && e.snoozedUntil > now) { ep.scheduler().rescheduleAll(); return@async }
                    ep.eventRepository().markMissed(eventId)
                    ep.notifier().cancel(eventId)
                    AlarmService.eventHandled(context, eventId)
                    ep.syncScheduler().requestSync()
                }
            }
        }
    }
}

/** Handles the Taken / Snooze / Skip buttons on a reminder notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) ?: return
        val ep = entry(context)
        async {
            when (intent.action) {
                ACTION_TAKEN -> ep.controller().taken(eventId)
                ACTION_SNOOZE -> ep.controller().snooze(eventId)
                ACTION_SKIP -> ep.controller().skip(eventId)
            }
        }
    }

    companion object {
        const val ACTION_TAKEN = "com.chefotech.jadibuti.action.TAKEN"
        const val ACTION_SNOOZE = "com.chefotech.jadibuti.action.SNOOZE"
        const val ACTION_SKIP = "com.chefotech.jadibuti.action.SKIP"
    }
}

/** Reboot, app update, time/zone change, exact-alarm permission change: rebuild everything from the database. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val zoneChanged = intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_DATE_CHANGED
        entry(context).syncScheduler().requestMaintenance(recomputeZone = zoneChanged)
    }
}
