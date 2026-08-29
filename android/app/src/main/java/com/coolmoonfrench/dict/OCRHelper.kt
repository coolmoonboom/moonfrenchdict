package com.coolmoonfrench.dict

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 离线 OCR 工具：基于 Google ML Kit 的 on-device 文本识别（Text Recognition V2）。
 * 支持拉丁语系（法语、西语），bundled 模型无需 Google Play 服务，离线可用。
 */
object OCRHelper {

    private var recognizer: TextRecognizer? = null

    private fun getRecognizer(): TextRecognizer {
        return recognizer ?: TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        ).also { recognizer = it }
    }

    /**
     * 识别图片中的文字，返回识别到的文本。
     * 失败时抛出带原因的异常。
     */
    suspend fun recognize(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = suspendCancellableCoroutine<String> { cont ->
                getRecognizer().process(image)
                    .addOnSuccessListener { text ->
                        val content = text.text ?: ""
                        if (cont.isCancelled) return@addOnSuccessListener
                        cont.resume(content)
                    }
                    .addOnFailureListener { e ->
                        if (cont.isCancelled) return@addOnFailureListener
                        cont.resumeWithException(e)
                    }
            }
            result.trim()
        } catch (e: Exception) {
            throw RuntimeException("文字识别失败：${e.message}", e)
        }
    }

    /**
     * 从 URI 解码 Bitmap（先读字节再解码，兼容非 seekable 流）。
     * 返回 null 表示解码失败。
     */
    fun decodeBitmap(context: Context, uri: Uri, maxSize: Int = 2048): Bitmap? {
        return try {
            val imageBytes = readAllBytes(context, uri) ?: return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

            var sample = 1
            var w = opts.outWidth
            var h = opts.outHeight
            // ML Kit 内部有缩放，maxSize 保守控制以免超大图处理慢
            while (w / (sample * 2) >= maxSize && h / (sample * 2) >= maxSize && sample < 8) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOpts)
                ?: return null
            // 应用 EXIF 方向（手机拍摄/部分截图带旋转信息，不校正会导致识别失败）
            return applyOrientation(context, uri, bmp)
        } catch (_: Exception) {
            null
        }
    }

    /** 读取 EXIF 方向并旋转 bitmap */
    private fun applyOrientation(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        var rotation = 0f
        var flipX = false
        var flipY = false
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return bmp
            val exif = stream.use { ExifInterface(it) }
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotation = 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> rotation = 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> rotation = 270f
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipX = true
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipY = true
                ExifInterface.ORIENTATION_TRANSPOSE -> { rotation = 270f; flipX = true }
                ExifInterface.ORIENTATION_TRANSVERSE -> { rotation = 90f; flipX = true }
                else -> return bmp
            }
        } catch (_: Exception) {
            return bmp
        }
        if (rotation == 0f && !flipX && !flipY) return bmp
        val matrix = Matrix().apply {
            if (rotation != 0f) postRotate(rotation)
            if (flipX) postScale(-1f, 1f)
            if (flipY) postScale(1f, -1f)
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true) ?: bmp
    }

    private fun readAllBytes(context: Context, uri: Uri): ByteArray? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        return input.use { stream ->
            val buf = ByteArrayOutputStream()
            val tmp = ByteArray(8192)
            var n: Int
            while (stream.read(tmp).also { n = it } >= 0) {
                buf.write(tmp, 0, n)
            }
            buf.toByteArray()
        }
    }
}