package com.coolmoonfrench.dict

import android.content.Context
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * sherpa-onnx 识别引擎封装（16k/单声道/16bit wav 输入）。
 *
 * - FR：流式 Zipformer transducer，分块喂入，端点切段，返回整段法语文本。
 * - WHISPER：离线 Whisper large-v3-turbo（语言锁定法语、transcribe）。
 *
 * 两个引擎均为重资源对象，识别完立即 close；并发识别由调用方串行保证。
 */
object SherpaAsr {

    private const val SAMPLE_RATE = 16000

    init {
        // 与 Kotlin API 同一套 JNI 库；重复 loadLibrary 安全
        System.loadLibrary("sherpa-onnx-jni")
    }

    /** 读取 wav 为 FloatArray（[-1,1]），复用 ffmpeg 生成的 44 字节头或 data 块 */
    private fun readWaveFloats(wav: File): FloatArray {
        val raw = wav.readBytes()
        var dataStart = -1
        var dataLen = 0
        var i = 0
        while (i + 8 <= raw.size) {
            val id = String(raw, i, 4, Charsets.ISO_8859_1)
            val len = (raw[i + 4].toInt() and 0xff) or
                (raw[i + 5].toInt() and 0xff shl 8) or
                (raw[i + 6].toInt() and 0xff shl 16) or
                (raw[i + 7].toInt() and 0xff shl 24)
            if (id == "data") { dataStart = i + 8; dataLen = len; break }
            i += 8 + len + (len % 2)
            if (id == "RIFF") i = 12
        }
        if (dataStart < 0) { dataStart = 44; dataLen = raw.size - 44 }
        val end = minOf(dataStart + dataLen, raw.size)
        val samples = (end - dataStart) / 2
        require(samples > 0) { "wav 中没有可用音频数据" }
        val out = FloatArray(samples)
        for (s in 0 until samples) {
            val lo = raw[dataStart + s * 2].toInt() and 0xff
            val hi = raw[dataStart + s * 2 + 1].toInt()
            out[s] = ((hi shl 8) or lo).toShort() / 32768f
        }
        return out
    }

    /** 识别 wav，返回法语文本。引擎未就绪时抛 IOException。 */
    suspend fun recognize(context: Context, wav: File, engine: AsrModelManager.Engine): String =
        when (engine) {
            AsrModelManager.Engine.FR -> recognizeFr(context, wav)
            AsrModelManager.Engine.WHISPER -> recognizeWhisper(context, wav)
        }

    // ---------- 方案A：流式 Zipformer ----------

    private suspend fun recognizeFr(context: Context, wav: File): String = withContext(Dispatchers.Default) {
        val dir = AsrModelManager.frModelFilesDir(context) ?: throw IOException("法语模型未安装")
        val files = dir.listFiles()?.toList() ?: throw IOException("法语模型目录为空")
        fun pick(vararg keys: String): File = files.firstOrNull { f ->
            f.isFile && f.name.lowercase().let { n -> keys.any { n.contains(it) } && (n.endsWith(".onnx") || n == "tokens.txt") }
        } ?: throw IOException("法语模型缺少文件: ${keys.joinToString()}")
        val encoder = pick("encoder")
        val decoder = pick("decoder", "!joiner")
        val joiner = pick("joiner")
        val tokens = File(dir, "turbo-tokens.txt").takeIf { it.isFile } ?: File(dir, "tokens.txt").takeIf { it.isFile } ?: pick("tokens")

        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    joiner = joiner.absolutePath,
                ),
                tokens = tokens.absolutePath,
                numThreads = 2,
                modelType = "transducer",
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0.0f),
                rule2 = EndpointRule(true, 1.2f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 20.0f),
            ),
            enableEndpoint = true,
        )
        val recognizer = OnlineRecognizer(config = config)
        try {
            val samples = readWaveFloats(wav)
            val stream = recognizer.createStream()
            try {
                val parts = StringBuilder()
                val chunk = SAMPLE_RATE / 10 // 0.1s
                var offset = 0
                while (offset < samples.size) {
                    coroutineContext.ensureActive()
                    val n = minOf(chunk, samples.size - offset)
                    stream.acceptWaveform(samples.copyOfRange(offset, offset + n), SAMPLE_RATE)
                    offset += n
                    while (recognizer.isReady(stream)) recognizer.decode(stream)
                    if (recognizer.isEndpoint(stream)) {
                        appendText(parts, recognizer.getResult(stream).text)
                        recognizer.reset(stream)
                    }
                }
                // 尾部静音促使最后一个端点触发
                stream.acceptWaveform(FloatArray(SAMPLE_RATE), SAMPLE_RATE)
                while (recognizer.isReady(stream)) recognizer.decode(stream)
                appendText(parts, recognizer.getResult(stream).text)
                val text = parts.toString().trim()
                if (text.isEmpty()) throw IOException("未识别到语音内容")
                text
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
        }
    }

    private fun appendText(sb: StringBuilder, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append(t)
    }

    // ---------- 方案C：Whisper ----------

    private suspend fun recognizeWhisper(context: Context, wav: File): String = withContext(Dispatchers.Default) {
        val dir = AsrModelManager.whisperDir(context)
        val encoder = File(dir, "turbo-encoder.int8.onnx")
        val decoder = File(dir, "turbo-decoder.int8.onnx")
        val tokens = File(dir, "turbo-tokens.txt").takeIf { it.isFile } ?: File(dir, "tokens.txt")
        if (!encoder.isFile || !decoder.isFile || !tokens.isFile) throw IOException("Whisper 模型未安装完整")

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 128),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    language = "fr",
                    task = "transcribe",
                    tailPaddings = 1000,
                ),
                tokens = tokens.absolutePath,
                numThreads = 4,
            ),
        )
        val recognizer = OfflineRecognizer(config = config)
        try {
            val samples = readWaveFloats(wav)
            val stream = recognizer.createStream()
            try {
                val chunk = SAMPLE_RATE * 30
                var offset = 0
                while (offset < samples.size) {
                    coroutineContext.ensureActive()
                    val n = minOf(chunk, samples.size - offset)
                    stream.acceptWaveform(samples.copyOfRange(offset, offset + n), SAMPLE_RATE)
                    offset += n
                }
                recognizer.decode(stream)
                val text = recognizer.getResult(stream).text.trim()
                if (text.isEmpty()) throw IOException("未识别到语音内容")
                text
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
        }
    }
}
