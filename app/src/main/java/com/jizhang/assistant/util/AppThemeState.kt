package com.jizhang.assistant.util

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 全局响应式状态：主题/档案修改后界面立即刷新。
 */
object AppThemeState {
    val accent = MutableStateFlow(DataStore.themeAccentString())
    val mode = MutableStateFlow(DataStore.themeModeString())

    val displayName = MutableStateFlow(DataStore.displayName())
    val avatarPath = MutableStateFlow(DataStore.avatarPath())

    fun updateTheme(accent: String, mode: String) {
        DataStore.setThemeAccent(accent)
        DataStore.setThemeMode(mode)
        this.accent.value = accent
        this.mode.value = mode
    }

    fun updateProfile(name: String, avatarPath: String) {
        DataStore.setDisplayName(name)
        DataStore.setAvatarPath(avatarPath)
        this.displayName.value = name
        this.avatarPath.value = avatarPath
    }
}
