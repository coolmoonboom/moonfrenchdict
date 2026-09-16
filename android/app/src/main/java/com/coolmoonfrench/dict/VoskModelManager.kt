package com.coolmoonfrench.dict

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Vosk 法语语音模型管理。
 *
 * 模型分两级：
 * - 内置小模型（assets/vosk/fr_small）：随 APK 打包，首次使用时复制到 filesDir/vosk_fr_small，快速但精度一般。
 * - 高精度大模型（vosk-model-fr-0.22）：体积约 1.4G，不打包进 APK，由本类通过 OkHttp 下载 zip 到 cache
 *   并解压到 filesDir/vosk_fr_large；仅在用户主动下载且设备内存充足时使用。
 *
 * 所有耗时操作（复制/下载/解压）都以挂起函数形式暴露，调用方需在协程中调用。
 */
object VoskModelManager {

    /** 内置小模型在 assets 中的目录 */
    const val ASSET_SMALL_PATH = "vosk/fr_small"

    /** 内置小模型的运行时目录名（相对 filesDir） */
    const val SMALL_DIR = "vosk_fr_small"

    /** 大模型运行时目录名（相对 filesDir） */
    const val LARGE_DIR = "vosk_fr_large"

    /** 大模型 zip 下载 URL */
    const val LARGE_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-fr-0.22.zip"

    /** 大模型 zip 名称（缓存在 cacheDir） */
    const val LARGE_MODEL_ZIP = "vosk-model-fr-0.22.zip"

    /** 建议的最小可用内存（字节），低于该值提示大模型可能卡顿/崩溃 */
    private const val LARGE_MODEL_MIN_MEM = 4L * 1024 * 1024 * 1024 // 4GB

    /** 模型可选状态 */
    sealed class ModelOption {
        /** 内置小模型（始终可用） */
        object Small : ModelOption()

        /** 高精度大模型（需下载） */
        object Large : ModelOption()
    }

    /** 大模型下载/就绪状态 */
    sealed class LargeModelState {
        object NotDownloaded : LargeModelState()
        data class Downloading(val percent: Int, val bytesRead: Long, val totalBytes: Long) : LargeModelState()
        data class DownloadFailed(val message: String) : LargeModelState()
        object Ready : LargeModelState()
    }

    /** 模型状态标记（模型选择持久化） */
    private const val K_MODEL_CHOICE = "vosk_model_choice"
    private const val VALUE_LARGE = "large"
    private const val VALUE_SMALL = "small"

    // ---------- 路径 ----------

    fun smallDir(context: Context): File = File(context.filesDir, SMALL_DIR)
    fun largeDir(context: Context): File = File(context.filesDir, LARGE_DIR)
    fun zipFile(context: Context): File = File(context.cacheDir, LARGE_MODEL_ZIP)

    // ---------- 状态查询 ----------

    /** 用户选择的模型（默认小模型；大模型未下载就绪时强制回落小模型） */
    fun getModelChoice(context: Context): ModelOption {
        val p = context.getSharedPreferences("video_text", Context.MODE_PRIVATE)
        val v = p.getString(K_MODEL_CHOICE, VALUE_SMALL)
        return if (v == VALUE_LARGE && isLargeReady(context)) ModelOption.Large else ModelOption.Small
    }

    fun setModelChoice(context: Context, large: Boolean) {
        context.getSharedPreferences("video_text", Context.MODE_PRIVATE)
            .edit().putString(K_MODEL_CHOICE, if (large) VALUE_LARGE else VALUE_SMALL).apply()
    }

    /** 当前生效模型的目录（返回 null 表示模型不可用） */
    fun currentModelDir(context: Context): File? {
        return when (getModelChoice(context)) {
            is ModelOption.Large -> largeDir(context)
            is ModelOption.Small -> smallDir(context)
        }.takeIf { isValidModelDir(it) }
    }

    /** 设备总内存是否满足大模型最低要求（>= 4GB） */
    fun hasEnoughMemory(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mi.totalMem >= LARGE_MODEL_MIN_MEM
        } catch (e: Exception) {
            true // 获取失败时不做硬性限制，仅作提示
        }
    }

    // ---------- 内置小模型：复制 ----------

    /**
     * 确保小模型已就绪：若 filesDir/vosk_fr_small 缺失，则从 assets/vosk/fr_small 复制。
     * 返回模型目录。
     */
    suspend fun ensureSmallModel(context: Context): File = withContext(Dispatchers.IO) {
        val dest = smallDir(context)
        if (isValidModelDir(dest)) {
            return@withContext dest
        }
        // 旧的残缺目录先清理
        if (dest.exists()) dest.deleteRecursively()
        dest.mkdirs()

        val names = context.assets.list(ASSET_SMALL_PATH)
            ?: throw IOException("assets 目录 $ASSET_SMALL_PATH 不存在")
        if (names.isEmpty()) throw IOException("assets 目录 $ASSET_SMALL_PATH 为空")

        names.forEach { name ->
            coroutineContext.ensureActive()
            copyAssetRecursive(context.assets, "$ASSET_SMALL_PATH/$name", dest)
        }

        if (!isValidModelDir(dest)) {
            dest.deleteRecursively()
            throw IOException("内置模型复制后校验失败")
        }
        dest
    }

    private fun copyAssetRecursive(
        assets: android.content.res.AssetManager,
        path: String,
        destDir: File
    ) {
        val outFile = File(destDir, path.substringAfterLast('/'))
        val sub = assets.list(path)
        if (sub != null && sub.isNotEmpty()) {
            // 目录：递归
            val subDir = File(destDir, path.substringAfterLast('/'))
            subDir.mkdirs()
            sub.forEach { copyAssetRecursive(assets, "$path/$it", subDir) }
        } else {
            // 文件：复制
            assets.open(path).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    // ---------- 高精度大模型：下载 + 解压 ----------

    /** 大模型是否已经下载并解压就绪 */
    fun isLargeReady(context: Context): Boolean = isValidModelDir(largeDir(context))

    /**
     * 下载并解压大模型（支持断点续传与取消）。
     * [onProgress] 在 IO 线程回调进度；协程被取消时自动中断下载并保留已下载片段供续传。
     */
    suspend fun downloadLargeModel(
        context: Context,
        onProgress: (LargeModelState) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                onProgress(LargeModelState.NotDownloaded)
                val zip = zipFile(context)
                // 断点续传：先探测已下载大小
                val existing = zip.length()
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .build()

                val reqBuilder = Request.Builder().url(LARGE_MODEL_URL)
                if (existing > 0) {
                    reqBuilder.header("Range", "bytes=$existing-")
                }
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    when {
                        // 服务器返回 206 说明支持续传
                        resp.code == 206 -> {
                            FileOutputStream(zip, true).use { out ->
                                var cur = existing
                                val len = existing + (resp.body?.contentLength() ?: 0)
                                resp.body?.byteStream()?.use { input ->
                                    val buf = ByteArray(128 * 1024)
                                    while (true) {
                                        coroutineContext.ensureActive()
                                        val n = input.read(buf)
                                        if (n <= 0) break
                                        out.write(buf, 0, n)
                                        cur += n
                                        if (len > 0) {
                                            onProgress(LargeModelState.Downloading(
                                                (cur * 100 / len).toInt(), cur, len
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                        // 服务器不支持 Range：从头全量下载
                        resp.code == 200 -> {
                            if (existing > 0) zip.delete() // 截断损坏片段
                            zip.outputStream().use { out ->
                                val total = resp.body?.contentLength() ?: 0L
                                var cur = 0L
                                resp.body?.byteStream()?.use { input ->
                                    val buf = ByteArray(128 * 1024)
                                    while (true) {
                                        coroutineContext.ensureActive()
                                        val n = input.read(buf)
                                        if (n <= 0) break
                                        out.write(buf, 0, n)
                                        cur += n
                                        if (total > 0) {
                                            onProgress(LargeModelState.Downloading(
                                                (cur * 100 / total).toInt(), cur, total
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            throw IOException("下载失败 HTTP ${resp.code}")
                        }
                    }
                }

                if (zip.length() == 0L) throw IOException("下载结果为空")

                // 解压到大模型目录
                val dest = largeDir(context)
                if (isValidModelDir(dest)) {
                    onProgress(LargeModelState.Ready)
                    return@withContext
                }
                if (dest.exists()) dest.deleteRecursively()
                dest.mkdirs()

                ZipInputStream(zip.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        coroutineContext.ensureActive()
                        val target = File(dest, entry.name)
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            // 防 zip-slip
                            if (!target.canonicalPath.startsWith(dest.canonicalPath)) {
                                throw IOException("非法解压路径：${entry.name}")
                            }
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out -> zis.copyTo(out) }
                        }
                        entry = zis.nextEntry
                    }
                }

                // Vosk 官方 zip 在顶层包了一个与模型同名目录（如 vosk-model-fr-0.22/），
                // 解压后模型文件实际位于 dest/<name>/ 下。将其内容提升到 dest 根目录，
                // 使 isValidModelDir(dest) 能直接校验。
                val nestedDir = dest.listFiles()?.firstOrNull { it.isDirectory && it.name.startsWith("vosk-model-") }
                if (nestedDir != null) {
                    nestedDir.listFiles()?.forEach { child ->
                        val target = File(dest, child.name)
                        if (child.isDirectory) {
                            if (!target.exists()) target.mkdirs()
                            child.copyRecursively(target, overwrite = true)
                            child.deleteRecursively()
                        } else {
                            child.copyTo(target, overwrite = true)
                            child.delete()
                        }
                    }
                    nestedDir.deleteRecursively()
                }

                // 解压后校验
                if (!isValidModelDir(dest)) {
                    dest.deleteRecursively()
                    zip.delete()
                    throw IOException("大模型解压校验失败，已清理")
                }

                // 解压成功后可删除 zip 释放缓存空间
                zip.delete()
                onProgress(LargeModelState.Ready)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // 主动取消：不改变状态，保留 zip 供续传
            } catch (e: Exception) {
                onProgress(LargeModelState.DownloadFailed(e.message ?: e.javaClass.simpleName))
                throw IOException("大模型下载失败：${e.message}", e)
            }
        }
    }

    /** 删除已下载的大模型（释放空间）；zip 一并清理。 */
    fun deleteLargeModel(context: Context) {
        largeDir(context).deleteRecursively()
        zipFile(context).delete()
        // 若用户当前选择大模型，回落回小模型
        setModelChoice(context, large = false)
    }

    // ---------- 通用校验 ----------

    /** 校验目录是否是完整的 Vosk 模型目录（含 conf/mfcc.conf 与 am 子目录、graph 目录）。 */
    fun isValidModelDir(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val confDir = File(dir, "conf")
        val am = File(dir, "am")
        val graph = File(dir, "graph")
        if (!confDir.isDirectory || !am.isDirectory || !graph.isDirectory) return false

        // 模型目录至少应该包含 am/final.mdl（或相似结构）
        val finalMdl = File(am, "final.mdl")
        return finalMdl.exists()
    }
}