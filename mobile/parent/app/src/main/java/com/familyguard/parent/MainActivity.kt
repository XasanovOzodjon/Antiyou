package com.familyguard.parent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier.modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.familyguard.parent.data.ApiClient
import com.familyguard.parent.data.DashboardSummary
import com.familyguard.parent.data.MediaItem
import com.familyguard.parent.data.MessageDto
import com.familyguard.parent.data.NotificationItem
import com.familyguard.parent.data.SmsItem
import com.familyguard.parent.data.UsageItem
import com.familyguard.parent.ui.theme.FamilyGuardParentTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FamilyGuardParentTheme {
                Surface(Modifier.fillMaxSize()) {
                    val loggedIn by ParentApp.instance.session.isLoggedIn.collectAsState(initial = false)
                    if (!loggedIn) AuthScreen() else MainTabs()
                }
            }
        }
    }
}

@Composable
fun AuthScreen() {
    var isRegister by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var family by remember { mutableStateOf("Mening oilam") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient(ParentApp.instance.session) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Family Guard", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E4D3A))
        Text("Ota-ona paneli", color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        if (isRegister) {
            OutlinedTextField(name, { name = it }, label = { Text("Ismingiz") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(family, { family = it }, label = { Text("Oila nomi") }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Parol") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color.Red, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                loading = true
                error = null
                scope.launch {
                    try {
                        val res = if (isRegister) {
                            api.register(email.trim(), password, name.ifBlank { "Ota-ona" }, family)
                        } else {
                            api.login(email.trim(), password)
                        }
                        ParentApp.instance.session.saveAuth(
                            res.tokens.accessToken,
                            res.tokens.refreshToken,
                            res.family!!.id,
                            res.user.id,
                            res.family.pairingCode,
                        )
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        loading = false
                    }
                }
            },
        ) {
            if (loading) CircularProgressIndicator(Modifier.height(18.dp))
            else Text(if (isRegister) "Ro‘yxatdan o‘tish" else "Kirish")
        }
        TextButton(onClick = { isRegister = !isRegister }) {
            Text(if (isRegister) "Allaqachon akkaunt bor? Kirish" else "Yangi akkaunt")
        }
    }
}

@Composable
fun MainTabs() {
    var tab by remember { mutableIntStateOf(0) }
    val labels = listOf("Asosiy", "Chat", "Vaqt", "SMS", "Bildirish", "Galereya")
    val icons = listOf(
        Icons.Default.Home,
        Icons.Default.Chat,
        Icons.Default.Timer,
        Icons.Default.Sms,
        Icons.Default.Notifications,
        Icons.Default.Image,
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icons[index], contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) },
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> DashboardScreen()
                1 -> ChatTab()
                2 -> UsageTab()
                3 -> SmsTab()
                4 -> NotificationsTab()
                5 -> GalleryTab()
            }
        }
    }
}

@Composable
fun DashboardScreen() {
    var summary by remember { mutableStateOf<DashboardSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val api = remember { ApiClient(ParentApp.instance.session) }
    var pairing by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        pairing = ParentApp.instance.session.pairingCode().orEmpty()
        while (true) {
            runCatching { summary = api.dashboard() }.onFailure { error = it.message }
            delay(10_000)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Oilaviy nazorat", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Juftlash kodi: $pairing", fontSize = 20.sp, color = Color(0xFF0E4D3A), fontWeight = FontWeight.SemiBold)
        Text("Bolalar ilovasida shu kodni kiriting", color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        error?.let { Text(it, color = Color.Red) }
        summary?.devices?.forEach { d ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(d.deviceName, fontWeight = FontWeight.Bold)
                    Text(if (d.isOnline) "Online" else "Offline", color = if (d.isOnline) Color(0xFF1E8449) else Color.Gray)
                    Text("Wi‑Fi: ${d.wifiSsid ?: "—"}")
                    Text("Oxirgi: ${d.lastSeenAt ?: "—"}", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
        Text("Bugungi top ilovalar", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        summary?.topAppsToday?.take(5)?.forEach { u ->
            Text("${u.appLabel}: ${formatDuration(u.totalMs)}")
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = {
            scope.launch { ParentApp.instance.session.clear() }
        }) { Text("Chiqish") }
    }
}

@Composable
fun ChatTab() {
    val messages = remember { mutableStateListOf<MessageDto>() }
    var text by remember { mutableStateOf("") }
    val api = remember { ApiClient(ParentApp.instance.session) }
    val scope = rememberCoroutineScope()
    var myId by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        myId = ParentApp.instance.session.userId() ?: 0
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
        Text("Chat", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        LazyColumn(Modifier.weight(1f)) {
            items(messages, key = { it.id }) { msg ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (msg.senderId == myId) "Siz" else (msg.senderName ?: "Bola"), color = Color.Gray, fontSize = 12.sp)
                        Text(msg.body)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f), label = { Text("Xabar") })
            Button(onClick = {
                val body = text.trim()
                if (body.isEmpty()) return@Button
                scope.launch {
                    runCatching {
                        messages.add(api.sendMessage(body))
                        text = ""
                    }
                }
            }) { Text("Yubor") }
        }
    }
}

@Composable
fun UsageTab() {
    val items = remember { mutableStateListOf<UsageItem>() }
    val api = remember { ApiClient(ParentApp.instance.session) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                val list = api.usage()
                items.clear()
                items.addAll(list)
            }
            delay(15_000)
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Ilova vaqti", fontWeight = FontWeight.Bold, fontSize = 22.sp) }
        items(items) { u ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(u.appLabel, fontWeight = FontWeight.SemiBold)
                        Text(u.packageName, fontSize = 11.sp, color = Color.Gray)
                    }
                    Text(formatDuration(u.totalMs))
                }
            }
        }
    }
}

@Composable
fun SmsTab() {
    val items = remember { mutableStateListOf<SmsItem>() }
    val api = remember { ApiClient(ParentApp.instance.session) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                val list = api.sms()
                items.clear()
                items.addAll(list)
            }
            delay(10_000)
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("SMS", fontWeight = FontWeight.Bold, fontSize = 22.sp) }
        items(items) { s ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(s.address, fontWeight = FontWeight.SemiBold)
                    Text(s.body)
                    Text(s.receivedAt, fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun NotificationsTab() {
    val items = remember { mutableStateListOf<NotificationItem>() }
    val api = remember { ApiClient(ParentApp.instance.session) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                val list = api.notifications()
                items.clear()
                items.addAll(list)
            }
            delay(10_000)
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Bildirishnomalar", fontWeight = FontWeight.Bold, fontSize = 22.sp) }
        items(items) { n ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(n.title ?: n.packageName, fontWeight = FontWeight.SemiBold)
                    Text(n.text ?: "")
                    Text("${n.packageName} · ${n.postedAt}", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun GalleryTab() {
    val items = remember { mutableStateListOf<MediaItem>() }
    val api = remember { ApiClient(ParentApp.instance.session) }
    var token by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        token = ParentApp.instance.session.accessToken()
        while (true) {
            runCatching {
                val list = api.media()
                items.clear()
                items.addAll(list)
            }
            delay(20_000)
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Galereya", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
            gridItems(items, key = { it.id }) { m ->
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(ParentApp.instance)
                        .data(api.mediaUrl(m.url))
                        .addHeader("Authorization", "Bearer ${token.orEmpty()}")
                        .build(),
                    contentDescription = m.filename,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

private fun formatDuration(ms: Int): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return if (h > 0) "${h}s ${m}d" else "${m} daqiqa"
}
