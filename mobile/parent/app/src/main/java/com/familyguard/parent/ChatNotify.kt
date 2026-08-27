package com.familyguard.parent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.familyguard.parent.data.MessageDto
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

object ChatNotify {
    private const val ChannelId = "family_chat"
    private const val NotifId = 71

    val chatOpen = AtomicBoolean(false)
    val openChatTick = MutableStateFlow(0)

    fun requestOpenChat() {
        openChatTick.value += 1
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ChannelId,
            "Family Chat",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Boladan kelgan xabarlar"
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun previewOf(msg: MessageDto): String {
        val body = msg.body.orEmpty().trim()
        return when (msg.kind) {
            "photo" -> body.ifBlank { "Rasm" }
            "voice" -> "Ovozli xabar"
            "video_note" -> "Video"
            "file" -> body.ifBlank { "Fayl" }
            else -> body.ifBlank { "Yangi xabar" }
        }
    }

    fun show(context: Context, preview: String) {
        if (chatOpen.get()) return
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            if (chatOpen.get()) return@post
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return@post
            }
            ensureChannel(app)
            val open = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_chat", true)
            }
            val pi = PendingIntent.getActivity(
                app,
                0,
                open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notif = NotificationCompat.Builder(app, ChannelId)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Bola")
                .setContentText(preview)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()
            NotificationManagerCompat.from(app).notify(NotifId, notif)
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NotifId)
    }
}
