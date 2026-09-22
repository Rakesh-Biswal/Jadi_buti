package com.chefotech.jadibuti.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class Frequency { DAILY, ALTERNATE_DAYS, SPECIFIC_WEEKDAYS, WEEKLY, CUSTOM_INTERVAL }

enum class FoodInstruction { BEFORE_FOOD, AFTER_FOOD, WITH_FOOD, EMPTY_STOMACH, NONE }

enum class MedicineType { TABLET, CAPSULE, SYRUP, LIQUID, DROPS, INJECTION, OTHER }

enum class EventStatus {
    UPCOMING, DUE, TAKEN, MISSED, SKIPPED, SNOOZED;

    /** Terminal states are user decisions and are never overwritten automatically. */
    val isTerminal: Boolean get() = this == TAKEN || this == SKIPPED
}

enum class LowStockType { PERCENT, QUANTITY }

enum class TransactionType { INITIAL, DEDUCT_TAKEN, REVERSAL, REFILL, CORRECTION }

/** A dose at a clock time, e.g. 08:00 -> 1 tablet, 20:00 -> 2 tablets. */
data class DoseTime(val time: LocalTime, val amount: Double)

/**
 * Recurrence rule. For interval-based frequencies (ALTERNATE_DAYS, CUSTOM_INTERVAL) the
 * cycle is anchored on [referenceDate] (defaults to the medicine start date), never on
 * weekday names.
 */
data class ScheduleRule(
    val frequency: Frequency,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val intervalDays: Int = 1,
    val referenceDate: LocalDate? = null,
)

/** Everything the engine needs to know about one medicine's schedule. */
data class MedicineSchedule(
    val medicineId: String,
    val memberId: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val rule: ScheduleRule,
    val doseTimes: List<DoseTime>,
    val doseUnit: String,
    val active: Boolean = true,
)

/** One concrete dose occurrence produced by the engine. */
data class ScheduledDose(
    val id: String,
    val medicineId: String,
    val memberId: String,
    val localDate: LocalDate,
    val time: LocalTime,
    val scheduledAt: Instant,
    val doseAmount: Double,
    val doseUnit: String,
)

/** Minimal inventory ledger entry used by [InventoryMath]. */
data class LedgerEntry(
    val type: TransactionType,
    val quantityDelta: Double,
    val createdAt: Long,
    val deleted: Boolean = false,
)

data class InventorySettings(
    val track: Boolean = true,
    val lowStockType: LowStockType = LowStockType.PERCENT,
    val lowStockValue: Double = 5.0,
)

data class StockSummary(
    val stock: Double,
    val baseline: Double,
    val dailyConsumption: Double,
    val estimatedDaysLeft: Int?,
    val lowStock: Boolean,
)
