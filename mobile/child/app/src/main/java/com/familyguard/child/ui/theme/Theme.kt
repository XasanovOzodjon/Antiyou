package com.familyguard.child.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = lightColorScheme(
    primary = Color(0xFF1B4F72),
    secondary = Color(0xFF2E86C1),
    background = Color(0xFFEAF2F8),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun FamilyGuardChildTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
