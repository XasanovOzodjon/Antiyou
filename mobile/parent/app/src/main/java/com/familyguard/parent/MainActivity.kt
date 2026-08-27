package com.familyguard.parent

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.familyguard.parent.data.ApiClient
import com.familyguard.parent.data.DashboardSummary
import com.familyguard.parent.data.MediaItem
import com.familyguard.parent.data.NotificationItem
import com.familyguard.parent.data.SnapshotStore
import com.familyguard.parent.data.UsageItem
import com.familyguard.parent.ui.FamilyChat
import com.familyguard.parent.ui.SmsTab
import com.familyguard.parent.ui.theme.FamilyGuardParentTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notifyPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("open_chat", false)) ChatNotify.requestOpenChat()
        ChatNotify.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33) {
            notifyPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            FamilyGuardParentTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AutoConnectGate()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_chat", false)) ChatNotify.requestOpenChat()
    }
}

@Composable
private fun AutoConnectGate() {
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val api = remember { ApiClient(ParentApp.instance.session) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                val res = api.autoJoin("parent")
                ParentApp.instance.session.saveAuth(
                    res.tokens.accessToken,
                    res.tokens.refreshToken,
                    res.family!!.id,
                    res.user.id,
                    res.family.pairingCode,
                )
                ready = true
                error = null
                return@LaunchedEffect
            }.onFailure { error = friendlyNet(it.message) }
            delay(3000)
        }
    }
    if (!ready) ConnectingScreen(error) else MainTabs()
}

@Composable
private fun ConnectingScreen(error: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Oilaga ulanmoqda…", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text("Login kerak emas", color = Color(0xFF8E9BA8), fontSize = 13.sp)
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
fun MainTabs() {
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val api = remember { ApiClient(ParentApp.instance.session) }
    val openTick by ChatNotify.openChatTick.collectAsState()
    LaunchedEffect(openTick) {
        if (openTick > 0) tab = 1
    }
    LaunchedEffect(Unit) {
        ChatNotify.ensureChannel(context)
        val familyId = ParentApp.instance.session.familyId() ?: 0
        val token = ParentApp.instance.session.accessToken().orEmpty()
        val myId = ParentApp.instance.session.userId() ?: 0
        while (isActive) {
            val closed = CompletableDeferred<Unit>()
            api.openChat(
                familyId,
                token,
                onMessage = { _, msg ->
                    if (msg.senderId != myId) {
                        ChatNotify.show(context, ChatNotify.previewOf(msg))
                    }
                },
                onRead = {},
                onClosed = { closed.complete(Unit) },
                listen = true,
            )
            closed.await()
            delay(1200)
        }
    }
    val labels = listOf("Uy", "Chat", "Vaqt", "SMS", "Xabar", "Rasm")
    val icons = listOf(
        Icons.Default.Home,
        Icons.AutoMirrored.Filled.Chat,
        Icons.Default.Timer,
        Icons.Default.Sms,
        Icons.Default.Notifications,
        Icons.Default.Image,
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xF217212B))
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels.forEachIndexed { index, label ->
                    val selected = tab == index
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { tab = index }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (selected) Color(0xFF2B5278) else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                icons[index],
                                contentDescription = label,
                                tint = if (selected) Color(0xFF6AB3F3) else Color(0xFF8E9BA8),
                            )
                        }
                        Text(
                            label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color(0xFF6AB3F3) else Color(0xFF8E9BA8),
                        )
                    }
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(140))
            },
            label = "tabs",
        ) { current ->
            when (current) {
                0 -> DashboardScreen()
                1 -> FamilyChat("Bola")
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
    var summary by remember { mutableStateOf(SnapshotStore.loadDashboard()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val api = remember { ApiClient(ParentApp.instance.session) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            runCatching {
                val next = api.dashboard()
                SnapshotStore.saveDashboard(next)
                summary = next
                error = null
            }.onFailure { error = friendlyNet(it.message) }
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { if (summary == null) refresh() }
    val device = summary?.devices?.firstOrNull()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        ScreenHeader("Family Guard", refreshing, onRefresh = { refresh() })
        Text("Bitta ota-ona · bitta bola", color = Color(0xFF8E9BA8))
        Spacer(Modifier.height(16.dp))
        error?.let { Text(it, color = Color(0xFFFF8A80)) }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF17212B)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF2B5278)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("B", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(device?.deviceName ?: "Bolani telefoni", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(
                                if (device?.isOnline == true) Color(0xFF4DCD7D) else Color(0xFF8E9BA8),
                            ),
                        )
                        Text(
                            if (device == null) "Hali ulanmagan" else if (device.isOnline) "  Online" else "  Offline",
                            color = if (device?.isOnline == true) Color(0xFF4DCD7D) else Color(0xFF8E9BA8),
                            fontSize = 13.sp,
                        )
                    }
                    Text("Wi‑Fi: ${device?.wifiSsid ?: "—"}", color = Color(0xFF8E9BA8), fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Bugun", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        val apps = summary?.topAppsToday.orEmpty()
        if (apps.isEmpty()) {
            EmptyHint("Ilova vaqti hali yo‘q. Bola telefonida Cover ochiq bo‘lsin va Usage Access yoqilsin.")
        } else {
            apps.take(5).forEach { u ->
                Text("${u.appLabel}  ·  ${formatDuration(u.totalMs)}", modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = Color(0xFF8E9BA8), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ScreenHeader(title: String, refreshing: Boolean, onRefresh: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        trailing()
        val spin by animateFloatAsState(if (refreshing) 360f else 0f, animationSpec = tween(500), label = "spin")
        IconButton(onClick = onRefresh, enabled = !refreshing) {
            if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF6AB3F3))
            else Icon(Icons.Default.Refresh, contentDescription = "Yangilash", tint = Color(0xFF6AB3F3), modifier = Modifier.rotate(spin))
        }
    }
}

@Composable
fun UsageTab() {
    val items = remember { mutableStateListOf<UsageItem>().also { it.addAll(SnapshotStore.loadUsage().orEmpty()) } }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val api = remember { ApiClient(ParentApp.instance.session) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            runCatching {
                val next = api.usage()
                SnapshotStore.saveUsage(next)
                items.clear()
                items.addAll(next)
                error = null
            }.onFailure { error = friendlyNet(it.message) }
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { if (items.isEmpty()) refresh() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ScreenHeader("Ilova vaqti", refreshing, onRefresh = { refresh() })
            error?.let { Text(it, color = Color(0xFFFF8A80)) }
            if (items.isEmpty() && error == null && !refreshing) EmptyHint("Hali ma’lumot yo‘q — bola telefonida Usage Access ni yoqing.")
        }
        items(items) { u ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17212B)), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(u.appLabel, fontWeight = FontWeight.SemiBold)
                        Text(u.packageName, fontSize = 11.sp, color = Color(0xFF8E9BA8))
                    }
                    Text(formatDuration(u.totalMs), color = Color(0xFF6AB3F3))
                }
            }
        }
    }
}

@Composable
fun NotificationsTab() {
    val items = remember { mutableStateListOf<NotificationItem>().also { it.addAll(SnapshotStore.loadNotifications().orEmpty()) } }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val api = remember { ApiClient(ParentApp.instance.session) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            runCatching {
                val next = api.notifications()
                SnapshotStore.saveNotifications(next)
                items.clear()
                items.addAll(next)
                error = null
            }.onFailure { error = friendlyNet(it.message) }
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { if (items.isEmpty()) refresh() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ScreenHeader("Bildirishnomalar", refreshing, onRefresh = { refresh() }) {
                if (items.isNotEmpty()) {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { api.deleteAllNotifications() }
                            items.clear()
                            SnapshotStore.saveNotifications(emptyList())
                        }
                    }) { Text("Tozalash") }
                }
            }
            error?.let { Text(it, color = Color(0xFFFF8A80)) }
            if (items.isEmpty() && error == null && !refreshing) {
                EmptyHint("Bildirishnoma yo‘q. Bola telefonida Notification Listener ni yoqing, keyin istalgan ilovadan xabar keling.")
            }
        }
        items(items, key = { it.id }) { n ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17212B)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(n.title ?: n.packageName, fontWeight = FontWeight.SemiBold)
                    if (!n.text.isNullOrBlank()) Text(n.text)
                    Text("${n.packageName} · ${n.postedAt}", fontSize = 11.sp, color = Color(0xFF8E9BA8))
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { api.deleteNotification(n.id) }
                            items.removeAll { it.id == n.id }
                            SnapshotStore.saveNotifications(items.toList())
                        }
                    }) { Text("O‘chirish") }
                }
            }
        }
    }
}

@Composable
fun GalleryTab() {
    val items = remember { mutableStateListOf<MediaItem>().also { it.addAll(SnapshotStore.loadMedia().orEmpty()) } }
    var error by remember { mutableStateOf<String?>(null) }
    val api = remember { ApiClient(ParentApp.instance.session) }
    var token by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf<MediaItem?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun cachePhotos(list: List<MediaItem>) {
        list.forEach { m ->
            val dest = SnapshotStore.galleryFile(m.id)
            if (!dest.exists() || dest.length() == 0L) {
                runCatching { api.downloadTo(m.url, dest) }
            }
        }
    }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            runCatching {
                val next = api.media()
                SnapshotStore.saveMedia(next)
                cachePhotos(next)
                items.clear()
                items.addAll(next)
                error = null
            }.onFailure { error = friendlyNet(it.message) }
            refreshing = false
        }
    }
    LaunchedEffect(Unit) {
        token = ParentApp.instance.session.accessToken().orEmpty()
        if (items.isEmpty()) refresh()
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader("Galereya", refreshing, onRefresh = { refresh() })
        error?.let { Text(it, color = Color(0xFFFF8A80)) }
        if (items.isEmpty() && error == null && !refreshing) EmptyHint("Rasmlar hali yo‘q. Bola telefonida galereya ruxsatini bering.")
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
            gridItems(items, key = { it.id }) { m ->
                val local = SnapshotStore.galleryFile(m.id)
                AsyncImage(
                    model = ImageRequest.Builder(ParentApp.instance)
                        .data(if (local.exists()) local else api.mediaUrl(m.url))
                        .memoryCacheKey("media-${m.id}")
                        .diskCacheKey("media-${m.id}")
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .apply {
                            if (!local.exists()) {
                                addHeader("Authorization", "Bearer $token")
                                addHeader("ngrok-skip-browser-warning", "1")
                            }
                        }
                        .build(),
                    contentDescription = m.filename,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(3.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .fillMaxWidth()
                        .clickable { opened = m },
                )
            }
        }
    }
    val current = opened
    if (current != null) {
        Dialog(
            onDismissRequest = { opened = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { opened = null },
                contentAlignment = Alignment.Center,
            ) {
                val local = SnapshotStore.galleryFile(current.id)
                AsyncImage(
                    model = ImageRequest.Builder(ParentApp.instance)
                        .data(if (local.exists()) local else api.mediaUrl(current.url))
                        .memoryCacheKey("media-${current.id}")
                        .diskCacheKey("media-${current.id}")
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .apply {
                            if (!local.exists()) {
                                addHeader("Authorization", "Bearer $token")
                                addHeader("ngrok-skip-browser-warning", "1")
                            }
                        }
                        .build(),
                    contentDescription = current.filename,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
                Text(
                    "Yopish",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                )
            }
        }
    }
}

private fun formatDuration(ms: Int): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return if (h > 0) "${h} soat ${m} daq" else "${m} daqiqa"
}

private fun friendlyNet(raw: String?): String {
    val m = raw.orEmpty()
    return when {
        "Unable to resolve host" in m || "No address associated" in m || "UnknownHost" in m ->
            "Server topilmadi. Mac o‘chmasin, internetni tekshiring. Telefondagi VPN ni o‘chiring."
        "timed out" in m.lowercase() || "timeout" in m.lowercase() ->
            "Ulanish sekin ketdi. Qayta urinib ko‘ring."
        "Failed to connect" in m ->
            "Serverga ulanib bo‘lmadi. Backend ishlayotganini tekshiring."
        else -> m.ifBlank { "Xatolik" }
    }
}
