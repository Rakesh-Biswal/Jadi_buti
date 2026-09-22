package com.chefotech.jadibuti.reminders

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.chefotech.jadibuti.data.prefs.AlarmTone
import com.chefotech.jadibuti.data.prefs.ReminderSettings
import com.chefotech.jadibuti.data.prefs.VibrationPattern
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the medication alarm: a looping tone on the ALARM audio stream (so it follows the
 * phone's alarm volume and, like a clock alarm, is not muted by the ringer) plus a repeating
 * vibration. One instance is shared by the alarm service and the settings preview.
 */
@Singleton
class AlarmPlayer @Inject constructor(@ApplicationContext private val context: Context) {
    private var player: MediaPlayer? = null
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    val isPlaying: Boolean get() = player?.isPlaying == true

    fun toneUri(tone: AlarmTone, customUri: String?): Uri = when {
        tone.rawName != null -> Uri.parse("android.resource://${context.packageName}/raw/${tone.rawName}")
        tone == AlarmTone.CUSTOM && customUri != null -> Uri.parse(customUri)
        else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    fun pattern(p: VibrationPattern): LongArray? = when (p) {
        VibrationPattern.OFF -> null
        VibrationPattern.GENTLE -> longArrayOf(0, 200, 800, 200, 800)
        VibrationPattern.NORMAL -> longArrayOf(0, 500, 500, 500, 1000)
        VibrationPattern.STRONG -> longArrayOf(0, 800, 300, 800, 300, 800, 1200)
    }

    /** Start (or restart) the alarm sound and vibration. Safe to call repeatedly. */
    fun start(settings: ReminderSettings, vibration: VibrationPattern = settings.effectiveVibration, loop: Boolean = true) {
        stop()
        val uri = toneUri(settings.tone, settings.customToneUri)
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(context, uri)
                isLooping = loop
                setOnErrorListener { _, what, extra -> Log.w(TAG, "player error $what/$extra"); false }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not play $uri, falling back to system alarm", e)
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    isLooping = loop
                    prepare()
                    start()
                }
            }.getOrNull()
        }
        vibrate(vibration, repeat = loop)
    }

    fun vibrate(p: VibrationPattern, repeat: Boolean) {
        val pattern = pattern(p) ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            val attrs = if (Build.VERSION.SDK_INT >= 33) android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_ALARM) else null
            val effect = VibrationEffect.createWaveform(pattern, if (repeat) 0 else -1)
            if (attrs != null) vibrator.vibrate(effect, attrs) else vibrator.vibrate(effect)
        }
    }

    /** Short single buzz for previews and haptic confirmation. */
    fun preview(settings: ReminderSettings) = start(settings, loop = false)

    fun stop() {
        runCatching { player?.let { if (it.isPlaying) it.stop(); it.release() } }
        player = null
        runCatching { vibrator.cancel() }
    }

    private companion object { const val TAG = "JadiButiAlarm" }
}
