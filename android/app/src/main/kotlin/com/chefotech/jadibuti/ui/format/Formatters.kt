package com.chefotech.jadibuti.ui.format

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.NoFood
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.chefotech.jadibuti.domain.MealSlot
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val time12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val dateLong = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val dateShort = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dateHeading = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

fun formatTime(hhmm: String): String = runCatching { LocalTime.parse(hhmm).format(time12) }.getOrDefault(hhmm)
fun formatTime(t: LocalTime): String = t.format(time12)
fun formatDate(iso: String): String = runCatching { LocalDate.parse(iso).format(dateLong) }.getOrDefault(iso)
fun formatDateShort(iso: String): String = runCatching { LocalDate.parse(iso).format(dateShort) }.getOrDefault(iso)
fun formatDateHeading(d: LocalDate): String = when (d) {
    LocalDate.now() -> "Today, " + d.format(dateShort)
    LocalDate.now().minusDays(1) -> "Yesterday, " + d.format(dateShort)
    else -> d.format(dateHeading)
}

fun formatQuantity(amount: Double): String = if (amount == Math.floor(amount)) amount.toLong().toString() else String.format(Locale.ENGLISH, "%.2f", amount).trimEnd('0').trimEnd('.')

/** "1 tablet", "2 tablets", "5 ml" */
fun formatDose(amount: Double, unit: String): String {
    val q = formatQuantity(amount)
    val u = unit.trim().ifBlank { "dose" }
    val plural = when {
        amount == 1.0 -> u
        u.equals("ml", true) || u.equals("mg", true) || u.equals("g", true) || u.endsWith("s") -> u
        else -> u + "s"
    }
    return "$q $plural"
}

fun foodLabel(code: String): String = when (code) {
    "BEFORE_FOOD" -> "Before food"
    "AFTER_FOOD" -> "After food"
    "WITH_FOOD" -> "With food"
    "EMPTY_STOMACH" -> "Empty stomach"
    else -> "No food instruction"
}

fun foodIcon(code: String): ImageVector = when (code) {
    "EMPTY_STOMACH" -> Icons.Default.NoFood
    "NONE" -> Icons.Default.Medication
    else -> Icons.Default.Restaurant
}

fun typeLabel(code: String): String = when (code) {
    "TABLET" -> "Tablet"; "CAPSULE" -> "Capsule"; "SYRUP" -> "Syrup"; "LIQUID" -> "Liquid"
    "DROPS" -> "Drops"; "INJECTION" -> "Injection"; else -> "Other"
}

fun typeIcon(code: String): ImageVector = when (code) {
    "SYRUP", "LIQUID" -> Icons.Default.LocalDrink
    "DROPS" -> Icons.Default.WaterDrop
    "INJECTION" -> Icons.Default.Vaccines
    else -> Icons.Default.Medication
}

fun defaultUnitFor(type: String): String = when (type) {
    "TABLET" -> "tablet"; "CAPSULE" -> "capsule"; "SYRUP", "LIQUID" -> "ml"; "DROPS" -> "drop"; "INJECTION" -> "dose"; else -> "dose"
}

fun mealIcon(slot: MealSlot): ImageVector = when (slot) {
    MealSlot.BREAKFAST -> Icons.Default.WbSunny
    MealSlot.LUNCH -> Icons.Default.LunchDining
    MealSlot.DINNER -> Icons.Default.DinnerDining
    MealSlot.BEDTIME -> Icons.Default.Bedtime
}

private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
fun weekdayName(iso: Int) = dayNames.getOrElse(iso - 1) { "?" }

fun frequencyLabel(frequency: String, weekdays: List<Int>, intervalDays: Int): String = when (frequency) {
    "DAILY" -> "Every day"
    "ALTERNATE_DAYS" -> "Alternate days"
    "SPECIFIC_WEEKDAYS" -> weekdays.sorted().joinToString(", ") { weekdayName(it) }.ifBlank { "Specific days" }
    "WEEKLY" -> "Weekly on " + weekdays.sorted().joinToString(", ") { weekdayName(it) }
    "CUSTOM_INTERVAL" -> "Every $intervalDays days"
    else -> frequency
}

fun statusLabel(status: String): String = when (status) {
    "TAKEN" -> "Taken"; "MISSED" -> "Missed"; "SKIPPED" -> "Skipped"; "SNOOZED" -> "Snoozed"; "DUE" -> "Due now"; else -> "Upcoming"
}
