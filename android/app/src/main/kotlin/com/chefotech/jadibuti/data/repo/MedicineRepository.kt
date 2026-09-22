package com.chefotech.jadibuti.data.repo

import com.chefotech.jadibuti.data.local.MedicineDao
import com.chefotech.jadibuti.data.local.MedicineEntity
import com.chefotech.jadibuti.data.local.TransactionDao
import com.chefotech.jadibuti.data.local.TransactionEntity
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.remote.DoseTimeDto
import com.chefotech.jadibuti.data.remote.MedicineDto
import com.chefotech.jadibuti.data.toDto
import com.chefotech.jadibuti.data.toEntity
import com.chefotech.jadibuti.reminders.ReminderScheduler
import com.chefotech.jadibuti.scheduling.EventGenerator
import com.chefotech.jadibuti.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the medicine form needs to save. */
data class MedicineDraft(
    val id: String? = null,
    val memberId: String,
    val name: String,
    val strength: String,
    val type: String,
    val doseUnit: String,
    val foodInstruction: String,
    val instructions: String,
    val startDate: String,
    val endDate: String?,
    val frequency: String,
    val weekdays: List<Int>,
    val intervalDays: Int,
    val doseTimes: List<DoseTimeDto>,
    val trackInventory: Boolean,
    val lowStockType: String,
    val lowStockValue: Double,
    /** Only used when creating: opening stock quantity. */
    val initialStock: Double?,
    val prescriptionId: String? = null,
    val active: Boolean = true,
)

@Singleton
class MedicineRepository @Inject constructor(
    private val dao: MedicineDao,
    private val transactions: TransactionDao,
    private val outbox: Outbox,
    private val session: SessionStore,
    private val generator: EventGenerator,
    private val reminders: ReminderScheduler,
    private val syncScheduler: SyncScheduler,
) {
    fun observe(familyId: String): Flow<List<MedicineEntity>> = dao.observe(familyId)
    fun observeForMember(memberId: String): Flow<List<MedicineEntity>> = dao.observeForMember(memberId)
    fun observeOne(id: String): Flow<MedicineEntity?> = dao.observeOne(id)
    suspend fun get(id: String) = dao.get(id)

    suspend fun save(draft: MedicineDraft): MedicineEntity {
        val familyId = requireNotNull(session.current.activeFamilyId) { "No active family" }
        val existing = draft.id?.let { dao.get(it) }
        val now = System.currentTimeMillis()
        val dto = MedicineDto(
            id = existing?.id ?: UUID.randomUUID().toString(),
            familyId = familyId,
            memberId = draft.memberId,
            name = draft.name.trim(),
            strength = draft.strength.trim(),
            type = draft.type,
            doseUnit = draft.doseUnit.trim().ifBlank { "dose" },
            foodInstruction = draft.foodInstruction,
            instructions = draft.instructions.trim(),
            startDate = draft.startDate,
            endDate = draft.endDate,
            schedule = com.chefotech.jadibuti.data.remote.ScheduleDto(
                frequency = draft.frequency,
                weekdays = draft.weekdays.sorted(),
                intervalDays = draft.intervalDays.coerceAtLeast(1),
                referenceDate = existing?.referenceDate ?: draft.startDate,
            ),
            doseTimes = draft.doseTimes.sortedBy { it.time },
            inventory = com.chefotech.jadibuti.data.remote.InventoryDto(draft.trackInventory, draft.lowStockType, draft.lowStockValue),
            prescriptionId = draft.prescriptionId ?: existing?.prescriptionId,
            active = draft.active,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            version = existing?.version ?: 0,
            deleted = false,
        )
        val entity = dto.toEntity(lowStockAlertedAt = existing?.lowStockAlertedAt)
        dao.upsert(entity)
        outbox.medicine(dto)
        if (existing == null && draft.initialStock != null && draft.trackInventory) {
            addTransaction(entity.id, "INITIAL", draft.initialStock, null, "Opening stock")
        }
        generator.refreshMedicine(entity)
        reminders.rescheduleAll()
        syncScheduler.requestSync()
        return entity
    }

    suspend fun setActive(id: String, active: Boolean) {
        val existing = dao.get(id) ?: return
        val entity = existing.copy(active = active, updatedAt = System.currentTimeMillis())
        dao.upsert(entity)
        outbox.medicine(entity.toDto())
        generator.refreshMedicine(entity)
        reminders.rescheduleAll()
        syncScheduler.requestSync()
    }

    suspend fun delete(id: String) {
        val existing = dao.get(id) ?: return
        val entity = existing.copy(deleted = true, active = false, updatedAt = System.currentTimeMillis())
        dao.upsert(entity)
        outbox.medicine(entity.toDto())
        generator.refreshMedicine(entity)
        reminders.rescheduleAll()
        syncScheduler.requestSync()
    }

    /** Appends a ledger entry (id defaults to a UUID; deterministic ids prevent duplicates). */
    suspend fun addTransaction(medicineId: String, type: String, quantityDelta: Double, eventId: String?, note: String, id: String? = null): Boolean {
        val familyId = requireNotNull(session.current.activeFamilyId)
        val now = System.currentTimeMillis()
        val tx = TransactionEntity(
            id = id ?: UUID.randomUUID().toString(), familyId = familyId, medicineId = medicineId, type = type,
            quantityDelta = quantityDelta, eventId = eventId, note = note, createdAt = now, byUserId = session.current.userId,
            updatedAt = now, version = 0, deleted = false,
        )
        val inserted = transactions.insertIgnore(tx) != -1L
        if (inserted) outbox.transaction(tx.toDto())
        return inserted
    }
}
