package com.chefotech.jadibuti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class InventoryAndStatusTest {
    private val daily = MedicineSchedule(
        "m", "p", LocalDate.of(2026, 9, 21), null, ScheduleRule(Frequency.DAILY),
        listOf(DoseTime(LocalTime.of(8, 0), 1.0), DoseTime(LocalTime.of(20, 0), 2.0)), "tablet",
    )
    private fun e(type: TransactionType, q: Double, at: Long, deleted: Boolean = false) = LedgerEntry(type, q, at, deleted)

    @Test
    fun `stock is the ledger sum and ignores deleted entries`() {
        val l = listOf(e(TransactionType.INITIAL, 30.0, 1), e(TransactionType.DEDUCT_TAKEN, -1.0, 2), e(TransactionType.DEDUCT_TAKEN, -2.0, 3), e(TransactionType.DEDUCT_TAKEN, -9.0, 4, true))
        assertEquals(27.0, InventoryMath.currentStock(l), 0.0)
    }

    @Test
    fun `daily consumption per frequency`() {
        assertEquals(3.0, InventoryMath.dailyConsumption(daily), 0.0)
        assertEquals(1.5, InventoryMath.dailyConsumption(daily.copy(rule = ScheduleRule(Frequency.ALTERNATE_DAYS))), 0.0)
        assertEquals(1.0, InventoryMath.dailyConsumption(daily.copy(rule = ScheduleRule(Frequency.CUSTOM_INTERVAL, intervalDays = 3))), 0.0)
    }

    @Test
    fun `percent thresholds at 5 and 3 percent and quantity threshold`() {
        val base = listOf(e(TransactionType.INITIAL, 100.0, 1))
        fun at(n: Double) = base + e(TransactionType.DEDUCT_TAKEN, -(100.0 - n), 2)
        val five = InventorySettings(true, LowStockType.PERCENT, 5.0)
        assertFalse(InventoryMath.isLowStock(five, InventoryMath.currentStock(at(6.0)), InventoryMath.baseline(base)))
        assertTrue(InventoryMath.isLowStock(five, InventoryMath.currentStock(at(5.0)), InventoryMath.baseline(base)))
        val three = InventorySettings(true, LowStockType.PERCENT, 3.0)
        assertFalse(InventoryMath.isLowStock(three, 4.0, 100.0))
        assertTrue(InventoryMath.isLowStock(three, 3.0, 100.0))
        val qty = InventorySettings(true, LowStockType.QUANTITY, 7.0)
        assertFalse(InventoryMath.isLowStock(qty, 8.0, 30.0))
        assertTrue(InventoryMath.isLowStock(qty, 7.0, 30.0))
        assertFalse(InventoryMath.isLowStock(InventorySettings(track = false), 0.0, 100.0))
    }

    @Test
    fun `refill resets baseline and summary estimates days`() {
        val l = listOf(e(TransactionType.INITIAL, 30.0, 1), e(TransactionType.DEDUCT_TAKEN, -28.0, 2), e(TransactionType.REFILL, 60.0, 3))
        val s = InventoryMath.summary(daily, InventorySettings(), l)
        assertEquals(62.0, s.stock, 0.0)
        assertEquals(62.0, s.baseline, 0.0)
        assertEquals(20, s.estimatedDaysLeft)
        assertFalse(s.lowStock)
    }

    @Test
    fun `status resolution over time`() {
        val at = Instant.parse("2026-09-21T02:30:00Z")
        val missedAfter = Duration.ofHours(2)
        assertEquals(EventStatus.UPCOMING, StatusPolicy.resolve(EventStatus.UPCOMING, at, null, at.minusSeconds(60), missedAfter))
        assertEquals(EventStatus.DUE, StatusPolicy.resolve(EventStatus.UPCOMING, at, null, at.plusSeconds(60), missedAfter))
        assertEquals(EventStatus.SNOOZED, StatusPolicy.resolve(EventStatus.SNOOZED, at, at.plusSeconds(900), at.plusSeconds(60), missedAfter))
        assertEquals(EventStatus.DUE, StatusPolicy.resolve(EventStatus.SNOOZED, at, at.plusSeconds(900), at.plusSeconds(1000), missedAfter))
        assertEquals(EventStatus.MISSED, StatusPolicy.resolve(EventStatus.DUE, at, null, at.plus(Duration.ofHours(3)), missedAfter))
        assertEquals(EventStatus.TAKEN, StatusPolicy.resolve(EventStatus.TAKEN, at, null, at.plus(Duration.ofHours(30)), missedAfter))
        assertEquals(at, StatusPolicy.nextTransitionAt(EventStatus.UPCOMING, at, null, at.minusSeconds(5), missedAfter))
        assertEquals(at.plus(missedAfter), StatusPolicy.nextTransitionAt(EventStatus.DUE, at, null, at.plusSeconds(5), missedAfter))
        assertEquals(null, StatusPolicy.nextTransitionAt(EventStatus.TAKEN, at, null, at, missedAfter))
    }

    @Test
    fun `merge never downgrades a terminal state`() {
        val taken = EventState(EventStatus.TAKEN, 100, 100, null, "", "u1")
        val missed = EventState(EventStatus.MISSED, null, 900, null, "", null)
        assertEquals(taken, MergePolicy.merge(taken, missed))
        assertEquals(taken, MergePolicy.merge(missed, taken))
        val skippedLater = EventState(EventStatus.SKIPPED, 200, 200, null, "", "u2")
        assertEquals(skippedLater, MergePolicy.merge(taken, skippedLater))
        val snoozed = EventState(EventStatus.SNOOZED, null, 50, 500, "", null)
        val due = EventState(EventStatus.DUE, null, 60, null, "", null)
        assertEquals(snoozed, MergePolicy.merge(due, snoozed))
    }
}
