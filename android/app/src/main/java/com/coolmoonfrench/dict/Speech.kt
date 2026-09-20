package com.coolmoonfrench.dict

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * 朗读统一入口。按文本形态选择引擎：
 *
 * - **孤立单词 / 字母名称 / 短词**（不含空白、长度有限、仅字母与连字符·撇号）：
 *   优先使用系统 TTS（[SystemTts]）。系统 TTS 的单词发音稳定、正确，
 *   且同一输入每次结果一致，彻底避开 Piper 模型「词首 /t/ → /s/」缺陷与随机抖动。
 *   设备没有法语语音时自动回退 Piper（[Espeak]），并用确定性参数合成。
 * - **整句 / 多词短语**：使用 Piper（[Espeak]），句子的自然度与准确度更好。
 *
 * 各界面统一调用本对象，不再直接调用 [Espeak]；引擎切换时会互相停止，避免叠加播放。
 */
object Speech {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun setSpeechRate(v: Float) {
        Espeak.setSpeechRate(v)
        SystemTts.setSpeechRate(v)
    }

    /** 预初始化两个引擎（幂等，非阻塞）。 */
    fun ensureInitialized(context: Context) {
        SystemTts.ensureInitialized(context)
        Espeak.ensureInitialized(context)
    }

    /** 停止所有正在进行的朗读。 */
    fun stop() {
        SystemTts.stop()
        Espeak.stop()
    }

    /** 朗读并自动弹出失败原因 Toast。快速连点只会播最新一次。 */
    fun speakWithFeedback(context: Context, text: String) {
        if (text.isBlank()) return
        if (isIsolatedWord(text)) {
            SystemTts.ensureInitialized(context)
            SystemTts.speakWhenReady(text) { ok ->
                if (ok) {
                    // 已交给系统 TTS：停掉可能在播的 Piper，避免两条语音叠加
                    Espeak.stop()
                } else {
                    // 系统 TTS 不可用：回退 Piper，并用 noiseless 参数保证短词结果稳定
                    SystemTts.stop()
                    mainHandler.post {
                        Espeak.speakWithFeedback(context, text, deterministic = true)
                    }
                }
            }
        } else {
            SystemTts.stop()
            Espeak.speakWithFeedback(context, text)
        }
    }

    /** 是否按「孤立单词」处理：不含空白、长度有限、仅字母与连字符/撇号。 */
    internal fun isIsolatedWord(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 24) return false
        if (t.any { it.isWhitespace() }) return false
        return t.all { it.isLetter() || it == '-' || it == '\'' || it == '’' }
    }
}
