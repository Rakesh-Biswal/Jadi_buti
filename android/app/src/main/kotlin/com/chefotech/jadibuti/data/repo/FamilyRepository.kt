package com.chefotech.jadibuti.data.repo

import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.remote.ApiService
import com.chefotech.jadibuti.data.remote.CreateFamilyRequest
import com.chefotech.jadibuti.data.remote.CreateInviteRequest
import com.chefotech.jadibuti.data.remote.FamilyWithMembership
import com.chefotech.jadibuti.data.remote.InviteDto
import com.chefotech.jadibuti.data.remote.JoinFamilyRequest
import com.chefotech.jadibuti.data.remote.MembershipDto
import com.chefotech.jadibuti.data.remote.MembershipPatch
import com.chefotech.jadibuti.data.remote.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyRepository @Inject constructor(private val api: ApiService, private val session: SessionStore) {
    suspend fun createFamily(name: String): FamilyWithMembership {
        val f = apiCall { api.createFamily(CreateFamilyRequest(name.trim())) }
        select(f)
        return f
    }

    suspend fun joinFamily(code: String): FamilyWithMembership {
        val f = apiCall { api.joinFamily(JoinFamilyRequest(code.trim().uppercase())) }
        select(f)
        return f
    }

    suspend fun families(): List<FamilyWithMembership> = apiCall { api.families() }

    fun select(f: FamilyWithMembership) = session.setActiveFamily(f.family.id, f.family.name, f.membership.role, f.membership.permissions.canEdit)

    suspend fun memberships(familyId: String): List<MembershipDto> = apiCall { api.memberships(familyId) }

    suspend fun createInvite(familyId: String, canEdit: Boolean): InviteDto = apiCall { api.createInvite(familyId, CreateInviteRequest(canEdit = canEdit)) }

    suspend fun updateMembership(familyId: String, userId: String, patch: MembershipPatch): MembershipDto = apiCall { api.updateMembership(familyId, userId, patch) }

    suspend fun removeMembership(familyId: String, userId: String): MembershipDto = apiCall { api.removeMembership(familyId, userId) }
}
