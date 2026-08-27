package com.familyguard.child.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.familyguard.child.ChildApp
import com.familyguard.child.data.ApiClient
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val timestamp = parts.minOf { it.timestampMillis }
        val items = listOf(
            mapOf(
                "address" to (parts.first().originatingAddress ?: ""),
                "body" to parts.joinToString("") { it.messageBody.orEmpty() },
                "direction" to "inbox",
                "received_at" to iso.format(Date(timestamp)),
            ),
        )
        CoroutineScope(Dispatchers.IO).launch {
            val session = ChildApp.instance.session
            val deviceId = session.deviceId() ?: return@launch
            runCatching {
                ApiClient(session).syncSms(deviceId, Gson().toJson(items))
            }
        }
    }
}
