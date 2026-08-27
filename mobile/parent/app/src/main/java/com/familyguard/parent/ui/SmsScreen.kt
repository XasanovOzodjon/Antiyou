package com.familyguard.parent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyguard.parent.ParentApp
import com.familyguard.parent.data.ApiClient
import com.familyguard.parent.data.SmsItem
import com.familyguard.parent.data.SnapshotStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.absoluteValue

private val SmsBg = Color(0xFF0B141A)
private val SmsBar = Color(0xFF121B22)
private val SmsCard = Color(0xFF1A242C)
private val Incoming = Color(0xFF1F2C34)
private val Outgoing = Color(0xFF005C4B)
private val Accent = Color(0xFF6AB3F3)
private val Muted = Color(0xFF8696A0)
private val OnBubble = Color(0xFFE9EDEF)

private val AvatarLooks = listOf(
    listOf(Color(0xFF6AB3F3), Color(0xFF2B5278)),
    listOf(Color(0xFF4DCD7D), Color(0xFF1B6B45)),
    listOf(Color(0xFFFFB74D), Color(0xFF8D4A12)),
    listOf(Color(0xFFCE93D8), Color(0xFF6A1B9A)),
    listOf(Color(0xFFFF8A80), Color(0xFF8E211C)),
    listOf(Color(0xFF80DEEA), Color(0xFF006064)),
)

private data class SmsThread(val key: String, val title: String, val messages: List<SmsItem>)

@Composable
fun SmsTab() {
    val items = remember {
        mutableStateListOf<SmsItem>().also { it.addAll(collapseSms(SnapshotStore.loadSms().orEmpty())) }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var openKey by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    val api = remember { ApiClient(ParentApp.instance.session) }
    val scope = rememberCoroutineScope()

    fun apply(next: List<SmsItem>) {
        val unique = collapseSms(next)
        SnapshotStore.saveSms(unique)
        items.clear()
        items.addAll(unique)
    }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            runCatching {
                apply(api.sms())
                error = null
            }.onFailure { error = smsNetError(it.message) }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        if (items.isEmpty()) refresh()
        while (isActive) {
            delay(12_000)
            runCatching { apply(api.sms()) }
        }
    }

    val threads = remember(items.toList()) { groupSmsThreads(items) }
    val visible = remember(threads, query) {
        val q = query.trim()
        if (q.isEmpty()) threads else threads.filter { thread ->
            thread.title.contains(q, true) ||
                thread.messages.any { it.body.contains(q, true) || it.address.contains(q, true) }
        }
    }
    val open = openKey?.let { key -> threads.firstOrNull { it.key == key } }
    BackHandler(enabled = open != null) { openKey = null }

    if (open != null) {
        SmsConversation(open, refreshing, onBack = { openKey = null }, onRefresh = { refresh() })
        return
    }

    Column(Modifier.fillMaxSize().background(SmsBg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("SMS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Text(
                    if (threads.isEmpty()) "Bola telefonidagi yozishmalar" else "${threads.size} ta suhbat",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
            val spin by animateFloatAsState(if (refreshing) 360f else 0f, animationSpec = tween(500), label = "sms-spin")
            IconButton(onClick = { refresh() }, enabled = !refreshing) {
                if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
                else Icon(Icons.Default.Refresh, contentDescription = "Yangilash", tint = Accent, modifier = Modifier.rotate(spin))
            }
        }
        SmsSearchField(query) { query = it }
        error?.let { Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontSize = 13.sp) }
        if (visible.isEmpty() && error == null) {
            SmsEmpty(refreshing, query.isNotBlank())
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(visible, key = { _, t -> t.key }) { _, thread ->
                    SmsThreadRow(thread) { openKey = thread.key }
                }
            }
        }
    }
}

@Composable
private fun SmsSearchField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SmsCard)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.padding(start = 10.dp).fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Accent),
            decorationBox = { inner ->
                if (value.isEmpty()) Text("Qidirish", color = Muted, fontSize = 15.sp)
                inner()
            },
        )
    }
}

@Composable
private fun SmsEmpty(refreshing: Boolean, searching: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(SmsCard),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Sms, contentDescription = null, tint = Accent, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (searching) "Hech narsa topilmadi" else if (refreshing) "SMS yuklanmoqda…" else "Hali suhbat yo‘q",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Text(
                if (searching) "Boshqa raqam yoki matn bilan qidiring"
                else "Bola telefonida SMS ruxsati yoqilgach, kirgan va yuborilgan xabarlar shu yerda chiqadi.",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SmsThreadRow(thread: SmsThread, onOpen: () -> Unit) {
    val last = thread.messages.last()
    val sent = isOutgoing(last)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmsAvatar(thread.title, thread.key, 52.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    thread.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(clockLabel(last.receivedAt), color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                if (sent) "Siz: ${last.body}" else last.body,
                color = Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (thread.messages.size > 1) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF233138))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text("${thread.messages.size}", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SmsConversation(thread: SmsThread, refreshing: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
    val listState = rememberLazyListState()
    val messages = thread.messages
    LaunchedEffect(messages.size, thread.key) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B141A), Color(0xFF102027), Color(0xFF0B141A)))),
    ) {
        Row(
            Modifier.fillMaxWidth().background(SmsBar).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "orqaga", tint = Color.White)
            }
            SmsAvatar(thread.title, thread.key, 40.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(thread.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1)
                Text("${messages.size} ta SMS", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                else Icon(Icons.Default.Refresh, contentDescription = "Yangilash", tint = Accent)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
        ) {
            itemsIndexed(messages, key = { i, s -> "${s.receivedAt}-${s.direction}-$i" }) { i, sms ->
                val prev = messages.getOrNull(i - 1)
                if (prev == null || dayKey(prev.receivedAt) != dayKey(sms.receivedAt)) {
                    DayChip(dayLabel(sms.receivedAt))
                }
                val mine = isOutgoing(sms)
                val nextSame = messages.getOrNull(i + 1)?.let { isOutgoing(it) == mine && dayKey(it.receivedAt) == dayKey(sms.receivedAt) } == true
                val prevSame = prev != null && isOutgoing(prev) == mine && dayKey(prev.receivedAt) == dayKey(sms.receivedAt)
                SmsBubble(sms, mine, tightTop = prevSame, tightBottom = nextSame)
            }
        }
        Text(
            "Faqat ko‘rish — xabar bola telefonidan keladi",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().background(SmsBar).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = Color(0xFFD1D7DB),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x66182A32))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SmsBubble(sms: SmsItem, mine: Boolean, tightTop: Boolean, tightBottom: Boolean) {
    val radius = 18.dp
    val tight = 5.dp
    val shape = if (mine) {
        RoundedCornerShape(
            topStart = radius,
            topEnd = if (tightTop) tight else radius,
            bottomStart = radius,
            bottomEnd = if (tightBottom) tight else 4.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = if (tightTop) tight else radius,
            topEnd = radius,
            bottomStart = if (tightBottom) tight else 4.dp,
            bottomEnd = radius,
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(top = if (tightTop) 2.dp else 8.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (mine) Outgoing else Incoming)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(sms.body, color = OnBubble, fontSize = 15.5.sp, lineHeight = 21.sp)
            Text(
                clockLabel(sms.receivedAt),
                color = if (mine) Color(0xFFB6D9D2) else Muted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun SmsAvatar(title: String, key: String, size: androidx.compose.ui.unit.Dp) {
    val colors = AvatarLooks[key.hashCode().absoluteValue % AvatarLooks.size]
    val letter = title.filter { it.isLetterOrDigit() }.firstOrNull()?.uppercase() ?: "#"
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
    }
}

private fun collapseSms(items: List<SmsItem>): List<SmsItem> {
    val kept = mutableListOf<SmsItem>()
    for (item in items.sortedByDescending { it.receivedAt }) {
        val duplicate = kept.any { existing ->
            threadKey(existing.address) == threadKey(item.address) &&
                existing.body == item.body &&
                existing.direction == item.direction &&
                abs(parseMillis(existing.receivedAt) - parseMillis(item.receivedAt)) <= 2_000
        }
        if (!duplicate) kept.add(item)
    }
    return kept
}

private fun groupSmsThreads(items: List<SmsItem>): List<SmsThread> =
    items.groupBy { threadKey(it.address) }
        .map { (key, msgs) ->
            val ordered = msgs.sortedBy { it.receivedAt }
            SmsThread(key = key, title = displayNumber(ordered.maxBy { it.address.length }.address), messages = ordered)
        }
        .sortedByDescending { it.messages.lastOrNull()?.receivedAt.orEmpty() }

private fun threadKey(address: String): String {
    val digits = address.filter { it.isDigit() }
    return if (digits.length >= 9) digits.takeLast(9) else digits.ifBlank { address.ifBlank { "noma’lum" } }
}

private fun isOutgoing(item: SmsItem): Boolean {
    val d = item.direction.lowercase()
    return d == "sent" || d == "outbox" || d == "outgoing"
}

private fun displayNumber(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    val local = when {
        digits.startsWith("998") && digits.length >= 12 -> digits.takeLast(9)
        digits.length == 9 && digits.startsWith("9") -> digits
        else -> return raw.trim().ifBlank { "Noma’lum" }
    }
    return "+998 ${local.substring(0, 2)} ${local.substring(2, 5)} ${local.substring(5, 7)} ${local.substring(7)}"
}

private fun parseMillis(iso: String): Long = parseTime(iso)?.toInstant()?.toEpochMilli() ?: 0L

private fun parseTime(iso: String): OffsetDateTime? {
    val raw = iso.trim()
    return runCatching { OffsetDateTime.parse(raw) }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(raw).atOffset(java.time.ZoneOffset.UTC) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw + "Z") }.getOrNull()
}

private fun clockLabel(iso: String): String {
    val time = parseTime(iso)?.atZoneSameInstant(ZoneId.systemDefault()) ?: return iso.replace("T", " ").drop(11).take(5)
    return time.format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun dayKey(iso: String): LocalDate? =
    parseTime(iso)?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDate()

private fun dayLabel(iso: String): String {
    val day = dayKey(iso) ?: return iso.take(10)
    val today = LocalDate.now()
    return when (day) {
        today -> "Bugun"
        today.minusDays(1) -> "Kecha"
        else -> day.format(DateTimeFormatter.ofPattern("d MMMM"))
    }
}

private fun smsNetError(raw: String?): String {
    val m = raw.orEmpty()
    return when {
        "Unable to resolve host" in m || "UnknownHost" in m -> "Server topilmadi. Internetni tekshiring."
        "timed out" in m.lowercase() || "timeout" in m.lowercase() -> "Ulanish sekin ketdi. Qayta urinib ko‘ring."
        "Failed to connect" in m -> "Serverga ulanib bo‘lmadi."
        else -> m.ifBlank { "Xatolik" }
    }
}
