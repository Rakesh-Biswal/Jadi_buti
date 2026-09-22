package com.chefotech.jadibuti.domain

import java.time.Duration
import java.time.Instant

/**
 * Derives the *display* status of a dose from what was stored plus the current time.
 * Stored terminal decisions (TAKEN/SKIPPED) always win. MISSED is only persisted by the
 * maintenance job; here it is derived so the UI is correct even before that job runs.
 */
object StatusPolicy {
    fun resolve(stored: EventStatus, scheduledAt: Instant, snoozedUntil: Instant?, now: Instant, missedAfter: Duration): EventStatus {
        if (stored.isTerminal) return stored
        if (stored == EventStatus.MISSED) return EventStatus.MISSED
        if (now.isBefore(scheduledAt)) return EventStatus.UPCOMING
        if (snoozedUntil != null && now.isBefore(snoozedUntil)) return EventStatus.SNOOZED
        if (now.isAfter(scheduledAt.plus(missedAfter))) return EventStatus.MISSED
        return EventStatus.DUE
    }

    /** When the next automatic status change happens for a non-terminal dose (for alarms). */
    fun nextTransitionAt(stored: EventStatus, scheduledAt: Instant, snoozedUntil: Instant?, now: Instant, missedAfter: Duration): Instant? {
        if (stored.isTerminal || stored == EventStatus.MISSED) return null
        if (now.isBefore(scheduledAt)) return scheduledAt
        if (snoozedUntil != null && now.isBefore(snoozedUntil)) return snoozedUntil
        val missedAt = scheduledAt.plus(missedAfter)
        return if (now.isBefore(missedAt)) missedAt else null
    }
}

private val RANK = mapOf(
    EventStatus.UPCOMING to 0, EventStatus.DUE to 1, EventStatus.SNOOZED to 2,
    EventStatus.MISSED to 3, EventStatus.SKIPPED to 4, EventStatus.TAKEN to 5,
)

data class EventState(
    val status: EventStatus,
    val actualAt: Long?,
    val updatedAt: Long,
    val snoozedUntil: Long?,
    val note: String,
    val recordedByUserId: String?,
)

/** Same conflict rules as backend/services/sync.js mergeEvent, for reconciling pulled data locally. */
object MergePolicy {
    fun merge(local: EventState, remote: EventState): EventState {
        val lt = local.status.isTerminal
        val rt = remote.status.isTerminal
        return when {
            lt && !rt -> local
            rt && !lt -> remote
            lt && rt -> if ((remote.actualAt ?: remote.updatedAt) >= (local.actualAt ?: local.updatedAt)) remote else local
            else -> {
                val r = RANK.getValue(remote.status) - RANK.getValue(local.status)
                when {
                    r > 0 -> remote
                    r < 0 -> local
                    else -> if (remote.updatedAt >= local.updatedAt) remote else local
                }
            }
        }
    }
}
