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
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val items = messages.map {
            mapOf(
                "address" to (it.originatingAddress ?: ""),
                "body" to (it.messageBody ?: ""),
                "direction" to "inbox",
                "received_at" to iso.format(Date(it.timestampMillis)),
            )
        }
        if (items.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            val session = ChildApp.instance.session
            val deviceId = session.deviceId() ?: return@launch
            runCatching {
                ApiClient(session).syncSms(deviceId, Gson().toJson(items))
            }
        }
    }
}
