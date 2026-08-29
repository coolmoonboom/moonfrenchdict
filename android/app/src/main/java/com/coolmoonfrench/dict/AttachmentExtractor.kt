package com.coolmoonfrench.dict

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 附件抽取器：从图片/PDF/DOCX/PPTX/TXT 中提取文本，
 * 将提取的文本发给 AI，让纯文本模型也能"看懂"文件内容。
 */
object AttachmentExtractor {

    private const val MAX_TEXT_LENGTH = 60000 // 单文件最大提取字符数

    /** 从 content URI 提取附件信息 */
    suspend fun extract(context: Context, uri: Uri, mimeType: String): AIAttachment {
        val name = getFileName(context, uri) ?: "unknown"
        val type = when {
            mimeType.startsWith("image/") -> "image"
            mimeType == "application/pdf" -> "pdf"
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            mimeType == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
            mimeType == "text/plain" || mimeType.endsWith("txt") -> "txt"
            else -> "txt"
        }

        val text = when (type) {
            "image" -> extractImageText(context, uri)
            "pdf" -> extractPdfText(context, uri)
            "docx" -> extractDocxText(context, uri)
            "pptx" -> extractPptxText(context, uri)
            "txt" -> extractTxtText(context, uri)
            else -> ""
        }

        return AIAttachment(
            type = type,
            name = name,
            localPath = uri.toString(),
            extractedText = text.take(MAX_TEXT_LENGTH)
        )
    }

    /** 获取文件夹名 */
    fun getFileName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        }
    }

    /** 读取输入流全部内容（分块，支持大文件） */
    private fun readStream(stream: InputStream): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        val tmp = ByteArray(8192)
        var n: Int
        while (stream.read(tmp).also { n = it } >= 0) {
            buf.write(tmp, 0, n)
        }
        return buf.toByteArray()
    }

    // ---------- 图片：OCR 提取文字 ----------

    private suspend fun extractImageText(context: Context, uri: Uri): String {
        val bmp = OCRHelper.decodeBitmap(context, uri)
            ?: throw RuntimeException("无法解码图片，请换一张清晰的图片")
        return OCRHelper.recognize(bmp).trim()
    }

    // ---------- PDF：PdfBox 提取文字 ----------

    private fun extractPdfText(context: Context, uri: Uri): String {
        return try {
            if (!PDFBoxResourceLoader.isReady()) {
                PDFBoxResourceLoader.init(context)
            }
            val stream = context.contentResolver.openInputStream(uri) ?: return ""
            val doc = BufferedInputStream(stream).use { PDDocument.load(it) }
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val text = stripper.getText(doc)
            doc.close()
            text.trim()
        } catch (e: Exception) {
            ""
        }
    }

    // ---------- DOCX：zip 中解析 word/document.xml ----------

    private fun extractDocxText(context: Context, uri: Uri): String {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return ""
            stream.use { extractTextFromZipXml(it, "word/document.xml") }
        } catch (e: Exception) {
            ""
        }
    }

    // ---------- PPTX：zip 中解析 ppt/slides/slide*.xml ----------

    private fun extractPptxText(context: Context, uri: Uri): String {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return ""
            stream.use { extractTextFromZipXml(it, "ppt/slides/slide", isPrefix = true) }
        } catch (e: Exception) {
            ""
        }
    }

    // ---------- TXT：直接读取文本 ----------

    private fun extractTxtText(context: Context, uri: Uri): String {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { readStream(it) }
                ?: return ""
            String(bytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            ""
        }
    }

    // ---------- 通用 zip+xml 文本抽取 ----------

    /** 从 zip 流中解析指定路径的 xml 并提取所有文本节点内容 */
    private fun extractTextFromZipXml(
        inputStream: InputStream,
        targetPath: String,
        isPrefix: Boolean = false
    ): String {
        val sb = StringBuilder()
        val zis = ZipInputStream(inputStream)
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        var entry = zis.nextEntry
        while (entry != null) {
            val name = entry.name
            val match = if (isPrefix) name.startsWith(targetPath) else name == targetPath
            if (match && !entry.isDirectory) {
                try {
                    // 先读取当前 entry 的字节到独立 ByteArray，再解析，避免 parser 消耗 ZipInputStream
                    val entryBytes = readStream(zis)
                    val doc = builder.parse(ByteArrayInputStream(entryBytes))
                    val textNodes = doc.getElementsByTagName("t")
                    for (i in 0 until textNodes.length) {
                        val text = textNodes.item(i).textContent?.trim()
                        if (!text.isNullOrBlank()) {
                            if (sb.isNotEmpty()) sb.append(' ')
                            sb.append(text)
                        }
                    }
                } catch (_: Exception) { }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        return sb.toString().trim()
    }
}