package com.familyguard.child.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("family_guard_child")

class SessionStore(private val context: Context) {
    private val access = stringPreferencesKey("access")
    private val refresh = stringPreferencesKey("refresh")
    private val familyId = intPreferencesKey("family_id")
    private val deviceId = intPreferencesKey("device_id")
    private val userId = intPreferencesKey("user_id")
    private val displayName = stringPreferencesKey("display_name")
    private val paired = stringPreferencesKey("paired")
    private val askedPerms = stringPreferencesKey("asked_perms")
    private val darkCover = stringPreferencesKey("dark_cover")

    val isPaired: Flow<Boolean> = context.dataStore.data.map { it[paired] == "1" }

    suspend fun saveAuth(
        accessToken: String,
        refreshToken: String,
        familyIdValue: Int,
        deviceIdValue: Int,
        userIdValue: Int,
        name: String,
    ) {
        context.dataStore.edit {
            it[access] = accessToken
            it[refresh] = refreshToken
            it[familyId] = familyIdValue
            it[deviceId] = deviceIdValue
            it[userId] = userIdValue
            it[displayName] = name
            it[paired] = "1"
        }
    }

    suspend fun accessToken(): String? = context.dataStore.data.first()[access]
    suspend fun familyId(): Int? = context.dataStore.data.first()[familyId]
    suspend fun deviceId(): Int? = context.dataStore.data.first()[deviceId]
    suspend fun userId(): Int? = context.dataStore.data.first()[userId]
    suspend fun name(): String? = context.dataStore.data.first()[displayName]

    suspend fun permissionsAsked(): Boolean = context.dataStore.data.first()[askedPerms] == "1"

    suspend fun markPermissionsAsked() {
        context.dataStore.edit { it[askedPerms] = "1" }
    }

    val isDarkCover: Flow<Boolean> = context.dataStore.data.map { it[darkCover] == "1" }

    suspend fun setDarkCover(dark: Boolean) {
        context.dataStore.edit { it[darkCover] = if (dark) "1" else "0" }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
