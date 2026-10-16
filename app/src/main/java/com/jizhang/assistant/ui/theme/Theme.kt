package com.jizhang.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.jizhang.assistant.util.AppThemeState

/**
 * 可配置主题（响应式：修改后整个 App 立即重新渲染）。
 */
@Composable
fun JizhangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val accentStr by AppThemeState.accent.collectAsState()
    val modeStr by AppThemeState.mode.collectAsState()

    val accent = when (accentStr) {
        "BLUE" -> Themes.Accent.BLUE
        "PURPLE" -> Themes.Accent.PURPLE
        "ORANGE" -> Themes.Accent.ORANGE
        else -> Themes.Accent.GREEN
    }

    val isDark = when (modeStr) {
        "light" -> false
        "dark" -> true
        else -> darkTheme
    }

    val colorScheme = if (isDark) Themes.darkScheme(accent) else Themes.lightScheme(accent)

    // key 强制整个内容树在主题变化时重建
    key(accentStr, modeStr) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
