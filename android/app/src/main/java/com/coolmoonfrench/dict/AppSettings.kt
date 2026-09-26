package com.coolmoonfrench.dict

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

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

    /** 朗读语速倍率（0.25 / 0.5 / 0.75 / 1.0 四档，默认 1.0） */
    var speechRate by mutableFloatStateOf(snapSpeechRate(prefs.getFloat("speech_rate", 1f)))
        private set

    /** 自动同步间隔（小时，0=关闭，仅手动同步） */
    var syncIntervalHours by mutableIntStateOf(prefs.getInt("sync_interval_hours", 0))
        private set

    /** 悬浮窗例句是否显示中文翻译 */
    var floatShowTranslation by mutableStateOf(prefs.getBoolean("float_show_translation", true))
        private set

    /** 悬浮窗字体大小倍率（0.8 ~ 1.6，默认 1.0） */
    var floatFontScale by mutableFloatStateOf(prefs.getFloat("float_font_scale", 1f))
        private set

    /** 悬浮窗词卡背景不透明度（0 完全透底，白字直接叠在桌面；1 全黑底；默认 1.0） */
    var floatBgAlpha by mutableFloatStateOf(snapFloatBgAlpha(prefs.getFloat("float_bg_alpha", 1f)))
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
        val snapped = snapSpeechRate(v)
        speechRate = snapped
        prefs.edit().putFloat("speech_rate", snapped).apply()
        Speech.setSpeechRate(snapped)
    }

    fun updateFloatShowTranslation(v: Boolean) {
        floatShowTranslation = v
        prefs.edit().putBoolean("float_show_translation", v).apply()
    }

    fun updateFloatFontScale(v: Float) {
        val clamped = v.coerceIn(0.8f, 1.6f)
        floatFontScale = clamped
        prefs.edit().putFloat("float_font_scale", clamped).apply()
    }

    fun updateFloatBgAlpha(v: Float) {
        val snapped = snapFloatBgAlpha(v)
        floatBgAlpha = snapped
        prefs.edit().putFloat("float_bg_alpha", snapped).apply()
    }

    companion object {
        /** 可选的朗读语速档位（倍率），UI 按此顺序展示。 */
        val SPEECH_RATE_OPTIONS = listOf(0.25f, 0.5f, 0.75f, 1f)

        /** 悬浮词卡背景可选不透明度档位；0 为完全透底（白字直接叠在桌面上），1 为全黑底。 */
        val FLOAT_BG_ALPHA_OPTIONS = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

        /** 吸附到最近的合法档位，避免历史遗留值落在档位之间导致 UI 无选中项。 */
        private fun snapSpeechRate(v: Float): Float =
            SPEECH_RATE_OPTIONS.minByOrNull { abs(it - v) } ?: 1f

        /** 吸附到最近的背景不透明度档位。 */
        private fun snapFloatBgAlpha(v: Float): Float =
            FLOAT_BG_ALPHA_OPTIONS.minByOrNull { abs(it - v) } ?: 1f
    }
}
