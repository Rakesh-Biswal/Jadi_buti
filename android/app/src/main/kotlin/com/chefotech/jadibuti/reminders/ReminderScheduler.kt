package com.chefotech.jadibuti.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import com.chefotech.jadibuti.data.local.EventDao
import com.chefotech.jadibuti.data.local.EventEntity
import com.chefotech.jadibuti.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules AlarmManager alarms for upcoming doses. Alarms carry only the event id; the
 * receiver re-reads the database when it fires, so stale or duplicate alarms are harmless.
 * Exact alarms are used when permitted; otherwise the app falls back to inexact alarms and
 * the UI tells the user how to enable exact reminders.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val events: EventDao,
    private val settings: SettingsStore,
) {
    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs get() = context.getSharedPreferences("jadibuti_alarms", Context.MODE_PRIVATE)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    private fun intent(type: String, eventId: String) = Intent(context, ReminderReceiver::class.java)
        .setAction("${ACTION_PREFIX}$type")
        .putExtra(EXTRA_EVENT_ID, eventId)
        .putExtra(EXTRA_TYPE, type)

    private fun requestCode(type: String, eventId: String) = "$type:$eventId".hashCode()

    private fun pending(type: String, eventId: String, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT) =
        PendingIntent.getBroadcast(context, requestCode(type, eventId), intent(type, eventId), flags or PendingIntent.FLAG_IMMUTABLE)

    suspend fun rescheduleAll() {
        val s = settings.current()
        val now = System.currentTimeMillis()
        val missedAfterMs = s.missedAfterMinutes * 60_000L
        val followUpMs = s.followUpMinutes * 60_000L

        cancelTracked()
        val pendingEvents = events.pendingBetweenAllFamilies(now - missedAfterMs - 24L * 3600_000, now + 2L * 24 * 3600_000)
        val planned = ArrayList<Triple<String, String, Long>>() // type, eventId, at
        for (e in pendingEvents) {
            val missedAt = e.scheduledAt + missedAfterMs
            val snoozing = e.status == "SNOOZED" && e.snoozedUntil != null && e.snoozedUntil > now
            when {
                snoozing -> planned += Triple(TYPE_REMIND, e.id, e.snoozedUntil!!)
                e.notifiedAt == null && missedAt > now -> planned += Triple(TYPE_REMIND, e.id, maxOf(e.scheduledAt, now + 1_000))
                e.notifiedAt != null && followUpMs > 0 && e.followUpNotifiedAt == null && missedAt > now ->
                    planned += Triple(TYPE_FOLLOW_UP, e.id, maxOf(e.notifiedAt + followUpMs, now + 1_000))
            }
            if (!snoozing) planned += Triple(TYPE_MISSED, e.id, maxOf(missedAt, now + 1_000))
        }
        val limited = planned.sortedBy { it.third }.take(MAX_ALARMS)
        val tracked = HashSet<String>()
        for ((type, id, at) in limited) {
            setAlarm(at, pending(type, id))
            tracked += "$type:$id"
        }
        prefs.edit { putStringSet(KEY_TRACKED, tracked) }
    }

    private fun setAlarm(at: Long, pi: PendingIntent) {
        try {
            if (canScheduleExact()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelTracked() {
        val tracked = prefs.getStringSet(KEY_TRACKED, emptySet()) ?: emptySet()
        for (key in tracked) {
            val type = key.substringBefore(':')
            val id = key.substringAfter(':')
            pending(type, id, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it); it.cancel() }
        }
        prefs.edit { remove(KEY_TRACKED) }
    }

    fun cancelForEvent(eventId: String) {
        for (type in listOf(TYPE_REMIND, TYPE_FOLLOW_UP, TYPE_MISSED)) {
            pending(type, eventId, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it); it.cancel() }
        }
    }

    /** For the reminder receiver: after a REMIND fires, plan its follow-up + missed alarms. */
    suspend fun scheduleAfterNotified(event: EventEntity) {
        val s = settings.current()
        val now = System.currentTimeMillis()
        val missedAt = event.scheduledAt + s.missedAfterMinutes * 60_000L
        if (s.followUpMinutes > 0 && event.followUpNotifiedAt == null) {
            val at = now + s.followUpMinutes * 60_000L
            if (at < missedAt) { setAlarm(at, pending(TYPE_FOLLOW_UP, event.id)); track("$TYPE_FOLLOW_UP:${event.id}") }
        }
        setAlarm(maxOf(missedAt, now + 1_000), pending(TYPE_MISSED, event.id))
        track("$TYPE_MISSED:${event.id}")
    }

    private fun track(key: String) {
        val set = HashSet(prefs.getStringSet(KEY_TRACKED, emptySet()) ?: emptySet())
        set += key
        prefs.edit { putStringSet(KEY_TRACKED, set) }
    }

    companion object {
        const val ACTION_PREFIX = "com.chefotech.jadibuti.REMINDER_"
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_TYPE = "type"
        const val TYPE_REMIND = "REMIND"
        const val TYPE_FOLLOW_UP = "FOLLOW_UP"
        const val TYPE_MISSED = "MISSED"
        private const val KEY_TRACKED = "tracked"
        private const val MAX_ALARMS = 90
    }
}
