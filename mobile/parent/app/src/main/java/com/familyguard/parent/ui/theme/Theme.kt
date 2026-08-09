package com.familyguard.parent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = lightColorScheme(
    primary = Color(0xFF0E4D3A),
    secondary = Color(0xFF1E8449),
    background = Color(0xFFF3F7F5),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun FamilyGuardParentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
