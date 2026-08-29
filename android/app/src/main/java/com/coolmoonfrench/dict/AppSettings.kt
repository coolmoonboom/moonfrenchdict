package com.coolmoonfrench.dict

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局应用设置持久化：
 * - 字体大小倍率（0.8 ~ 1.6）
 * - 查词历史最大条数（10 ~ 100）
 * - 深色模式开关（null=跟随系统，true=深色，false=浅色）
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var fontScale by mutableFloatStateOf(prefs.getFloat("font_scale", 1f))
        private set

    var historyLimit by mutableIntStateOf(prefs.getInt("history_limit", 50))
        private set

    var darkModeEnabled by mutableStateOf(prefs.getBoolean("dark_mode", false))
        private set

    var debugLogEnabled by mutableStateOf(prefs.getBoolean("debug_log", false))
        private set

    fun updateFontScale(v: Float) {
        val clamped = v.coerceIn(0.8f, 1.6f)
        fontScale = clamped
        prefs.edit().putFloat("font_scale", clamped).apply()
    }

    fun updateHistoryLimit(v: Int) {
        val clamped = v.coerceIn(10, 100)
        historyLimit = clamped
        prefs.edit().putInt("history_limit", clamped).apply()
    }

    fun updateDarkMode(v: Boolean) {
        darkModeEnabled = v
        prefs.edit().putBoolean("dark_mode", v).apply()
    }

    fun updateDebugLog(v: Boolean) {
        debugLogEnabled = v
        prefs.edit().putBoolean("debug_log", v).apply()
    }
}
