package com.familyguard.parent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = darkColorScheme(
    primary = Color(0xFF6AB3F3),
    secondary = Color(0xFF4DCD7D),
    background = Color(0xFF0E1621),
    surface = Color(0xFF17212B),
    onPrimary = Color.White,
    onBackground = Color(0xFFE8EEF4),
    onSurface = Color(0xFFE8EEF4),
    error = Color(0xFFFF8A80),
)

@Composable
fun FamilyGuardParentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
