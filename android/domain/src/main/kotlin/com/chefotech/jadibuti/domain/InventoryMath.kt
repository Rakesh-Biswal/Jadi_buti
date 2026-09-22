package com.chefotech.jadibuti.domain

import kotlin.math.floor
import kotlin.math.max

/** Pure inventory arithmetic over the append-only ledger. Mirrors backend/services/inventory.js. */
object InventoryMath {
    fun currentStock(ledger: List<LedgerEntry>): Double = ledger.filter { !it.deleted }.sumOf { it.quantityDelta }

    /** Reference quantity for percentage thresholds: running stock right after the last INITIAL/REFILL/CORRECTION. */
    fun baseline(ledger: List<LedgerEntry>): Double {
        var running = 0.0
        var baseline = 0.0
        for (e in ledger.filter { !it.deleted }.sortedBy { it.createdAt }) {
            running += e.quantityDelta
            if (e.type == TransactionType.INITIAL || e.type == TransactionType.REFILL || e.type == TransactionType.CORRECTION) {
                baseline = max(running, 0.0)
            }
        }
        return baseline
    }

    fun dailyConsumption(schedule: MedicineSchedule): Double {
        val perDoseDay = schedule.doseTimes.sumOf { it.amount }
        val r = schedule.rule
        return when (r.frequency) {
            Frequency.DAILY -> perDoseDay
            Frequency.ALTERNATE_DAYS -> perDoseDay / 2
            Frequency.CUSTOM_INTERVAL -> perDoseDay / r.intervalDays.coerceAtLeast(1)
            Frequency.SPECIFIC_WEEKDAYS -> perDoseDay * r.weekdays.size / 7.0
            Frequency.WEEKLY -> perDoseDay * r.weekdays.size.coerceAtLeast(1) / 7.0
        }
    }

    fun isLowStock(settings: InventorySettings, stock: Double, baseline: Double): Boolean {
        if (!settings.track) return false
        return when (settings.lowStockType) {
            LowStockType.QUANTITY -> stock <= settings.lowStockValue
            LowStockType.PERCENT -> if (baseline <= 0) stock <= 0 else (stock / baseline) * 100 <= settings.lowStockValue
        }
    }

    fun summary(schedule: MedicineSchedule, settings: InventorySettings, ledger: List<LedgerEntry>): StockSummary {
        val stock = currentStock(ledger)
        val base = baseline(ledger)
        val perDay = dailyConsumption(schedule)
        val days = if (perDay > 0) floor(max(stock, 0.0) / perDay).toInt() else null
        return StockSummary(stock, base, perDay, days, isLowStock(settings, stock, base))
    }
}
