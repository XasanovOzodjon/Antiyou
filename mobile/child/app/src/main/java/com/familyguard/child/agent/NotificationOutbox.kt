package com.familyguard.child.agent

import com.familyguard.child.ChildApp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object NotificationOutbox {
    private val gson = Gson()
    private val lock = Any()

    fun add(item: Map<String, Any?>) {
        synchronized(lock) {
            val next = drainLocked().toMutableList()
            next.add(item)
            writeLocked(next)
        }
    }

    fun drain(): List<Map<String, Any?>> = synchronized(lock) { drainLocked() }

    fun restore(items: List<Map<String, Any?>>) {
        if (items.isEmpty()) return
        synchronized(lock) {
            val next = drainLocked().toMutableList()
            next.addAll(0, items)
            writeLocked(next)
        }
    }

    private fun file(): File = File(ChildApp.instance.filesDir, "notif_outbox.json")

    private fun drainLocked(): List<Map<String, Any?>> {
        val f = file()
        if (!f.exists()) return emptyList()
        val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
        val list = runCatching { gson.fromJson<List<Map<String, Any?>>>(f.readText(), type) }.getOrNull().orEmpty()
        f.delete()
        return list
    }

    private fun writeLocked(items: List<Map<String, Any?>>) {
        if (items.isEmpty()) {
            file().delete()
            return
        }
        file().writeText(gson.toJson(items))
    }
}
