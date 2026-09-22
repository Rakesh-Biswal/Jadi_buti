package com.chefotech.jadibuti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DoseReconcilerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 9, 22)
    private val now: Instant = today.atTime(22, 0).atZone(zone).toInstant()

    private fun schedule(vararg times: LocalTime) = MedicineSchedule(
        "med1", "mem1", today.minusDays(5), null, ScheduleRule(Frequency.DAILY), times.map { DoseTime(it, 1.0) }, "tablet", true,
    )

    private fun expected(vararg times: LocalTime) = ScheduleEngine.generate(schedule(*times), today.minusDays(1), today.plusDays(7), zone)

    private fun existing(date: LocalDate, time: LocalTime, status: EventStatus, deleted: Boolean = false, amount: Double = 1.0) = ExistingDose(
        id = "med1:$date:${"%02d:%02d".format(time.hour, time.minute)}", localDate = date, time = time,
        scheduledAt = date.atTime(time).atZone(zone).toInstant(), doseAmount = amount, doseUnit = "tablet", status = status, deleted = deleted,
    )

    private val t0800 = LocalTime.of(8, 0)
    private val t0830 = LocalTime.of(8, 30)
    private val t2000 = LocalTime.of(20, 0)

    @Test
    fun `changing a dose time moves the taken dose instead of adding a second one`() {
        // Yesterday..today generated at 08:00; today's dose was taken. User edits the time to 08:30.
        val stored = listOf(existing(today.minusDays(1), t0800, EventStatus.TAKEN), existing(today, t0800, EventStatus.TAKEN)) +
            (1..7).map { existing(today.plusDays(it.toLong()), t0800, EventStatus.UPCOMING) }
        val actions = DoseReconciler.reconcile(stored, expected(t0830), now)

        assertTrue("nothing should be inserted for days that already had a dose", actions.none { it is DoseAction.Insert })
        assertTrue("nothing should be retired", actions.none { it is DoseAction.Retire })
        val retimes = actions.filterIsInstance<DoseAction.Retime>()
        assertEquals(9, retimes.size)
        val todays = retimes.single { it.existingId == "med1:$today:08:00" }
        assertEquals(t0830, todays.slot.time)
        assertEquals("med1:$today:08:30", todays.slot.id)
    }

    @Test
    fun `adding a second dose time only inserts the new slots`() {
        val stored = listOf(existing(today.minusDays(1), t0800, EventStatus.TAKEN), existing(today, t0800, EventStatus.TAKEN)) +
            (1..7).map { existing(today.plusDays(it.toLong()), t0800, EventStatus.UPCOMING) }
        val actions = DoseReconciler.reconcile(stored, expected(t0800, t2000), now)
        assertTrue(actions.none { it is DoseAction.Retime || it is DoseAction.Retire })
        val inserted = actions.filterIsInstance<DoseAction.Insert>().map { it.slot.time }.toSet()
        assertEquals(setOf(t2000), inserted)
        assertEquals(9, actions.count { it is DoseAction.Insert }) // yesterday..+7 at 20:00
    }

    @Test
    fun `removing a dose time retires future pending doses but keeps history`() {
        val stored = listOf(existing(today, t0800, EventStatus.TAKEN), existing(today, t2000, EventStatus.TAKEN)) +
            (1..3).flatMap { d -> listOf(existing(today.plusDays(d.toLong()), t0800, EventStatus.UPCOMING), existing(today.plusDays(d.toLong()), t2000, EventStatus.UPCOMING)) }
        val actions = DoseReconciler.reconcile(stored, expected(t0800), now)
        val retired = actions.filterIsInstance<DoseAction.Retire>().map { it.existingId }.toSet()
        assertEquals((1..3).map { "med1:${today.plusDays(it.toLong())}:20:00" }.toSet(), retired)
        assertTrue("today's taken 20:00 dose stays as history", "med1:$today:20:00" !in retired)
        assertTrue(actions.none { it is DoseAction.Retime })
    }

    @Test
    fun `a retimed row from another device wins over a locally generated pending duplicate`() {
        // Another phone already moved today's taken dose to 08:30 (id still :08:00). This phone had
        // generated a fresh pending :08:30 row before the sync arrived.
        val movedTaken = existing(today, t0800, EventStatus.TAKEN).copy(time = t0830, scheduledAt = today.atTime(t0830).atZone(zone).toInstant())
        val localPending = existing(today, t0830, EventStatus.UPCOMING)
        val actions = DoseReconciler.reconcile(listOf(movedTaken, localPending), expected(t0830).filter { it.localDate == today }, now)
        assertEquals(listOf(DoseAction.Retire(localPending.id)), actions)
    }

    @Test
    fun `reconciling twice is a no-op`() {
        val stored = listOf(existing(today, t0800, EventStatus.TAKEN))
        val first = DoseReconciler.reconcile(stored, expected(t0830).filter { it.localDate == today }, now)
        val moved = first.filterIsInstance<DoseAction.Retime>().single()
        val after = stored.map { it.copy(time = moved.slot.time, scheduledAt = moved.slot.scheduledAt) }
        val second = DoseReconciler.reconcile(after, expected(t0830).filter { it.localDate == today }, now)
        // The only action is the same idempotent retime; the caller skips it because nothing changes.
        assertEquals(1, second.size)
        assertTrue(second.single() is DoseAction.Retime)
    }

    @Test
    fun `retired dose returns when the schedule brings it back`() {
        val tomorrow = today.plusDays(1)
        val stored = listOf(existing(tomorrow, t0800, EventStatus.UPCOMING, deleted = true))
        val actions = DoseReconciler.reconcile(stored, expected(t0800).filter { it.localDate == tomorrow }, now)
        assertEquals(1, actions.size)
        assertTrue(actions.single() is DoseAction.Restore)
    }
}
