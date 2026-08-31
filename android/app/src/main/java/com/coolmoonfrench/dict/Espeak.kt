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
 */
object Espeak {

    private const val TAG = "Espeak"

    @Volatile
    private var initialized = false

    @Volatile
    private var sampleRate = 44100

    @Volatile
    private var playing = false

    private var lastError: String? = null

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
     * 初始化：解压语音数据并调用 nativeInit。幂等。
     * 返回 true 表示成功。
     */
    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (initialized) return@withContext true
        try {
            load()
            val voicesDir = File(context.filesDir, "voices")
            if (!voicesDir.exists() || !File(voicesDir, "siwis_fr_zoe_hts.htsvoice").exists()) {
                voicesDir.deleteRecursively()
                voicesDir.mkdirs()
                copyAssetsRecursive(context, "voices", voicesDir)
            }
            val sr = nativeInit(context.filesDir.absolutePath)
            if (sr <= 0) {
                lastError = "Mimic 初始化失败(nativeInit=$sr)"
                return@withContext false
            }
            sampleRate = sr
            initialized = true
            lastError = null
            true
        } catch (e: Throwable) {
            initialized = false
            lastError = "初始化异常: ${e.message}"
            Log.e(TAG, "initialize failed", e)
            false
        }
    }

    private fun copyAssetsRecursive(context: Context, assetPath: String, targetDir: File) {
        val assetManager = context.assets
        val children = assetManager.list(assetPath) ?: return
        for (child in children) {
            val p = "$assetPath/$child"
            val out = File(targetDir, child)
            if (out.exists()) continue
            // assets.list() 对目录返回子项数组，对文件返回 null。
            if (assetManager.list(p)?.isEmpty() == false) {
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
     * 返回 true 表示播放已开始；false 表示未初始化。
     * 合成与播放均在后台线程执行，不阻塞调用线程。
     */
    fun speak(text: String, rate: Int = 150): Boolean {
        if (!initialized) {
            lastError = "语音引擎未初始化"
            return false
        }
        if (playing) {
            return true
        }
        playing = true
        val t = Thread {
            try {
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

    /** 合成文本，返回 PCM（short[]，采样率见 [sampleRate]） */
    suspend fun synthesize(text: String, voice: String = "fr", rate: Int = 150): ShortArray? =
        withContext(Dispatchers.IO) {
            if (!initialized) return@withContext null
            try {
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
