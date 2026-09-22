package com.chefotech.jadibuti.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MemberEntity::class, MedicineEntity::class, EventEntity::class, TransactionEntity::class, OutboxEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun members(): MemberDao
    abstract fun medicines(): MedicineDao
    abstract fun events(): EventDao
    abstract fun transactions(): TransactionDao
    abstract fun outbox(): OutboxDao
    abstract fun syncState(): SyncStateDao
}
