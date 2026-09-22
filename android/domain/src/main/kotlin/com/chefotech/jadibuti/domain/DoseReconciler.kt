package com.chefotech.jadibuti.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** What the events table already holds for one medicine inside the generation window. */
data class ExistingDose(
    val id: String,
    val localDate: LocalDate,
    val time: LocalTime,
    val scheduledAt: Instant,
    val doseAmount: Double,
    val doseUnit: String,
    val status: EventStatus,
    val deleted: Boolean,
) {
    /** TAKEN / SKIPPED / MISSED are never regenerated or overwritten automatically. */
    val settled: Boolean get() = status.isTerminal || status == EventStatus.MISSED
}

sealed class DoseAction {
    /** A brand new dose the schedule now produces. */
    data class Insert(val slot: ScheduledDose) : DoseAction()

    /**
     * The schedule was edited and this dose moved to a different clock time on the same day.
     * The row keeps its id, status, history and inventory link; only its time changes.
     */
    data class Retime(val existingId: String, val slot: ScheduledDose) : DoseAction()

    /** Amount or instant changed for a dose that has not been acted on yet. */
    data class Update(val existingId: String, val slot: ScheduledDose) : DoseAction()

    /** A previously retired dose is back in the schedule. */
    data class Restore(val existingId: String, val slot: ScheduledDose) : DoseAction()

    /** The schedule no longer produces this pending dose (soft delete). */
    data class Retire(val existingId: String) : DoseAction()
}

/**
 * Reconciles the doses a schedule *now* produces with the doses already stored.
 *
 * Event ids are deterministic (`medicineId:date:HH:mm`), which is what keeps every device
 * and the server in agreement — but it also means that changing a dose from 08:00 to 08:30
 * produces a new id. Without this step the old row (possibly already TAKEN) stays and a new
 * 08:30 row appears next to it: the same dose twice, one of them marked taken.
 *
 * The rule: on any given day, doses whose time is no longer in the schedule ("orphans") are
 * paired, in clock order, with new slots on that day that have no row yet. A pair is treated
 * as the same dose that moved ([DoseAction.Retime]); it keeps its id and whatever the user
 * already recorded. Only slots left unpaired become new rows, and only unpaired *pending*
 * future orphans are retired. Pure and deterministic, so every device converges on the same
 * result from the same data.
 */
object DoseReconciler {
    fun reconcile(existing: List<ExistingDose>, expected: List<ScheduledDose>, now: Instant): List<DoseAction> {
        val expectedById = expected.associateBy { it.id }
        val existingById = existing.associateBy { it.id }
        val actions = mutableListOf<DoseAction>()
        val handled = HashSet<String>()
        val claimedSlots = HashSet<String>()

        // 1. Pair orphans with free slots on the same day, in clock order.
        val live = existing.filter { !it.deleted }
        val orphansByDate = live.filter { it.id !in expectedById }.groupBy { it.localDate }
        val freeByDate = expected.filter { it.id !in existingById }.groupBy { it.localDate }
        for ((date, orphans) in orphansByDate) {
            val free = freeByDate[date]?.sortedBy { it.time } ?: continue
            for ((orphan, slot) in orphans.sortedBy { it.time }.zip(free)) {
                actions += DoseAction.Retime(orphan.id, slot)
                handled += orphan.id
                claimedSlots += slot.id
            }
        }

        // 2. Whatever is still free is genuinely new.
        for (slot in expected) if (slot.id !in existingById && slot.id !in claimedSlots) actions += DoseAction.Insert(slot)

        // 3. Everything else follows the plain rules.
        for (e in existing) {
            if (e.id in handled) continue
            val slot = expectedById[e.id]
            when {
                slot == null && !e.deleted && !e.settled && e.scheduledAt.isAfter(now) -> { actions += DoseAction.Retire(e.id); handled += e.id }
                slot != null && e.deleted && slot.scheduledAt.isAfter(now) -> { actions += DoseAction.Restore(e.id, slot); handled += e.id }
                slot != null && !e.deleted && !e.settled &&
                    (slot.doseAmount != e.doseAmount || slot.doseUnit != e.doseUnit || slot.scheduledAt != e.scheduledAt) -> { actions += DoseAction.Update(e.id, slot); handled += e.id }
            }
        }

        // 4. Two live rows at the same wall-clock time, one already acted on (e.g. a retimed row that
        //    arrived from another device after this one had generated the slot): drop the pending one.
        val retimed = actions.filterIsInstance<DoseAction.Retime>().associate { it.existingId to it.slot }
        fun keyOf(e: ExistingDose): Pair<LocalDate, LocalTime> = retimed[e.id]?.let { it.localDate to it.time } ?: (e.localDate to e.time)
        val settledKeys = live.filter { it.settled }.map(::keyOf).toSet()
        val retired = actions.filterIsInstance<DoseAction.Retire>().map { it.existingId }.toSet()
        for (e in live) {
            if (e.settled || e.id in retired) continue
            if (keyOf(e) in settledKeys) actions += DoseAction.Retire(e.id)
        }
        return actions
    }
}
