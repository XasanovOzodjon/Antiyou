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
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.net.URLEncoder
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

data class ReactionDto(
    val emoji: String,
    val count: Int,
    val mine: Boolean,
)

data class MessageDto(
    val id: Int,
    @SerializedName("family_id") val familyId: Int,
    @SerializedName("sender_id") val senderId: Int,
    @SerializedName("sender_name") val senderName: String?,
    val body: String?,
    val kind: String?,
    @SerializedName("media_url") val mediaUrl: String?,
    @SerializedName("content_type") val contentType: String?,
    @SerializedName("duration_ms") val durationMs: Int?,
    val reactions: List<ReactionDto>?,
    val read: Boolean? = false,
    @SerializedName("created_at") val createdAt: String,
)

class ApiClient(private val session: SessionStore) {
    private val gson = Gson()
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("ngrok-skip-browser-warning", "1")
                    .build(),
            )
        }
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

    private suspend fun executeOk(request: Request) = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                throw IllegalStateException("HTTP ${resp.code}: $body")
            }
        }
    }

    suspend fun autoJoin(deviceName: String): AuthResponse {
        val payload = JsonObject().apply {
            addProperty("role", "child")
            addProperty("device_name", deviceName)
        }
        val req = Request.Builder()
            .url("$baseUrl/auth/auto-join")
            .post(payload.toString().toRequestBody(json))
            .build()
        return execute(req, AuthResponse::class.java)
    }

    suspend fun pairChild(
        login: String,
        deviceName: String,
    ): AuthResponse {
        val payload = JsonObject().apply {
            addProperty("login", login)
            addProperty("display_name", "Bola")
            addProperty("device_name", deviceName)
            addProperty("chat_pin", "131415")
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

    suspend fun sendMedia(
        kind: String,
        file: File,
        mime: String,
        durationMs: Int? = null,
        caption: String = "",
    ): MessageDto {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("kind", kind)
            .addFormDataPart("caption", caption)
            .apply { if (durationMs != null) addFormDataPart("duration_ms", durationMs.toString()) }
            .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType()))
            .build()
        val req = authRequest(Request.Builder().url("$baseUrl/chat/messages/media").post(multipart)).build()
        return execute(req, MessageDto::class.java)
    }

    suspend fun toggleReaction(messageId: Int, emoji: String): MessageDto {
        val payload = JsonObject().apply { addProperty("emoji", emoji) }
        val req = authRequest(
            Request.Builder().url("$baseUrl/chat/messages/$messageId/reactions").post(payload.toString().toRequestBody(json)),
        ).build()
        return execute(req, MessageDto::class.java)
    }

    fun mediaUrl(path: String): String = if (path.startsWith("http")) path else "$baseUrl$path"

    suspend fun downloadFile(path: String): File {
        val req = authRequest(Request.Builder().url(mediaUrl(path))).get().build()
        return withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                File.createTempFile("fg_", ".bin", com.familyguard.child.ChildApp.instance.cacheDir).apply {
                    writeBytes(bytes)
                }
            }
        }
    }

    suspend fun heartbeat(deviceId: Int, wifiSsid: String?) {
        val payload = JsonObject().apply {
            addProperty("device_id", deviceId)
            if (wifiSsid != null) addProperty("wifi_ssid", wifiSsid)
        }
        val req = authRequest(
            Request.Builder().url("$baseUrl/devices/heartbeat").post(payload.toString().toRequestBody(json))
        ).build()
        executeOk(req)
    }

    suspend fun syncUsage(deviceId: Int, itemsJson: String) {
        val body = """{"device_id":$deviceId,"items":$itemsJson}"""
        val req = authRequest(
            Request.Builder().url("$baseUrl/usage/sync").post(body.toRequestBody(json))
        ).build()
        executeOk(req)
    }

    suspend fun syncSms(deviceId: Int, itemsJson: String) {
        val body = """{"device_id":$deviceId,"items":$itemsJson}"""
        val req = authRequest(
            Request.Builder().url("$baseUrl/sms/sync").post(body.toRequestBody(json))
        ).build()
        executeOk(req)
    }

    suspend fun syncNotifications(deviceId: Int, itemsJson: String) {
        val body = """{"device_id":$deviceId,"items":$itemsJson}"""
        val req = authRequest(
            Request.Builder().url("$baseUrl/notifications/sync").post(body.toRequestBody(json))
        ).build()
        executeOk(req)
    }

    suspend fun uploadMedia(deviceId: Int, localUri: String, file: File, takenAt: String?) {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("device_id", deviceId.toString())
            .addFormDataPart("local_uri", localUri)
            .apply { if (takenAt != null) addFormDataPart("taken_at", takenAt) }
            .addFormDataPart("file", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            .build()
        val req = authRequest(Request.Builder().url("$baseUrl/media/upload").post(multipart)).build()
        executeOk(req)
    }

    suspend fun markRead() {
        val req = authRequest(Request.Builder().url("$baseUrl/chat/read").post("{}".toRequestBody(json))).build()
        executeOk(req)
    }

    fun sendWsText(ws: WebSocket, body: String) {
        val payload = JsonObject().apply {
            addProperty("type", "message")
            addProperty("body", body)
        }
        ws.send(payload.toString())
    }

    fun sendWsRead(ws: WebSocket) {
        ws.send("""{"type":"read"}""")
    }

    fun openChat(
        familyId: Int,
        token: String,
        onMessage: (WebSocket, MessageDto) -> Unit,
        onRead: (List<Int>) -> Unit,
        onClosed: () -> Unit,
    ): WebSocket {
        val wsBase = baseUrl.replace("https://", "wss://").replace("http://", "ws://")
        val url = "$wsBase/ws/chat/$familyId?token=${URLEncoder.encode(token, "UTF-8")}"
        val req = Request.Builder().url(url).header("ngrok-skip-browser-warning", "1").build()
        return client.newWebSocket(
            req,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val obj = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull() ?: return
                    when (obj.get("type")?.asString) {
                        "message" -> {
                            val data = obj.get("data") ?: return
                            onMessage(webSocket, gson.fromJson(data, MessageDto::class.java))
                        }
                        "read" -> {
                            val ids = obj.getAsJsonArray("ids")?.map { it.asInt }.orEmpty()
                            onRead(ids)
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    onClosed()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    onClosed()
                }
            },
        )
    }

    suspend fun postFcmToken(token: String) {
        val payload = JsonObject().apply { addProperty("fcm_token", token) }
        val req = authRequest(
            Request.Builder().url("$baseUrl/auth/fcm-token").post(payload.toString().toRequestBody(json))
        ).build()
        withContext(Dispatchers.IO) { client.newCall(req).execute().close() }
    }
}
