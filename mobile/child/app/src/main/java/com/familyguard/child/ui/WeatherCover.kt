package com.familyguard.child.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyguard.child.ChildApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

private const val COVER_PIN = "131415"
private const val WeatherUrl =
    "https://api.open-meteo.com/v1/forecast?latitude=41.3&longitude=69.24&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min&timezone=auto"

@Composable
fun WeatherCover(onOpenChat: () -> Unit) {
    var temp by remember { mutableStateOf("--") }
    var desc by remember { mutableStateOf("Ob-havoga ulanmoqda…") }
    var high by remember { mutableStateOf("--") }
    var low by remember { mutableStateOf("--") }
    var humidity by remember { mutableStateOf("--") }
    var wind by remember { mutableStateOf("--") }
    var code by remember { mutableStateOf(1) }
    var loaded by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    val dark by ChildApp.instance.session.isDarkCover.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val body = OkHttpClient().newCall(Request.Builder().url(WeatherUrl).build()).execute().body?.string().orEmpty()
                    val json = JSONObject(body)
                    val current = json.getJSONObject("current")
                    val daily = json.getJSONObject("daily")
                    temp = String.format(Locale.getDefault(), "%.0f°", current.getDouble("temperature_2m"))
                    code = current.getInt("weather_code")
                    desc = weatherDesc(code)
                    humidity = "${current.getInt("relative_humidity_2m")}%"
                    wind = String.format(Locale.getDefault(), "%.0f km/soat", current.getDouble("wind_speed_10m"))
                    high = String.format(Locale.getDefault(), "%.0f°", daily.getJSONArray("temperature_2m_max").getDouble(0))
                    low = String.format(Locale.getDefault(), "%.0f°", daily.getJSONArray("temperature_2m_min").getDouble(0))
                    loaded = true
                }
            }
            delay(15 * 60_000L)
        }
    }

    val sky = if (dark) {
        listOf(Color(0xFF02040A), Color(0xFF0B1220), Color(0xFF152238), Color(0xFF1B2A44))
    } else {
        listOf(Color(0xFF0A3D7A), Color(0xFF1E88C8), Color(0xFF7EC8E3), Color(0xFFFFE0A3))
    }
    val ink = Color.White
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(sky))) {
        if (dark) StarField()
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Toshkent", color = ink.copy(alpha = 0.92f), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("O‘zbekiston", color = ink.copy(alpha = 0.55f), fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))
            Text(weatherEmoji(code), fontSize = 64.sp)
            Text(
                temp,
                color = ink,
                fontSize = 96.sp,
                fontWeight = FontWeight.Thin,
            )
            Text(desc, color = ink.copy(alpha = 0.9f), fontSize = 22.sp, fontWeight = FontWeight.Medium)
            if (loaded) {
                Text("Yuqori $high   ·   Past $low", color = ink.copy(alpha = 0.7f), fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(36.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WeatherChip("Namlik", humidity, Modifier.weight(1f), dark)
                WeatherChip(
                    label = "Shamol",
                    value = wind,
                    modifier = Modifier.weight(1f),
                    dark = dark,
                    showWindIcon = true,
                    onClick = if (dark) {
                        {
                            pin = ""
                            pinError = null
                            askPin = true
                        }
                    } else {
                        null
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (dark) 0.12f else 0.22f))
                    .pointerInput(dark) {
                        detectTapGestures(
                            onTap = { scope.launch { ChildApp.instance.session.setDarkCover(!dark) } },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "tema",
                    tint = ink,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text("Open-Meteo", color = ink.copy(alpha = 0.35f), fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp))
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
            dismissButton = { TextButton(onClick = { askPin = false }) { Text("Bekor") } },
            shape = RoundedCornerShape(20.dp),
        )
    }
}

@Composable
private fun WeatherChip(
    label: String,
    value: String,
    modifier: Modifier,
    dark: Boolean,
    showWindIcon: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val buttonish = onClick != null
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (buttonish) 0.18f else if (dark) 0.08f else 0.18f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showWindIcon) {
            Icon(Icons.Default.Air, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp).padding(bottom = 4.dp))
        }
        Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StarField() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        var i = 0
        while (i < 70) {
            val x = abs((i * 97f) % w)
            val y = abs((i * 53f) % (h * 0.65f))
            drawCircle(Color.White.copy(alpha = 0.15f + (i % 5) * 0.08f), 1.4f + (i % 3), Offset(x, y))
            i++
        }
    }
}

private fun weatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2, 3 -> "⛅"
    45, 48 -> "🌫️"
    51, 53, 55, 61, 63, 65 -> "🌧️"
    71, 73, 75 -> "❄️"
    95, 96, 99 -> "⛈️"
    else -> "🌤️"
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
