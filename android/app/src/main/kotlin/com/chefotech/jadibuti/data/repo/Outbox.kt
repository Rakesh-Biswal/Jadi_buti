package com.chefotech.jadibuti.data.repo

import com.chefotech.jadibuti.data.local.OutboxDao
import com.chefotech.jadibuti.data.local.OutboxEntity
import com.chefotech.jadibuti.data.remote.EventDto
import com.chefotech.jadibuti.data.remote.MedicineDto
import com.chefotech.jadibuti.data.remote.MemberDto
import com.chefotech.jadibuti.data.remote.TransactionDto
import com.chefotech.jadibuti.data.remote.json
import kotlinx.serialization.KSerializer
import javax.inject.Inject
import javax.inject.Singleton

/** Records local changes for later push. The latest snapshot per entity replaces older ones. */
@Singleton
class Outbox @Inject constructor(private val dao: OutboxDao) {
    private suspend fun <T> put(familyId: String, entity: String, id: String, value: T, serializer: KSerializer<T>) {
        dao.enqueue(OutboxEntity(familyId = familyId, entity = entity, entityId = id, payloadJson = json.encodeToString(serializer, value), createdAt = System.currentTimeMillis()))
    }

    suspend fun member(m: MemberDto) = put(m.familyId, "member", m.id, m, MemberDto.serializer())
    suspend fun medicine(m: MedicineDto) = put(m.familyId, "medicine", m.id, m, MedicineDto.serializer())
    suspend fun event(e: EventDto) = put(e.familyId, "event", e.id, e, EventDto.serializer())
    suspend fun transaction(t: TransactionDto) = put(t.familyId, "transaction", t.id, t, TransactionDto.serializer())
    suspend fun hasPending(entity: String, id: String) = dao.countPending(entity, id) > 0
    fun observeCount() = dao.observeCount()
}
