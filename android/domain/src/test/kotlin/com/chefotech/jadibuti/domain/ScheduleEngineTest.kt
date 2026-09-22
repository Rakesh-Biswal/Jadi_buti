package com.chefotech.jadibuti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ScheduleEngineTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val monday = LocalDate.of(2026, 9, 21) // a Monday

    private fun schedule(
        frequency: Frequency,
        start: LocalDate = monday,
        end: LocalDate? = null,
        weekdays: Set<DayOfWeek> = emptySet(),
        interval: Int = 1,
        reference: LocalDate? = null,
        doses: List<DoseTime> = listOf(DoseTime(LocalTime.of(8, 0), 1.0)),
        active: Boolean = true,
    ) = MedicineSchedule("med1", "mem1", start, end, ScheduleRule(frequency, weekdays, interval, reference), doses, "tablet", active)

    @Test
    fun `daily generates one dose per day per time`() {
        val s = schedule(Frequency.DAILY, doses = listOf(DoseTime(LocalTime.of(8, 0), 1.0), DoseTime(LocalTime.of(20, 0), 2.0)))
        val doses = ScheduleEngine.generate(s, monday, monday.plusDays(6), zone)
        assertEquals(14, doses.size)
        assertEquals(1.0, doses[0].doseAmount, 0.0)
        assertEquals(2.0, doses[1].doseAmount, 0.0)
        assertEquals("med1:2026-09-21:08:00", doses[0].id)
    }

    @Test
    fun `alternate days is anchored on start date, not weekday names`() {
        val s = schedule(Frequency.ALTERNATE_DAYS)
        val days = (0..6).map { monday.plusDays(it.toLong()) }.filter { ScheduleEngine.occursOn(s, it) }
        assertEquals(listOf(monday, monday.plusDays(2), monday.plusDays(4), monday.plusDays(6)), days)
        // Same rule started on Tuesday shifts the whole cycle.
        val s2 = schedule(Frequency.ALTERNATE_DAYS, start = monday.plusDays(1))
        assertFalse(ScheduleEngine.occursOn(s2, monday.plusDays(2)))
        assertTrue(ScheduleEngine.occursOn(s2, monday.plusDays(3)))
    }

    @Test
    fun `alternate days keeps its cycle when regenerated from a later window`() {
        val s = schedule(Frequency.ALTERNATE_DAYS)
        val later = ScheduleEngine.generate(s, monday.plusDays(30), monday.plusDays(33), zone).map { it.localDate }
        assertEquals(listOf(monday.plusDays(30), monday.plusDays(32)), later)
    }

    @Test
    fun `specific weekdays and weekly`() {
        val s = schedule(Frequency.SPECIFIC_WEEKDAYS, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val days = ScheduleEngine.generate(s, monday, monday.plusDays(13), zone).map { it.localDate.dayOfWeek }.toSet()
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), days)
        assertEquals(4, ScheduleEngine.generate(s, monday, monday.plusDays(13), zone).size)
        val w = schedule(Frequency.WEEKLY, weekdays = setOf(DayOfWeek.SUNDAY))
        assertEquals(2, ScheduleEngine.generate(w, monday, monday.plusDays(13), zone).size)
    }

    @Test
    fun `custom interval every 3 days with explicit reference date`() {
        val s = schedule(Frequency.CUSTOM_INTERVAL, interval = 3, reference = monday.minusDays(1))
        val days = ScheduleEngine.generate(s, monday, monday.plusDays(9), zone).map { it.localDate }
        assertEquals(listOf(monday.plusDays(2), monday.plusDays(5), monday.plusDays(8)), days)
    }

    @Test
    fun `start and end dates bound the doses, ongoing has no end`() {
        val s = schedule(Frequency.DAILY, start = monday.plusDays(2), end = monday.plusDays(4))
        val days = ScheduleEngine.generate(s, monday, monday.plusDays(10), zone).map { it.localDate }
        assertEquals(listOf(monday.plusDays(2), monday.plusDays(3), monday.plusDays(4)), days)
        val ongoing = schedule(Frequency.DAILY)
        assertEquals(366, ScheduleEngine.generate(ongoing, monday, monday.plusDays(365), zone).size)
    }

    @Test
    fun `inactive schedules and empty windows produce nothing`() {
        assertTrue(ScheduleEngine.generate(schedule(Frequency.DAILY, active = false), monday, monday.plusDays(3), zone).isEmpty())
        assertTrue(ScheduleEngine.generate(schedule(Frequency.DAILY), monday.plusDays(3), monday, zone).isEmpty())
    }

    @Test
    fun `scheduled instant respects the zone`() {
        val s = schedule(Frequency.DAILY)
        val dose = ScheduleEngine.generate(s, monday, monday, zone).single()
        // 08:00 IST == 02:30 UTC
        assertEquals("2026-09-21T02:30:00Z", dose.scheduledAt.toString())
        val ny = ScheduleEngine.generate(s, monday, monday, ZoneId.of("America/New_York")).single()
        assertEquals("2026-09-21T12:00:00Z", ny.scheduledAt.toString())
    }

    @Test
    fun `DST gap resolves to a valid instant instead of crashing`() {
        val s = schedule(Frequency.DAILY, start = LocalDate.of(2026, 3, 8), doses = listOf(DoseTime(LocalTime.of(2, 30), 1.0)))
        val dose = ScheduleEngine.generate(s, LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 8), ZoneId.of("America/New_York")).single()
        // 02:30 does not exist on spring-forward day; java.time shifts forward by the gap.
        assertEquals("2026-03-08T07:30:00Z", dose.scheduledAt.toString())
    }

    @Test
    fun `ids are stable across regenerations and different windows`() {
        val s = schedule(Frequency.DAILY)
        val a = ScheduleEngine.generate(s, monday, monday.plusDays(7), zone).map { it.id }
        val b = ScheduleEngine.generate(s, monday.plusDays(3), monday.plusDays(10), zone).map { it.id }
        assertEquals(a.drop(3), b.take(5))
    }

    @Test
    fun `next dose date skips non-dose days and honours end date`() {
        val s = schedule(Frequency.ALTERNATE_DAYS, end = monday.plusDays(4))
        assertEquals(monday.plusDays(2), ScheduleEngine.nextDoseDate(s, monday))
        assertEquals(monday.plusDays(4), ScheduleEngine.nextDoseDate(s, monday.plusDays(2)))
        assertEquals(null, ScheduleEngine.nextDoseDate(s, monday.plusDays(4)))
    }
}
