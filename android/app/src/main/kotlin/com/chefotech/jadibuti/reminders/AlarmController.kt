package com.chefotech.jadibuti.reminders

import android.content.Context
import com.chefotech.jadibuti.data.repo.EventRepository
import com.chefotech.jadibuti.data.repo.TakenResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single place that resolves a dose from any surface (alarm screen, notification button,
 * dashboard): records the decision, clears that dose's notification, and tells the ringing
 * alarm service the dose is handled. Each dose is handled on its own, so several medicines
 * due at the same time never affect each other.
 */
@Singleton
class AlarmController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val events: EventRepository,
    private val notifier: ReminderNotifier,
) {
    suspend fun taken(eventId: String): TakenResult? {
        val r = events.markTaken(eventId)
        finish(eventId)
        return r
    }

    suspend fun snooze(eventId: String, minutes: Int? = null) {
        events.snooze(eventId, minutes)
        finish(eventId)
    }

    suspend fun skip(eventId: String) {
        events.markSkipped(eventId)
        finish(eventId)
    }

    fun stopSound() = AlarmService.stopSound(context)

    private fun finish(eventId: String) {
        notifier.cancel(eventId)
        AlarmService.eventHandled(context, eventId)
    }
}
