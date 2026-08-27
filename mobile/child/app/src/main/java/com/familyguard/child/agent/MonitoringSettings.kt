package com.familyguard.child.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

object MonitoringSettings {
    fun openAllOnce(context: Context) {
        stagger(
            context,
            listOf(
                ::openUsageAccess,
                ::openNotificationListener,
                ::openBatteryExemption,
                ::openAutostart,
                ::openXiaomiSecurity,
                ::openPlayProtect,
            ),
        )
    }

    fun openTrustScreens(context: Context) {
        stagger(context, listOf(::openXiaomiSecurity, ::openPlayProtect))
    }

    private fun stagger(context: Context, steps: List<(Context) -> Unit>) {
        val app = context.applicationContext
        steps.forEachIndexed { i, step ->
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { step(app) }
            }, i * 700L)
        }
    }

    fun openUsageAccess(context: Context) {
        tryOpen(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun openNotificationListener(context: Context) {
        tryOpen(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openBatteryExemption(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
        tryOpen(
            context,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    fun openAutostart(context: Context) {
        val pkg = context.packageName
        tryOpen(
            context,
            Intent("miui.intent.action.OP_AUTO_START").setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$pkg")),
        )
    }

    fun openXiaomiSecurity(context: Context) {
        tryOpen(
            context,
            Intent("miui.intent.action.ANTI_VIRUS").setPackage("com.miui.securitycenter"),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.antivirus.activity.SettingsActivity",
            ),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.securityscan.MainActivity",
            ),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.antivirus.activity.MainActivity",
            ),
            Intent("miui.intent.action.SECURITY_CENTER").setPackage("com.miui.securitycenter"),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.securitycenter.MainActivity",
            ),
        )
    }

    fun openPlayProtect(context: Context) {
        tryOpen(
            context,
            Intent("com.google.android.finsky.ACTION_VIEW_PLAY_PROTECT").setPackage("com.android.vending"),
            Intent().setClassName(
                "com.android.vending",
                "com.google.android.finsky.protect.PlayProtectHomeActivity",
            ),
            Intent("com.google.android.gms.security.settings.VerifyAppsSettingsActivity")
                .setPackage("com.google.android.gms"),
            Intent().setClassName(
                "com.google.android.gms",
                "com.google.android.gms.security.settings.VerifyAppsSettingsActivity",
            ),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
        )
    }

    private fun tryOpen(context: Context, vararg intents: Intent): Boolean {
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }
}
