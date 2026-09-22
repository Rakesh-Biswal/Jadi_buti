package com.chefotech.jadibuti.data.repo

import android.os.Build
import com.chefotech.jadibuti.data.local.AppDatabase
import com.chefotech.jadibuti.data.prefs.SessionStore
import com.chefotech.jadibuti.data.remote.ApiService
import com.chefotech.jadibuti.data.remote.AuthResponse
import com.chefotech.jadibuti.data.remote.FamilyWithMembership
import com.chefotech.jadibuti.data.remote.LoginRequest
import com.chefotech.jadibuti.data.remote.RefreshRequest
import com.chefotech.jadibuti.data.remote.RegisterRequest
import com.chefotech.jadibuti.data.remote.apiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionStore,
    private val db: AppDatabase,
    private val syncScheduler: com.chefotech.jadibuti.sync.SyncScheduler,
) {
    private val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    suspend fun register(name: String, email: String, password: String): List<FamilyWithMembership> {
        val auth = apiCall { api.register(RegisterRequest(email.trim(), password, name.trim(), deviceName)) }
        return onAuthenticated(auth)
    }

    suspend fun login(email: String, password: String): List<FamilyWithMembership> {
        val auth = apiCall { api.login(LoginRequest(email.trim(), password, deviceName)) }
        return onAuthenticated(auth)
    }

    private suspend fun onAuthenticated(auth: AuthResponse): List<FamilyWithMembership> {
        val previousUser = session.current.userId
        if (previousUser != null && previousUser != auth.user.id) withContext(Dispatchers.IO) { db.clearAllTables() }
        session.saveTokens(auth.accessToken, auth.refreshToken)
        session.saveUser(auth.user.id, auth.user.name, auth.user.email)
        val families = runCatching { apiCall { api.families() } }.getOrDefault(emptyList())
        selectFamilyIfSingle(families)
        return families
    }

    /** Called on app start when logged in: refreshes family list (best effort, works offline). */
    suspend fun refreshFamilies(): List<FamilyWithMembership>? {
        if (!session.current.isLoggedIn) return null
        val me = runCatching { apiCall { api.me() } }.getOrNull() ?: return null
        session.saveUser(me.user.id, me.user.name, me.user.email)
        val active = session.current.activeFamilyId
        val match = me.families.firstOrNull { it.family.id == active }
        if (active != null && match == null) {
            // Access revoked or family gone: drop local data for that family.
            session.setActiveFamily(null, null, null, true)
        } else if (match != null) {
            session.setActiveFamily(match.family.id, match.family.name, match.membership.role, match.membership.permissions.canEdit)
        }
        selectFamilyIfSingle(me.families)
        return me.families
    }

    private fun selectFamilyIfSingle(families: List<FamilyWithMembership>) {
        if (session.current.activeFamilyId == null && families.size == 1) {
            val f = families.first()
            session.setActiveFamily(f.family.id, f.family.name, f.membership.role, f.membership.permissions.canEdit)
        }
        // Pull the family's data right away so the dashboard is populated after sign-in.
        if (session.current.activeFamilyId != null) syncScheduler.requestSync()
    }

    suspend fun logout() {
        val refresh = session.refreshToken
        if (refresh != null) runCatching { apiCall { api.logout(RefreshRequest(refresh)) } }
        session.clearAll()
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }
}
