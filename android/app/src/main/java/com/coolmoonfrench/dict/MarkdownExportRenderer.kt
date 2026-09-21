package com.coolmoonfrench.dict

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max

/**
 * Markdown 排版渲染器：把 [MarkdownBlocks] 解析出的结构真正「画」出来，
 * 而不是把标记符号当纯文本输出。
 *
 * 支持：标题（分级字号/加粗）、段落换行、粗体 / 斜体 / 粗斜体 / 删除线 / 链接 / 行内代码、
 * 有序与无序列表、围栏代码块、引用块、分割线，以及带边框、表头底色、单元格自动换行的**真实表格**。
 *
 * 中文与拉丁文混排按字符宽度自动折行；同一个渲染器同时输出位图（分享图片）与 PDF
 * （矢量分页绘制，分页点对齐到行首，避免文字被页边界切断）。
 */
internal class MarkdownExportRenderer(
    markdown: String,
    private val pageWidth: Float,
    private val margin: Float,
    private val baseSize: Float
) {

    private val contentWidth = pageWidth - margin * 2
    private val lineFactor = 1.55f

    private val textColor = Color.rgb(26, 26, 26)
    private val linkColor = Color.rgb(21, 101, 192)
    private val codeColor = Color.rgb(150, 40, 40)
    private val quoteColor = Color.rgb(90, 90, 90)
    private val ruleColor = Color.rgb(200, 200, 200)
    private val borderColor = Color.rgb(190, 190, 190)
    private val headerBg = Color.rgb(240, 240, 240)
    private val zebraBg = Color.rgb(250, 250, 250)
    private val codeBg = Color.rgb(245, 245, 245)

    private data class Spec(
        val size: Float,
        val bold: Boolean,
        val italic: Boolean,
        val mono: Boolean,
        val strike: Boolean,
        val link: Boolean,
        val color: Int
    )

    private class Run(val text: String, val spec: Spec)

    private class Token(val text: String, val spec: Spec, val space: Boolean)

    private class Cmd(val top: Float, val bottom: Float, val draw: (Canvas) -> Unit)

    private val paints = HashMap<Spec, Paint>()
    private val cmds = ArrayList<Cmd>()

    /** 可分页点（每行 / 每表格行的起始 y），PDF 分页时对齐到这里。 */
    private val pageBreaks = ArrayList<Float>()

    private var cursor = 0f

    var height: Float = 0f
        private set

    init {
        val blocks = MarkdownBlocks.parse(
            MarkdownSanitizer.sanitize(markdown.replace('\u00A0', ' '))
        )
        cursor = margin
        layout(blocks)
        height = cursor + margin
    }

    // ---------- 对外渲染 ----------

    fun drawAll(canvas: Canvas) {
        for (c in cmds) c.draw(canvas)
    }

    fun drawRange(canvas: Canvas, top: Float, bottom: Float) {
        for (c in cmds) if (c.bottom > top && c.top < bottom) c.draw(canvas)
    }

    /** 按行首对齐的可分页点集合（升序）。 */
    fun breakPoints(): List<Float> = pageBreaks.sorted()

    /** 取 (from, to] 区间内最后一个可分页点；没有则返回 to。 */
    fun pageEnd(from: Float, to: Float): Float =
        pageBreaks.lastOrNull { it > from + margin * 0.75f && it <= to } ?: to

    companion object {
        /** 与历史行为一致：默认 900px 宽、白底黑字。 */
        const val DEFAULT_WIDTH_PX = 900

        fun toBitmap(markdown: String, widthPx: Int = DEFAULT_WIDTH_PX): Bitmap {
            val base = widthPx * 0.033f
            val renderer = MarkdownExportRenderer(markdown, widthPx.toFloat(), base * 1.5f, base)
            val h = max(ceil(renderer.height).toInt(), 120)
            val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            renderer.drawAll(canvas)
            return bmp
        }

        /** 直接分页绘制到 PDF（矢量文字，可选中复制）。 */
        fun toPdf(markdown: String, dest: File): Boolean {
            val pageW = 595f
            val pageH = 842f
            val margin = 40f
            val renderer = MarkdownExportRenderer(markdown, pageW, margin, 12.5f)
            return try {
                val document = PdfDocument()
                var pageTop = 0f
                var pageIndex = 1
                val end = renderer.height
                while (pageTop < end && pageIndex <= 2000) {
                    val pageBottom = pageTop + pageH
                    var top = renderer.pageEnd(pageTop, pageBottom)
                    if (top <= pageTop + 1f) top = pageBottom
                    val info = PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), pageIndex).create()
                    val page = document.startPage(info)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    canvas.save()
                    canvas.translate(0f, -pageTop)
                    renderer.drawRange(canvas, pageTop, top)
                    canvas.restore()
                    document.finishPage(page)
                    pageTop = top
                    pageIndex++
                }
                document.writeTo(FileOutputStream(dest))
                document.close()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    // ---------- 画笔 ----------

    private fun paintFor(spec: Spec): Paint = paints.getOrPut(spec) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = spec.size
            color = spec.color
            typeface = if (spec.mono) {
                Typeface.create(Typeface.MONOSPACE, if (spec.bold) Typeface.BOLD else Typeface.NORMAL)
            } else {
                val style = when {
                    spec.bold && spec.italic -> Typeface.BOLD_ITALIC
                    spec.bold -> Typeface.BOLD
                    spec.italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                Typeface.create(Typeface.DEFAULT, style)
            }
            isUnderlineText = spec.link
            isStrikeThruText = spec.strike
        }
    }

    private fun specOf(span: MdSpan, size: Float, forceBold: Boolean, color: Int): Spec = Spec(
        size = size,
        bold = span.bold || forceBold,
        italic = span.italic,
        mono = span.code,
        strike = span.strike,
        link = span.link != null,
        color = if (span.link != null) linkColor else color
    )

    private fun measure(spec: Spec, text: String): Float = paintFor(spec).measureText(text)

    // ---------- 布局 ----------

    private fun layout(blocks: List<MdBlock>) {
        for (b in blocks) when (b) {
            is MdBlock.Heading -> layoutHeading(b)
            is MdBlock.Paragraph -> {
                val runs = runsOf(b.spans, baseSize, forceBold = false, color = textColor)
                if (runs.isNotEmpty()) {
                    layoutWrapped(runs, contentWidth, baseSize)
                    cursor += baseSize * 0.7f
                }
            }
            is MdBlock.BulletList -> layoutList(b.items, orderedStart = null)
            is MdBlock.OrderedList -> layoutList(b.items, orderedStart = b.start)
            is MdBlock.Quote -> layoutQuote(b)
            is MdBlock.CodeBlock -> layoutCode(b)
            is MdBlock.Rule -> layoutRule()
            is MdBlock.Table -> layoutTable(b)
        }
    }

    private fun layoutRule() {
        cursor += baseSize * 0.6f
        val y = cursor
        emit(y, y + 1f) { c ->
            val p = Paint().apply { color = ruleColor; strokeWidth = max(1f, baseSize * 0.06f) }
            c.drawLine(margin, y, pageWidth - margin, y, p)
        }
        cursor = y + baseSize * 0.9f
    }

    private fun layoutHeading(h: MdBlock.Heading) {
        val size = when (h.level) {
            1 -> baseSize * 1.9f
            2 -> baseSize * 1.6f
            3 -> baseSize * 1.35f
            4 -> baseSize * 1.2f
            5 -> baseSize * 1.1f
            else -> baseSize * 1.05f
        }
        cursor += if (h.level <= 2) baseSize * 0.7f else baseSize * 0.45f
        val runs = runsOf(h.spans, size, forceBold = true, color = textColor)
        if (runs.isNotEmpty()) layoutWrapped(runs, contentWidth, size)
        cursor += baseSize * 0.4f
    }

    private fun layoutList(items: List<List<MdSpan>>, orderedStart: Int?) {
        val indent = baseSize * 1.6f
        items.forEachIndexed { index, spans ->
            val marker = if (orderedStart == null) "\u2022" else "${orderedStart + index}."
            val markerSpec = Spec(baseSize, false, false, false, false, false, textColor)
            val x = margin + max(indent, measure(markerSpec, marker) + baseSize * 0.5f)
            pageBreaks.add(cursor)
            val runs = runsOf(spans, baseSize, forceBold = false, color = textColor)
            val lines = wrap(runs, contentWidth - (x - margin))
            var first = true
            for (line in lines) {
                cursor += baseSize * lineFactor
                val baseline = cursor - baseSize * (lineFactor - 1.15f)
                if (first) {
                    emit(baseline - baseSize, baseline + baseSize * 0.3f) { c ->
                        c.drawText(marker, margin, baseline, paintFor(markerSpec))
                    }
                    first = false
                }
                emitLine(x, baseline, line, baseSize)
            }
            cursor += baseSize * 0.2f
        }
        cursor += baseSize * 0.5f
    }

    private fun layoutQuote(q: MdBlock.Quote) {
        val indent = baseSize * 1.3f
        val startY = cursor
        pageBreaks.add(startY)
        var first = true
        for (p in q.paragraphs) {
            val runs = runsOf(p, baseSize, forceBold = false, color = quoteColor)
            if (runs.isEmpty()) continue
            if (!first) cursor += baseSize * 0.3f
            first = false
            for (line in wrap(runs, contentWidth - indent)) {
                cursor += baseSize * lineFactor
                val baseline = cursor - baseSize * (lineFactor - 1.15f)
                emitLine(margin + indent, baseline, line, baseSize)
            }
        }
        val endY = cursor
        if (endY > startY) {
            val barW = max(1.5f, baseSize * 0.16f)
            emit(startY, endY) { c ->
                val p = Paint().apply { color = ruleColor }
                c.drawRect(margin, startY, margin + barW, endY, p)
            }
        }
        cursor += baseSize * 0.8f
    }

    private fun layoutCode(code: MdBlock.CodeBlock) {
        val size = baseSize * 0.95f
        val padY = size * 0.5f
        val padX = size * 0.8f
        val mono = Spec(size, false, false, true, false, false, textColor)
        val boxTop = cursor + padY * 0.5f
        var y = boxTop
        pageBreaks.add(cursor)
        val maxW = contentWidth - padX * 2
        for (raw in code.lines) {
            var rest = raw.ifEmpty { " " }
            while (rest.isNotEmpty()) {
                val n = fitChars(mono, rest, maxW)
                val piece = if (n >= rest.length) rest else rest.substring(0, n)
                rest = if (n >= rest.length) "" else rest.substring(n)
                y += size * lineFactor
                val baseline = y - size * (lineFactor - 1.15f)
                emit(baseline - size, baseline + size * 0.3f) { c ->
                    c.drawText(piece, margin + padX, baseline, paintFor(mono))
                }
            }
        }
        val boxBottom = y + padY
        emit(boxTop - padY * 0.5f, boxBottom) { c ->
            val p = Paint().apply { color = codeBg }
            c.drawRect(margin, boxTop - padY * 0.5f, pageWidth - margin, boxBottom, p)
        }
        cursor = boxBottom + baseSize * 0.8f
    }

    private fun layoutTable(table: MdBlock.Table) {
        val colCount = max(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
        if (colCount == 0) return
        val size = baseSize * 0.95f
        val lineBox = size * lineFactor
        val padX = size * 0.55f
        val padY = size * 0.42f
        val border = max(1f, size * 0.07f)

        fun cellSpans(cells: List<List<MdSpan>>, c: Int): List<MdSpan> =
            if (c < cells.size) cells[c] else emptyList()

        val natural = FloatArray(colCount)
        for (c in 0 until colCount) {
            var w = runsWidth(runsOf(cellSpans(table.header, c), size, forceBold = true, color = textColor))
            for (row in table.rows) {
                w = max(w, runsWidth(runsOf(cellSpans(row, c), size, forceBold = false, color = textColor)))
            }
            natural[c] = w + padX * 2
        }
        val cap = contentWidth * 0.5f
        val cappedSum = (0 until colCount).sumOf { minOf(natural[it], cap).toDouble() }.toFloat()
        val widths = FloatArray(colCount)
        if (cappedSum <= contentWidth) {
            for (c in 0 until colCount) widths[c] = minOf(natural[c], cap)
        } else {
            val factor = contentWidth / cappedSum
            for (c in 0 until colCount) widths[c] = max(minOf(natural[c], cap) * factor, contentWidth * 0.12f)
        }
        val tableWidth = widths.sum()
        val x0 = margin
        val x1 = minOf(pageWidth - margin, margin + tableWidth)

        pageBreaks.add(cursor)
        val tableTop = cursor

        val headerLines = (0 until colCount).map { c ->
            wrap(runsOf(cellSpans(table.header, c), size, forceBold = true, color = textColor), widths[c] - padX * 2)
        }
        cursor += rowHeight(headerLines, lineBox, padY)
        val headerBottom = cursor
        emit(tableTop, headerBottom) { c ->
            val p = Paint().apply { color = headerBg }
            c.drawRect(x0, tableTop, x1, headerBottom, p)
        }
        drawRow(headerLines, colCount, widths, x0, tableTop, size, lineBox, padX, padY, table.aligns)

        for ((r, row) in table.rows.withIndex()) {
            val rowLines = (0 until colCount).map { c ->
                wrap(runsOf(cellSpans(row, c), size, forceBold = false, color = textColor), widths[c] - padX * 2)
            }
            val rowTop = cursor
            cursor += rowHeight(rowLines, lineBox, padY)
            val rowBottom = cursor
            if (r % 2 == 1) {
                emit(rowTop, rowBottom) { c ->
                    val p = Paint().apply { color = zebraBg }
                    c.drawRect(x0, rowTop, x1, rowBottom, p)
                }
            }
            drawRow(rowLines, colCount, widths, x0, rowTop, size, lineBox, padX, padY, table.aligns)
        }
        val tableBottom = cursor

        emit(tableTop, tableBottom) { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                strokeWidth = border
                style = Paint.Style.STROKE
            }
            c.drawRect(x0, tableTop, x1, tableBottom, p)
            var x = x0
            for (cIdx in 0 until colCount - 1) {
                x += widths[cIdx]
                c.drawLine(x, tableTop, x, tableBottom, p)
            }
            // 表头下边框加粗
            val hp = Paint().apply { color = borderColor; strokeWidth = border }
            c.drawLine(x0, headerBottom, x1, headerBottom, hp)
        }
        cursor += baseSize * 0.9f
    }

    private fun rowHeight(lines: List<List<List<Run>>>, lineBox: Float, padY: Float): Float =
        (lines.maxOfOrNull { it.size } ?: 1) * lineBox + padY * 2

    private fun drawRow(
        cellLines: List<List<List<Run>>>,
        colCount: Int,
        widths: FloatArray,
        x0: Float,
        rowTop: Float,
        size: Float,
        lineBox: Float,
        padX: Float,
        padY: Float,
        aligns: List<MdAlign>
    ) {
        var x = x0
        for (c in 0 until colCount) {
            val align = aligns.getOrElse(c) { MdAlign.LEFT }
            val avail = widths[c] - padX * 2
            for ((li, line) in cellLines[c].withIndex()) {
                val lineWidth = runsWidth(line)
                val offset = when (align) {
                    MdAlign.LEFT -> 0f
                    MdAlign.CENTER -> (avail - lineWidth) / 2f
                    MdAlign.RIGHT -> avail - lineWidth
                }
                val baseline = rowTop + padY + (li + 1) * lineBox - size * (lineFactor - 1.15f)
                emitLine(x + padX + offset, baseline, line, size)
            }
            x += widths[c]
        }
    }

    // ---------- 通用绘制 ----------

    private fun runsOf(spans: List<MdSpan>, size: Float, forceBold: Boolean, color: Int): List<Run> =
        spans.mapNotNull { s ->
            if (s.text.isEmpty()) null
            else {
                val c = if (s.code && color == textColor) codeColor else color
                Run(s.text, specOf(s, size, forceBold, c))
            }
        }

    private fun runsWidth(runs: List<Run>): Float =
        runs.sumOf { measure(it.spec, it.text).toDouble() }.toFloat()

    private fun layoutWrapped(runs: List<Run>, maxWidth: Float, size: Float) {
        for (line in wrap(runs, maxWidth)) {
            pageBreaks.add(cursor)
            cursor += size * lineFactor
            val baseline = cursor - size * (lineFactor - 1.15f)
            emitLine(margin, baseline, line, size)
        }
    }

    private fun emitLine(x: Float, baseline: Float, runs: List<Run>, size: Float) {
        if (runs.isEmpty()) return
        emit(baseline - size, baseline + size * 0.3f) { c ->
            var cx = x
            for (r in runs) {
                val p = paintFor(r.spec)
                c.drawText(r.text, cx, baseline, p)
                cx += p.measureText(r.text)
            }
        }
    }

    private fun emit(top: Float, bottom: Float, draw: (Canvas) -> Unit) {
        cmds.add(Cmd(top, bottom, draw))
    }

    // ---------- 折行 ----------

    private fun wrap(runs: List<Run>, maxWidth: Float): List<List<Run>> {
        val tokens = tokenize(runs)
        val lines = ArrayList<List<Run>>()
        var cur = ArrayList<Run>()
        var curW = 0f
        var pending: Run? = null

        fun push() {
            if (cur.isNotEmpty()) lines.add(cur)
            cur = ArrayList()
            curW = 0f
            pending = null
        }

        for (tk in tokens) {
            if (tk.space) {
                if (cur.isNotEmpty()) pending = Run(tk.text, tk.spec)
                continue
            }
            var rest = tk.text
            while (rest.isNotEmpty()) {
                val spaceW = pending?.let { measure(it.spec, it.text) } ?: 0f
                val w = measure(tk.spec, rest)
                if (curW + spaceW + w <= maxWidth) {
                    pending?.let { cur.add(it); curW += spaceW; pending = null }
                    cur.add(Run(rest, tk.spec))
                    curW += w
                    rest = ""
                } else if (cur.isEmpty()) {
                    val n = fitChars(tk.spec, rest, maxWidth)
                    if (n >= rest.length) {
                        cur.add(Run(rest, tk.spec))
                        rest = ""
                    } else {
                        cur.add(Run(rest.substring(0, n), tk.spec))
                        rest = rest.substring(n)
                        push()
                    }
                } else {
                    push()
                }
            }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        return lines
    }

    /** 在 maxWidth 内最多能放下的字符数（至少 1 个，避免死循环）。 */
    private fun fitChars(spec: Spec, text: String, maxWidth: Float): Int {
        var w = 0f
        for (i in text.indices) {
            val cw = measure(spec, text[i].toString())
            if (w + cw > maxWidth && i > 0) return i
            w += cw
        }
        return text.length
    }

    private fun tokenize(runs: List<Run>): List<Token> {
        val tokens = ArrayList<Token>()
        for (run in runs) {
            val sb = StringBuilder()
            var sbIsSpace = false
            fun flush() {
                if (sb.isNotEmpty()) {
                    tokens.add(Token(sb.toString(), run.spec, sbIsSpace))
                    sb.setLength(0)
                }
            }
            for (ch in run.text) {
                when {
                    ch == ' ' || ch == '\t' || ch == '\n' -> {
                        if (!sbIsSpace) flush()
                        sbIsSpace = true
                        sb.append(' ')
                    }
                    isCjk(ch) -> {
                        flush()
                        tokens.add(Token(ch.toString(), run.spec, false))
                    }
                    else -> {
                        if (sbIsSpace) flush()
                        sbIsSpace = false
                        sb.append(ch)
                    }
                }
            }
            flush()
        }
        return tokens
    }

    private fun isCjk(ch: Char): Boolean {
        val c = ch.code
        return (c in 0x1100..0x11FF) || (c in 0x2E80..0x303F) || (c in 0x3040..0x33FF) ||
            (c in 0x3400..0x4DBF) || (c in 0x4E00..0x9FFF) || (c in 0xA000..0xA4CF) ||
            (c in 0xAC00..0xD7A3) || (c in 0xF900..0xFAFF) || (c in 0xFE30..0xFE4F) ||
            (c in 0xFF00..0xFF60) || (c in 0xFFE0..0xFFE6)
    }
}
