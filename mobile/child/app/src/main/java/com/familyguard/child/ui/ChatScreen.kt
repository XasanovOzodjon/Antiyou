package com.familyguard.child.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.familyguard.child.ChildApp
import com.familyguard.child.data.ApiClient
import com.familyguard.child.data.MessageDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.WebSocket
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.sin

private val ChatBg = Color(0xFF0E1621)
private val Bar = Color(0xFF17212B)
private val Field = Color(0xFF242F3D)
private val Outgoing = Color(0xFF2B5278)
private val Incoming = Color(0xFF182533)
private val Accent = Color(0xFF6AB3F3)
private val Muted = Color(0xFF8E9BA8)
private val TickRead = Color(0xFF6AB3F3)
private val ActionBlue = Color(0xFF3390EC)
private val Emojis = listOf("❤️", "👍", "😂", "🔥", "😮", "😢")
private const val VoiceMaxMs = 120_000
private const val VideoMaxMs = 60_000

private data class GalleryThumb(val uri: Uri, val mime: String, val video: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyChat(title: String, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val api = remember { ApiClient(ChildApp.instance.session) }
    val messages = remember { mutableStateListOf<MessageDto>() }
    var text by remember { mutableStateOf("") }
    var myId by remember { mutableIntStateOf(0) }
    var token by remember { mutableStateOf("") }
    var videoMode by remember { mutableStateOf(false) }
    var recKind by remember { mutableStateOf<String?>(null) }
    var recMs by remember { mutableIntStateOf(0) }
    var recCancel by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf(false) }
    var attachTab by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var socket by remember { mutableStateOf<WebSocket?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var camVideoFile by remember { mutableStateOf<File?>(null) }
    var frontCam by remember { mutableStateOf(true) }
    val gallery = remember { mutableStateListOf<GalleryThumb>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val recorder = remember { VoiceHolder(context) }
    val noteCam = remember { NoteCam(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var noteFile by remember { mutableStateOf<File?>(null) }

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

    fun sendMedia(kind: String, file: File, mime: String, duration: Int? = null) {
        scope.launch {
            runCatching { upsert(api.sendMedia(kind, file, mime, duration)) }.onFailure { error = it.message }
        }
    }

    fun sendUri(uri: Uri) {
        scope.launch {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val ext = mime.substringAfter("/", "bin")
                val file = copyUri(context, uri, "send_${System.currentTimeMillis()}.$ext")
                val kind = when {
                    mime.startsWith("image/") -> "photo"
                    else -> "file"
                }
                upsert(api.sendMedia(kind, file, mime))
            }.onFailure { error = it.message }
        }
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = photoFile
        if (ok && file != null) sendMedia("photo", file, "image/jpeg")
    }
    val takeCamVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val file = camVideoFile
        if (ok && file != null) sendMedia("file", file, "video/mp4")
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) sendUri(uri)
    }
    val recPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    fun askRec() {
        recPerm.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
    }
    val galleryPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            gallery.clear()
            gallery.addAll(loadGallery(context))
        }
    }

    fun openAttach() {
        attach = true
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            gallery.clear()
            gallery.addAll(loadGallery(context))
        } else {
            galleryPerm.launch(perms)
        }
    }

    fun granted(perm: String) = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    fun finishVoice(send: Boolean) {
        val file = voiceFile
        val ms = recorder.stop()
        recKind = null
        recCancel = false
        recMs = 0
        if (send && file != null && ms >= 400) sendMedia("voice", file, "audio/mp4", ms)
    }

    fun finishNote(send: Boolean) {
        val file = noteFile
        recKind = null
        recCancel = false
        recMs = 0
        scope.launch(Dispatchers.IO) {
            val ms = noteCam.stop()
            val ready = file != null && file.exists() && file.length() > 0 && ms >= 400
            withContext(Dispatchers.Main) {
                if (send && ready) sendMedia("video_note", file!!, "video/mp4", ms)
                else if (send) error = "Video yozilmadi. Kamerani bosib turing, keyin qo‘yib yuboring."
            }
        }
    }

    LaunchedEffect(Unit) {
        myId = ChildApp.instance.session.userId() ?: 0
        token = ChildApp.instance.session.accessToken().orEmpty()
        val familyId = ChildApp.instance.session.familyId() ?: 0
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
                onRead = { ids -> scope.launch(Dispatchers.Main.immediate) { applyRead(ids) } },
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
    LaunchedEffect(recKind) {
        recMs = 0
        val cap = if (recKind == "video") VideoMaxMs else VoiceMaxMs
        while (recKind != null) {
            delay(100)
            recMs += 100
            if (recMs >= cap) {
                if (recKind == "video") finishNote(true) else finishVoice(true)
                break
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
            noteCam.release()
        }
    }

    Box(Modifier.fillMaxSize().background(ChatBg).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            ChatHeader(title, onBack)
            error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ChatWallpaper(Modifier.fillMaxSize())
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                if (recKind == "video") {
                    RoundVideoLayer(
                        noteCam = noteCam,
                        lifecycleOwner = lifecycleOwner,
                        front = frontCam,
                        recMs = recMs,
                        cancel = recCancel,
                        onFlip = { frontCam = !frontCam },
                    )
                }
            }
            ChatComposer(
                text = text,
                onText = { text = it },
                videoMode = videoMode,
                recKind = recKind,
                recMs = recMs,
                recCancel = recCancel,
                onAttach = { openAttach() },
                onSendText = {
                    val body = text.trim()
                    text = ""
                    val ws = socket
                    if (ws != null) api.sendWsText(ws, body) else scope.launch {
                        runCatching { upsert(api.sendMessage(body)) }.onFailure { error = it.message }
                    }
                },
                onToggleMode = { videoMode = !videoMode },
                onRecStart = {
                    recCancel = false
                    if (videoMode) {
                        if (!granted(Manifest.permission.CAMERA) || !granted(Manifest.permission.RECORD_AUDIO)) {
                            askRec()
                            return@ChatComposer
                        }
                        val file = File(context.cacheDir, "note_${System.currentTimeMillis()}.mp4")
                        noteFile = file
                        recKind = "video"
                        noteCam.arm(file)
                    } else {
                        if (!granted(Manifest.permission.RECORD_AUDIO)) {
                            askRec()
                            return@ChatComposer
                        }
                        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                        voiceFile = file
                        recorder.start(file)
                        recKind = "voice"
                    }
                },
                onRecDrag = { recCancel = it },
                onRecEnd = {
                    if (recKind == "video") finishNote(!recCancel) else if (recKind == "voice") finishVoice(!recCancel)
                },
            )
        }
        if (attach) {
            ModalBottomSheet(
                onDismissRequest = { attach = false },
                sheetState = sheetState,
                containerColor = Color(0xFF1C2733),
            ) {
                AttachSheet(
                    tab = attachTab,
                    onTab = { attachTab = it },
                    thumbs = gallery,
                    onCameraPhoto = {
                        attach = false
                        if (!granted(Manifest.permission.CAMERA)) {
                            askRec()
                            return@AttachSheet
                        }
                        val created = cacheFile(context, "cam_${System.currentTimeMillis()}.jpg")
                        photoFile = created.first
                        takePhoto.launch(created.second)
                    },
                    onCameraVideo = {
                        attach = false
                        if (!granted(Manifest.permission.CAMERA)) {
                            askRec()
                            return@AttachSheet
                        }
                        val created = cacheFile(context, "camv_${System.currentTimeMillis()}.mp4")
                        camVideoFile = created.first
                        takeCamVideo.launch(created.second)
                    },
                    onThumb = { thumb ->
                        attach = false
                        sendUri(thumb.uri)
                    },
                    onFile = {
                        attach = false
                        pickFile.launch("*/*")
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(title: String, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(Bar).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2B5278)),
            contentAlignment = Alignment.Center,
        ) {
            Text(title.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("onlayn", color = Color(0xFF4DCD7D), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChatWallpaper(modifier: Modifier) {
    Canvas(modifier) {
        val ink = Color(0x22AECBDB)
        var i = 0
        var y = 24f
        while (y < size.height) {
            var x = 18f + (i % 3) * 22f
            while (x < size.width) {
                when (i % 5) {
                    0 -> drawCircle(ink, 7f, Offset(x, y), style = Stroke(1.6f))
                    1 -> drawLine(ink, Offset(x - 8f, y), Offset(x + 8f, y + 10f), 1.6f)
                    2 -> {
                        val p = Path().apply {
                            moveTo(x, y - 8f)
                            lineTo(x + 8f, y + 8f)
                            lineTo(x - 8f, y + 8f)
                            close()
                        }
                        drawPath(p, ink, style = Stroke(1.6f))
                    }
                    else -> drawCircle(ink, 3f, Offset(x, y))
                }
                x += 86f
                i++
            }
            y += 64f
        }
    }
}

@Composable
private fun ChatComposer(
    text: String,
    onText: (String) -> Unit,
    videoMode: Boolean,
    recKind: String?,
    recMs: Int,
    recCancel: Boolean,
    onAttach: () -> Unit,
    onSendText: () -> Unit,
    onToggleMode: () -> Unit,
    onRecStart: () -> Unit,
    onRecDrag: (Boolean) -> Unit,
    onRecEnd: () -> Unit,
) {
    val onToggleMode by rememberUpdatedState(onToggleMode)
    val onRecStart by rememberUpdatedState(onRecStart)
    val onRecDrag by rememberUpdatedState(onRecDrag)
    val onRecEnd by rememberUpdatedState(onRecEnd)
    val recording = recKind != null
    Row(
        Modifier
            .fillMaxWidth()
            .background(Bar)
            .navigationBarsPadding()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (recording) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE53935)))
                Text(
                    "  ${formatMs(recMs)}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (recCancel) "    Bekor" else "    〈 Chapga — bekor",
                    color = if (recCancel) Color(0xFFFF8A80) else Muted,
                    fontSize = 13.sp,
                )
            }
        } else {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Field)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.EmojiEmotions, null, tint = Muted, modifier = Modifier.padding(8.dp).size(22.dp))
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Accent),
                    maxLines = 5,
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Xabar", color = Muted, fontSize = 16.sp)
                        inner()
                    },
                )
                IconButton(onClick = onAttach) {
                    Icon(Icons.Default.AttachFile, "skripka", tint = Muted)
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        val canSend = text.isNotBlank()
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (recCancel) Color(0xFFE53935) else ActionBlue)
                .then(
                    if (canSend) Modifier.clickable(onClick = onSendText)
                    else Modifier.pointerInput(videoMode) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val origin = down.position
                            val quick = withTimeoutOrNull(200) { waitForUpOrCancellation() }
                            if (quick != null) {
                                onToggleMode()
                                return@awaitEachGesture
                            }
                            onRecStart()
                            var cancel = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                cancel = change.position.x - origin.x < -80f
                                onRecDrag(cancel)
                                if (!change.pressed) {
                                    onRecEnd()
                                    break
                                }
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    canSend -> Icons.AutoMirrored.Filled.Send
                    videoMode || recKind == "video" -> Icons.Default.Videocam
                    else -> Icons.Default.Mic
                },
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun RoundVideoLayer(
    noteCam: NoteCam,
    lifecycleOwner: LifecycleOwner,
    front: Boolean,
    recMs: Int,
    cancel: Boolean,
    onFlip: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Color(0xCC0E1621)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(248.dp), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.size(240.dp).clip(CircleShape),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { view -> noteCam.bind(lifecycleOwner, view, front) },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = onFlip, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0x6624282E))) {
                Icon(Icons.Default.Cameraswitch, "agdar", tint = Color.White)
            }
            Text(formatMs(recMs), color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(if (cancel) "Bekor" else "〈 Chapga — bekor", color = if (cancel) Color(0xFFFF8A80) else Muted)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AttachSheet(
    tab: Int,
    onTab: (Int) -> Unit,
    thumbs: List<GalleryThumb>,
    onCameraPhoto: () -> Unit,
    onCameraVideo: () -> Unit,
    onThumb: (GalleryThumb) -> Unit,
    onFile: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().height(420.dp).navigationBarsPadding()) {
        if (tab == 0) {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                item {
                    Box(
                        Modifier
                            .padding(3.dp)
                            .aspect()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2A3A48))
                            .combinedClickable(onClick = onCameraPhoto, onLongClick = onCameraVideo),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.PhotoCamera, "kamera", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
                items(thumbs, key = { it.uri.toString() }) { thumb ->
                    Box(Modifier.padding(3.dp).clip(RoundedCornerShape(10.dp)).clickable { onThumb(thumb) }) {
                        AsyncImage(
                            model = thumb.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.aspect().clip(RoundedCornerShape(10.dp)),
                        )
                        if (thumb.video) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onFile)) {
                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Accent, modifier = Modifier.size(48.dp))
                    Text("Fayl tanlash", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            AttachTab("Galereya", Icons.Default.Image, tab == 0) { onTab(0) }
            AttachTab("Fayl", Icons.AutoMirrored.Filled.InsertDriveFile, tab == 1) { onTab(1) }
        }
    }
}

@Composable
private fun AttachTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) {
        Icon(icon, null, tint = if (selected) Accent else Muted)
        Text(label, color = if (selected) Accent else Muted, fontSize = 11.sp)
    }
}

private fun Modifier.aspect() = this.then(Modifier.fillMaxWidth().height(110.dp))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(msg: MessageDto, mine: Boolean, token: String, api: ApiClient, onReact: (String) -> Unit) {
    var showReacts by remember { mutableStateOf(false) }
    val kind = msg.kind ?: "text"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (showReacts) {
                Row(Modifier.background(Bar, RoundedCornerShape(18.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Emojis.forEach { e ->
                        Text(e, fontSize = 20.sp, modifier = Modifier.padding(4.dp).combinedClickable(onClick = {
                            onReact(e)
                            showReacts = false
                        }))
                    }
                }
            }
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(if (kind == "video_note") 0.dp else 16.dp))
                    .background(if (kind == "video_note") Color.Transparent else if (mine) Outgoing else Incoming)
                    .combinedClickable(onClick = {}, onLongClick = { showReacts = !showReacts })
                    .padding(if (kind == "video_note") 0.dp else 8.dp),
            ) {
                if (!mine && kind != "video_note") {
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
                    "video_note" -> if (msg.mediaUrl != null) CircleNote(api, msg.mediaUrl, msg.durationMs)
                    "voice" -> VoiceNote(api, msg)
                    "file" -> FileRow(msg)
                    else -> {}
                }
                if (!msg.body.isNullOrBlank() && kind != "file") {
                    Text(msg.body, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                }
                if (kind != "video_note") {
                    MetaRow(msg, mine)
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(formatMs(msg.durationMs ?: 0), color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        MetaRow(msg, mine)
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
private fun MetaRow(msg: MessageDto, mine: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(clockLabel(msg.createdAt), color = Color(0xFFB0BEC5), fontSize = 11.sp)
        if (mine) {
            Icon(
                if (msg.read == true) Icons.Default.DoneAll else Icons.Default.Check,
                contentDescription = null,
                tint = if (msg.read == true) TickRead else Muted,
                modifier = Modifier.padding(start = 3.dp).size(14.dp),
            )
        }
    }
}

@Composable
private fun FileRow(msg: MessageDto) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A8EE6)), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color.White)
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(msg.body?.ifBlank { "fayl" } ?: "fayl", color = Color.White, fontSize = 14.sp, maxLines = 1)
            Text(msg.contentType ?: "file", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CircleNote(api: ApiClient, path: String, durationMs: Int? = null) {
    var file by remember { mutableStateOf<File?>(null) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(path) { runCatching { file = api.downloadFile(path) } }
    Box(Modifier.size(200.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
        val local = file
        if (local != null && playing) {
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
            Icon(
                Icons.Default.PlayArrow,
                null,
                tint = Color.White,
                modifier = Modifier.size(48.dp).clickable { playing = true },
            )
        }
    }
}

@Composable
private fun VoiceNote(api: ApiClient, msg: MessageDto) {
    var playing by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable {
                if (msg.mediaUrl == null) return@clickable
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
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Canvas(Modifier.weight(1f).height(28.dp).padding(horizontal = 8.dp)) {
            val bars = 28
            val w = size.width / bars
            for (i in 0 until bars) {
                val h = (6f + 16f * kotlin.math.abs(sin((i + 1) * 0.7f)))
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(i * w, (size.height - h) / 2f),
                    size = androidx.compose.ui.geometry.Size(w * 0.45f, h),
                )
            }
        }
        Text(formatMs(msg.durationMs ?: 0), color = Color.White, fontSize = 12.sp)
    }
}

private fun formatMs(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun clockLabel(iso: String): String {
    val time = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(iso.trim()).atZone(ZoneId.systemDefault()) }.getOrNull()
    return time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: iso.replace("T", " ").drop(11).take(5)
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

private fun loadGallery(context: Context): List<GalleryThumb> {
    val out = mutableListOf<GalleryThumb>()
    val uri = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.MIME_TYPE,
    )
    val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
    val args = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
    )
    runCatching {
        context.contentResolver.query(uri, projection, selection, args, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val mimeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            var n = 0
            while (c.moveToNext() && n < 48) {
                val id = c.getLong(idIdx)
                val type = c.getInt(typeIdx)
                val mime = c.getString(mimeIdx) ?: "image/jpeg"
                val video = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val content = if (video) {
                    Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                } else {
                    Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                }
                out.add(GalleryThumb(content, mime, video))
                n++
            }
        }
    }
    return out
}

private class VoiceHolder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var started = 0L

    fun start(file: File) {
        release()
        started = System.currentTimeMillis()
        recorder = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
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

private class NoteCam(private val context: Context) {
    private val recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.fromOrderedList(
                listOf(Quality.SD, Quality.HD, Quality.LOWEST),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
            ),
        )
        .build()
    private val videoCapture = VideoCapture.withOutput(recorder)
    private var provider: ProcessCameraProvider? = null
    private var recording: Recording? = null
    private var started = 0L
    private var boundFront: Boolean? = null
    private var pendingFile: File? = null
    private var outputFile: File? = null
    private var finalizeLatch: CountDownLatch? = null
    private var finalizeOk = false

    fun bind(owner: LifecycleOwner, previewView: PreviewView, front: Boolean) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cam = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = cam
            if (boundFront == front && recording != null) return@addListener
            if (boundFront == front && recording == null && pendingFile == null) return@addListener
            cam.unbindAll()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val bound = runCatching { cam.bindToLifecycle(owner, selector, preview, videoCapture) }.isSuccess
            boundFront = if (bound) front else null
            if (bound) tryStart()
        }, ContextCompat.getMainExecutor(context))
    }

    fun arm(file: File) {
        pendingFile = file
        tryStart()
    }

    fun start(file: File) {
        arm(file)
    }

    private fun tryStart() {
        val file = pendingFile ?: return
        if (provider == null || recording != null || boundFront == null) return
        pendingFile = null
        outputFile = file
        started = System.currentTimeMillis()
        finalizeOk = false
        val latch = CountDownLatch(1)
        finalizeLatch = latch
        val opts = FileOutputOptions.Builder(file).build()
        val pending = videoCapture.output.prepareRecording(context, opts)
        recording = runCatching {
            val starter = if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                pending.withAudioEnabled()
            } else {
                pending
            }
            starter.start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    finalizeOk = !event.hasError() && file.exists() && file.length() > 0
                    latch.countDown()
                }
            }
        }.getOrNull()
        if (recording == null) {
            finalizeOk = false
            latch.countDown()
        }
    }

    fun stop(): Int {
        val rec = recording ?: run {
            pendingFile = null
            return 0
        }
        runCatching { rec.stop() }
        finalizeLatch?.await(12, TimeUnit.SECONDS)
        recording = null
        val file = outputFile
        val ms = (System.currentTimeMillis() - started).toInt()
        val ok = finalizeOk && file != null && file.exists() && file.length() > 0
        return if (ok) ms else 0
    }

    fun release() {
        runCatching { recording?.stop() }
        recording = null
        pendingFile = null
        provider?.unbindAll()
        boundFront = null
    }
}
