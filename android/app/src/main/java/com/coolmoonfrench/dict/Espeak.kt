package com.coolmoonfrench.dict

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 法语 TTS 封装（Sherpa-ONNX + Piper 神经网络语音）。
 * - 引擎：sherpa-onnx（自带 ONNX Runtime + espeak-ng 音素化），Piper 声线
 *   fr_FR-siwis-medium（22050Hz，深度神经网络合成，听感自然）
 * - 模型：assets/tts/fr_FR-siwis-medium/{*.onnx,tokens.txt,espeak-ng-data}，
 *   首次运行时解压到 filesDir/tts
 * - 初始化状态机：NOT_READY -> INITIALIZING -> READY | FAILED，失败后可重试，
 *   失败原因通过 [lastError] 暴露，UI 可直接展示。
 * - 播放采用单例协程 Job：每次 play 前先取消上一次，杜绝快速连点时的语音叠加。
 */
object Espeak {

    private const val TAG = "Espeak"

    enum class State { NOT_READY, INITIALIZING, READY, FAILED }

    @Volatile
    private var state = State.NOT_READY

    @Volatile
    private var sampleRate = 22050

    @Volatile
    private var playing = false

    @Volatile
    private var lastError: String? = null

    private var tts: OfflineTts? = null

    /** 朗读语速倍率（0.75 ~ 1.5，默认 1.0）。Piper 引擎 speed 参数，1.0 为正常语速。 */
    @Volatile
    var speechRate: Float = 1f
        private set

    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null

    fun setSpeechRate(v: Float) {
        speechRate = v.coerceIn(0.75f, 1.5f)
    }

    fun state(): State = state

    fun isReady(): Boolean = state == State.READY

    /** 最近一次错误信息（null 表示无错误），供 UI 展示诊断信息 */
    fun lastError(): String? = lastError

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
        val ttsDir = File(context.filesDir, "tts/fr_FR-siwis-medium")
        val model = File(ttsDir, "fr_FR-siwis-medium.onnx")
        val tokens = File(ttsDir, "tokens.txt")
        val espeakData = File(ttsDir, "espeak-ng-data")
        // 模型文件缺失或大小异常（可能上次复制中断）时，重建并重新复制。
        if (!model.exists() || model.length() < 50_000_000L ||
            !tokens.exists() || !espeakData.isDirectory
        ) {
            runCatching { ttsDir.deleteRecursively() }
            ttsDir.mkdirs()
            copyAssetsRecursive(context, "tts/fr_FR-siwis-medium", ttsDir)
            if (!model.exists() || model.length() < 50_000_000L || !espeakData.isDirectory) {
                lastError = "语音模型解压失败(onnx 或 espeak-ng-data 缺失)"
                return false
            }
        }
        return try {
            val tts = OfflineTts(
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = model.absolutePath,
                            lexicon = "",
                            tokens = tokens.absolutePath,
                            dataDir = espeakData.absolutePath,
                            dictDir = "",
                            noiseScale = 0.667f,
                            noiseScaleW = 0.8f,
                            lengthScale = 1.0f
                        ),
                        numThreads = 2,
                        debug = false
                    )
                )
            )
            val sr = tts.sampleRate()
            if (sr <= 0) {
                lastError = "TTS 引擎返回无效采样率($sr)"
                return false
            }
            sampleRate = sr
            this.tts = tts
            true
        } catch (e: Throwable) {
            lastError = "TTS 引擎初始化失败: ${e.message}"
            Log.e(TAG, "OfflineTts init failed", e)
            false
        }
    }

    private fun copyAssetsRecursive(context: Context, assetPath: String, targetDir: File) {
        val assetManager = context.assets
        val children = assetManager.list(assetPath) ?: return
        for (child in children) {
            val p = "$assetPath/$child"
            val out = File(targetDir, child)
            // 不跳过已存在文件，确保复制完整（源文件会覆盖，幂等无害）
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

    /** 用 Piper 引擎合成，返回 22050Hz 16bit 单声道 PCM 字节流 */
    private fun synthesizePcm(text: String, speed: Float): ByteArray? {
        val engine = tts ?: return null
        val samples = engine.generate(text, 0, speed).samples
        if (samples.isEmpty()) return null
        // float[] -> 16bit PCM
        val bytes = ByteArray(samples.size * 2)
        var i = 0
        for (s in samples) {
            val v = (s.coerceIn(-1f, 1f) * 32767f).toInt()
            bytes[i++] = (v and 0xFF).toByte()
            bytes[i++] = ((v shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * 合成并播放法语文本。
     * 返回 true 表示播放任务已启动；false 表示引擎已失败或参数非法（同步可判）。
     * 若引擎仍在初始化中，会在后台等待就绪后自动合成并播放。
     * 合成/播放失败时通过 [onError] 回调告知具体原因（UI 可用 Toast 展示）。
     * 播放采用单例 Job：本方法会先取消正在进行的上一次播放，保证不会叠加。
     */
    fun speak(text: String, onError: ((String) -> Unit)? = null): Boolean {
        if (text.isBlank()) return true
        if (state == State.FAILED) {
            // 引擎已确定失败：同步返回 false，让 UI 立即提示真实原因
            val msg = lastError ?: "语音引擎初始化失败"
            onError?.invoke(msg)
            return false
        }
        stop()

        playbackJob = playbackScope.launch {
            try {
                // 引擎尚未就绪时等待（最长 10 秒），而非直接失败
                ensureEngineReady(onError)

                // 语速：Piper 的 speed 参数，1.0 = 正常，>1 快，<1 慢
                val speed = speechRate.coerceIn(0.75f, 1.5f)
                val bytes = synthesizePcm(text, speed)
                ensureActive()
                if (bytes == null || bytes.isEmpty()) {
                    val msg = lastError ?: "语音合成失败(无 PCM)"
                    onError?.invoke(msg)
                    return@launch
                }
                playPcm(bytes, onError)
            } catch (_: CancellationException) {
                // 被新的 speak/stop 取消：静默退出，不弹任何提示
            } catch (e: Throwable) {
                val msg = "播放失败: ${e.message}"
                lastError = msg
                onError?.invoke(msg)
                Log.e(TAG, "speak failed", e)
            } finally {
                if (playbackJob === coroutineContext[Job]) {
                    playing = false
                }
            }
        }
        return true
    }

    private suspend fun ensureEngineReady(onError: ((String) -> Unit)?) {
        if (state == State.READY) return
        val deadline = System.currentTimeMillis() + 10_000L
        while (state == State.INITIALIZING && System.currentTimeMillis() < deadline) {
            delay(50)
        }
        if (state != State.READY) {
            val msg = when (state) {
                State.FAILED -> lastError ?: "语音引擎初始化失败"
                State.INITIALIZING -> "语音引擎初始化超时"
                else -> "语音引擎未就绪"
            }
            lastError = msg
            onError?.invoke(msg)
            throw CancellationException()
        }
    }

    /** 朗读并自动弹出失败原因 Toast。供 UI 按钮统一调用，快速连点只会播最新一次。 */
    fun speakWithFeedback(context: Context, text: String) {
        val app = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        val ok = speak(text) { err ->
            mainHandler.post {
                Toast.makeText(app, "朗读失败：$err", Toast.LENGTH_LONG).show()
            }
        }
        if (!ok) {
            mainHandler.post {
                Toast.makeText(app, "朗读失败：${lastError ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 合成文本，返回 PCM（short[]，采样率见 [sampleRate]）。speed 沿用当前语速倍率 */
    suspend fun synthesize(text: String, voice: String = "fr"): ShortArray? =
        withContext(Dispatchers.IO) {
            if (state != State.READY) return@withContext null
            try {
                val bytes = synthesizePcm(text, speechRate.coerceIn(0.75f, 1.5f)) ?: run {
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

    /** 停止播放：取消当前播放 Job 并释放 AudioTrack。 */
    fun stop() {
        playbackJob?.cancel()
        playbackJob = null

        val track = audioTrack ?: return
        audioTrack = null
        runCatching {
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                track.stop()
            }
        }
        track.release()
        playing = false
    }

    private fun buildAudioTrack(bufferSizeBytes: Int): AudioTrack =
        AudioTrack.Builder()
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
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSizeBytes)
            .build()

    /**
     * 分块写入 PCM 并排空播放（协程内执行，可被取消）。
     * 采用 MODE_STREAM：每写一块后若未在播放则 play()，由系统按 buffer 节奏消费；
     * 排空阶段按播放头位置轮询，直到播完或被取消。
     */
    private suspend fun playPcm(bytes: ByteArray, onError: ((String) -> Unit)? = null) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            val msg = "AudioTrack minBuffer 无效($minBuf)"
            lastError = msg
            onError?.invoke(msg)
            return
        }
        val track = buildAudioTrack(maxOf(minBuf, bytes.size))
        audioTrack = track
        try {
            var written = 0
            val chunkSize = 4096
            while (written < bytes.size) {
                currentCoroutineContext().ensureActive()
                val len = minOf(chunkSize, bytes.size - written)
                val n = track.write(bytes, written, len, AudioTrack.WRITE_BLOCKING)
                if (n <= 0) {
                    val msg = "AudioTrack 写入中断(written=$written/$n)"
                    lastError = msg
                    onError?.invoke(msg)
                    return
                }
                written += n
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
            }

            // 排空：按播放头完成度判断，期间可被取消
            val totalFrames = bytes.size / 2
            val deadline = System.currentTimeMillis() + 60_000L
            while (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                currentCoroutineContext().ensureActive()
                if (track.playbackHeadPosition >= totalFrames) break
                if (System.currentTimeMillis() >= deadline) break
                delay(20)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val msg = "播放异常: ${e.message}"
            lastError = msg
            onError?.invoke(msg)
            Log.e(TAG, "AudioTrack error", e)
        } finally {
            if (audioTrack === track) {
                audioTrack = null
                cleanupTrack(track)
                playing = false
            } else {
                cleanupTrack(track)
            }
        }
    }

    private fun cleanupTrack(track: AudioTrack?) {
        if (track == null) return
        runCatching {
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                track.stop()
            }
        }
        track.release()
    }
}