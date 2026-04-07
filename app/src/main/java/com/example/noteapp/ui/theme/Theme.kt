package com.example.noteapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ==================== MÀU SẮC MODERN DARK (Neon Purple + Indigo) ====================
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B5CF6),           // Tím neon chính (rất đẹp cho nút chính)
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6D28D9),
    onPrimaryContainer = Color.White,

    secondary = Color(0xFF22D3EE),         // Cyan neon accent
    onSecondary = Color.Black,

    tertiary = Color(0xFFFF9800),          // Cam nổi bật cho FAB (+)
    onTertiary = Color.White,

    background = Color(0xFF0A0A0A),        // Nền tối sâu, sang trọng
    onBackground = Color(0xFFE0E0E0),

    surface = Color(0xFF1A1A1A),           // Nền Card
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF252525),

    error = Color(0xFFEF4444),
    onError = Color.White
)

// Light theme (nếu người dùng bật sáng)
private val LightColors = lightColorScheme(
    primary = Color(0xFF6366F1),           // Indigo cho light mode
    onPrimary = Color.White,
    secondary = Color(0xFF14B8A6),
    tertiary = Color(0xFFF59E0B),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFB91C1C)
)

@Composable
fun NoteAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,     // Giữ Dynamic Color theo wallpaper
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,      // giữ nguyên Typography của bạn
        content = content
    )
}