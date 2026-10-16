package com.jizhang.assistant.util

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 轻量全局状态：当前账本选择（SharedPreferences 持久化）。
 * 由 JizhangApp 初始化。
 */
object DataStore {

    private var prefs: android.content.SharedPreferences? = null

    val currentBid = MutableStateFlow(com.jizhang.assistant.data.entity.BookEntity.DEFAULT_BID)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("jizhang_prefs", Context.MODE_PRIVATE)
        currentBid.value = prefs?.getString("currentBid", currentBid.value) ?: currentBid.value
    }

    fun setCurrentBid(bid: String) {
        currentBid.value = bid
        prefs?.edit()?.putString("currentBid", bid)?.apply()
    }

    // ── 隐私锁 ──
    private const val KEY_LOCK_PIN = "lock_pin"

    fun lockPin(): String? = prefs?.getString(KEY_LOCK_PIN, null)

    fun setLockPin(pin: String?) {
        if (pin == null) prefs?.edit()?.remove(KEY_LOCK_PIN)?.apply()
        else prefs?.edit()?.putString(KEY_LOCK_PIN, pin)?.apply()
    }

    // ── AI 接口配置（可配置，支持任意 OpenAI 兼容服务）──
    // AI 服务配置（从设置页读取）
    private const val KEY_AI_ENDPOINT = "ai_endpoint"     // 如 https://api.deepseek.com/v1
    private const val KEY_AI_APIKEY = "ai_apikey"
    private const val KEY_AI_MODEL = "ai_model"           // 如 deepseek-chat

    /** 内置默认（从 secrets.properties 注入，源码不含密钥） */
    object Defaults {
        const val ENDPOINT = ""
        const val MODEL = ""
        val API_KEY = com.jizhang.assistant.BuildConfig.AI_API_KEY
    }


    fun aiEndpoint(): String = prefs?.getString(KEY_AI_ENDPOINT, Defaults.ENDPOINT) ?: Defaults.ENDPOINT
    fun setAiEndpoint(url: String) { prefs?.edit()?.putString(KEY_AI_ENDPOINT, url)?.apply() }

    fun aiApiKey(): String = prefs?.getString(KEY_AI_APIKEY, Defaults.API_KEY) ?: Defaults.API_KEY
    fun setAiApiKey(key: String) { prefs?.edit()?.putString(KEY_AI_APIKEY, key)?.apply() }

    fun aiModel(): String = prefs?.getString(KEY_AI_MODEL, Defaults.MODEL) ?: Defaults.MODEL
    fun setAiModel(model: String) { prefs?.edit()?.putString(KEY_AI_MODEL, model)?.apply() }


    // ── 主题配置 ──
    private const val KEY_THEME_MODE = "theme_mode"       // auto/light/dark
    private const val KEY_THEME_ACCENT = "theme_accent"   // GREEN/BLUE/PURPLE/ORANGE

    fun themeMode(): com.jizhang.assistant.ui.theme.Themes.Mode {
        return when (prefs?.getString(KEY_THEME_MODE, "auto")) {
            "light" -> com.jizhang.assistant.ui.theme.Themes.Mode.LIGHT
            "dark" -> com.jizhang.assistant.ui.theme.Themes.Mode.DARK
            else -> com.jizhang.assistant.ui.theme.Themes.Mode.AUTO
        }
    }
    fun themeModeString(): String = prefs?.getString(KEY_THEME_MODE, "auto") ?: "auto"
    fun setThemeMode(mode: String) { prefs?.edit()?.putString(KEY_THEME_MODE, mode)?.apply() }

    fun themeAccent(): com.jizhang.assistant.ui.theme.Themes.Accent {
        return when (prefs?.getString(KEY_THEME_ACCENT, "GREEN")) {
            "BLUE" -> com.jizhang.assistant.ui.theme.Themes.Accent.BLUE
            "PURPLE" -> com.jizhang.assistant.ui.theme.Themes.Accent.PURPLE
            "ORANGE" -> com.jizhang.assistant.ui.theme.Themes.Accent.ORANGE
            else -> com.jizhang.assistant.ui.theme.Themes.Accent.GREEN
        }
    }
    fun themeAccentString(): String = prefs?.getString(KEY_THEME_ACCENT, "GREEN") ?: "GREEN"
    fun setThemeAccent(accent: String) { prefs?.edit()?.putString(KEY_THEME_ACCENT, accent)?.apply() }

    // ── 用户档案 ──
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_AVATAR_PATH = "avatar_path"

    fun displayName(): String = prefs?.getString(KEY_DISPLAY_NAME, "") ?: ""
    fun setDisplayName(name: String) { prefs?.edit()?.putString(KEY_DISPLAY_NAME, name)?.apply() }

    fun avatarPath(): String = prefs?.getString(KEY_AVATAR_PATH, "") ?: ""
    fun setAvatarPath(path: String) { prefs?.edit()?.putString(KEY_AVATAR_PATH, path)?.apply() }

    // ── 更新公告 ──
    private const val KEY_LAST_ANNOUNCED = "last_announced_version"
    fun lastAnnouncedVersion(): Int = prefs?.getInt(KEY_LAST_ANNOUNCED, 0) ?: 0
    fun setLastAnnouncedVersion(v: Int) { prefs?.edit()?.putInt(KEY_LAST_ANNOUNCED, v)?.apply() }

    // ── 云账号 ──
    private const val KEY_CLOUD_SERVER = "cloud_server"
    private const val KEY_CLOUD_TOKEN = "cloud_token"
    private const val KEY_CLOUD_USER_ID = "cloud_user_id"
    private const val KEY_CLOUD_USERNAME = "cloud_username"
    private const val KEY_CLOUD_VERSION = "cloud_version"   // 本地已同步的版本号
    private const val KEY_LAST_SYNC = "last_sync_time"

    fun cloudServer(): String = prefs?.getString(KEY_CLOUD_SERVER, "") ?: ""
    fun setCloudServer(v: String) { prefs?.edit()?.putString(KEY_CLOUD_SERVER, v)?.apply() }

    fun cloudToken(): String = prefs?.getString(KEY_CLOUD_TOKEN, "") ?: ""
    fun setCloudToken(v: String) { prefs?.edit()?.putString(KEY_CLOUD_TOKEN, v)?.apply() }

    fun cloudUserId(): String = prefs?.getString(KEY_CLOUD_USER_ID, "") ?: ""
    fun setCloudUserId(v: String) { prefs?.edit()?.putString(KEY_CLOUD_USER_ID, v)?.apply() }

    fun cloudUsername(): String = prefs?.getString(KEY_CLOUD_USERNAME, "") ?: ""
    fun setCloudUsername(v: String) { prefs?.edit()?.putString(KEY_CLOUD_USERNAME, v)?.apply() }

    fun cloudVersion(): Long = prefs?.getLong(KEY_CLOUD_VERSION, 0L) ?: 0L
    fun setCloudVersion(v: Long) { prefs?.edit()?.putLong(KEY_CLOUD_VERSION, v)?.apply() }

    fun lastSyncTime(): Long = prefs?.getLong(KEY_LAST_SYNC, 0L) ?: 0L
    fun setLastSyncTime(v: Long) { prefs?.edit()?.putLong(KEY_LAST_SYNC, v)?.apply() }

    fun isLoggedIn(): Boolean = cloudToken().isNotBlank()

    // ── 登录跳过标记（暂不登录后不再每次弹登录页）──
    private const val KEY_LOGIN_SKIPPED = "login_skipped"
    fun isLoginSkipped(): Boolean = prefs?.getBoolean(KEY_LOGIN_SKIPPED, false) ?: false
    fun setLoginSkipped(v: Boolean) { prefs?.edit()?.putBoolean(KEY_LOGIN_SKIPPED, v)?.apply() }

    fun clearCloudAccount() {
        prefs?.edit()
            ?.remove(KEY_CLOUD_TOKEN)?.remove(KEY_CLOUD_USER_ID)
            ?.remove(KEY_CLOUD_USERNAME)?.remove(KEY_CLOUD_VERSION)
            ?.remove(KEY_LAST_SYNC)?.apply()
    }

    // ── 首次引导 ──
    private const val KEY_GUIDED = "guided"
    fun isGuided(): Boolean = prefs?.getBoolean(KEY_GUIDED, false) ?: false
    fun setGuided() { prefs?.edit()?.putBoolean(KEY_GUIDED, true)?.apply() }
}
