package com.chefotech.jadibuti.data.repo

import com.chefotech.jadibuti.data.encodeHistory
import com.chefotech.jadibuti.data.history
import com.chefotech.jadibuti.data.inventorySettings
import com.chefotech.jadibuti.data.local.EventDao
import com.chefotech.jadibuti.data.local.EventEntity
import com.chefotech.jadibuti.data.local.EventWithDetails
import com.chefotech.jadibuti.data.local.MedicineDao
import com.chefotech.jadibuti.data.local.TransactionDao
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.prefs.SettingsStore
import com.chefotech.jadibuti.data.remote.StatusChangeDto
import com.chefotech.jadibuti.data.toDto
import com.chefotech.jadibuti.data.toLedger
import com.chefotech.jadibuti.domain.InventoryMath
import com.chefotech.jadibuti.reminders.ReminderScheduler
import com.chefotech.jadibuti.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/** Result of marking a dose taken, so the UI can warn about stock problems. */
data class TakenResult(val deducted: Double, val stockAfter: Double?, val insufficientStock: Boolean)

@Singleton
class EventRepository @Inject constructor(
    private val events: EventDao,
    private val medicines: MedicineDao,
    private val transactions: TransactionDao,
    private val medicineRepo: MedicineRepository,
    private val outbox: Outbox,
    private val session: SessionStore,
    private val settings: SettingsStore,
    private val reminders: ReminderScheduler,
    private val syncScheduler: SyncScheduler,
) {
    fun observeForDate(familyId: String, date: String): Flow<List<EventWithDetails>> = events.observeForDate(familyId, date)
    fun observeRange(familyId: String, from: String, to: String, memberId: String?, medicineId: String?): Flow<List<EventWithDetails>> =
        events.observeRange(familyId, from, to, memberId, medicineId)

    suspend fun get(id: String) = events.get(id)
    suspend fun getWithDetails(id: String) = events.getWithDetails(id)

    private fun EventEntity.withStatus(status: String, actualAt: Long?, snoozedUntil: Long?, byUser: String?, now: Long, note: String = this.note): EventEntity =
        copy(
            status = status,
            actualAt = actualAt,
            snoozedUntil = snoozedUntil,
            recordedByUserId = byUser ?: recordedByUserId,
            note = note,
            statusHistoryJson = encodeHistory(history() + StatusChangeDto(status, now, byUser, actualAt)),
            updatedAt = now,
        )

    /** Marks the dose taken and deducts stock exactly once (ledger id `deduct:<eventId>`). */
    suspend fun markTaken(eventId: String, actualAt: Long = System.currentTimeMillis()): TakenResult? {
        val event = events.get(eventId) ?: return null
        val now = System.currentTimeMillis()
        val byUser = session.current.userId
        val wasSkipped = event.status == "SKIPPED"
        val updated = event.withStatus("TAKEN", actualAt, null, byUser, now)
        events.upsert(updated)
        outbox.event(updated.toDto())

        var result = TakenResult(0.0, null, false)
        val medicine = medicines.get(event.medicineId)
        if (medicine != null && medicine.trackInventory) {
            val ledger = transactions.forMedicine(medicine.id)
            val stock = InventoryMath.currentStock(ledger.map { it.toLedger() })
            // If this dose was previously deducted (e.g. taken -> skipped -> taken), the reversal already restored stock;
            // a new deduction id keeps the ledger append-only and duplicate-safe.
            val alreadyDeducted = ledger.any { it.id == "deduct:$eventId" }
            val reversed = ledger.any { it.id == "reversal:$eventId" }
            val txId = if (alreadyDeducted && reversed) "deduct:$eventId:${wasSkipped.hashCode()}:$actualAt" else "deduct:$eventId"
            if (!alreadyDeducted || reversed) {
                val available = max(stock, 0.0)
                val deduct = min(event.doseAmount, available)
                val insufficient = available < event.doseAmount
                val note = if (insufficient) "Insufficient stock: recorded ${deduct} of ${event.doseAmount}" else "Dose taken"
                medicineRepo.addTransaction(medicine.id, "DEDUCT_TAKEN", -deduct, eventId, note, id = txId)
                result = TakenResult(deduct, stock - deduct, insufficient)
            } else {
                result = TakenResult(0.0, stock, false)
            }
        }
        reminders.cancelForEvent(eventId)
        reminders.rescheduleAll()
        syncScheduler.requestSync()
        return result
    }

    suspend fun markSkipped(eventId: String, note: String = "") {
        val event = events.get(eventId) ?: return
        val now = System.currentTimeMillis()
        val updated = event.withStatus("SKIPPED", now, null, session.current.userId, now, note)
        events.upsert(updated)
        outbox.event(updated.toDto())
        if (event.status == "TAKEN") {
            // Undo an earlier deduction (append-only reversal, never delete history).
            val ledger = transactions.forMedicine(event.medicineId)
            val deduction = ledger.firstOrNull { it.eventId == eventId && it.type == "DEDUCT_TAKEN" }
            if (deduction != null && ledger.none { it.id == "reversal:$eventId" }) {
                medicineRepo.addTransaction(event.medicineId, "REVERSAL", -deduction.quantityDelta, eventId, "Dose changed to skipped", id = "reversal:$eventId")
            }
        }
        reminders.cancelForEvent(eventId)
        reminders.rescheduleAll()
        syncScheduler.requestSync()
    }

    suspend fun snooze(eventId: String, minutes: Int? = null) {
        val event = events.get(eventId) ?: return
        if (event.status == "TAKEN" || event.status == "SKIPPED") return
        val now = System.currentTimeMillis()
        val mins = minutes ?: settings.current().snoozeMinutes
        val until = now + mins * 60_000L
        val updated = event.withStatus("SNOOZED", null, until, session.current.userId, now)
        events.upsert(updated)
        outbox.event(updated.toDto())
        reminders.cancelForEvent(eventId)
        reminders.rescheduleAll()
        syncScheduler.requestSync()
    }

    /** Persist MISSED for doses past the grace window (called by alarms / maintenance). */
    suspend fun markMissed(eventId: String): EventEntity? {
        val event = events.get(eventId) ?: return null
        if (event.status == "TAKEN" || event.status == "SKIPPED" || event.status == "MISSED") return event
        val now = System.currentTimeMillis()
        val updated = event.withStatus("MISSED", null, null, null, now)
        events.upsert(updated)
        outbox.event(updated.toDto())
        return updated
    }

    /** Local-only transition UPCOMING -> DUE when the reminder fires (not worth a sync round-trip). */
    suspend fun markDueLocally(eventId: String) {
        val event = events.get(eventId) ?: return
        if (event.status == "UPCOMING") events.upsert(event.copy(status = "DUE"))
    }

    suspend fun stockFor(medicineId: String): Double? {
        val m = medicines.get(medicineId) ?: return null
        if (!m.trackInventory) return null
        return InventoryMath.currentStock(transactions.forMedicine(medicineId).map { it.toLedger() })
    }

    suspend fun lowStockFor(medicineId: String): Boolean {
        val m = medicines.get(medicineId) ?: return false
        val ledger = transactions.forMedicine(medicineId).map { it.toLedger() }
        return InventoryMath.isLowStock(m.inventorySettings(), InventoryMath.currentStock(ledger), InventoryMath.baseline(ledger))
    }
}
