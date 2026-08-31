package com.coolmoonfrench.dict

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * eSpeak-NG 法语 TTS 封装。
 * - 原生库：libespeak-ng + libttsbridge（JNI 桥）
 * - 语音数据：assets/espeak-ng-data，首次运行时解压到 filesDir
 */
object Espeak {

    private var initialized = false
    private var sampleRate = 22050
    private var playing = false

    /** 加载原生库（必须在 speak 前调用） */
    private fun load() {
        System.loadLibrary("espeak-ng")
        System.loadLibrary("ttsbridge")
    }

    /**
     * 初始化：解压语音数据并调用 nativeInit。幂等。
     * 返回 true 表示成功。
     */
    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (initialized) return@withContext true
        try {
            load()
            val dataDir = File(context.filesDir, "espeak-ng-data")
            if (!dataDir.exists() || !File(dataDir, "phondata").exists()) {
                dataDir.deleteRecursively()
                dataDir.mkdirs()
                copyAssetsRecursive(context, "espeak-ng-data", dataDir)
            }
            // espeak_Initialize 的 path 应为包含 espeak-ng-data 的父目录
            val sr = nativeInit(context.filesDir.absolutePath)
            if (sr <= 0) return@withContext false
            sampleRate = sr
            initialized = true
            true
        } catch (e: Throwable) {
            initialized = false
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

    /** 合成并播放法语文本 */
    fun speak(text: String, rate: Int = 150) {
        if (!initialized || playing) return
        playPcmInternal(text, "fr", rate)
    }

    /** 合成文本，返回 PCM（short[]，采样率见 [sampleRate]） */
    suspend fun synthesize(text: String, voice: String = "fr", rate: Int = 150): ShortArray? =
        withContext(Dispatchers.IO) {
            if (!initialized) return@withContext null
            try {
                val bytes = nativeSpeak(text, voice, rate) ?: return@withContext null
                ShortArray(bytes.size / 2) { i ->
                    ((bytes[i * 2].toInt() and 0xFF) or
                        (bytes[i * 2 + 1].toInt() shl 8)).toShort()
                }
            } catch (e: Throwable) {
                null
            }
        }

    fun isPlaying(): Boolean = playing

    /** 停止播放 */
    fun stop() { playing = false }

    private fun playPcmInternal(text: String, voice: String, rate: Int) {
        threadPlay {
            if (!initialized) return@threadPlay
            val bytes = nativeSpeak(text, voice, rate) ?: return@threadPlay
            playBytes(bytes)
        }
    }

    private fun threadPlay(block: () -> Unit) {
        val t = Thread(block)
        t.isDaemon = true
        t.start()
    }

    private fun playBytes(bytes: ByteArray) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
            .setBufferSizeInBytes(bytes.size)
            .build()
        try {
            track.write(bytes, 0, bytes.size)
            playing = true
            track.play()
            while (playing && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                Thread.sleep(50)
            }
        } catch (e: Throwable) {
            // ignore
        } finally {
            playing = false
            track.release()
        }
    }

    private external fun nativeInit(dataDir: String): Int
    private external fun nativeSpeak(text: String, voice: String, rate: Int): ByteArray?
}