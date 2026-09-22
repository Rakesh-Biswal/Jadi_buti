package com.chefotech.jadibuti.domain

import java.time.LocalTime

/**
 * Meal-based grouping for the dashboard (Breakfast / Lunch / Dinner / Bedtime).
 *
 * A dose time may carry an explicit meal chosen by the user; otherwise the slot is
 * inferred from the clock time. Food instructions (before/after/with food...) are shown
 * on the dose itself and are independent of the slot.
 */
enum class MealSlot(val label: String, val timeHint: String) {
    BREAKFAST("Breakfast", "Morning"),
    LUNCH("Lunch", "Midday"),
    DINNER("Dinner", "Evening"),
    BEDTIME("Bedtime", "Night");

    companion object {
        /** Breakfast 04:00–10:59, Lunch 11:00–15:59, Dinner 16:00–20:59, Bedtime 21:00–03:59. */
        fun infer(time: LocalTime): MealSlot = when (time.hour) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 16..20 -> DINNER
            else -> BEDTIME
        }

        fun forDose(explicit: String?, time: LocalTime): MealSlot =
            explicit?.let { e -> entries.firstOrNull { it.name == e } } ?: infer(time)

        /** Suggested default clock time when the user picks a meal first. */
        fun defaultTime(slot: MealSlot): LocalTime = when (slot) {
            BREAKFAST -> LocalTime.of(8, 0)
            LUNCH -> LocalTime.of(13, 0)
            DINNER -> LocalTime.of(20, 0)
            BEDTIME -> LocalTime.of(22, 0)
        }
    }
}
