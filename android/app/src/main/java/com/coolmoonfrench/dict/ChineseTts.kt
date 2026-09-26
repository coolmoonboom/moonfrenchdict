package com.coolmoonfrench.dict

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * 中文系统 TTS：仅用于朗读收藏词的中文释义。
 *
 * 内置的 Piper 语音模型只支持法语，无法朗读中文，因此这里复用设备自带的 Android
 * [TextToSpeech] 引擎。设备未安装中文语音数据时，初始化后 [isAvailable] 为 false，
 * 调用方应静默跳过，不影响法语播报主流程。
 */
object ChineseTts {

    private const val TAG = "ChineseTts"
    private const val UTTERANCE_ID = "zh-meaning"
    private const val SPEAK_TIMEOUT_MS = 30_000L
    private const val READY_TIMEOUT_MS = 5_000L

    enum class State { NOT_READY, INITIALIZING, READY, UNAVAILABLE, FAILED }

    @Volatile
    private var state = State.NOT_READY

    @Volatile
    private var available = false

    private var tts: TextToSpeech? = null
    private var done: CompletableDeferred<Unit>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var speechRate: Float = 1f

    /** 中文语音语速（0.25-1.5），在 [speakAwait] 朗读前应用。 */
    fun setSpeechRate(v: Float) {
        speechRate = v.coerceIn(0.25f, 1.5f)
    }

    /** 是否已就绪且设备支持中文语音。 */
    fun isAvailable(): Boolean = state == State.READY && available

    /** 启动初始化（幂等，非阻塞）。 */
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
            }
        }
    }

    private fun onInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            synchronized(this) { state = State.FAILED }
            return
        }
        val res = try {
            engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        } catch (e: Throwable) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        available = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                done?.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                done?.complete(Unit)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                done?.complete(Unit)
            }
        })
        synchronized(this) { state = if (available) State.READY else State.UNAVAILABLE }
    }

    /**
     * 挂起朗读中文释义；播完、失败、超时或被取消后返回。
     * 设备不支持中文语音时立即返回，不抛异常。
     */
    suspend fun speakAwait(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (state == State.NOT_READY) return
        if (state == State.INITIALIZING) {
            val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
            while (state == State.INITIALIZING && System.currentTimeMillis() < deadline) {
                delay(50)
            }
        }
        if (state != State.READY) return
        val engine = tts ?: return
        runCatching { engine.setSpeechRate(speechRate) }
        val d = CompletableDeferred<Unit>()
        done = d
        val result = try {
            engine.speak(t, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        } catch (e: Throwable) {
            Log.e(TAG, "speak failed", e)
            TextToSpeech.ERROR
        }
        if (result != TextToSpeech.SUCCESS) {
            done = null
            return
        }
        val finished = withTimeoutOrNull(SPEAK_TIMEOUT_MS) { d.await() }
        done = null
        if (finished == null) {
            // 中文解释过长超出超时仍在朗读：主动停止，避免与下一个单词的法语语音叠加。
            runCatching { engine.stop() }
        }
    }

    /** 停止当前朗读并解除等待。 */
    fun stop() {
        runCatching { tts?.stop() }
        done?.complete(Unit)
        done = null
    }
}
