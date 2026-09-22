package com.chefotech.jadibuti.sync

import android.util.Log
import com.chefotech.jadibuti.data.local.AppDatabase
import com.chefotech.jadibuti.data.local.SyncStateEntity
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.remote.ApiException
import com.chefotech.jadibuti.data.remote.ApiService
import com.chefotech.jadibuti.data.remote.EventDto
import com.chefotech.jadibuti.data.remote.MedicineDto
import com.chefotech.jadibuti.data.remote.MemberDto
import com.chefotech.jadibuti.data.remote.NetworkUnavailableException
import com.chefotech.jadibuti.data.remote.SyncChange
import com.chefotech.jadibuti.data.remote.SyncPushRequest
import com.chefotech.jadibuti.data.remote.TransactionDto
import com.chefotech.jadibuti.data.remote.apiCall
import com.chefotech.jadibuti.data.remote.json
import com.chefotech.jadibuti.data.toEntity
import com.chefotech.jadibuti.domain.EventState
import com.chefotech.jadibuti.domain.EventStatus
import com.chefotech.jadibuti.domain.MergePolicy
import com.chefotech.jadibuti.scheduling.MaintenanceService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncOutcome {
    data object Success : SyncOutcome()
    data class Offline(val message: String) : SyncOutcome()
    data class Failed(val message: String) : SyncOutcome()
}

/**
 * Push local outbox, then pull server changes since the family cursor and reconcile.
 * Safe to run repeatedly; guarded by a mutex so overlapping triggers don't interleave.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val api: ApiService,
    private val db: AppDatabase,
    private val session: SessionStore,
    private val maintenance: MaintenanceService,
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncOutcome {
        val familyId = session.current.activeFamilyId ?: return SyncOutcome.Success
        if (session.accessToken == null) return SyncOutcome.Failed("Not signed in")
        return mutex.withLock {
            try {
                pushOutbox(familyId)
                pull(familyId)
                db.syncState().upsert(SyncStateEntity(familyId, db.syncState().get(familyId)?.cursor ?: 0, System.currentTimeMillis(), null))
                maintenance.runAll()
                SyncOutcome.Success
            } catch (e: NetworkUnavailableException) {
                Log.i(TAG, "offline: ${e.message}")
                recordError(familyId, "Offline")
                SyncOutcome.Offline("You are offline. Changes are saved on this phone and will sync later.")
            } catch (e: ApiException) {
                Log.w(TAG, "sync failed ${e.status} ${e.code}: ${e.message}")
                recordError(familyId, e.message)
                SyncOutcome.Failed(e.message ?: "Sync failed")
            } catch (e: Exception) {
                Log.e(TAG, "sync crashed", e)
                recordError(familyId, e.message)
                SyncOutcome.Failed(e.message ?: "Sync failed")
            }
        }
    }

    private suspend fun recordError(familyId: String, message: String?) {
        val prev = db.syncState().get(familyId)
        db.syncState().upsert(SyncStateEntity(familyId, prev?.cursor ?: 0, prev?.lastSyncAt, message))
    }

    private suspend fun pushOutbox(familyId: String) {
        val outbox = db.outbox()
        while (true) {
            val batch = outbox.next(familyId, 100)
            if (batch.isEmpty()) return
            val changes = batch.map { SyncChange(it.entity, json.parseToJsonElement(it.payloadJson).jsonObject) }
            val response = apiCall { api.push(SyncPushRequest(familyId, changes)) }
            val okSeqs = ArrayList<Long>()
            val dropSeqs = ArrayList<Long>()
            val retrySeqs = ArrayList<Long>()
            for ((row, result) in batch.zip(response.results)) {
                if (result.ok) {
                    okSeqs += row.seq
                    result.doc?.let { applyServerDoc(row.entity, it, row.entityId) }
                } else if (result.error?.contains("Invalid payload", ignoreCase = true) == true || result.error?.contains("belong", ignoreCase = true) == true) {
                    Log.w(TAG, "dropping rejected change ${row.entity}/${row.entityId}: ${result.error}")
                    dropSeqs += row.seq
                } else {
                    retrySeqs += row.seq
                }
            }
            if (okSeqs.isNotEmpty()) outbox.delete(okSeqs)
            if (dropSeqs.isNotEmpty()) outbox.delete(dropSeqs)
            if (retrySeqs.isNotEmpty()) {
                outbox.markFailed(retrySeqs, "rejected")
                if (okSeqs.isEmpty() && dropSeqs.isEmpty()) return // avoid a tight loop on persistent rejects
            }
        }
    }

    /** The server echo is authoritative (version, merged status). Skip if a newer local change is pending. */
    private suspend fun applyServerDoc(entity: String, doc: JsonObject, id: String) {
        if (db.outbox().countPending(entity, id) > 0) return
        when (entity) {
            "member" -> db.members().upsert(json.decodeFromJsonElement(MemberDto.serializer(), doc).toEntity())
            "medicine" -> {
                val local = db.medicines().get(id)
                db.medicines().upsert(json.decodeFromJsonElement(MedicineDto.serializer(), doc).toEntity(local?.lowStockAlertedAt))
            }
            "event" -> db.events().upsert(json.decodeFromJsonElement(EventDto.serializer(), doc).toEntity(db.events().get(id)))
            "transaction" -> db.transactions().upsertAll(listOf(json.decodeFromJsonElement(TransactionDto.serializer(), doc).toEntity()))
        }
    }

    private suspend fun pull(familyId: String) {
        var cursor = db.syncState().get(familyId)?.cursor ?: 0L
        var guard = 0
        do {
            val page = apiCall { api.pull(familyId, cursor) }
            reconcile(page.members, page.medicines, page.events, page.transactions)
            cursor = maxOf(cursor, page.cursor)
            db.syncState().upsert(SyncStateEntity(familyId, cursor, System.currentTimeMillis(), null))
            guard++
        } while (page.hasMore && guard < 20)
    }

    private suspend fun reconcile(members: List<MemberDto>, medicines: List<MedicineDto>, events: List<EventDto>, transactions: List<TransactionDto>) {
        val outbox = db.outbox()
        for (m in members) if (outbox.countPending("member", m.id) == 0) db.members().upsert(m.toEntity())
        for (m in medicines) if (outbox.countPending("medicine", m.id) == 0) {
            db.medicines().upsert(m.toEntity(db.medicines().get(m.id)?.lowStockAlertedAt))
        }
        for (t in transactions) if (outbox.countPending("transaction", t.id) == 0) db.transactions().upsertAll(listOf(t.toEntity()))
        for (remote in events) {
            if (outbox.countPending("event", remote.id) > 0) continue
            val local = db.events().get(remote.id)
            if (local == null) { db.events().upsert(remote.toEntity()); continue }
            val merged = MergePolicy.merge(
                EventState(status(local.status), local.actualAt, local.updatedAt, local.snoozedUntil, local.note, local.recordedByUserId),
                EventState(status(remote.status), remote.actualAt, remote.updatedAt, remote.snoozedUntil, remote.note, remote.recordedByUserId),
            )
            val remoteWins = merged.status == status(remote.status) && merged.updatedAt == remote.updatedAt
            if (remoteWins || remote.version > local.version) db.events().upsert(remote.toEntity(local))
        }
    }

    private fun status(s: String) = runCatching { EventStatus.valueOf(s) }.getOrDefault(EventStatus.UPCOMING)

    companion object { private const val TAG = "JadiButiSync" }
}
