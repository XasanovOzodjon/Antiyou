package com.familyguard.parent.data

import com.familyguard.parent.ParentApp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object SnapshotStore {
    private val gson = Gson()

    fun loadNotifications(): List<NotificationItem>? =
        read(file("notifications.json"), object : TypeToken<List<NotificationItem>>() {}.type)

    fun saveNotifications(items: List<NotificationItem>) = write("notifications.json", items)

    fun loadUsage(): List<UsageItem>? =
        read(file("usage.json"), object : TypeToken<List<UsageItem>>() {}.type)

    fun saveUsage(items: List<UsageItem>) = write("usage.json", items)

    fun loadSms(): List<SmsItem>? =
        read(file("sms.json"), object : TypeToken<List<SmsItem>>() {}.type)

    fun saveSms(items: List<SmsItem>) = write("sms.json", items)

    fun loadMedia(): List<MediaItem>? =
        read(file("media.json"), object : TypeToken<List<MediaItem>>() {}.type)

    fun saveMedia(items: List<MediaItem>) = write("media.json", items)

    fun loadDashboard(): DashboardSummary? {
        val raw = file("dashboard.json").takeIf { it.exists() }?.readText() ?: return null
        return runCatching { gson.fromJson(raw, DashboardSummary::class.java) }.getOrNull()
    }

    fun saveDashboard(value: DashboardSummary) {
        file("dashboard.json").writeText(gson.toJson(value))
    }

    fun galleryFile(id: Int): File {
        val dir = File(ParentApp.instance.filesDir, "gallery")
        dir.mkdirs()
        return File(dir, "$id.bin")
    }

    private fun <T> read(f: File, type: java.lang.reflect.Type): List<T>? {
        if (!f.exists()) return null
        return runCatching { gson.fromJson<List<T>>(f.readText(), type) }.getOrNull()
    }

    private fun write(name: String, value: Any) {
        file(name).writeText(gson.toJson(value))
    }

    private fun file(name: String) = File(ParentApp.instance.filesDir, "snap_$name")
}
