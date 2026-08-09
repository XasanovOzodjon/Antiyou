package com.familyguard.child.agent

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FamilyNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence("android.title")?.toString()
        val text = extras?.getCharSequence("android.text")?.toString()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        GuardForegroundService.pendingNotifications.offer(
            mapOf(
                "package_name" to sbn.packageName,
                "title" to title,
                "text" to text,
                "posted_at" to iso.format(Date(sbn.postTime)),
            )
        )
    }
}
