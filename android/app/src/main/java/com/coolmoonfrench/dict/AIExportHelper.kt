package com.coolmoonfrench.dict

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object AIExportHelper {

    const val EXPORT_WIDTH_PX = MarkdownExportRenderer.DEFAULT_WIDTH_PX

    /**
     * 将一段 markdown 渲染为位图（白底）。
     * 使用 [MarkdownExportRenderer] 真正排版：标题分级、粗体/斜体、列表、引用、代码块，
     * 以及带边框与自动换行的真实表格，而不是把标记当纯文本输出。
     */
    fun renderTextToBitmap(markdown: String, widthPx: Int = EXPORT_WIDTH_PX): Bitmap =
        MarkdownExportRenderer.toBitmap(markdown, widthPx)

    /** 将 markdown 直接绘制为矢量 PDF（文字可选中、分页对齐到行首）。 */
    fun renderTextToPdf(markdown: String, dest: File): Boolean =
        MarkdownExportRenderer.toPdf(markdown, dest)

    /** 将位图保存为 PDF 文件（位图分页贴图，作为矢量导出的降级方案保留）。 */
    fun bitmapToPdf(bmp: Bitmap, dest: File): Boolean {
        return try {
            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val scale = (pageWidth - 40).toFloat() / bmp.width
            val scaledHeight = (bmp.height * scale).toInt()
            var yOffset = 0
            var pageIndex = 1
            while (yOffset < scaledHeight) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
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
