package com.coolmoonfrench.dict

/**
 * 轻量 Markdown 解析模型（纯 Kotlin，无 Android 依赖，可单元测试）。
 *
 * 同一个解析结果同时服务于两处渲染：
 *  - 收藏详情页的表格组件（[MarkdownTableView]）；
 *  - 图片 / PDF 导出（[MarkdownExportRenderer]）。
 *
 * 覆盖的结构：标题、段落（软换行合并）、有序 / 无序列表、围栏代码块、引用块、
 * 分割线、GFM 表格；行内覆盖：粗体、斜体、粗斜体、删除线、行内代码、链接、反斜杠转义。
 */

/** 行内片段：一段文本及其叠加的样式。 */
internal data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null
)

internal enum class MdAlign { LEFT, CENTER, RIGHT }

internal sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class BulletList(val items: List<List<MdSpan>>) : MdBlock
    data class OrderedList(val start: Int, val items: List<List<MdSpan>>) : MdBlock
    data class CodeBlock(val lines: List<String>) : MdBlock
    data class Quote(val paragraphs: List<List<MdSpan>>) : MdBlock
    data object Rule : MdBlock
    data class Table(
        val header: List<List<MdSpan>>,
        val rows: List<List<List<MdSpan>>>,
        val aligns: List<MdAlign>
    ) : MdBlock
}

/** 行内 Markdown 解析：返回带样式的片段序列。 */
internal object MarkdownInline {

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|>~"

    private data class Ctx(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false
    )

    fun parse(src: String): List<MdSpan> {
        val out = ArrayList<MdSpan>()
        append(out, src, Ctx())
        return out.filter { it.text.isNotEmpty() }
    }

    /** 去掉所有标记后的纯文本，供排版测量与降级输出使用。 */
    fun plainText(spans: List<MdSpan>): String = spans.joinToString("") { it.text }

    private fun flush(out: MutableList<MdSpan>, buf: StringBuilder, ctx: Ctx, link: String?) {
        if (buf.isEmpty()) return
        out.add(MdSpan(buf.toString(), ctx.bold, ctx.italic, false, ctx.strike, link))
        buf.setLength(0)
    }

    private fun append(out: MutableList<MdSpan>, s: String, ctx: Ctx, link: String? = null) {
        val n = s.length
        val buf = StringBuilder()
        var i = 0
        while (i < n) {
            val c = s[i]
            when {
                c == '`' -> {
                    val j = s.indexOf('`', i + 1)
                    if (j > i + 1) {
                        flush(out, buf, ctx, link)
                        out.add(MdSpan(s.substring(i + 1, j), ctx.bold, ctx.italic, code = true, ctx.strike, link))
                        i = j + 1
                    } else {
                        buf.append('`'); i++
                    }
                }
                c == '\\' && i + 1 < n && ESCAPABLE.indexOf(s[i + 1]) >= 0 -> {
                    buf.append(s[i + 1]); i += 2
                }
                c == '!' && i + 1 < n && s[i + 1] == '[' -> {
                    val lb = s.indexOf(']', i + 2)
                    if (lb > i + 2 && s.startsWith("](", lb)) {
                        val urlStart = lb + 2
                        val urlEnd = s.indexOf(')', urlStart)
                        if (urlEnd > urlStart) {
                            flush(out, buf, ctx, link)
                            val label = s.substring(i + 2, lb)
                            append(out, if (label.isBlank()) "图片" else label, ctx, link)
                            i = urlEnd + 1
                        } else {
                            buf.append('!'); i++
                        }
                    } else {
                        buf.append('!'); i++
                    }
                }
                c == '[' -> {
                    val lb = s.indexOf(']', i + 1)
                    if (lb > i + 1 && s.startsWith("](", lb)) {
                        val urlStart = lb + 2
                        val urlEnd = s.indexOf(')', urlStart)
                        if (urlEnd > urlStart) {
                            val label = s.substring(i + 1, lb)
                            val url = s.substring(urlStart, urlEnd).trim()
                            val abs = if (url.startsWith("http://") || url.startsWith("https://") ||
                                url.startsWith("mailto:")
                            ) url else null
                            flush(out, buf, ctx, link)
                            val inner = ArrayList<MdSpan>()
                            append(inner, label, ctx, abs ?: link)
                            out.addAll(inner)
                            i = urlEnd + 1
                        } else {
                            buf.append('['); i++
                        }
                    } else {
                        buf.append('['); i++
                    }
                }
                c == '*' -> i = star(out, buf, s, i, ctx, link)
                c == '_' -> i = underscore(out, buf, s, i, ctx, link)
                c == '~' && i + 1 < n && s[i + 1] == '~' -> {
                    val k = s.indexOf("~~", i + 2)
                    if (k > i + 2) {
                        flush(out, buf, ctx, link)
                        append(out, s.substring(i + 2, k), ctx.copy(strike = true), link)
                        i = k + 2
                    } else {
                        buf.append('~'); i++
                    }
                }
                else -> {
                    buf.append(c); i++
                }
            }
        }
        flush(out, buf, ctx, link)
    }

    /** 处理 * / ** / *** 强调，返回新的游标位置。 */
    private fun star(
        out: MutableList<MdSpan>, buf: StringBuilder, s: String, start: Int, ctx: Ctx, link: String?
    ): Int {
        val n = s.length
        var run = 0
        while (start + run < n && s[start + run] == '*') run++
        val openOk = start == 0 || !isWordChar(s[start - 1])
        if (!openOk) {
            buf.append("*".repeat(run)); return start + run
        }
        if (run >= 3) {
            val k = s.indexOf("***", start + run)
            if (k > start + run) {
                flush(out, buf, ctx, link)
                append(out, s.substring(start + run, k), ctx.copy(bold = true, italic = true), link)
                return k + 3
            }
        }
        if (run >= 2) {
            val k = s.indexOf("**", start + run)
            if (k > start + run) {
                flush(out, buf, ctx, link)
                append(out, s.substring(start + run, k), ctx.copy(bold = true), link)
                return k + 2
            }
            buf.append("*".repeat(run)); return start + run
        }
        var idx = start + 1
        var close = -1
        while (idx < n) {
            idx = s.indexOf('*', idx)
            if (idx < 0) break
            if (idx + 1 < n && s[idx + 1] == '*') { idx += 2; continue }
            val closeOk = idx == n - 1 || !isWordChar(s[idx + 1])
            if (closeOk && idx > start + 1) { close = idx; break }
            idx += 1
        }
        if (close > start + 1) {
            flush(out, buf, ctx, link)
            append(out, s.substring(start + 1, close), ctx.copy(italic = true), link)
            return close + 1
        }
        buf.append('*'); return start + 1
    }

    /** 处理 _ 斜体 / __ 粗体（避免单词内下划线被误伤）。 */
    private fun underscore(
        out: MutableList<MdSpan>, buf: StringBuilder, s: String, start: Int, ctx: Ctx, link: String?
    ): Int {
        val n = s.length
        val openOk = start == 0 || !isWordChar(s[start - 1])
        if (!openOk) { buf.append('_'); return start + 1 }
        if (start + 1 < n && s[start + 1] == '_') {
            val k = s.indexOf("__", start + 2)
            if (k > start + 2) {
                flush(out, buf, ctx, link)
                append(out, s.substring(start + 2, k), ctx.copy(bold = true), link)
                return k + 2
            }
            buf.append("__"); return start + 2
        }
        var idx = start + 1
        var close = -1
        while (idx < n) {
            idx = s.indexOf('_', idx)
            if (idx < 0) break
            if (idx + 1 < n && s[idx + 1] == '_') { idx += 2; continue }
            val closeOk = idx == n - 1 || !isWordChar(s[idx + 1])
            if (closeOk && idx > start + 1) { close = idx; break }
            idx += 1
        }
        if (close > start + 1) {
            flush(out, buf, ctx, link)
            append(out, s.substring(start + 1, close), ctx.copy(italic = true), link)
            return close + 1
        }
        buf.append('_'); return start + 1
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit()
}

/** GFM 表格的行解析工具（供解析器与 Compose 表格组件共用）。 */
internal object MarkdownTables {

    private val separator = Regex("^:?-+:?$")

    /** 拆出一行的单元格，支持 `\|` 转义。 */
    fun splitRow(line: String): List<String> {
        var inner = line.trim()
        if (inner.startsWith("|")) inner = inner.substring(1)
        if (inner.endsWith("|") && !inner.endsWith("\\|")) inner = inner.dropLast(1)
        val cells = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            if (c == '\\' && i + 1 < inner.length && inner[i + 1] == '|') {
                cur.append('|'); i += 2
            } else if (c == '|') {
                cells.add(cur.toString().trim()); cur.setLength(0); i++
            } else {
                cur.append(c); i++
            }
        }
        cells.add(cur.toString().trim())
        return cells
    }

    fun isSeparatorRow(cells: List<String>): Boolean =
        cells.isNotEmpty() && cells.all { separator.matches(it) }

    fun alignsOf(cells: List<String>): List<MdAlign> = cells.map { c ->
        val left = c.startsWith(":")
        val right = c.endsWith(":")
        when {
            left && right -> MdAlign.CENTER
            right -> MdAlign.RIGHT
            else -> MdAlign.LEFT
        }
    }
}

/** 块级 Markdown 解析。 */
internal object MarkdownBlocks {

    private val fenceOpenRe = Regex("^ {0,3}(`{3,}|~{3,}).*$")
    private val hrRe = Regex("^ {0,3}([-*_])( *\\1){2,}[ \t]*$")
    private val ulRe = Regex("^ {0,3}([-+*])[ \t]+(.*)$")
    private val olRe = Regex("^ {0,3}(\\d{1,9})[.)][ \t]+(.*)$")

    fun parse(md: String): List<MdBlock> {
        if (md.isBlank()) return emptyList()
        val lines = md.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        val out = ArrayList<MdBlock>()
        val para = ArrayList<String>()

        fun flushPara() {
            if (para.isEmpty()) return
            val text = para.joinToString(" ").trim()
            if (text.isNotEmpty()) out.add(MdBlock.Paragraph(MarkdownInline.parse(text)))
            para.clear()
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()

            if (t.isEmpty()) { flushPara(); i++; continue }

            // 围栏代码块
            if (fenceOpenRe.matches(t)) {
                flushPara()
                val fc = t[0]
                val need = t.takeWhile { it == fc }.length
                val body = ArrayList<String>()
                var j = i + 1
                while (j < lines.size && !isFenceClose(lines[j].trim(), fc, need)) {
                    body.add(lines[j]); j++
                }
                out.add(MdBlock.CodeBlock(body))
                i = j + 1
                continue
            }

            if (hrRe.matches(t)) { flushPara(); out.add(MdBlock.Rule); i++; continue }

            val h = headingLevel(t)
            if (h > 0) {
                flushPara()
                out.add(MdBlock.Heading(h, MarkdownInline.parse(t.substring(h + 1).trim())))
                i++
                continue
            }

            if (t.startsWith(">")) {
                flushPara()
                val quoteLines = ArrayList<String>()
                while (i < lines.size) {
                    val tt = lines[i].trim()
                    if (tt.startsWith(">")) {
                        quoteLines.add(tt.drop(1).trimStart()); i++
                    } else break
                }
                out.add(MdBlock.Quote(groupParagraphs(quoteLines)))
                continue
            }

            if (t.startsWith("|")) {
                val table = parseTable(lines, i)
                if (table != null) {
                    flushPara()
                    out.add(table.block)
                    i = table.next
                    continue
                }
            }

            val marker = listMarker(t)
            if (marker != null) {
                flushPara()
                val (block, next) = parseList(lines, i, marker.first)
                out.add(block)
                i = next
                continue
            }

            para.add(line)
            i++
        }
        flushPara()
        return out
    }

    private class ParsedTable(val block: MdBlock.Table, val next: Int)

    /** 解析一个表格块；行数不足或首行即分隔行时返回 null，交给段落处理。 */
    private fun parseTable(lines: List<String>, start: Int): ParsedTable? {
        val raw = ArrayList<List<String>>()
        var j = start
        while (j < lines.size) {
            val t = lines[j].trim()
            if (t.isEmpty() || !t.startsWith("|")) break
            raw.add(MarkdownTables.splitRow(t))
            j++
        }
        if (raw.size < 2) return null
        val sepIndex = raw.indexOfFirst { MarkdownTables.isSeparatorRow(it) }
        if (sepIndex == 0) return null

        val header = raw[0]
        val aligns = if (sepIndex > 0) MarkdownTables.alignsOf(raw[sepIndex]) else emptyList()
        val rows = if (sepIndex > 0) raw.drop(sepIndex + 1) else raw.drop(1)
        if (header.isEmpty()) return null

        fun cellsOf(cells: List<String>): List<List<MdSpan>> =
            cells.map { MarkdownInline.parse(it) }

        return ParsedTable(
            block = MdBlock.Table(
                header = cellsOf(header),
                rows = rows.map { cellsOf(it) },
                aligns = aligns
            ),
            next = j
        )
    }

    private fun isFenceClose(t: String, fc: Char, need: Int): Boolean {
        if (t.isEmpty() || t[0] != fc) return false
        val len = t.takeWhile { it == fc }.length
        return len >= need && t.dropWhile { it == fc }.isBlank()
    }

    private fun headingLevel(t: String): Int {
        val hashes = t.takeWhile { it == '#' }
        if (hashes.isEmpty() || hashes.length > 6) return 0
        val rest = t.drop(hashes.length)
        if (rest.isEmpty() || rest[0] != ' ') return 0
        return hashes.length
    }

    private fun listMarker(t: String): Pair<Boolean, String>? {
        ulRe.find(t)?.let { return false to it.groupValues[2] }
        olRe.find(t)?.let { return true to it.groupValues[2] }
        return null
    }

    private fun orderedStart(t: String): Int =
        olRe.find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    private fun startsBlock(t: String): Boolean =
        fenceOpenRe.matches(t) || hrRe.matches(t) || headingLevel(t) > 0 ||
            t.startsWith(">") || t.startsWith("|") || listMarker(t) != null

    /** 把一个列表块解析成有序 / 无序列表，返回块与下一行号。 */
    private fun parseList(lines: List<String>, start: Int, ordered: Boolean): Pair<MdBlock, Int> {
        val items = ArrayList<List<MdSpan>>()
        var i = start
        val firstStart = if (ordered) orderedStart(lines[i].trim()) else 1
        var current: ArrayList<String>? = null

        fun closeItem() {
            current?.let { lns ->
                val text = lns.joinToString(" ").trim()
                if (text.isNotEmpty()) items.add(MarkdownInline.parse(text))
            }
            current = null
        }

        while (i < lines.size) {
            val t = lines[i].trim()
            if (t.isEmpty()) break
            val m = listMarker(t)
            if (m != null && m.first == ordered) {
                closeItem()
                current = ArrayList()
                current!!.add(m.second)
                i++
                continue
            }
            if (current != null && !startsBlock(t)) {
                current!!.add(t); i++
                continue
            }
            break
        }
        closeItem()
        if (i == start) i++
        val block = if (ordered) MdBlock.OrderedList(firstStart, items) else MdBlock.BulletList(items)
        return block to i
    }

    /** 引用块内部按空行切段。 */
    private fun groupParagraphs(lines: List<String>): List<List<MdSpan>> {
        val result = ArrayList<List<MdSpan>>()
        val cur = ArrayList<String>()
        for (l in lines) {
            if (l.isBlank()) {
                if (cur.isNotEmpty()) {
                    result.add(MarkdownInline.parse(cur.joinToString(" ").trim()))
                    cur.clear()
                }
            } else {
                cur.add(l)
            }
        }
        if (cur.isNotEmpty()) result.add(MarkdownInline.parse(cur.joinToString(" ").trim()))
        return result
    }
}
