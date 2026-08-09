package com.familyguard.parent.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("family_guard_parent")

class SessionStore(private val context: Context) {
    private val access = stringPreferencesKey("access")
    private val refresh = stringPreferencesKey("refresh")
    private val familyId = intPreferencesKey("family_id")
    private val userId = intPreferencesKey("user_id")
    private val pairingCode = stringPreferencesKey("pairing_code")
    private val loggedIn = stringPreferencesKey("logged_in")

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[loggedIn] == "1" }

    suspend fun saveAuth(
        accessToken: String,
        refreshToken: String,
        familyIdValue: Int,
        userIdValue: Int,
        code: String,
    ) {
        context.dataStore.edit {
            it[access] = accessToken
            it[refresh] = refreshToken
            it[familyId] = familyIdValue
            it[userId] = userIdValue
            it[pairingCode] = code
            it[loggedIn] = "1"
        }
    }

    suspend fun accessToken(): String? = context.dataStore.data.first()[access]
    suspend fun familyId(): Int? = context.dataStore.data.first()[familyId]
    suspend fun userId(): Int? = context.dataStore.data.first()[userId]
    suspend fun pairingCode(): String? = context.dataStore.data.first()[pairingCode]

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
