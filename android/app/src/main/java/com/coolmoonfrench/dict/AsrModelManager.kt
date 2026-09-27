package com.coolmoonfrench.dict

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * sherpa-onnx 识别模型管理（视频转文字「识别模型」卡片）。
 *
 * 两种可选模型：
 * - FR（方案A）：法语流式 Zipformer（kroko-2025），约 52MB，识别快、纯法语。
 *   自托管 zip 包（官方 tar.bz2 的 Android 端无法解 bzip2，因此以 zip 形式发布在仓库 release）。
 * - WHISPER（方案C）：Whisper large-v3-turbo（int8 量化 encoder/decoder + tokens 三文件），
 *   约 1GB，多语言高精度；每个文件配主/备两个下载源。
 *
 * 两个模型都未下载/未选中时，识别自动静默使用内置 vosk 小模型（不在此类中体现）。
 */
object AsrModelManager {

    /** UI 可选引擎 */
    enum class Engine { FR, WHISPER }

    /** 当前模型就绪状态 */
    sealed class State {
        object NotDownloaded : State()
        data class Downloading(val percent: Int, val bytesRead: Long, val totalBytes: Long) : State()
        data class Failed(val message: String) : State()
        object Installing : State()
        object Ready : State()
        data class Paused(val percent: Int) : State()
    }

    /** 暂停信号：读循环抛出，保留半截文件供续传 */
    class PausedSignal : IOException("下载已暂停")

    @Volatile
    private var paused = false

    /** 请求暂停当前下载（手动按钮或界面退后台时调用）；再次开始下载即续传。 */
    fun pauseDownload() {
        paused = true
    }

    fun pauseRequested(): Boolean = paused

    private const val PREF = "video_text"
    private const val K_ENGINE = "asr_engine_v1"

    /** A 模型 zip（自托管） */
    const val FR_ZIP_URL =
        "https://github.com/coolmoonboom/moonfrenchdict/releases/download/asr-models/asr-fr-zipformer-kroko-2025.zip"
    private const val FR_ZIP_NAME = "asr-fr-zipformer-kroko-2025.zip"

    /** C 模型三个文件；每个文件一组候选 URL（按顺序尝试） */
    private const val WHISPER_TAG = "asr-models"
    private val WHISPER_FILES = listOf(
        WhisperFile(
            "turbo-encoder.int8.onnx", 674_716_297L,
            listOf(
                "https://github.com/coolmoonboom/moonfrenchdict/releases/download/$WHISPER_TAG/asr-whisper-turbo-encoder.int8.onnx",
                "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-large-v3-turbo/resolve/main/turbo-encoder.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-large-v3-turbo/resolve/main/turbo-encoder.int8.onnx",
            )
        ),
        WhisperFile(
            "turbo-decoder.int8.onnx", 361_080_764L,
            listOf(
                "https://github.com/coolmoonboom/moonfrenchdict/releases/download/$WHISPER_TAG/asr-whisper-turbo-decoder.int8.onnx",
                "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-large-v3-turbo/resolve/main/turbo-decoder.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-large-v3-turbo/resolve/main/turbo-decoder.int8.onnx",
            )
        ),
        WhisperFile(
            "turbo-tokens.txt", 816_730L,
            listOf(
                "https://github.com/coolmoonboom/moonfrenchdict/releases/download/$WHISPER_TAG/asr-whisper-turbo-tokens.txt",
                "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-large-v3-turbo/resolve/main/turbo-tokens.txt",
                "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-large-v3-turbo/resolve/main/turbo-tokens.txt",
            )
        ),
    )

    private data class WhisperFile(val name: String, val approxSize: Long, val urls: List<String>)

    /** whisper 总字节估算（用于总体进度条） */
    val whisperTotalBytesApprox: Long get() = WHISPER_FILES.sumOf { it.approxSize }

    /** 运行 whisper 建议的最低可用内存：int8 模型原生加载 + onnxruntime 运行余量 */
    private const val WHISPER_MIN_FREE_MEM = 3L * 1024 * 1024 * 1024 // 3GB

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // ---------- 路径 ----------

    fun frDir(context: Context): File = File(context.filesDir, "asr_fr_zipformer")
    fun whisperDir(context: Context): File = File(context.filesDir, "asr_whisper_turbo")
    private fun cacheFile(context: Context, name: String): File = File(context.cacheDir, name)

    // ---------- 状态 ----------

    fun isFrReady(context: Context): Boolean =
        requiredFiles(File(frDir(context), "asr-fr-zipformer-kroko-2025")).isNotEmpty() ||
            requiredFiles(frDir(context)).isNotEmpty()

    private fun requiredFiles(dir: File): List<String> {
        if (!dir.isDirectory) return emptyList()
        val names = dir.listFiles()?.mapNotNull { f ->
            val n = f.name.lowercase()
            when {
                f.isFile && n.contains("encoder") && n.endsWith(".onnx") -> "encoder"
                f.isFile && n.contains("decoder") && n.endsWith(".onnx") -> "decoder"
                f.isFile && n.contains("joiner") && n.endsWith(".onnx") -> "joiner"
                f.isFile && n == "tokens.txt" -> "tokens"
                else -> null
            }
        } ?: return emptyList()
        return if (names.toSet().containsAll(listOf("encoder", "decoder", "joiner", "tokens")))
            listOf("encoder", "decoder", "joiner", "tokens") else emptyList()
    }

    /** A 模型四个文件所在目录（可能位于解压后的一级子目录内） */
    fun frModelFilesDir(context: Context): File? {
        val root = frDir(context)
        return if (requiredFiles(root).isNotEmpty()) root
        else root.listFiles()?.firstOrNull { it.isDirectory && requiredFiles(it).isNotEmpty() }
            ?: root.takeIf { it.isDirectory }
    }

    fun isWhisperReady(context: Context): Boolean =
        WHISPER_FILES.all { File(whisperDir(context), it.name).length() > 0 }

    fun hasEnoughMemoryForWhisper(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return true
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem >= WHISPER_MIN_FREE_MEM
    }

    /** 用户选择的引擎；对应模型未就绪时返回 null（调用方静默走 vosk） */
    fun engineInUse(context: Context): Engine? = when (selected(context)) {
        Engine.FR -> if (isFrReady(context)) Engine.FR else null
        Engine.WHISPER -> if (isWhisperReady(context)) Engine.WHISPER else null
    }

    fun selected(context: Context): Engine {
        val v = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(K_ENGINE, Engine.FR.name)
        return runCatching { Engine.valueOf(v ?: Engine.FR.name) }.getOrDefault(Engine.FR)
    }

    fun setSelected(context: Context, engine: Engine) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(K_ENGINE, engine.name).apply()
    }

    /** 打开界面时的初始状态 */
    fun initialState(context: Context, engine: Engine): State = when (engine) {
        Engine.FR -> if (isFrReady(context)) State.Ready else State.NotDownloaded
        Engine.WHISPER -> if (isWhisperReady(context)) State.Ready else State.NotDownloaded
    }

    fun deleteModel(context: Context, engine: Engine) {
        (if (engine == Engine.FR) frDir(context) else whisperDir(context)).deleteRecursively()
    }

    // ---------- 下载 ----------

    suspend fun downloadFr(context: Context, onState: (State) -> Unit) = withContext(Dispatchers.IO) {
        val zip = cacheFile(context, FR_ZIP_NAME)
        var lastPct = 0
        try {
            paused = false
            onState(State.Downloading(0, 0, 0))
            httpDownload(FR_ZIP_URL, zip, zip.length()) { read, total ->
                lastPct = if (total > 0) ((read * 100) / total).toInt().coerceIn(0, 100) else 0
                onState(State.Downloading(lastPct, read, total))
            }
            onState(State.Installing)
            val dest = frDir(context)
            dest.deleteRecursively()
            dest.mkdirs()
            unzip(zip, dest)
            zip.delete()
            if (requiredFiles(dest).isEmpty() && frModelFilesDir(context) == null) {
                throw IOException("模型解压后缺少必要文件")
            }
            onState(State.Ready)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (e is PausedSignal || paused) {
                paused = false
                onState(State.Paused(lastPct))
            } else onState(State.Failed(e.message ?: "下载失败"))
        }
    }

    suspend fun downloadWhisper(context: Context, onState: (State) -> Unit) = withContext(Dispatchers.IO) {
        val dir = whisperDir(context).apply { mkdirs() }
        var lastPct = 0
        try {
            paused = false
            var doneBase = 0L
            for ((index, f) in WHISPER_FILES.withIndex()) {
                coroutineContext.ensureActive()
                val target = File(dir, f.name)
                if (target.length() >= f.approxSize) {
                    doneBase += f.approxSize
                    continue
                }
                var lastErr: Exception? = null
                var ok = false
                for (url in f.urls) {
                    coroutineContext.ensureActive()
                    try {
                        httpDownload(url, target, target.length()) { read, total ->
                            val overall = doneBase + read
                            val totalAll = doneBase + if (total > 0) total else f.approxSize
                            lastPct = if (totalAll > 0) ((overall * 100) / totalAll).toInt().coerceIn(0, 100) else 0
                            onState(State.Downloading(lastPct, overall, totalAll))
                        }
                        ok = true
                        break
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        if (e is PausedSignal) throw e
                        lastErr = e
                        // 换源时保留已下部分仅在源支持 Range 时成立；对断点续传失败的源直接重下
                    }
                }
                if (!ok) throw lastErr ?: IOException("无法下载 ${f.name}")
                doneBase += f.approxSize
            }
            if (!isWhisperReady(context)) throw IOException("模型文件不完整")
            onState(State.Ready)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (e is PausedSignal || paused) {
                paused = false
                onState(State.Paused(lastPct))
            } else onState(State.Failed(e.message ?: "下载失败"))
        }
    }

    /** 带断点续传与失败重试的下载；total 未知时为 0 */
    private suspend fun httpDownload(
        url: String,
        target: File,
        resumeFrom: Long,
        onProgress: (read: Long, total: Long) -> Unit
    ) {
        var attempt = 0
        var lastErr: Exception = IOException("下载失败: $url")
        while (attempt < 3) {
            attempt++
            try {
                httpDownloadOnce(url, target, resumeFrom = if (attempt == 1) resumeFrom else 0L, onProgress)
                return
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e is PausedSignal) throw e
                lastErr = e
                kotlinx.coroutines.delay(2000L * attempt)
            }
        }
        throw lastErr
    }

    private suspend fun httpDownloadOnce(
        url: String,
        target: File,
        resumeFrom: Long,
        onProgress: (read: Long, total: Long) -> Unit
    ) {
        var start = if (target.exists()) target.length() else 0L
        if (start == 0L || resumeFrom == 0L) start = 0L
        val reqBuilder = Request.Builder().url(url)
        if (start > 0) reqBuilder.header("Range", "bytes=$start-")
        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.code == 416 && target.exists() && target.length() > 0) return // 已完整
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} $url")
            val body = resp.body ?: throw IOException("空响应")
            val resume = resp.code == 206 && start > 0
            if (!resume && start > 0 && resumeFrom > 0) {
                // 源不支持续传：从头下载覆盖
                start = 0L
            }
            val contentLength = body.contentLength()
            val total = if (contentLength > 0) contentLength + start else 0L
            FileOutputStream(target, resume).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var written = start
                    var lastUi = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        if (paused) throw PausedSignal()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - lastUi > 500) {
                            lastUi = now
                            onProgress(written, total)
                        }
                    }
                    out.flush()
                    onProgress(written, total)
                }
            }
        }
    }

    private fun unzip(zip: File, dest: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val outFile = File(dest, entry.name)
                if (!outFile.canonicalPath.startsWith(dest.canonicalPath + File.separator)) continue
                if (entry.isDirectory) {
                    outFile.mkdirs()
                    continue
                }
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { out -> zis.copyTo(out) }
            }
        }
    }
}
