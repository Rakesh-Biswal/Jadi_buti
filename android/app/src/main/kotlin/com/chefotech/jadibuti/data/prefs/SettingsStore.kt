package com.chefotech.jadibuti.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Alarm tones: four original looping tones bundled in res/raw, the phone's alarm sound, or a user-picked sound. */
enum class AlarmTone(val label: String, val rawName: String?) {
    CLASSIC("Classic alarm", "alarm_classic"),
    CHIME("Bright chime", "alarm_chime"),
    GENTLE("Gentle rise", "alarm_gentle"),
    URGENT("Urgent", "alarm_urgent"),
    SYSTEM("Phone's alarm sound", null),
    CUSTOM("Choose a sound…", null),
}

enum class VibrationPattern(val label: String) { OFF("Off"), GENTLE("Gentle"), NORMAL("Normal"), STRONG("Strong") }

data class ReminderSettings(
    val tone: AlarmTone = AlarmTone.CLASSIC,
    val customToneUri: String? = null,
    val vibrationEnabled: Boolean = true,
    val vibration: VibrationPattern = VibrationPattern.NORMAL,
    val overdueVibration: VibrationPattern = VibrationPattern.STRONG,
    /** How long the alarm rings before it falls back to a silent notification (seconds). */
    val ringSeconds: Int = 60,
    val followUpMinutes: Int = 10, // 0 = off
    val missedAfterMinutes: Int = 120,
    val caregiverAlerts: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val snoozeMinutes: Int = 10,
    val onboardingDone: Boolean = false,
) {
    /** Notification channels are immutable once created; the id encodes the vibration choice. */
    val reminderChannelId: String get() = "alarm_${if (vibrationEnabled) vibration.name.lowercase() else "off"}_v2"
    val overdueChannelId: String get() = "overdue_${if (vibrationEnabled) overdueVibration.name.lowercase() else "off"}_v2"
    val effectiveVibration: VibrationPattern get() = if (vibrationEnabled) vibration else VibrationPattern.OFF
    val effectiveOverdueVibration: VibrationPattern get() = if (vibrationEnabled) overdueVibration else VibrationPattern.OFF
}

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val tone = stringPreferencesKey("alarm_tone")
        val customToneUri = stringPreferencesKey("custom_tone_uri")
        val vibrationEnabled = booleanPreferencesKey("vibration_enabled")
        val vibration = stringPreferencesKey("vibration")
        val overdueVibration = stringPreferencesKey("overdue_vibration")
        val ringSeconds = intPreferencesKey("ring_seconds")
        val followUp = intPreferencesKey("follow_up_minutes")
        val missedAfter = intPreferencesKey("missed_after_minutes")
        val caregiverAlerts = booleanPreferencesKey("caregiver_alerts")
        val haptics = booleanPreferencesKey("haptics")
        val snooze = intPreferencesKey("snooze_minutes")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<ReminderSettings> = context.settingsDataStore.data.map { p ->
        ReminderSettings(
            tone = p[Keys.tone]?.let { runCatching { AlarmTone.valueOf(it) }.getOrNull() } ?: AlarmTone.CLASSIC,
            customToneUri = p[Keys.customToneUri],
            vibrationEnabled = p[Keys.vibrationEnabled] ?: true,
            vibration = p[Keys.vibration]?.let { runCatching { VibrationPattern.valueOf(it) }.getOrNull() } ?: VibrationPattern.NORMAL,
            overdueVibration = p[Keys.overdueVibration]?.let { runCatching { VibrationPattern.valueOf(it) }.getOrNull() } ?: VibrationPattern.STRONG,
            ringSeconds = p[Keys.ringSeconds] ?: 60,
            followUpMinutes = p[Keys.followUp] ?: 10,
            missedAfterMinutes = p[Keys.missedAfter] ?: 120,
            caregiverAlerts = p[Keys.caregiverAlerts] ?: false,
            hapticsEnabled = p[Keys.haptics] ?: true,
            snoozeMinutes = p[Keys.snooze] ?: 10,
            onboardingDone = p[Keys.onboarding] ?: false,
        )
    }

    suspend fun current(): ReminderSettings = settings.first()

    suspend fun update(transform: (ReminderSettings) -> ReminderSettings) {
        val next = transform(current())
        context.settingsDataStore.edit { p ->
            p[Keys.tone] = next.tone.name
            if (next.customToneUri == null) p.remove(Keys.customToneUri) else p[Keys.customToneUri] = next.customToneUri
            p[Keys.vibrationEnabled] = next.vibrationEnabled
            p[Keys.vibration] = next.vibration.name
            p[Keys.overdueVibration] = next.overdueVibration.name
            p[Keys.ringSeconds] = next.ringSeconds
            p[Keys.followUp] = next.followUpMinutes
            p[Keys.missedAfter] = next.missedAfterMinutes
            p[Keys.caregiverAlerts] = next.caregiverAlerts
            p[Keys.haptics] = next.hapticsEnabled
            p[Keys.snooze] = next.snoozeMinutes
            p[Keys.onboarding] = next.onboardingDone
        }
    }
}
