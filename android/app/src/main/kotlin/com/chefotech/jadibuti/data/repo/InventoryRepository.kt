package com.chefotech.jadibuti.data.repo

import com.chefotech.jadibuti.data.inventorySettings
import com.chefotech.jadibuti.data.local.MedicineDao
import com.chefotech.jadibuti.data.local.MedicineEntity
import com.chefotech.jadibuti.data.local.MemberDao
import com.chefotech.jadibuti.data.local.TransactionDao
import com.chefotech.jadibuti.data.local.TransactionEntity
import com.chefotech.jadibuti.data.toLedger
import com.chefotech.jadibuti.data.toSchedule
import com.chefotech.jadibuti.domain.InventoryMath
import com.chefotech.jadibuti.domain.StockSummary
import com.chefotech.jadibuti.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class StockRow(val medicine: MedicineEntity, val memberName: String, val summary: StockSummary)

@Singleton
class InventoryRepository @Inject constructor(
    private val medicines: MedicineDao,
    private val members: MemberDao,
    private val transactions: TransactionDao,
    private val medicineRepo: MedicineRepository,
    private val syncScheduler: SyncScheduler,
) {
    fun observeStock(familyId: String): Flow<List<StockRow>> =
        combine(medicines.observe(familyId), members.observe(familyId), transactions.observeFamily(familyId)) { meds, mems, txs ->
            val byMed = txs.groupBy { it.medicineId }
            val names = mems.associate { it.id to it.name }
            meds.filter { it.active && it.trackInventory }.map { m ->
                StockRow(m, names[m.memberId] ?: "", InventoryMath.summary(m.toSchedule(), m.inventorySettings(), (byMed[m.id] ?: emptyList()).map { it.toLedger() }))
            }.sortedWith(compareByDescending<StockRow> { it.summary.lowStock }.thenBy { it.medicine.name })
        }

    fun observeLedger(medicineId: String): Flow<List<TransactionEntity>> = transactions.observeForMedicine(medicineId)

    suspend fun summary(medicine: MedicineEntity): StockSummary =
        InventoryMath.summary(medicine.toSchedule(), medicine.inventorySettings(), transactions.forMedicine(medicine.id).map { it.toLedger() })

    suspend fun refill(medicineId: String, quantity: Double, note: String = "Refill") {
        require(quantity > 0) { "Quantity must be positive" }
        medicineRepo.addTransaction(medicineId, "REFILL", quantity, null, note)
        clearLowStockFlagIfRecovered(medicineId)
        syncScheduler.requestSync()
    }

    /** Sets the stock to an exact counted quantity (manual correction). */
    suspend fun correctTo(medicineId: String, countedQuantity: Double, note: String = "Manual correction") {
        require(countedQuantity >= 0) { "Quantity cannot be negative" }
        val current = InventoryMath.currentStock(transactions.forMedicine(medicineId).map { it.toLedger() })
        val delta = countedQuantity - current
        if (delta == 0.0) return
        medicineRepo.addTransaction(medicineId, "CORRECTION", delta, null, note)
        clearLowStockFlagIfRecovered(medicineId)
        syncScheduler.requestSync()
    }

    private suspend fun clearLowStockFlagIfRecovered(medicineId: String) {
        val m = medicines.get(medicineId) ?: return
        val ledger = transactions.forMedicine(medicineId).map { it.toLedger() }
        if (!InventoryMath.isLowStock(m.inventorySettings(), InventoryMath.currentStock(ledger), InventoryMath.baseline(ledger))) {
            medicines.setLowStockAlertedAt(medicineId, null)
        }
    }
}
