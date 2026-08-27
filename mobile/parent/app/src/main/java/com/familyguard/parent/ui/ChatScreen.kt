package com.familyguard.parent.ui

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.familyguard.parent.ParentApp
import com.familyguard.parent.data.ApiClient
import com.familyguard.parent.data.MessageDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.WebSocket
import java.io.File

private val ChatBg = Color(0xFF0E1621)
private val Bar = Color(0xFF17212B)
private val Outgoing = Color(0xFF2B5278)
private val Incoming = Color(0xFF182533)
private val Accent = Color(0xFF6AB3F3)
private val Emojis = listOf("❤️", "👍", "😂", "🔥", "😮", "😢")

@Composable
fun FamilyChat(title: String, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val api = remember { ApiClient(ParentApp.instance.session) }
    val messages = remember { mutableStateListOf<MessageDto>() }
    var text by remember { mutableStateOf("") }
    var myId by remember { mutableStateOf(0) }
    var token by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recMs by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var socket by remember { mutableStateOf<WebSocket?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val recorder = remember { VoiceHolder() }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var videoFile by remember { mutableStateOf<File?>(null) }

    fun upsert(msg: MessageDto) {
        val i = messages.indexOfFirst { it.id == msg.id }
        if (i >= 0) messages[i] = msg else messages.add(msg)
    }

    fun applyRead(ids: List<Int>) {
        ids.forEach { id ->
            val i = messages.indexOfFirst { it.id == id }
            if (i >= 0) messages[i] = messages[i].copy(read = true)
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val file = copyUri(context, uri, "photo_${System.currentTimeMillis()}.jpg")
                upsert(api.sendMedia("photo", file, "image/jpeg"))
            }.onFailure { error = it.message }
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val name = "file_${System.currentTimeMillis()}"
                val file = copyUri(context, uri, name)
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                upsert(api.sendMedia("file", file, mime))
            }.onFailure { error = it.message }
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = photoFile
        if (!ok || file == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { upsert(api.sendMedia("photo", file, "image/jpeg")) }.onFailure { error = it.message }
        }
    }
    val takeVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val file = videoFile
        if (!ok || file == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { upsert(api.sendMedia("video_note", file, "video/mp4")) }.onFailure { error = it.message }
        }
    }
    val recPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        myId = ParentApp.instance.session.userId() ?: 0
        token = ParentApp.instance.session.accessToken().orEmpty()
        val familyId = ParentApp.instance.session.familyId() ?: 0
        runCatching {
            messages.clear()
            messages.addAll(api.messages())
        }.onFailure { error = it.message }
        runCatching { api.markRead() }
        while (isActive) {
            val closed = CompletableDeferred<Unit>()
            val ws = api.openChat(
                familyId,
                token,
                onMessage = { wsSock, msg ->
                    scope.launch(Dispatchers.Main.immediate) {
                        upsert(msg)
                        if (msg.senderId != myId) api.sendWsRead(wsSock)
                    }
                },
                onRead = { ids ->
                    scope.launch(Dispatchers.Main.immediate) { applyRead(ids) }
                },
                onClosed = { closed.complete(Unit) },
            )
            socket = ws
            api.sendWsRead(ws)
            closed.await()
            socket = null
            delay(1200)
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(recording) {
        recMs = 0
        while (recording) {
            delay(200)
            recMs += 200
        }
    }
    DisposableEffect(Unit) { onDispose { recorder.release() } }

    Column(Modifier.fillMaxSize().background(ChatBg)) {
        Row(
            Modifier.fillMaxWidth().background(Bar).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("onlayn", color = Color(0xFF4DCD7D), fontSize = 12.sp)
            }
        }
        error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                Bubble(
                    msg = msg,
                    mine = msg.senderId == myId,
                    token = token,
                    api = api,
                    onReact = { emoji ->
                        scope.launch { runCatching { upsert(api.toggleReaction(msg.id, emoji)) } }
                    },
                )
            }
        }
        if (recording) {
            Text(
                "Yozilmoqda  ${formatMs(recMs)}  — qo‘yib yuboring",
                color = Color(0xFFFF8A80),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().background(Bar).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.Add, "media", tint = Accent)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rasm") }, onClick = {
                        menu = false
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    DropdownMenuItem(text = { Text("Kamera") }, onClick = {
                        menu = false
                        val created = cacheFile(context, "cam_${System.currentTimeMillis()}.jpg")
                        photoFile = created.first
                        takePhoto.launch(created.second)
                    })
                    DropdownMenuItem(text = { Text("Dumaloq video") }, onClick = {
                        menu = false
                        val created = cacheFile(context, "note_${System.currentTimeMillis()}.mp4")
                        videoFile = created.first
                        takeVideo.launch(created.second)
                    })
                    DropdownMenuItem(text = { Text("Fayl") }, onClick = {
                        menu = false
                        pickFile.launch("*/*")
                    })
                }
            }
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Xabar", color = Color(0xFF8E9BA8)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF242F3D),
                    unfocusedContainerColor = Color(0xFF242F3D),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Accent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(22.dp),
            )
            if (text.isBlank()) {
                IconButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            recPerm.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                                    android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    return@detectTapGestures
                                }
                                val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                                recorder.start(file)
                                recording = true
                                tryAwaitRelease()
                                val duration = recorder.stop()
                                recording = false
                                if (duration >= 400) {
                                    scope.launch {
                                        runCatching {
                                            upsert(api.sendMedia("voice", file, "audio/mp4", duration))
                                        }.onFailure { error = it.message }
                                    }
                                }
                            },
                        )
                    },
                ) {
                    Icon(Icons.Default.Mic, "ovoz", tint = if (recording) Color(0xFFFF8A80) else Accent)
                }
            } else {
                IconButton(onClick = {
                    val body = text.trim()
                    text = ""
                    val ws = socket
                    if (ws != null) {
                        api.sendWsText(ws, body)
                    } else {
                        scope.launch {
                            runCatching { upsert(api.sendMessage(body)) }.onFailure { error = it.message }
                        }
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, "yubor", tint = Accent)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    msg: MessageDto,
    mine: Boolean,
    token: String,
    api: ApiClient,
    onReact: (String) -> Unit,
) {
    var showReacts by remember { mutableStateOf(false) }
    val kind = msg.kind ?: "text"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (showReacts) {
                Row(Modifier.background(Bar, RoundedCornerShape(18.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Emojis.forEach { e ->
                        Text(
                            e,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(4.dp).combinedClickable(onClick = {
                                onReact(e)
                                showReacts = false
                            }),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .background(if (mine) Outgoing else Incoming, RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = {}, onLongClick = { showReacts = !showReacts })
                    .padding(10.dp),
            ) {
                if (!mine) {
                    Text(msg.senderName ?: "Oila", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                when (kind) {
                    "photo" -> if (msg.mediaUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(api.mediaUrl(msg.mediaUrl))
                                .addHeader("Authorization", "Bearer $token")
                                .addHeader("ngrok-skip-browser-warning", "1")
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    "video_note" -> if (msg.mediaUrl != null) CircleNote(api, msg.mediaUrl)
                    "voice" -> VoiceNote(api, msg)
                    "file" -> Text("📎 ${msg.body?.ifBlank { "fayl" } ?: "fayl"}", color = Color.White)
                    else -> {}
                }
                if (!msg.body.isNullOrBlank() && kind != "file") {
                    Text(msg.body, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(msg.createdAt.take(16).replace("T", " "), color = Color(0xFFB0BEC5), fontSize = 10.sp)
                    if (mine) {
                        Text(
                            if (msg.read == true) "  **" else "  *",
                            color = if (msg.read == true) Color(0xFF6AB3F3) else Color(0xFFB0BEC5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            val reactions = msg.reactions.orEmpty()
            if (reactions.isNotEmpty()) {
                Row(Modifier.padding(top = 4.dp)) {
                    reactions.forEach { r ->
                        Text(
                            "${r.emoji} ${r.count}",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .background(if (r.mine) Outgoing else Bar, RoundedCornerShape(12.dp))
                                .combinedClickable(onClick = { onReact(r.emoji) })
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleNote(api: ApiClient, path: String) {
    var file by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(path) { runCatching { file = api.downloadFile(path) } }
    Box(
        Modifier.size(220.dp).clip(CircleShape).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val local = file
        if (local != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setVideoPath(local.absolutePath)
                        setOnPreparedListener {
                            it.isLooping = true
                            start()
                        }
                    }
                },
            )
        } else {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun VoiceNote(api: ApiClient, msg: MessageDto) {
    var playing by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (msg.mediaUrl == null) return@IconButton
            scope.launch {
                val f = runCatching { api.downloadFile(msg.mediaUrl) }.getOrNull() ?: return@launch
                withContext(Dispatchers.Main) {
                    if (playing) {
                        player.stop()
                        playing = false
                    } else {
                        player.reset()
                        player.setDataSource(f.absolutePath)
                        player.prepare()
                        player.setOnCompletionListener { playing = false }
                        player.start()
                        playing = true
                    }
                }
            }
        }) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
        }
        Text(formatMs(msg.durationMs ?: 0), color = Color.White)
    }
}

private fun formatMs(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun copyUri(context: Context, uri: Uri, name: String): File {
    val out = File(context.cacheDir, name)
    context.contentResolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { input.copyTo(it) }
    } ?: error("fayl ochilmadi")
    return out
}

private fun cacheFile(context: Context, name: String): Pair<File, Uri> {
    val file = File(context.cacheDir, name)
    file.createNewFile()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    return file to uri
}

private class VoiceHolder {
    private var recorder: MediaRecorder? = null
    private var started = 0L

    fun start(file: File) {
        release()
        started = System.currentTimeMillis()
        recorder = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(ParentApp.instance)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    fun stop(): Int {
        return try {
            recorder?.stop()
            (System.currentTimeMillis() - started).toInt()
        } catch (_: Exception) {
            0
        } finally {
            release()
        }
    }

    fun release() {
        runCatching { recorder?.release() }
        recorder = null
    }
}
