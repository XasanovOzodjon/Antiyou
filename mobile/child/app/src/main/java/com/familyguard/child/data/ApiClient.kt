package com.familyguard.child.data

import com.familyguard.child.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

data class AuthResponse(
    val user: UserDto,
    val family: FamilyDto?,
    val tokens: TokensDto,
    @SerializedName("device_id") val deviceId: Int?,
)

data class UserDto(
    val id: Int,
    val email: String,
    @SerializedName("display_name") val displayName: String,
    val role: String,
    @SerializedName("family_id") val familyId: Int?,
)

data class FamilyDto(val id: Int, val name: String, @SerializedName("pairing_code") val pairingCode: String)
data class TokensDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
)

data class MessageDto(
    val id: Int,
    @SerializedName("family_id") val familyId: Int,
    @SerializedName("sender_id") val senderId: Int,
    @SerializedName("sender_name") val senderName: String?,
    val body: String,
    @SerializedName("created_at") val createdAt: String,
)

class ApiClient(private val session: SessionStore) {
    private val gson = Gson()
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    val baseUrl: String get() = BuildConfig.API_BASE_URL.trimEnd('/')

    private suspend fun authRequest(builder: Request.Builder): Request.Builder {
        session.accessToken()?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

    private suspend fun <T> execute(request: Request, clazz: Class<T>): T = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: $body")
            gson.fromJson(body, clazz)
        }
    }

    suspend fun pairChild(
        pairingCode: String,
        displayName: String,
        deviceName: String,
        chatPin: String,
    ): AuthResponse {
        val payload = JsonObject().apply {
            addProperty("pairing_code", pairingCode)
            addProperty("display_name", displayName)
            addProperty("device_name", deviceName)
            addProperty("chat_pin", chatPin)
        }
        val req = Request.Builder()
            .url("$baseUrl/auth/pair-child")
            .post(payload.toString().toRequestBody(json))
            .build()
        return execute(req, AuthResponse::class.java)
    }

    suspend fun verifyPin(pin: String): Boolean {
        val payload = JsonObject().apply { addProperty("pin", pin) }
        val req = authRequest(
            Request.Builder().url("$baseUrl/auth/verify-chat-pin").post(payload.toString().toRequestBody(json))
        ).build()
        return withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    suspend fun messages(): List<MessageDto> {
        val req = authRequest(Request.Builder().url("$baseUrl/chat/messages")).get().build()
        return withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException(body)
                gson.fromJson(body, object : TypeToken<List<MessageDto>>() {}.type)
            }
        }
    }

    suspend fun sendMessage(text: String): MessageDto {
        val payload = JsonObject().apply { addProperty("body", text) }
        val req = authRequest(
            Request.Builder().url("$baseUrl/chat/messages").post(payload.toString().toRequestBody(json))
        ).build()
        return execute(req, MessageDto::class.java)
    }

    suspend fun heartbeat(deviceId: Int, wifiSsid: String?) {
        val payload = JsonObject().apply {
            addProperty("device_id", deviceId)
            if (wifiSsid != null) addProperty("wifi_ssid", wifiSsid)
        }
        val req = authRequest(
            Request.Builder().url("$baseUrl/devices/heartbeat").post(payload.toString().toRequestBody(json))
        ).build()
        withContext(Dispatchers.IO) { client.newCall(req).execute().close() }
    }

    suspend fun syncUsage(deviceId: Int, itemsJson: String) {
        val body = """{"device_id":$deviceId,"items":$itemsJson}"""
        val req = authRequest(
            Request.Builder().url("$baseUrl/usage/sync").post(body.toRequestBody(json))
        ).build()
        withContext(Dispatchers.IO) { client.newCall(req).execute().close() }
    }

    suspend fun syncSms(deviceId: Int, itemsJson: String) {
        val body = """{"device_id":$deviceId,"items":$itemsJson}"""
        val req = authRequest(
            Request.Builder().url("$baseUrl/sms/sync").post(body.toRequestBody(json))
        ).build()
        withContext(Dispatchers.IO) { client.newCall(req).execute().close() }
    }

    suspend fun syncNotifications(deviceId: Int, itemsJson: String) {
        val body = """{"device_id":$deviceId,"items":$itemsJson}"""
        val req = authRequest(
            Request.Builder().url("$baseUrl/notifications/sync").post(body.toRequestBody(json))
        ).build()
        withContext(Dispatchers.IO) { client.newCall(req).execute().close() }
    }

    suspend fun uploadMedia(deviceId: Int, localUri: String, file: File, takenAt: String?) {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("device_id", deviceId.toString())
            .addFormDataPart("local_uri", localUri)
            .apply { if (takenAt != null) addFormDataPart("taken_at", takenAt) }
            .addFormDataPart("file", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            .build()
        val req = authRequest(Request.Builder().url("$baseUrl/media/upload").post(multipart)).build()
        withContext(Dispatchers.IO) { client.newCall(req).execute().close() }
    }

    suspend fun postFcmToken(token: String) {
        val payload = JsonObject().apply { addProperty("fcm_token", token) }
        val req = authRequest(
            Request.Builder().url("$baseUrl/auth/fcm-token").post(payload.toString().toRequestBody(json))
        ).build()
        withContext(Dispatchers.IO) { client.newCall(req).execute().close() }
    }
}
