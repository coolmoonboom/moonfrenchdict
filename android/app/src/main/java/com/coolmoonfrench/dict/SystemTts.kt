package com.coolmoonfrench.dict

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 系统 TTS 封装（设备自带的 Android [TextToSpeech] 引擎）。
 *
 * 用途：朗读**孤立单词、字母名称、短词**。系统 TTS 的发音词典专为单词朗读设计，
 * 能稳定给出正确的词首辅音（当前 Piper siwis 模型存在「词首 /t/ → /s/」缺陷），
 * 且同一输入每次结果一致，没有神经合成逐次采样的随机抖动。
 *
 * 设备没有安装法语语音数据时 [isFrenchAvailable] 为 false，调用方应回退到 Piper（[Espeak]）。
 * 初始化是异步的：未就绪期间提交的朗读请求会排队，初始化完成后自动播放；
 * 若最终确定不可用，则回调 false 让调用方回退。
 */
object SystemTts {

    private const val TAG = "SystemTts"

    enum class State { NOT_READY, INITIALIZING, READY, UNAVAILABLE, FAILED }

    @Volatile
    private var state = State.NOT_READY

    @Volatile
    private var french = false

    /** 朗读语速倍率（0.25 ~ 1.5，默认 1.0），与 Piper 侧保持同一语义。 */
    @Volatile
    var speechRate: Float = 1f
        private set

    private var tts: TextToSpeech? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pending: Pair<String, (Boolean) -> Unit>? = null
    private var utteranceSeq = 0L

    fun state(): State = state

    fun isFrenchAvailable(): Boolean = state == State.READY && french

    fun setSpeechRate(v: Float) {
        speechRate = v.coerceIn(0.25f, 1.5f)
        tts?.let { runCatching { it.setSpeechRate(speechRate) } }
    }

    /** 启动初始化（幂等，非阻塞）。READY/INITIALIZING 时直接返回。 */
    fun ensureInitialized(context: Context) {
        if (state != State.NOT_READY) return
        synchronized(this) {
            if (state != State.NOT_READY) return
            state = State.INITIALIZING
        }
        val app = context.applicationContext
        mainHandler.post {
            try {
                tts = TextToSpeech(app) { status -> onInit(status) }
            } catch (e: Throwable) {
                Log.e(TAG, "create TextToSpeech failed", e)
                synchronized(this) { state = State.FAILED }
                flushPending(false)
            }
        }
    }

    private fun onInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            synchronized(this) { state = State.FAILED }
            flushPending(false)
            return
        }
        val res = try {
            engine.setLanguage(Locale.FRENCH)
        } catch (e: Throwable) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        french = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
        runCatching { engine.setSpeechRate(speechRate) }
        synchronized(this) { state = if (french) State.READY else State.UNAVAILABLE }
        flushPending(french)
    }

    /**
     * 请求朗读。onResult(true) 表示已交由系统 TTS 播放；onResult(false) 表示不可用，
     * 调用方应回退到 Piper。引擎初始化期间会排队，就绪后自动处理。
     */
    fun speakWhenReady(text: String, onResult: (Boolean) -> Unit) {
        val t = text.trim()
        if (t.isEmpty()) {
            onResult(true)
            return
        }
        when (state) {
            State.READY -> if (french && speak(t)) onResult(true) else onResult(false)
            State.INITIALIZING -> pending = t to onResult
            else -> onResult(false)
        }
    }

    private fun speak(text: String): Boolean {
        val engine = tts ?: return false
        utteranceSeq++
        val result = try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fr-$utteranceSeq")
        } catch (e: Throwable) {
            Log.e(TAG, "speak failed", e)
            TextToSpeech.ERROR
        }
        return result == TextToSpeech.SUCCESS
    }

    /** 停止当前朗读并清空排队请求。 */
    fun stop() {
        synchronized(this) { pending = null }
        runCatching { tts?.stop() }
    }

    private fun flushPending(available: Boolean) {
        val request = synchronized(this) {
            val r = pending
            pending = null
            r
        } ?: return
        val (text, callback) = request
        if (available && french && speak(text)) callback(true) else callback(false)
    }
}
