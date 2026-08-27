package com.familyguard.child.agent

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object SmsInbox {
    private const val PREFS = "sync"
    private const val INBOX_TS = "sms_inbox_last_ts"
    private const val SENT_TS = "sms_sent_last_ts"
    private const val LEGACY_TS = "sms_last_ts"
    private const val WEEK_MS = 7L * 86_400_000L

    fun pending(context: Context): List<Map<String, Any>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lookback = System.currentTimeMillis() - WEEK_MS
        val inboxFrom = prefs.getLong(INBOX_TS, prefs.getLong(LEGACY_TS, lookback))
        val sentFrom = prefs.getLong(SENT_TS, lookback)
        val inbox = readBox(context, Telephony.Sms.Inbox.CONTENT_URI, "inbox", inboxFrom)
        val sent = readBox(context, Telephony.Sms.Sent.CONTENT_URI, "sent", sentFrom)
        return inbox + sent
    }

    fun markSynced(context: Context, items: List<Map<String, Any>>) {
        if (items.isEmpty()) return
        var inboxMax = 0L
        var sentMax = 0L
        for (item in items) {
            val ts = item["_ts"] as? Long ?: continue
            when (item["direction"]) {
                "sent" -> if (ts > sentMax) sentMax = ts
                else -> if (ts > inboxMax) inboxMax = ts
            }
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (inboxMax > 0) editor.putLong(INBOX_TS, inboxMax)
        if (sentMax > 0) editor.putLong(SENT_TS, sentMax)
        editor.apply()
    }

    fun payload(items: List<Map<String, Any>>): List<Map<String, Any>> =
        items.map { it.filterKeys { key -> key != "_ts" } }

    private fun readBox(context: Context, uri: android.net.Uri, direction: String, after: Long): List<Map<String, Any>> {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val cursor: Cursor? = try {
            context.contentResolver.query(
                uri,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ?",
                arrayOf(after.toString()),
                "${Telephony.Sms.DATE} DESC",
            )
        } catch (_: SecurityException) {
            null
        }
        val items = mutableListOf<Map<String, Any>>()
        cursor?.use {
            while (it.moveToNext()) {
                val date = it.getLong(2)
                items.add(
                    mapOf(
                        "address" to (it.getString(0) ?: ""),
                        "body" to (it.getString(1) ?: ""),
                        "direction" to direction,
                        "received_at" to iso.format(Date(date)),
                        "_ts" to date,
                    ),
                )
            }
        }
        return items
    }
}
