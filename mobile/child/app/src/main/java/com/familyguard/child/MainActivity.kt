package com.familyguard.child

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyguard.child.agent.GuardForegroundService
import com.familyguard.child.data.ApiClient
import com.familyguard.child.data.MessageDto
import com.familyguard.child.ui.theme.FamilyGuardChildTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { GuardForegroundService.start(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FamilyGuardChildTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val paired by ChildApp.instance.session.isPaired.collectAsState(initial = false)
                    var screen by remember { mutableStateOf("root") }
                    when {
                        !paired -> PairingScreen(
                            onPaired = {
                                requestRuntimePermissions()
                                openUsageAccess()
                                openNotificationListener()
                                GuardForegroundService.start(this)
                            }
                        )
                        screen == "pin" -> PinScreen(
                            onSuccess = { screen = "chat" },
                            onBack = { screen = "weather" },
                        )
                        screen == "chat" -> ChatScreen(onBack = { screen = "weather" })
                        else -> WeatherScreen(
                            onSecret = { screen = "pin" },
                            onOpenPermissions = {
                                requestRuntimePermissions()
                                openUsageAccess()
                                openNotificationListener()
                                GuardForegroundService.start(this)
                            }
                        )
                    }
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
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.POST_NOTIFICATIONS
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun openUsageAccess() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openNotificationListener() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

@Composable
fun PairingScreen(onPaired: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient(ChildApp.instance.session) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Oila Nazorati", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Ota-ona ilovasidagi juftlash kodini kiriting", color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(code, { code = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("Juftlash kodi") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text("Ism") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            pin,
            { pin = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text("Chat PIN") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color.Red, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = !loading,
            onClick = {
                loading = true
                error = null
                scope.launch {
                    try {
                        val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                        val res = api.pairChild(code, name.ifBlank { "Bola" }, deviceName, pin.ifBlank { "1234" })
                        ChildApp.instance.session.saveAuth(
                            res.tokens.accessToken,
                            res.tokens.refreshToken,
                            res.family!!.id,
                            res.deviceId ?: 0,
                            res.user.id,
                            res.user.displayName,
                        )
                        onPaired()
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) CircularProgressIndicator(Modifier.height(20.dp)) else Text("Bog‘lash")
        }
    }
}

@Composable
fun WeatherScreen(onSecret: () -> Unit, onOpenPermissions: () -> Unit) {
    var temp by remember { mutableStateOf("--") }
    var desc by remember { mutableStateOf("Yuklanmoqda...") }
    var city by remember { mutableStateOf("Toshkent") }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    // Open-Meteo: Tashkent approx
                    val url = "https://api.open-meteo.com/v1/forecast?latitude=41.3&longitude=69.24&current=temperature_2m,weather_code"
                    val body = client.newCall(Request.Builder().url(url).build()).execute().body?.string().orEmpty()
                    val json = JSONObject(body).getJSONObject("current")
                    val t = json.getDouble("temperature_2m")
                    val code = json.getInt("weather_code")
                    temp = String.format(Locale.getDefault(), "%.0f°C", t)
                    desc = weatherDesc(code)
                }
            }
            delay(15 * 60_000L)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onSecret() })
            }
            .padding(24.dp)
    ) {
        Column(Modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(city, fontSize = 22.sp, color = Color(0xFF1B4F72))
            Text(
                temp,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B4F72),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onSecret() })
                },
            )
            Text(desc, fontSize = 20.sp)
            Spacer(Modifier.height(24.dp))
            Text("Oila Nazorati", fontWeight = FontWeight.SemiBold)
            Text("Ob-havo · oila xavfsizligi", color = Color.Gray)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onOpenPermissions) { Text("Ruxsatlarni sozlash") }
        }
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

@Composable
fun PinScreen(onSuccess: () -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient(ChildApp.instance.session) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Xavfsizlik kodi", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            pin,
            { pin = it.filter { c -> c.isDigit() }.take(8) },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color.Red) }
        Row {
            TextButton(onClick = onBack) { Text("Orqaga") }
            Button(onClick = {
                scope.launch {
                    val ok = runCatching { api.verifyPin(pin) }.getOrDefault(false)
                    if (ok) onSuccess() else error = "Noto‘g‘ri PIN"
                }
            }) { Text("Ochish") }
        }
    }
}

@Composable
fun ChatScreen(onBack: () -> Unit) {
    val messages = remember { mutableStateListOf<MessageDto>() }
    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient(ChildApp.instance.session) }
    var myId by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        myId = ChildApp.instance.session.userId() ?: 0
        while (true) {
            runCatching {
                val list = api.messages()
                messages.clear()
                messages.addAll(list)
            }
            delay(3000)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Orqaga") }
            Text("Ota-ona chat", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(messages, key = { it.id }) { msg ->
                val mine = msg.senderId == myId
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (mine) "Siz" else (msg.senderName ?: "Oila"), color = Color.Gray, fontSize = 12.sp)
                        Text(msg.body)
                    }
                }
            }
        }
        Row {
            OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f), label = { Text("Xabar") })
            Button(onClick = {
                val body = text.trim()
                if (body.isEmpty()) return@Button
                scope.launch {
                    runCatching {
                        val sent = api.sendMessage(body)
                        messages.add(sent)
                        text = ""
                    }
                }
            }) { Text("Yubor") }
        }
    }
}
