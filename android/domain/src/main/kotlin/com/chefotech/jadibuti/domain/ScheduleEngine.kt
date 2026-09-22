package com.chefotech.jadibuti.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Deterministic medication scheduling engine.
 *
 * Medicine configuration -> schedule rule -> concrete [ScheduledDose]s.
 * Event ids are `medicineId:yyyy-MM-dd:HH:mm`, so every device and the server
 * derive the same identity for the same dose without coordination, which is what
 * prevents duplicate reminders and duplicate inventory deductions.
 */
object ScheduleEngine {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun eventId(medicineId: String, date: LocalDate, time: LocalTime): String = "$medicineId:$date:${time.format(TIME)}"

    /** Does this schedule have doses on [date]? (Ignores [MedicineSchedule.active].) */
    fun occursOn(schedule: MedicineSchedule, date: LocalDate): Boolean {
        if (date.isBefore(schedule.startDate)) return false
        schedule.endDate?.let { if (date.isAfter(it)) return false }
        val rule = schedule.rule
        return when (rule.frequency) {
            Frequency.DAILY -> true
            Frequency.ALTERNATE_DAYS -> daysSinceAnchor(schedule, date) % 2 == 0L
            Frequency.CUSTOM_INTERVAL -> daysSinceAnchor(schedule, date) % rule.intervalDays.coerceAtLeast(1) == 0L
            Frequency.SPECIFIC_WEEKDAYS, Frequency.WEEKLY -> date.dayOfWeek in rule.weekdays
        }
    }

    private fun daysSinceAnchor(schedule: MedicineSchedule, date: LocalDate): Long {
        val anchor = schedule.rule.referenceDate ?: schedule.startDate
        val diff = ChronoUnit.DAYS.between(anchor, date)
        // Normalise negative remainders so dates before the anchor still fall on the cycle.
        val interval = if (schedule.rule.frequency == Frequency.ALTERNATE_DAYS) 2 else schedule.rule.intervalDays.coerceAtLeast(1)
        return ((diff % interval) + interval) % interval
    }

    /**
     * All doses in [from, to] (inclusive) for one schedule. Times are resolved in [zone]
     * with java.time rules, so DST gaps/overlaps and timezone changes are handled by
     * regenerating from local wall-clock time rather than from stored UTC instants.
     */
    fun generate(schedule: MedicineSchedule, from: LocalDate, to: LocalDate, zone: ZoneId): List<ScheduledDose> {
        if (!schedule.active || schedule.doseTimes.isEmpty() || to.isBefore(from)) return emptyList()
        val start = maxOf(from, schedule.startDate)
        val end = schedule.endDate?.let { minOf(to, it) } ?: to
        if (end.isBefore(start)) return emptyList()
        val out = ArrayList<ScheduledDose>()
        var date = start
        while (!date.isAfter(end)) {
            if (occursOn(schedule, date)) {
                for (dose in schedule.doseTimes.sortedBy { it.time }) {
                    out += ScheduledDose(
                        id = eventId(schedule.medicineId, date, dose.time),
                        medicineId = schedule.medicineId,
                        memberId = schedule.memberId,
                        localDate = date,
                        time = dose.time,
                        scheduledAt = date.atTime(dose.time).atZone(zone).toInstant(),
                        doseAmount = dose.amount,
                        doseUnit = schedule.doseUnit,
                    )
                }
            }
            date = date.plusDays(1)
        }
        return out
    }

    /** Doses across many schedules, sorted by time. */
    fun generateAll(schedules: Collection<MedicineSchedule>, from: LocalDate, to: LocalDate, zone: ZoneId): List<ScheduledDose> =
        schedules.flatMap { generate(it, from, to, zone) }.sortedWith(compareBy({ it.scheduledAt }, { it.medicineId }))

    /** Next date with a dose strictly after [after], within [lookaheadDays]; null when none. */
    fun nextDoseDate(schedule: MedicineSchedule, after: LocalDate, lookaheadDays: Int = 400): LocalDate? {
        var d = after.plusDays(1)
        repeat(lookaheadDays) {
            if (schedule.endDate != null && d.isAfter(schedule.endDate)) return null
            if (occursOn(schedule, d)) return d
            d = d.plusDays(1)
        }
        return null
    }
}
