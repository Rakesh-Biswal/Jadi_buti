package com.chefotech.jadibuti.data.repo

import com.chefotech.jadibuti.data.local.MemberDao
import com.chefotech.jadibuti.data.local.MemberEntity
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.toDto
import com.chefotech.jadibuti.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberRepository @Inject constructor(
    private val dao: MemberDao,
    private val outbox: Outbox,
    private val session: SessionStore,
    private val syncScheduler: SyncScheduler,
) {
    fun observe(familyId: String): Flow<List<MemberEntity>> = dao.observe(familyId)
    fun observeOne(id: String): Flow<MemberEntity?> = dao.observeOne(id)
    suspend fun get(id: String) = dao.get(id)

    suspend fun save(id: String?, name: String, dateOfBirth: String?, notes: String, active: Boolean): MemberEntity {
        val familyId = requireNotNull(session.current.activeFamilyId) { "No active family" }
        val existing = id?.let { dao.get(it) }
        val now = System.currentTimeMillis()
        val entity = MemberEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            familyId = familyId,
            name = name.trim(),
            dateOfBirth = dateOfBirth?.takeIf { it.isNotBlank() },
            notes = notes.trim(),
            photoPath = existing?.photoPath,
            active = active,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            version = existing?.version ?: 0,
            deleted = false,
        )
        dao.upsert(entity)
        outbox.member(entity.toDto())
        syncScheduler.requestSync()
        return entity
    }

    suspend fun delete(id: String) {
        val existing = dao.get(id) ?: return
        val entity = existing.copy(deleted = true, active = false, updatedAt = System.currentTimeMillis())
        dao.upsert(entity)
        outbox.member(entity.toDto())
        syncScheduler.requestSync()
    }
}
