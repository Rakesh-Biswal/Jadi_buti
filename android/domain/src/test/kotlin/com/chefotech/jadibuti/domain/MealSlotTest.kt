package com.chefotech.jadibuti.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class MealSlotTest {
    @Test
    fun `slots are inferred from clock time`() {
        assertEquals(MealSlot.BREAKFAST, MealSlot.infer(LocalTime.of(4, 0)))
        assertEquals(MealSlot.BREAKFAST, MealSlot.infer(LocalTime.of(8, 0)))
        assertEquals(MealSlot.BREAKFAST, MealSlot.infer(LocalTime.of(10, 59)))
        assertEquals(MealSlot.LUNCH, MealSlot.infer(LocalTime.of(11, 0)))
        assertEquals(MealSlot.LUNCH, MealSlot.infer(LocalTime.of(14, 0)))
        assertEquals(MealSlot.DINNER, MealSlot.infer(LocalTime.of(16, 0)))
        assertEquals(MealSlot.DINNER, MealSlot.infer(LocalTime.of(20, 30)))
        assertEquals(MealSlot.BEDTIME, MealSlot.infer(LocalTime.of(21, 0)))
        assertEquals(MealSlot.BEDTIME, MealSlot.infer(LocalTime.of(0, 4)))
        assertEquals(MealSlot.BEDTIME, MealSlot.infer(LocalTime.of(3, 59)))
    }

    @Test
    fun `explicit meal wins over inferred, unknown falls back`() {
        assertEquals(MealSlot.DINNER, MealSlot.forDose("DINNER", LocalTime.of(8, 0)))
        assertEquals(MealSlot.BREAKFAST, MealSlot.forDose(null, LocalTime.of(8, 0)))
        assertEquals(MealSlot.LUNCH, MealSlot.forDose("BRUNCH", LocalTime.of(12, 0)))
    }

    @Test
    fun `a medicine with three doses lands in three slots`() {
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0))
        assertEquals(listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER), times.map { MealSlot.infer(it) })
    }
}
