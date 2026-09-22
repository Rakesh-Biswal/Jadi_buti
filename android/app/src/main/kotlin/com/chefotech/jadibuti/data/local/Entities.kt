package com.chefotech.jadibuti.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities mirror the server documents (same ids, same fields) plus a few
 * local-only bookkeeping columns (notification timestamps) that are never synced.
 */

@Entity(tableName = "members", indices = [Index("familyId")])
data class MemberEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val name: String,
    val dateOfBirth: String?,
    val notes: String,
    val photoPath: String?,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int,
    val deleted: Boolean,
)

@Entity(tableName = "medicines", indices = [Index("familyId"), Index("memberId")])
data class MedicineEntity(
    @PrimaryKey val id: String,
    val familyId: String,
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
    val weekdays: String, // comma separated ISO weekday numbers
    val intervalDays: Int,
    val referenceDate: String?,
    val doseTimesJson: String, // [{"time":"08:00","amount":1.0}]
    val trackInventory: Boolean,
    val lowStockType: String,
    val lowStockValue: Double,
    val prescriptionId: String?,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int,
    val deleted: Boolean,
    /** local-only: when the current low-stock state was last alerted (null = not in low state). */
    val lowStockAlertedAt: Long? = null,
)

@Entity(tableName = "events", indices = [Index("familyId", "localDate"), Index("medicineId"), Index("scheduledAt")])
data class EventEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val memberId: String,
    val medicineId: String,
    val localDate: String,
    val time: String,
    val zoneId: String,
    val scheduledAt: Long,
    val doseAmount: Double,
    val doseUnit: String,
    val status: String,
    val actualAt: Long?,
    val recordedByUserId: String?,
    val snoozedUntil: Long?,
    val note: String,
    val statusHistoryJson: String,
    val updatedAt: Long,
    val version: Int,
    val deleted: Boolean,
    /** local-only reminder bookkeeping */
    val notifiedAt: Long? = null,
    val followUpNotifiedAt: Long? = null,
    val caregiverAlertedAt: Long? = null,
)

@Entity(tableName = "transactions", indices = [Index("familyId"), Index("medicineId")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val medicineId: String,
    val type: String,
    val quantityDelta: Double,
    val eventId: String?,
    val note: String,
    val createdAt: Long,
    val byUserId: String?,
    val updatedAt: Long,
    val version: Int,
    val deleted: Boolean,
)

/** Pending local changes waiting to be pushed. One row per entity id (latest snapshot wins). */
@Entity(tableName = "outbox", indices = [Index("familyId"), Index(value = ["entity", "entityId"], unique = true)])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val familyId: String,
    val entity: String, // member | medicine | event | transaction
    val entityId: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

/** Per-family sync cursor. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val familyId: String,
    val cursor: Long,
    val lastSyncAt: Long?,
    val lastError: String?,
)

/** Join row used by the dashboard and history. */
data class EventWithDetails(
    @Embedded val event: EventEntity,
    val memberName: String,
    val medicineName: String,
    val medicineStrength: String,
    val foodInstruction: String,
    val medicineType: String,
)
