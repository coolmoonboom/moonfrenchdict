package com.coolmoonfrench.dict

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 法语 TTS 封装（Mimic 引擎 + siwis HTS 语音）。
 * - 原生库：libttsmimiccore + libHTSEngine + libpcre2-8 + libttsmimic_french
 *   + libttsmimic_siwis_fr_zoe_hts + libmimicbridge（JNI 桥）
 * - 语音数据：assets/voices/siwis_fr_zoe_hts.htsvoice，首次运行时解压到 filesDir/voices
 * - 初始化状态机：NOT_READY -> INITIALIZING -> READY | FAILED，失败后可重试，
 *   失败原因通过 [lastError] 暴露，UI 可直接展示。
 */
object Espeak {

    private const val TAG = "Espeak"

    enum class State { NOT_READY, INITIALIZING, READY, FAILED }

    @Volatile
    private var state = State.NOT_READY

    @Volatile
    private var sampleRate = 44100

    @Volatile
    private var playing = false

    @Volatile
    private var lastError: String? = null

    /** 朗读语速倍率（0.75 ~ 1.5，默认 1.0）。rate 基准 150，越大越快。 */
    @Volatile
    var speechRate: Float = 1f
        private set

    fun setSpeechRate(v: Float) {
        speechRate = v.coerceIn(0.75f, 1.5f)
    }

    fun state(): State = state

    fun isReady(): Boolean = state == State.READY

    /** 最近一次错误信息（null 表示无错误），供 UI 展示诊断信息 */
    fun lastError(): String? = lastError

    /** 加载原生库（必须在 speak 前调用） */
    private fun load() {
        System.loadLibrary("ttsmimiccore")
        System.loadLibrary("HTSEngine")
        System.loadLibrary("pcre2-8")
        System.loadLibrary("ttsmimic_french")
        System.loadLibrary("ttsmimic_siwis_fr_zoe_hts")
        System.loadLibrary("mimicbridge")
    }

    /**
     * 启动初始化（幂等，非阻塞）。READY/INITIALIZING 时直接返回。
     * 初始化在后台线程执行，完成后 [state] 变为 READY 或 FAILED，
     * 失败后再次调用本方法会重新初始化。
     */
    fun ensureInitialized(context: Context) {
        if (state == State.READY || state == State.INITIALIZING) return
        synchronized(this) {
            if (state == State.READY || state == State.INITIALIZING) return
            state = State.INITIALIZING
            lastError = null
        }
        val app = context.applicationContext
        Thread {
            try {
                val ok = doInit(app)
                synchronized(this) {
                    state = if (ok) State.READY else State.FAILED
                }
            } catch (e: Throwable) {
                Log.e(TAG, "initialize failed", e)
                synchronized(this) {
                    state = State.FAILED
                    lastError = "初始化异常: ${e.message}"
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun doInit(context: Context): Boolean {
        load()
        val voicesDir = File(context.filesDir, "voices")
        if (!voicesDir.exists() || !File(voicesDir, "siwis_fr_zoe_hts.htsvoice").exists()) {
            voicesDir.deleteRecursively()
            voicesDir.mkdirs()
            copyAssetsRecursive(context, "voices", voicesDir)
        }
        val sr = nativeInit(context.filesDir.absolutePath)
        if (sr <= 0) {
            lastError = "语音引擎初始化失败(nativeInit=$sr)"
            return false
        }
        sampleRate = sr
        return true
    }

    private fun copyAssetsRecursive(context: Context, assetPath: String, targetDir: File) {
        val assetManager = context.assets
        val children = assetManager.list(assetPath) ?: return
        for (child in children) {
            val p = "$assetPath/$child"
            val out = File(targetDir, child)
            if (out.exists()) continue
            // assets.list() 对目录返回子项数组，对文件返回 null。
            if (assetManager.list(p)?.isNotEmpty() == true) {
                out.mkdirs()
                copyAssetsRecursive(context, p, out)
            } else {
                out.parentFile?.mkdirs()
                assetManager.open(p).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    /**
     * 合成并播放法语文本。
     * 返回 true 表示播放任务已启动；false 表示引擎无法就绪或参数非法。
     * 若引擎仍在初始化中，会在后台等待就绪后自动合成并播放，
     * 不再让调用方反复看到"正在初始化"。
     */
    fun speak(text: String): Boolean {
        if (text.isBlank()) return true
        if (playing) stop()
        playing = true
        val t = Thread {
            try {
                // 引擎尚未就绪时，在后台等待（最长 10 秒），而非直接失败
                if (state != State.READY) {
                    val deadline = System.currentTimeMillis() + 10_000L
                    while (state == State.INITIALIZING && System.currentTimeMillis() < deadline) {
                        Thread.sleep(50)
                    }
                    if (state != State.READY) {
                        lastError = when (state) {
                            State.FAILED -> "语音引擎初始化失败"
                            State.INITIALIZING -> "语音引擎初始化超时"
                            else -> "语音引擎未就绪"
                        }
                        return@Thread
                    }
                }
                // 语速：rate 基准 150（1.0x），>150 更快，<150 更慢
                val rate = (150f * speechRate).toInt().coerceAtLeast(50)
                val bytes = nativeSpeak(text, "fr", rate)
                if (bytes == null || bytes.isEmpty()) {
                    lastError = "语音合成失败(无 PCM)"
                    return@Thread
                }
                playBytes(bytes)
            } catch (e: Throwable) {
                lastError = "播放失败: ${e.message}"
                Log.e(TAG, "speak failed", e)
            } finally {
                playing = false
            }
        }
        t.isDaemon = true
        t.start()
        return true
    }

    /** 合成文本，返回 PCM（short[]，采样率见 [sampleRate]）。rate 沿用当前语速倍率 */
    suspend fun synthesize(text: String, voice: String = "fr"): ShortArray? =
        withContext(Dispatchers.IO) {
            if (state != State.READY) return@withContext null
            try {
                val rate = (150f * speechRate).toInt().coerceAtLeast(50)
                val bytes = nativeSpeak(text, voice, rate) ?: run {
                    lastError = "合成失败(空 PCM)"
                    return@withContext null
                }
                ShortArray(bytes.size / 2) { i ->
                    ((bytes[i * 2].toInt() and 0xFF) or
                        (bytes[i * 2 + 1].toInt() shl 8)).toShort()
                }
            } catch (e: Throwable) {
                lastError = "合成异常: ${e.message}"
                Log.e(TAG, "synthesize failed", e)
                null
            }
        }

    fun isPlaying(): Boolean = playing

    /** 停止播放 */
    fun stop() { playing = false }

    private fun playBytes(bytes: ByteArray) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            lastError = "AudioTrack minBuffer 无效($minBuf)"
            return
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(maxOf(bytes.size, minBuf))
            .build()
        try {
            val written = track.write(bytes, 0, bytes.size)
            if (written != bytes.size) {
                lastError = "AudioTrack 写入不完整($written/${bytes.size})"
            }
            playing = true
            track.play()
            // 播放循环带超时保护，避免异常设备上死循环
            val deadline = System.currentTimeMillis() + 30_000L
            while (playing && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                if (System.currentTimeMillis() > deadline) break
                Thread.sleep(50)
            }
        } catch (e: Throwable) {
            lastError = "播放异常: ${e.message}"
            Log.e(TAG, "AudioTrack error", e)
        } finally {
            playing = false
            try { track.stop() } catch (_: Throwable) {}
            track.release()
        }
    }

    private external fun nativeInit(dataDir: String): Int
    private external fun nativeSpeak(text: String, voice: String, rate: Int): ByteArray?
}
