package com.chefotech.jadibuti.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE familyId = :familyId AND deleted = 0 ORDER BY active DESC, name")
    fun observe(familyId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun get(id: String): MemberEntity?

    @Query("SELECT * FROM members WHERE id = :id")
    fun observeOne(id: String): Flow<MemberEntity?>

    @Upsert
    suspend fun upsert(member: MemberEntity)

    @Upsert
    suspend fun upsertAll(members: List<MemberEntity>)
}

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines WHERE familyId = :familyId AND deleted = 0 ORDER BY active DESC, name")
    fun observe(familyId: String): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE familyId = :familyId AND deleted = 0")
    suspend fun listActiveFamily(familyId: String): List<MedicineEntity>

    @Query("SELECT * FROM medicines WHERE memberId = :memberId AND deleted = 0 ORDER BY active DESC, name")
    fun observeForMember(memberId: String): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun get(id: String): MedicineEntity?

    @Query("SELECT * FROM medicines WHERE id = :id")
    fun observeOne(id: String): Flow<MedicineEntity?>

    @Upsert
    suspend fun upsert(medicine: MedicineEntity)

    @Upsert
    suspend fun upsertAll(medicines: List<MedicineEntity>)

    @Query("UPDATE medicines SET lowStockAlertedAt = :at WHERE id = :id")
    suspend fun setLowStockAlertedAt(id: String, at: Long?)
}

@Dao
interface EventDao {
    @Query(
        """SELECT e.*, m.name AS memberName, d.name AS medicineName, d.strength AS medicineStrength,
                  d.foodInstruction AS foodInstruction, d.type AS medicineType
           FROM events e JOIN members m ON m.id = e.memberId JOIN medicines d ON d.id = e.medicineId
           WHERE e.familyId = :familyId AND e.localDate = :localDate AND e.deleted = 0
           ORDER BY e.scheduledAt, m.name""",
    )
    fun observeForDate(familyId: String, localDate: String): Flow<List<EventWithDetails>>

    @Query(
        """SELECT e.*, m.name AS memberName, d.name AS medicineName, d.strength AS medicineStrength,
                  d.foodInstruction AS foodInstruction, d.type AS medicineType
           FROM events e JOIN members m ON m.id = e.memberId JOIN medicines d ON d.id = e.medicineId
           WHERE e.familyId = :familyId AND e.localDate BETWEEN :from AND :to AND e.deleted = 0
             AND (:memberId IS NULL OR e.memberId = :memberId)
             AND (:medicineId IS NULL OR e.medicineId = :medicineId)
           ORDER BY e.scheduledAt DESC""",
    )
    fun observeRange(familyId: String, from: String, to: String, memberId: String?, medicineId: String?): Flow<List<EventWithDetails>>

    @Query(
        """SELECT e.*, m.name AS memberName, d.name AS medicineName, d.strength AS medicineStrength,
                  d.foodInstruction AS foodInstruction, d.type AS medicineType
           FROM events e JOIN members m ON m.id = e.memberId JOIN medicines d ON d.id = e.medicineId
           WHERE e.id = :id""",
    )
    suspend fun getWithDetails(id: String): EventWithDetails?

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun get(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE familyId = :familyId AND deleted = 0 AND status IN ('UPCOMING','DUE','SNOOZED') AND scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt")
    suspend fun pendingBetween(familyId: String, from: Long, to: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE deleted = 0 AND status IN ('UPCOMING','DUE','SNOOZED') AND scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt")
    suspend fun pendingBetweenAllFamilies(from: Long, to: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE medicineId = :medicineId AND deleted = 0 AND localDate BETWEEN :from AND :to")
    suspend fun forMedicineBetween(medicineId: String, from: String, to: String): List<EventEntity>

    /** Includes soft-deleted rows, so the generator can restore a retired dose under its existing id. */
    @Query("SELECT * FROM events WHERE medicineId = :medicineId AND localDate BETWEEN :from AND :to")
    suspend fun forMedicineBetweenAll(medicineId: String, from: String, to: String): List<EventEntity>

    @Query("SELECT id FROM events WHERE familyId = :familyId AND localDate BETWEEN :from AND :to")
    suspend fun idsBetween(familyId: String, from: String, to: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(events: List<EventEntity>): List<Long>

    @Upsert
    suspend fun upsert(event: EventEntity)

    @Upsert
    suspend fun upsertAll(events: List<EventEntity>)

    @Query("UPDATE events SET notifiedAt = :at WHERE id = :id")
    suspend fun setNotifiedAt(id: String, at: Long)

    @Query("UPDATE events SET followUpNotifiedAt = :at WHERE id = :id")
    suspend fun setFollowUpNotifiedAt(id: String, at: Long)

    @Query("UPDATE events SET caregiverAlertedAt = :at WHERE id = :id")
    suspend fun setCaregiverAlertedAt(id: String, at: Long)

    @Query("SELECT COUNT(*) FROM events WHERE familyId = :familyId AND localDate = :localDate AND deleted = 0")
    suspend fun countForDate(familyId: String, localDate: String): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE medicineId = :medicineId AND deleted = 0 ORDER BY createdAt")
    suspend fun forMedicine(medicineId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE medicineId = :medicineId AND deleted = 0 ORDER BY createdAt DESC")
    fun observeForMedicine(medicineId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE familyId = :familyId AND deleted = 0 ORDER BY createdAt")
    fun observeFamily(familyId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE familyId = :familyId AND deleted = 0 ORDER BY createdAt")
    suspend fun listFamily(familyId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tx: TransactionEntity): Long

    @Upsert
    suspend fun upsertAll(txs: List<TransactionEntity>)
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE familyId = :familyId ORDER BY seq LIMIT :limit")
    suspend fun next(familyId: String, limit: Int): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT DISTINCT familyId FROM outbox")
    suspend fun familiesWithPending(): List<String>

    @Query("SELECT COUNT(*) FROM outbox WHERE entity = :entity AND entityId = :entityId")
    suspend fun countPending(entity: String, entityId: String): Int

    @Query("DELETE FROM outbox WHERE entity = :entity AND entityId = :entityId")
    suspend fun deleteFor(entity: String, entityId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: OutboxEntity)

    @Transaction
    suspend fun enqueue(row: OutboxEntity) {
        deleteFor(row.entity, row.entityId)
        insert(row)
    }

    @Query("DELETE FROM outbox WHERE seq IN (:seqs)")
    suspend fun delete(seqs: List<Long>)

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE seq IN (:seqs)")
    suspend fun markFailed(seqs: List<Long>, error: String?)
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE familyId = :familyId")
    suspend fun get(familyId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE familyId = :familyId")
    fun observe(familyId: String): Flow<SyncStateEntity?>

    @Upsert
    suspend fun upsert(state: SyncStateEntity)
}
