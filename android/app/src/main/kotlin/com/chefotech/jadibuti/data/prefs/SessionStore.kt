package com.chefotech.jadibuti.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chefotech.jadibuti.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SessionSnapshot(
    val userId: String?,
    val userName: String?,
    val userEmail: String?,
    val activeFamilyId: String?,
    val activeFamilyName: String?,
    val role: String?,
    val canEdit: Boolean,
) {
    val isLoggedIn: Boolean get() = userId != null
    val hasFamily: Boolean get() = activeFamilyId != null
    val isOwner: Boolean get() = role == "OWNER"
}

/** Tokens and identity in EncryptedSharedPreferences (Android Keystore backed). */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = runCatching {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "jadibuti_session", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Keystore corruption fallback: wipe and recreate rather than crash on launch.
        context.deleteSharedPreferences("jadibuti_session")
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "jadibuti_session", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<SessionSnapshot> = _snapshot

    private fun read() = SessionSnapshot(
        userId = prefs.getString(K_USER_ID, null),
        userName = prefs.getString(K_USER_NAME, null),
        userEmail = prefs.getString(K_USER_EMAIL, null),
        activeFamilyId = prefs.getString(K_FAMILY_ID, null),
        activeFamilyName = prefs.getString(K_FAMILY_NAME, null),
        role = prefs.getString(K_ROLE, null),
        canEdit = prefs.getBoolean(K_CAN_EDIT, true),
    )

    private fun refresh() { _snapshot.value = read() }

    val accessToken: String? get() = prefs.getString(K_ACCESS, null)
    val refreshToken: String? get() = prefs.getString(K_REFRESH, null)
    val apiBaseUrl: String get() = prefs.getString(K_BASE_URL, null) ?: BuildConfig.DEFAULT_API_BASE_URL
    val current: SessionSnapshot get() = _snapshot.value

    fun setApiBaseUrl(url: String) { prefs.edit { putString(K_BASE_URL, url.trim().trimEnd('/') + "/") } }

    fun saveTokens(access: String, refresh: String) { prefs.edit { putString(K_ACCESS, access); putString(K_REFRESH, refresh) } }

    fun clearTokens() { prefs.edit { remove(K_ACCESS); remove(K_REFRESH) } }

    fun saveUser(id: String, name: String, email: String) {
        prefs.edit { putString(K_USER_ID, id); putString(K_USER_NAME, name); putString(K_USER_EMAIL, email) }
        refresh()
    }

    fun setActiveFamily(id: String?, name: String?, role: String?, canEdit: Boolean) {
        prefs.edit {
            if (id == null) { remove(K_FAMILY_ID); remove(K_FAMILY_NAME); remove(K_ROLE); remove(K_CAN_EDIT) } else {
                putString(K_FAMILY_ID, id); putString(K_FAMILY_NAME, name); putString(K_ROLE, role); putBoolean(K_CAN_EDIT, canEdit)
            }
        }
        refresh()
    }

    fun clearAll() {
        val base = prefs.getString(K_BASE_URL, null)
        prefs.edit { clear(); if (base != null) putString(K_BASE_URL, base) }
        refresh()
    }

    private companion object {
        const val K_ACCESS = "access_token"
        const val K_REFRESH = "refresh_token"
        const val K_USER_ID = "user_id"
        const val K_USER_NAME = "user_name"
        const val K_USER_EMAIL = "user_email"
        const val K_FAMILY_ID = "family_id"
        const val K_FAMILY_NAME = "family_name"
        const val K_ROLE = "role"
        const val K_CAN_EDIT = "can_edit"
        const val K_BASE_URL = "api_base_url"
    }
}
