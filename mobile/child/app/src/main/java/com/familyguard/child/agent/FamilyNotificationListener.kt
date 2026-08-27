package com.familyguard.child.agent

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.familyguard.child.ChildApp
import com.familyguard.child.data.ApiClient
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FamilyNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence("android.title")?.toString()
        val text = extras?.getCharSequence("android.text")?.toString()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val item = mapOf(
            "package_name" to sbn.packageName,
            "title" to title,
            "text" to text,
            "posted_at" to iso.format(Date(sbn.postTime)),
        )
        NotificationOutbox.add(item)
        scope.launch { flushNow() }
    }

    private suspend fun flushNow() {
        val deviceId = runCatching { ChildApp.instance.session.deviceId() }.getOrNull() ?: return
        val batch = NotificationOutbox.drain()
        if (batch.isEmpty()) return
        val api = ApiClient(ChildApp.instance.session)
        runCatching { api.syncNotifications(deviceId, gson.toJson(batch)) }
            .onFailure { NotificationOutbox.restore(batch) }
    }
}
