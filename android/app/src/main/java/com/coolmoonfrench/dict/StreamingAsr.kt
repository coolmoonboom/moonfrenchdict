package com.coolmoonfrench.dict

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.concurrent.thread

/**
 * 基于 Vosk 的**流式**麦克风语音识别（离线）。
 *
 * 与 [VideoToText] 的批量 wav 识别不同，本类直接从 [AudioRecord] 读取 PCM 并持续喂给
 * Vosk [Recognizer]，因此可以边说话边给出识别结果，并在松开按钮时得到最终文本。
 *
 * 调用约定：
 * - [start] 后立即在后台线程录音识别；
 * - [onPartial] 会频繁回调当前已识别的文本（可能为空），用于界面实时展示；
 * - [stop] 停止录音并返回最终识别文本（可能为空串）；
 * - [cancel] 丢弃本次识别且不返回文本。
 *
 * 全程离线，不依赖网络。识别所用模型由 [VoskModelManager] 决定（默认内置小模型）。
 */
class StreamingAsr(private val model: Model) {

    interface Listener {
        fun onPartial(text: String)
        fun onError(message: String)
    }

    private val sampleRate = 16000

    @Volatile
    private var running = false

    private var recognizer: Recognizer? = null
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    /** 开始录音识别；重复调用会被忽略。 */
    @SuppressLint("MissingPermission")
    fun start(listener: Listener) {
        if (running) return
        val rec = try {
            Recognizer(model, sampleRate.toFloat())
        } catch (e: Exception) {
            listener.onError("识别器初始化失败：${e.message}")
            return
        }
        recognizer = rec

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufBytes = if (minBuf > 0) minBuf * 2 else sampleRate * 2
        val ar = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufBytes
            )
        } catch (e: Exception) {
            listener.onError("无法创建录音器：${e.message}")
            runCatching { rec.close() }
            recognizer = null
            return
        }
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            runCatching { rec.close() }
            recognizer = null
            listener.onError("麦克风不可用，请检查录音权限")
            return
        }
        record = ar
        running = true
        try {
            ar.startRecording()
        } catch (e: Exception) {
            running = false
            ar.release()
            record = null
            runCatching { rec.close() }
            recognizer = null
            listener.onError("无法开始录音：${e.message}")
            return
        }

        worker = thread(name = "vosk-asr", isDaemon = true) {
            val buf = ByteArray(8192)
            try {
                while (running) {
                    val n = ar.read(buf, 0, buf.size)
                    if (n < 0) break
                    if (n == 0) continue
                    val endpoint = rec.acceptWaveForm(buf, n)
                    if (endpoint) {
                        val text = voskField(rec.result, "text")
                        if (text.isNotBlank()) listener.onPartial(text)
                    } else {
                        listener.onPartial(voskField(rec.partialResult, "partial"))
                    }
                }
            } catch (e: Throwable) {
                if (running) listener.onError("识别中断：${e.message}")
            }
        }
    }

    /** 停止录音并返回最终识别文本；同时释放本次识别资源。 */
    fun stop(): String {
        running = false
        runCatching { worker?.join(1500) }
        worker = null
        val rec = recognizer
        recognizer = null
        val text = if (rec != null) {
            val t = voskField(rec.finalResult, "text")
            runCatching { rec.close() }
            t
        } else ""
        releaseAudio()
        return text.trim()
    }

    /** 取消本次识别，不返回文本。 */
    fun cancel() {
        running = false
        runCatching { worker?.join(800) }
        worker = null
        runCatching { recognizer?.close() }
        recognizer = null
        releaseAudio()
    }

    private fun releaseAudio() {
        val ar = record ?: return
        record = null
        runCatching {
            if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) ar.stop()
        }
        runCatching { ar.release() }
    }

}

/** 从 Vosk 返回的 JSON 中安全取出指定字段；解析失败或无该字段时返回空串。 */
internal fun voskField(json: String, key: String): String =
    runCatching { JSONObject(json).optString(key, "") }.getOrDefault("")
