package com.chefotech.jadibuti.reminders

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.chefotech.jadibuti.data.local.EventDao
import com.chefotech.jadibuti.data.prefs.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that rings the medication alarm for one or more due doses.
 *
 * Started by [ReminderReceiver] when a reminder alarm fires (exact-alarm broadcasts are exempt
 * from background-start restrictions). It shows a full-screen-capable alarm notification, plays
 * the looping tone and vibration, and stops when every dose has been handled, when the user
 * dismisses the sound, or after the configured ring duration (then the silent notifications remain).
 */
@AndroidEntryPoint
class AlarmService : Service() {
    @Inject lateinit var player: AlarmPlayer
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var events: EventDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timeoutJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val ringing = LinkedHashSet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SOUND -> { stopRinging(); return START_NOT_STICKY }
            ACTION_EVENT_HANDLED -> {
                intent.getStringExtra(EXTRA_EVENT_ID)?.let { ringing.remove(it) }
                if (ringing.isEmpty()) stopRinging()
                return START_NOT_STICKY
            }
        }
        val ids = intent?.getStringArrayListExtra(EXTRA_EVENT_IDS).orEmpty()
        val overdue = intent?.getBooleanExtra(EXTRA_OVERDUE, false) == true
        // Android requires startForeground() promptly after startForegroundService(); do it before any I/O.
        val foregroundOk = try {
            notifier.ensureAlarmChannel()
            ServiceCompat.startForeground(this, ALARM_NOTIFICATION_ID, notifier.buildAlarmPlaceholder(ids.size, overdue), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
            true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground refused", e)
            false
        }
        if (ids.isEmpty()) { stopRinging(); return START_NOT_STICKY }
        ringing += ids
        scope.launch { ring(ids, overdue, foregroundOk) }
        return START_NOT_STICKY
    }

    private suspend fun ring(ids: List<String>, overdue: Boolean, foregroundOk: Boolean) {
        val s = settings.current()
        val rows = ids.mapNotNull { events.getWithDetails(it) }.filter { !it.event.deleted && it.event.status !in setOf("TAKEN", "SKIPPED", "MISSED") }
        if (rows.isEmpty()) { stopRinging(); return }
        notifier.ensureChannels(s)
        rows.forEach { notifier.showReminder(it, s, overdue) }
        if (!foregroundOk) { stopRinging(); return }
        notifier.updateAlarmNotification(ALARM_NOTIFICATION_ID, rows, overdue)
        acquireWakeLock()
        player.start(s, if (overdue) s.effectiveOverdueVibration else s.effectiveVibration)
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(s.ringSeconds.coerceIn(10, 600) * 1000L)
            stopRinging()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jadibuti:alarm").apply { acquire(10 * 60 * 1000L) }
    }

    private fun stopRinging() {
        timeoutJob?.cancel()
        player.stop()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        ringing.clear()
        // Keep the per-dose notifications (they carry the actions); drop only the foreground summary.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        timeoutJob?.cancel()
        player.stop()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ALARM_NOTIFICATION_ID = 1_000_002
        const val EXTRA_EVENT_IDS = "eventIds"
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_OVERDUE = "overdue"
        const val ACTION_STOP_SOUND = "com.chefotech.jadibuti.alarm.STOP_SOUND"
        const val ACTION_EVENT_HANDLED = "com.chefotech.jadibuti.alarm.EVENT_HANDLED"
        private const val TAG = "JadiButiAlarmService"

        fun start(context: Context, eventIds: List<String>, overdue: Boolean) {
            val intent = Intent(context, AlarmService::class.java).putStringArrayListExtra(EXTRA_EVENT_IDS, ArrayList(eventIds)).putExtra(EXTRA_OVERDUE, overdue)
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "could not start alarm service", e)
            }
        }

        fun stopSound(context: Context) = runCatching { context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP_SOUND)) }
        fun eventHandled(context: Context, eventId: String) = runCatching { context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_EVENT_HANDLED).putExtra(EXTRA_EVENT_ID, eventId)) }
    }
}
