package com.jizhang.assistant.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * 主题管理：4 种配色 × 明暗模式。
 */
object Themes {

    enum class Mode { AUTO, LIGHT, DARK }
    enum class Accent { GREEN, BLUE, PURPLE, ORANGE }

    data class ThemeColors(
        val primary: Color,
        val primaryDark: Color,
        val primaryContainer: Color,
        val expense: Color,
    )

    val accents = listOf(
        Accent.GREEN to "清新绿",
        Accent.BLUE to "海洋蓝",
        Accent.PURPLE to "梦幻紫",
        Accent.ORANGE to "活力橙",
    )

    fun colors(accent: Accent): ThemeColors = when (accent) {
        Accent.GREEN -> ThemeColors(
            Color(0xFF00B478), Color(0xFF00875A),
            Color(0xFFB9F2DA), Color(0xFFF25C3B),
        )
        Accent.BLUE -> ThemeColors(
            Color(0xFF2196F3), Color(0xFF1565C0),
            Color(0xFFBBDEFB), Color(0xFFEF5350),
        )
        Accent.PURPLE -> ThemeColors(
            Color(0xFF7C4DFF), Color(0xFF651FFF),
            Color(0xFFE1BEE7), Color(0xFFFF5252),
        )
        Accent.ORANGE -> ThemeColors(
            Color(0xFFFF9800), Color(0xFFF57C00),
            Color(0xFFFFE0B2), Color(0xFFE53935),
        )
    }

    fun lightScheme(accent: Accent): ColorScheme {
        val c = colors(accent)
        return lightColorScheme(
            primary = c.primary,
            onPrimary = Color.White,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.primaryDark,
            secondary = c.expense,
            background = Color(0xFFF6F7FB),
            surface = Color.White,
            onBackground = Color(0xFF1B1C1E),
            onSurface = Color(0xFF1B1C1E),
            onSurfaceVariant = Color(0xFF8A8D93),
            surfaceVariant = Color(0xFFE8EAF0),
            error = Color(0xFFE53935),
        )
    }

    fun darkScheme(accent: Accent): ColorScheme {
        val c = colors(accent)
        return darkColorScheme(
            primary = c.primary,
            onPrimary = Color.White,
            primaryContainer = c.primaryDark,
            onPrimaryContainer = c.primaryContainer,
            secondary = c.expense,
            background = Color(0xFF121417),
            surface = Color(0xFF1C1F24),
            onBackground = Color(0xFFE4E6EA),
            onSurface = Color(0xFFE4E6EA),
            onSurfaceVariant = Color(0xFF9BA0A8),
            surfaceVariant = Color(0xFF2A2E35),
            error = Color(0xFFFF6E6E),
        )
    }
}
