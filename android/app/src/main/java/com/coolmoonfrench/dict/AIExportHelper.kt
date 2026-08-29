package com.coolmoonfrench.dict

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.io.FileOutputStream

object AIExportHelper {

    const val EXPORT_WIDTH_PX = 900

    /**
     * 将一段 markdown 文本渲染为位图（白底）。
     * 说明：此处用简化的文本排版渲染，重点保证可分享为图片/PDF。
     */
    fun renderTextToBitmap(markdown: String, widthPx: Int = EXPORT_WIDTH_PX): Bitmap {
        val plain = stripMarkdown(markdown)
        val padding = 48f
        val lineHeight = 40f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            typeface = android.graphics.Typeface.DEFAULT
        }
        val available = widthPx - padding * 2
        // 计算行数
        val lines = mutableListOf<String>()
        plain.split("\n").forEach { para ->
            if (para.isBlank()) {
                lines.add("")
            } else {
                val words = para.split(" ")
                var cur = ""
                for (w in words) {
                    val test = if (cur.isEmpty()) w else "$cur $w"
                    if (paint.measureText(test) <= available) {
                        cur = test
                    } else {
                        if (cur.isNotEmpty()) lines.add(cur)
                        cur = w
                    }
                }
                if (cur.isNotEmpty()) lines.add(cur)
            }
        }
        val height = (lines.size * lineHeight + padding * 2).toInt().coerceAtLeast(200)
        val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        var y = padding + lineHeight
        for (line in lines) {
            if (line.isBlank()) {
                y += lineHeight * 0.5f
            } else {
                canvas.drawText(line, padding, y, paint)
                y += lineHeight
            }
        }
        return bmp
    }

    /** 极简 markdown 剥除：去掉标记符号，保留可读文本 */
    private fun stripMarkdown(md: String): String {
        var s = md
        s = s.replace(Regex("!\\[([^\\]]*)\\]\\([^)]*\\)"), "图片: $1")
        s = s.replace(Regex("\\[(.*?)\\]\\([^)]*\\)"), "$1")
        s = s.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "• ")
        s = s.replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "")
        s = s.replace("**", "").replace("__", "").replace("`", "").replace("*", "").replace("_", "")
        s = s.replace(Regex("^\\s*>\\s?", RegexOption.MULTILINE), "")
        s = s.replace("~~", "")
        s = s.replace("```", "")
        return s
    }

    /** 将位图保存为 PDF 文件 */
    fun bitmapToPdf(bmp: Bitmap, dest: File): Boolean {
        return try {
            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            // 将位图拆分成多页
            val scale = (pageWidth - 40).toFloat() / bmp.width
            val scaledHeight = (bmp.height * scale).toInt()
            var yOffset = 0
            var pageIndex = 1
            while (yOffset < scaledHeight) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                // 计算本页需要绘制的源区域
                val availH = pageHeight - 40
                val srcTopPx = ((yOffset) / scale).toInt()
                val srcBottomPx = (((yOffset + availH) / scale).toInt()).coerceAtMost(bmp.height)
                val srcH = (srcBottomPx - srcTopPx).coerceAtLeast(1)
                val srcRect = android.graphics.Rect(0, srcTopPx, bmp.width, srcTopPx + srcH)
                val dstRect = android.graphics.Rect(20, 20, pageWidth - 20, 20 + (srcH * scale).toInt())
                canvas.drawBitmap(bmp, srcRect, dstRect, null)
                document.finishPage(page)
                yOffset += availH
                pageIndex++
            }
            document.writeTo(FileOutputStream(dest))
            document.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 通过 FileProvider 分享文件 */
    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "com.coolmoonfrench.dict.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}