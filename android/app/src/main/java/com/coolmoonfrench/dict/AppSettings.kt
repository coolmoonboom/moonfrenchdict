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

    /** 朗读语速倍率（0.75 ~ 1.5，默认 1.0） */
    var speechRate by mutableFloatStateOf(prefs.getFloat("speech_rate", 1f))
        private set

    /** 自动同步间隔（小时，0=关闭，仅手动同步） */
    var syncIntervalHours by mutableIntStateOf(prefs.getInt("sync_interval_hours", 0))
        private set

    fun updateSyncInterval(v: Int) {
        val clamped = v.coerceIn(0, 24)
        syncIntervalHours = clamped
        prefs.edit().putInt("sync_interval_hours", clamped).apply()
    }

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

    fun updateSpeechRate(v: Float) {
        val clamped = v.coerceIn(0.75f, 1.5f)
        speechRate = clamped
        prefs.edit().putFloat("speech_rate", clamped).apply()
        Espeak.setSpeechRate(clamped)
    }
}
