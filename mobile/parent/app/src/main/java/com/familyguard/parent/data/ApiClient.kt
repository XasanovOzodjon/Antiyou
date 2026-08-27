package com.familyguard.parent.data

import com.familyguard.parent.BuildConfig
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

data class UsageItem(
    @SerializedName("package_name") val packageName: String,
    @SerializedName("app_label") val appLabel: String,
    @SerializedName("total_ms") val totalMs: Int,
    val day: String,
)

data class SmsItem(
    val address: String,
    val body: String,
    val direction: String,
    @SerializedName("received_at") val receivedAt: String,
)

data class NotificationItem(
    val id: Int,
    @SerializedName("package_name") val packageName: String,
    val title: String?,
    val text: String?,
    @SerializedName("posted_at") val postedAt: String,
)

data class MediaItem(
    val id: Int,
    val filename: String,
    @SerializedName("content_type") val contentType: String,
    val url: String,
    @SerializedName("taken_at") val takenAt: String?,
    @SerializedName("created_at") val createdAt: String,
)

data class DeviceDto(
    val id: Int,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("wifi_ssid") val wifiSsid: String?,
    @SerializedName("last_seen_at") val lastSeenAt: String?,
    @SerializedName("is_online") val isOnline: Boolean,
    @SerializedName("child_user_id") val childUserId: Int,
)

data class DashboardSummary(
    val family: FamilyDto,
    val devices: List<DeviceDto>,
    @SerializedName("top_apps_today") val topAppsToday: List<UsageItem>,
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

    private suspend fun auth(builder: Request.Builder): Request.Builder {
        session.accessToken()?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

    private suspend fun <T> exec(request: Request, clazz: Class<T>): T = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: $body")
            gson.fromJson(body, clazz)
        }
    }

    private suspend fun <T> execList(request: Request, type: java.lang.reflect.Type): T = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: $body")
            gson.fromJson(body, type)
        }
    }

    suspend fun register(email: String, password: String, name: String, family: String): AuthResponse {
        val payload = JsonObject().apply {
            addProperty("email", email)
            addProperty("password", password)
            addProperty("display_name", name)
            addProperty("family_name", family)
        }
        return exec(
            Request.Builder().url("$baseUrl/auth/register").post(payload.toString().toRequestBody(json)).build(),
            AuthResponse::class.java,
        )
    }

    suspend fun autoJoin(role: String, deviceName: String = "Android"): AuthResponse {
        val payload = JsonObject().apply {
            addProperty("role", role)
            addProperty("device_name", deviceName)
        }
        return exec(
            Request.Builder().url("$baseUrl/auth/auto-join").post(payload.toString().toRequestBody(json)).build(),
            AuthResponse::class.java,
        )
    }

    suspend fun login(email: String, password: String): AuthResponse {
        val payload = JsonObject().apply {
            addProperty("email", email)
            addProperty("password", password)
        }
        return exec(
            Request.Builder().url("$baseUrl/auth/login").post(payload.toString().toRequestBody(json)).build(),
            AuthResponse::class.java,
        )
    }

    suspend fun dashboard(): DashboardSummary {
        val req = auth(Request.Builder().url("$baseUrl/dashboard/summary")).get().build()
        return exec(req, DashboardSummary::class.java)
    }

    suspend fun messages(): List<MessageDto> {
        val req = auth(Request.Builder().url("$baseUrl/chat/messages")).get().build()
        return execList(req, object : TypeToken<List<MessageDto>>() {}.type)
    }

    suspend fun sendMessage(text: String): MessageDto {
        val payload = JsonObject().apply { addProperty("body", text) }
        val req = auth(Request.Builder().url("$baseUrl/chat/messages").post(payload.toString().toRequestBody(json))).build()
        return exec(req, MessageDto::class.java)
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
        val req = auth(Request.Builder().url("$baseUrl/chat/messages/media").post(multipart)).build()
        return exec(req, MessageDto::class.java)
    }

    suspend fun toggleReaction(messageId: Int, emoji: String): MessageDto {
        val payload = JsonObject().apply { addProperty("emoji", emoji) }
        val req = auth(
            Request.Builder().url("$baseUrl/chat/messages/$messageId/reactions").post(payload.toString().toRequestBody(json)),
        ).build()
        return exec(req, MessageDto::class.java)
    }

    suspend fun downloadTo(path: String, dest: File) {
        val req = auth(Request.Builder().url(mediaUrl(path))).get().build()
        withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                dest.parentFile?.mkdirs()
                dest.outputStream().use { out ->
                    resp.body?.byteStream()?.copyTo(out)
                }
            }
        }
    }

    suspend fun downloadFile(path: String): File {
        val req = auth(Request.Builder().url(mediaUrl(path))).get().build()
        return withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                File.createTempFile("fg_", ".bin", com.familyguard.parent.ParentApp.instance.cacheDir).apply {
                    writeBytes(bytes)
                }
            }
        }
    }

    suspend fun usage(): List<UsageItem> {
        val req = auth(Request.Builder().url("$baseUrl/usage")).get().build()
        return execList(req, object : TypeToken<List<UsageItem>>() {}.type)
    }

    suspend fun sms(): List<SmsItem> {
        val req = auth(Request.Builder().url("$baseUrl/sms")).get().build()
        return execList(req, object : TypeToken<List<SmsItem>>() {}.type)
    }

    suspend fun notifications(): List<NotificationItem> {
        val req = auth(Request.Builder().url("$baseUrl/notifications")).get().build()
        return execList(req, object : TypeToken<List<NotificationItem>>() {}.type)
    }

    suspend fun deleteNotification(id: Int) {
        val req = auth(Request.Builder().url("$baseUrl/notifications/$id")).delete().build()
        withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            }
        }
    }

    suspend fun deleteAllNotifications() {
        val req = auth(Request.Builder().url("$baseUrl/notifications")).delete().build()
        withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            }
        }
    }

    suspend fun media(): List<MediaItem> {
        val req = auth(Request.Builder().url("$baseUrl/media")).get().build()
        return execList(req, object : TypeToken<List<MediaItem>>() {}.type)
    }

    fun mediaUrl(path: String): String = if (path.startsWith("http")) path else "$baseUrl$path"

    suspend fun markRead() {
        val req = auth(Request.Builder().url("$baseUrl/chat/read").post("{}".toRequestBody(json))).build()
        withContext(Dispatchers.IO) { client.newCall(req).execute().close() }
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
        val req = auth(Request.Builder().url("$baseUrl/auth/fcm-token").post(payload.toString().toRequestBody(json))).build()
        withContext(Dispatchers.IO) { client.newCall(req).execute().close() }
    }
}
