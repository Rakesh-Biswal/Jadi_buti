package com.chefotech.jadibuti.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chefotech.jadibuti.MainActivity
import com.chefotech.jadibuti.R
import com.chefotech.jadibuti.data.local.EventWithDetails
import com.chefotech.jadibuti.data.local.MedicineEntity
import com.chefotech.jadibuti.data.prefs.ReminderSettings
import com.chefotech.jadibuti.data.prefs.VibrationPattern
import com.chefotech.jadibuti.domain.StockSummary
import com.chefotech.jadibuti.ui.format.foodLabel
import com.chefotech.jadibuti.ui.format.formatDose
import com.chefotech.jadibuti.ui.format.formatTime
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and posts all notifications; owns notification channels.
 *
 * Medication reminders are alarm-style: the sound and vibration are produced by [AlarmService]
 * / [AlarmPlayer] on the ALARM stream, so the reminder channels themselves are silent. This
 * avoids double-ringing and lets the user pick long, looping tones and preview them.
 */
@Singleton
class ReminderNotifier @Inject constructor(@ApplicationContext private val context: Context, private val player: AlarmPlayer) {
    private val nm get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notificationsEnabled(): Boolean = hasPermission() && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Android 14+: full-screen alarms need this to be allowed for the app. */
    fun canUseFullScreenIntent(): Boolean = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()

    // ---------- channels ----------

    fun ensureChannels(s: ReminderSettings) {
        val keep = setOf(s.reminderChannelId, s.overdueChannelId, CH_ALARM, CH_INVENTORY, CH_CAREGIVER)
        ensureAlarmChannel()
        nm.notificationChannels.filter { (it.id.startsWith("reminders_") || it.id.startsWith("alarm_") || it.id.startsWith("overdue_")) && it.id !in keep }
            .forEach { nm.deleteNotificationChannel(it.id) }
        nm.createNotificationChannel(
            NotificationChannel(s.reminderChannelId, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_reminders_desc)
                setSound(null, null) // sound comes from AlarmService (looping, alarm stream)
                applyVibration(this, s.effectiveVibration)
                setBypassDnd(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(s.overdueChannelId, context.getString(R.string.channel_overdue), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_overdue_desc)
                setSound(null, null)
                applyVibration(this, s.effectiveOverdueVibration)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        nm.createNotificationChannel(NotificationChannel(CH_INVENTORY, context.getString(R.string.channel_inventory), NotificationManager.IMPORTANCE_DEFAULT).apply { description = context.getString(R.string.channel_inventory_desc) })
        nm.createNotificationChannel(NotificationChannel(CH_CAREGIVER, context.getString(R.string.channel_caregiver), NotificationManager.IMPORTANCE_HIGH).apply { description = context.getString(R.string.channel_caregiver_desc) })
    }

    private fun applyVibration(ch: NotificationChannel, p: VibrationPattern) {
        val pattern = player.pattern(p)
        ch.enableVibration(pattern != null)
        if (pattern != null) ch.vibrationPattern = pattern
    }

    // ---------- reminders ----------

    fun notificationId(eventId: String) = eventId.hashCode()

    private fun alarmActivityIntent(eventIds: List<String>) = Intent(context, AlarmActivity::class.java)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putStringArrayListExtra(AlarmActivity.EXTRA_EVENT_IDS, ArrayList(eventIds))

    private fun doseLine(row: EventWithDetails): String {
        val e = row.event
        val strength = if (row.medicineStrength.isNotBlank()) " ${row.medicineStrength}" else ""
        return "${row.memberName}: ${row.medicineName}$strength — ${formatDose(e.doseAmount, e.doseUnit)}, ${foodLabel(row.foodInstruction).lowercase()}"
    }

    /** Fixed, silent channel for the ringing-alarm foreground notification (sound comes from AlarmService). */
    fun ensureAlarmChannel() {
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "Ringing medicine alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while a medicine alarm is ringing"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    /** Minimal notification used to enter the foreground immediately; replaced once doses are loaded. */
    fun buildAlarmPlaceholder(count: Int, overdue: Boolean): Notification =
        NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (overdue) "Medicine still not taken" else "Time to take medicine")
            .setContentText(if (count > 1) "$count doses are due" else "A dose is due")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    fun updateAlarmNotification(id: Int, rows: List<EventWithDetails>, overdue: Boolean) {
        if (hasPermission()) nm.notify(id, buildAlarmNotification(rows, overdue))
    }

    /**
     * The foreground notification for the ringing alarm: one card for all doses due now, with a
     * full-screen intent so the alarm screen appears over the lock screen.
     */
    fun buildAlarmNotification(rows: List<EventWithDetails>, overdue: Boolean): Notification {
        val ids = rows.map { it.event.id }
        val fullScreen = PendingIntent.getActivity(context, ("fs:" + ids.joinToString()).hashCode(), alarmActivityIntent(ids), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(context, 7001, Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_STOP_SOUND), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = when {
            overdue -> "Medicine still not taken"
            rows.size == 1 -> "Time to take medicine — ${rows.first().memberName}"
            else -> "Time to take medicine — ${rows.size} doses"
        }
        val lines = rows.joinToString("\n") { doseLine(it) }
        return NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (rows.size == 1) doseLine(rows.first()) else "Tap to see all doses")
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Open", fullScreen)
            .addAction(0, "Stop sound", stop)
            .setGroup(GROUP_REMINDERS)
            .build()
    }

    /** Per-dose notification with Taken / Snooze / Skip. Tapping opens the alarm screen for that dose. */
    fun showReminder(row: EventWithDetails, s: ReminderSettings, overdue: Boolean) {
        if (!hasPermission()) return
        val e = row.event
        val title = if (overdue) "${row.memberName}'s medicine is still due" else "${row.memberName}'s medicine is due"
        val text = "${row.medicineName}${if (row.medicineStrength.isNotBlank()) " ${row.medicineStrength}" else ""} — ${formatDose(e.doseAmount, e.doseUnit)} ${foodLabel(row.foodInstruction).lowercase()}"
        val big = "$text\nScheduled ${formatTime(e.time)}"
        val open = PendingIntent.getActivity(context, notificationId(e.id), alarmActivityIntent(listOf(e.id)), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, if (overdue) s.overdueChannelId else s.reminderChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setGroup(GROUP_REMINDERS)
            .setWhen(e.scheduledAt)
            .setShowWhen(true)
            .addAction(0, context.getString(R.string.action_taken), action(NotificationActionReceiver.ACTION_TAKEN, e.id))
            .addAction(0, context.getString(R.string.action_snooze), action(NotificationActionReceiver.ACTION_SNOOZE, e.id))
            .addAction(0, context.getString(R.string.action_skip), action(NotificationActionReceiver.ACTION_SKIP, e.id))
        nm.notify(notificationId(e.id), builder.build())
        nm.notify(
            GROUP_SUMMARY_ID,
            NotificationCompat.Builder(context, s.reminderChannelId).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Medicine reminders").setGroup(GROUP_REMINDERS).setGroupSummary(true).setOnlyAlertOnce(true).build(),
        )
    }

    private fun action(action: String, eventId: String): PendingIntent = PendingIntent.getBroadcast(
        context, "$action:$eventId".hashCode(),
        Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(ReminderScheduler.EXTRA_EVENT_ID, eventId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun cancel(eventId: String) = nm.cancel(notificationId(eventId))

    // ---------- inventory / caregiver ----------

    fun showLowStock(m: MedicineEntity, memberName: String, summary: StockSummary) {
        if (!hasPermission()) return
        val remaining = formatDose(summary.stock.coerceAtLeast(0.0), m.doseUnit)
        val days = summary.estimatedDaysLeft?.let { "Estimated supply: approximately $it day${if (it == 1) "" else "s"}." } ?: ""
        val text = "$remaining remaining. $days Consider arranging a refill."
        val open = PendingIntent.getActivity(context, ("stock:" + m.id).hashCode(), Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_STOCK, true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            ("stock:" + m.id).hashCode(),
            NotificationCompat.Builder(context, CH_INVENTORY).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${m.name} is running low${if (memberName.isNotBlank()) " ($memberName)" else ""}")
                .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_STATUS).build(),
        )
    }

    fun showCaregiverAlert(row: EventWithDetails) {
        if (!hasPermission()) return
        val e = row.event
        val text = "${row.medicineName} (${formatDose(e.doseAmount, e.doseUnit)}) scheduled ${formatTime(e.time)} was not marked as taken."
        val open = PendingIntent.getActivity(context, ("cg:" + e.id).hashCode(), Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_EVENT_ID, e.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            ("cg:" + e.id).hashCode(),
            NotificationCompat.Builder(context, CH_CAREGIVER).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${row.memberName} missed a dose").setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER).build(),
        )
    }

    companion object {
        const val CH_ALARM = "alarm_ringing_v1"
        const val CH_INVENTORY = "inventory_v1"
        const val CH_CAREGIVER = "caregiver_v1"
        const val GROUP_REMINDERS = "com.chefotech.jadibuti.REMINDERS"
        const val GROUP_SUMMARY_ID = 1_000_001
    }
}
