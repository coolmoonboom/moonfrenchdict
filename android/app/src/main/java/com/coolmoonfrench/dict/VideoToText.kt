package com.coolmoonfrench.dict

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException

/**
 * 视频转文字：ffmpeg 提取音频 → Vosk 识别法语文本。
 *
 * 对外主入口是 [processVideo]：
 * - 输入：视频文件的 content:// Uri（或任意可用 Uri 字符串）
 * - 内部流程：拷贝到临时文件 → ffmpeg 转为 16k/单声道/PCM s16le → Vosk 识别 → 返回法语文本
 * - 全流程都在 IO 线程执行，可安全地在协程中调用。
 *
 * 使用哪个模型由 [VoskModelManager.getModelChoice] 决定，调用方无需感知模型细节。
 */
object VideoToText {

    /**
     * 识别视频中的法语语音，返回识别文本。
     * 成功返回 Result.success(text)；失败返回 Result.failure(exception)。
     * 识别过程中的临时文件（视频副本、中间 wav）会在 finally 中清理。
     */
    suspend fun processVideo(context: Context, videoUri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            // ffmpeg-kit 只提供 arm64-v8a / x86_64 的 native 库；armeabi-v7a 设备若被安装则必然
            // 加载不到 libffmpegkit.so 直接崩溃，先做运行时检查给出可读错误。
            if (!isAbiSupported()) {
                return@withContext Result.failure(
                    IOException("当前设备架构(${android.os.Build.SUPPORTED_ABIS.firstOrNull()})不支持视频转文字，请使用 64 位设备")
                )
            }
            var tempVideo: File? = null
            var tempWav: File? = null
            try {
                // content:// 不能直接交给 ffmpeg，先拷贝到缓存目录
                tempVideo = File(context.cacheDir, "video_in_${System.currentTimeMillis()}.mp4")
                copyUriToFile(context, videoUri, tempVideo)
                if (tempVideo.length() == 0L) {
                    throw IOException("视频文件为空或不可读")
                }

                // 提取 16k 单声道 PCM 音频
                tempWav = File(context.cacheDir, "audio_${System.currentTimeMillis()}.wav")
                extractWav(tempVideo, tempWav)

                // 识别
                val text = recognizeWav(context, tempWav)
                Result.success(text)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                tempVideo?.delete()
                tempWav?.delete()
            }
        }

    /** ffmpeg-kit-maintained 6.0.3 仅发布 arm64-v8a 与 x86_64 native 库。 */
    private fun isAbiSupported(): Boolean {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        return abi == "arm64-v8a" || abi == "x86_64"
    }

    /** 把 Uri 指向的内容完整拷贝到目标文件。 */
    private fun copyUriToFile(context: Context, uri: Uri, dest: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("无法打开视频 Uri：$uri")
    }

    /** 用 ffmpeg-kit 从视频提取 16kHz 单声道 PCM16 音频到 wav。 */
    private fun extractWav(video: File, wav: File) {
        // -y 覆盖；-vn 丢弃视频流；-ar 16000 -ac 1 强制采样率/声道；-c:a pcm_s16le 输出 WAV
        val args = arrayOf(
            "-y",
            "-i", video.absolutePath,
            "-vn",
            "-ar", "16000",
            "-ac", "1",
            "-c:a", "pcm_s16le",
            wav.absolutePath
        )
        val session = FFmpegKit.executeWithArguments(args)
        val rc = session.returnCode
        if (!ReturnCode.isSuccess(rc)) {
            val logs = session.allLogsAsString
            throw IOException("ffmpeg 提取音频失败，rc=${rc?.value}\n${logs.take(500)}")
        }
        if (!wav.exists() || wav.length() == 0L) {
            throw IOException("ffmpeg 未生成有效音频文件")
        }
    }

    /** 读取 wav，用小模型或大模型做语音识别，返回识别文本。 */
    private suspend fun recognizeWav(context: Context, wav: File): String {
        val modelDir = VoskModelManager.currentModelDir(context)
            ?: // 小模型兜底（理论上 currentModelDir 对 Small 恒可用，这里防御）
            VoskModelManager.ensureSmallModel(context)

        var model: Model? = null
        var recognizer: Recognizer? = null
        try {
            model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, 16000f)
            recognizer.setWords(true)

            val pcm = readWaveData(wav)
            // Vosk 接受 byte[] + 字节长度（16bit/单声道）；按帧喂入避免一次性占用过大内存
            require(pcm.size > 0) { "wav 中没有可用音频数据" }

            // 分块喂入（5 秒 ≈ 160000 字节），避免大文件一次性传入 JNI
            val chunkBytes = 16_000 * 2 * 5 // 5 秒 PCM（16bit 单声道）
            val buf = ByteArray(chunkBytes)
            var offset = 0
            while (offset < pcm.size) {
                val n = minOf(chunkBytes, pcm.size - offset)
                System.arraycopy(pcm, offset, buf, 0, n)
                recognizer.acceptWaveForm(buf, n)
                offset += n
            }

            // 喂完所有音频后取最终识别结果（JSON：{"text": "..."}）
            val finalResult = recognizer.finalResult
            val jsonText = runCatching {
                JSONObject(finalResult).optString("text", "")
            }.getOrNull() ?: ""
            if (jsonText.isBlank()) throw IOException("未识别到语音内容")
            return jsonText.trim()
        } finally {
            recognizer?.close()
            model?.close()
        }
    }

    /**
     * 读取 WAV 文件中的 PCM 数据。
     * 仅支持由 ffmpeg 生成的 16k/单声道/16bit PCM 的 WAV 格式，读取 44 字节头后的音频数据。
     */
    private fun readWaveData(wav: File): ByteArray {
        val raw = wav.readBytes()
        // WAV 头固定 44 字节（本流程由 ffmpeg 生成的标准格式）
        val dataStart = findDataChunk(raw)
        val headerLen = if (dataStart >= 0) dataStart else 44
        return ByteArray(raw.size - headerLen).also {
            System.arraycopy(raw, headerLen, it, 0, it.size)
        }
    }

    /** 查找 data 块起始偏移（兼容带扩展块头的 WAV）。 */
    private fun findDataChunk(raw: ByteArray): Int {
        var i = 12 // 跳过 RIFF/WAVE 头
        while (i + 8 <= raw.size) {
            val id = String(raw, i, 4, Charsets.US_ASCII)
            val size = ((raw[i + 4].toInt() and 0xff)) or
                ((raw[i + 5].toInt() and 0xff) shl 8) or
                ((raw[i + 6].toInt() and 0xff) shl 16) or
                ((raw[i + 7].toInt() and 0xff) shl 24)
            if (id == "data") {
                return i + 8
            }
            i += 8 + size
        }
        return -1
    }
}