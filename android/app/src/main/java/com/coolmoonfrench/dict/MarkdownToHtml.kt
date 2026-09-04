package com.coolmoonfrench.dict

/**
 * Markdown -> HTML 转换器。
 *
 * 供气泡内的系统 TextView(Html.fromHtml) 复用：长按文本时能弹出系统级文本选择菜单，
 * 同时把常见的 Markdown 结构渲染成 fromHtml 支持的标签，保证任意合法输入都不会
 * 把 `**` / `*` / `` ` `` 等原始符号漏到屏幕上：
 *  - 段落合并软换行（单换行只当空格），只有空行才开新段落；
 *  - 表格按列宽对齐渲染成 <pre> 块（TextView 无法布局真实表格，等宽对齐是折中）；
 *  - 列表 / 引用 / 代码块 / 标题 / 分割线 / 链接 均做块级识别；
 *  - 粗体 / 斜体 / 代码 / 删除线 / 链接 的行内转换在段落与表格单元格中统一生效。
 */
object MarkdownToHtml {

    // ---------- 输出 ----------

    fun convert(md: String): String {
        if (md.isBlank()) return ""
        val src = md.replace("\r\n", "\n").replace('\r', '\n') + "\n\n"
        val lines = src.split("\n")
        val n = lines.size
        val out = StringBuilder()
        var i = 0
        var para = ArrayList<String>()

        fun flushPara() {
            if (para.isEmpty()) return
            out.append("<p>").append(para.joinToString(" ") { inline(it.trim()) }).append("</p>")
            para.clear()
        }

        while (i < n) {
            val line = lines[i]
            val t = line.trim()

            if (t.isEmpty()) { flushPara(); i++; continue }

            // 代码块
            if (isFenceOpen(t)) {
                flushPara()
                val fc = t[0]
                val need = t.takeWhile { it == fc }.length
                val body = StringBuilder()
                var j = i + 1
                while (j < n && !isFenceClose(lines[j].trim(), fc, need)) {
                    body.append(lines[j]).append('\n')
                    j++
                }
                // fromHtml 对 <pre> 内换行/空格支持不稳，代码块同样转成 <code> + <br/> + &nbsp; 保证换行与缩进可见
                val codeLines = esc(body.toString()).split("\n")
                out.append("<code>")
                codeLines.forEachIndexed { idx, l ->
                    if (idx > 0) out.append("<br/>")
                    out.append(l.replace(" ", "&nbsp;"))
                }
                out.append("</code>")
                i = j + 1
                continue
            }

            // 分割线
            if (isHr(t)) { flushPara(); out.append("<hr/>"); i++; continue }

            // 标题
            val h = headingLevel(t)
            if (h > 0) {
                flushPara()
                val content = t.substring(h + 1).trim()
                out.append("<h$h>").append(inline(content)).append("</h$h>")
                i++
                continue
            }

            // 引用块
            if (t.startsWith(">")) {
                flushPara()
                val quote = ArrayList<String>()
                while (i < n) {
                    val tt = lines[i].trim()
                    if (tt.startsWith(">")) {
                        quote.add(inline(tt.drop(1).trim()))
                        i++
                    } else break
                }
                out.append("<blockquote>").append(quote.joinToString("<br/>")).append("</blockquote>")
                continue
            }

            // 表格（行首以 | 开头）
            if (t.startsWith("|") && t.endsWith("|")) {
                flushPara()
                i = renderTable(out, lines, i)
                continue
            }

            // 列表
            if (listMarker(t) != null) {
                flushPara()
                i = renderList(out, lines, i)
                continue
            }

            // 普通段落行，先缓存，遇到空行/新块时合并为一个 <p>
            para.add(line)
            i++
        }
        flushPara()
        return out.toString()
    }

    /** 用户纯文本消息转 HTML（转义 + 换行） */
    fun plainTextToHtml(s: String): String =
        "<p>" + esc(s).replace("\n", "<br/>") + "</p>"

    // ---------- 块级解析 ----------

    private val fenceOpenRe = Regex("^ {0,3}(`{3,}|~{3,}).*$")
    private val fenceCloseRe = Regex("^ {0,3}(`{3,}|~{3,})[ \t]*$")
    private val hrRe = Regex("^ {0,3}([-*_])( *\\1){2,}[ \t]*$")
    private val headingRe = Regex("^ {0,3}(#{1,6})[ \t]+(.*)$")
    private val ulRe = Regex("^ {0,3}([-+*])[ \t]+(.*)$")
    private val olRe = Regex("^ {0,3}(\\d{1,9})[.)][ \t]+(.*)$")
    private val sepCellRe = Regex("^:?-{1,}:?$")

    private fun isFenceOpen(t: String) = fenceOpenRe.matches(t)

    private fun isFenceClose(t: String, fc: Char, need: Int): Boolean {
        if (t.isEmpty() || t[0] != fc) return false
        val len = t.takeWhile { it == fc }.length
        return len >= need && t.dropWhile { it == fc }.isBlank()
    }

    private fun isHr(t: String) = hrRe.matches(t)

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

    /** 渲染一个列表块，返回下一个待处理行号。 */
    private fun renderList(out: StringBuilder, lines: List<String>, start: Int): Int {
        var i = start
        var openOrdered: Boolean? = null
        var itemLines: ArrayList<String>? = null

        fun closeListTag() {
            when (openOrdered) {
                true -> out.append("</ol>")
                false -> out.append("</ul>")
                null -> Unit
            }
            openOrdered = null
        }
        fun closeItem() {
            itemLines?.let { lns ->
                if (lns.isNotEmpty()) {
                    out.append("<li>").append(lns.joinToString(" ") { inline(it.trim()) }).append("</li>")
                }
            }
            itemLines = null
        }

        while (i < lines.size) {
            val t = lines[i].trim()
            if (t.isEmpty()) break
            val m = listMarker(t)
            if (m != null) {
                closeItem()
                if (openOrdered != m.first) {
                    closeListTag()
                    out.append(if (m.first) "<ol>" else "<ul>")
                    openOrdered = m.first
                }
                itemLines = ArrayList()
                itemLines!!.add(m.second)
                i++
                continue
            }
            // 列表项的续行
            if (openOrdered != null && itemLines != null && !startsBlock(t)) {
                itemLines!!.add(t)
                i++
                continue
            }
            break
        }
        closeItem()
        closeListTag()
        if (i == start) i++
        return i
    }

    private fun startsBlock(t: String): Boolean =
        isFenceOpen(t) || isHr(t) || headingLevel(t) > 0 ||
            t.startsWith(">") || t.startsWith("|") || listMarker(t) != null

    /** 渲染 GFM 表格为等宽对齐的 <pre> 文本块。 */
    private fun renderTable(out: StringBuilder, lines: List<String>, start: Int): Int {
        val raw = ArrayList<List<String>>()
        var j = start
        while (j < lines.size) {
            val t = lines[j].trim()
            if (t.isEmpty()) break
            if (t.startsWith("|") && t.endsWith("|")) {
                val cells = splitCells(t)
                if (cells.isNotEmpty() && cells.all { sepCellRe.matches(it) }) {
                    j++
                    continue
                }
                raw.add(cells)
                j++
            } else break
        }
        if (raw.isEmpty()) return j

        val colCount = raw.first().size
        val rows = raw.map { row -> row.pad(colCount, "") }
        val header = rows.first()
        val widths = IntArray(colCount) { c -> rows.maxOf { row -> visibleLen(row[c]) } }

        fun rowLine(row: List<String>, boldHeader: Boolean): String =
            row.mapIndexed { c, cell ->
                val pad = (widths[c] - visibleLen(cell)).coerceAtLeast(0)
                val htmlCell = inline(cell)
                if (boldHeader) "<b>$htmlCell${" ".repeat(pad)}</b>"
                else "$htmlCell${" ".repeat(pad)}"
            }.joinToString("  ")

        // Html.fromHtml 不支持 <pre>，行内空格/换行会被折叠导致表格挤成一团。
        // 改用 <tt>（等宽）+ &nbsp;（保留对齐空格）+ <br/>（保留行结构）保证表格在 TextView 里可读。
        val bodyLines = ArrayList<String>()
        bodyLines.add(rowLine(header, boldHeader = true))
        rows.drop(1).forEach { bodyLines.add(rowLine(it, boldHeader = false)) }
        out.append("<tt>")
        bodyLines.forEachIndexed { idx, line ->
            if (idx > 0) out.append("<br/>")
            out.append(line.replace(" ", "&nbsp;"))
        }
        out.append("</tt>")
        return j
    }

    private fun List<String>.pad(size: Int, fill: String): List<String> =
        if (this.size >= size) take(size)
        else this + List(size - this.size) { fill }

    private fun splitCells(line: String): List<String> {
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

    // ---------- 行内解析 ----------

    private val escapable = "\\`*_{}[]()#+-.!|>~".toSet()

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit()

    private fun inline(s: String): String {
        val out = StringBuilder()
        val n = s.length
        var i = 0
        while (i < n) {
            val c = s[i]
            when {
                c == '`' -> {
                    val j = s.indexOf('`', i + 1)
                    if (j > i + 1) {
                        out.append("<code>").append(esc(s.substring(i + 1, j))).append("</code>")
                        i = j + 1
                    } else {
                        out.append("`"); i++
                    }
                }
                c == '\\' && i + 1 < n && s[i + 1] in escapable -> {
                    out.append(esc(s[i + 1].toString())); i += 2
                }
                c == '[' -> {
                    val lb = s.indexOf(']', i + 1)
                    if (lb > i + 1 && s.startsWith("](", lb)) {
                        val urlStart = lb + 2
                        val urlEnd = s.indexOf(')', urlStart)
                        if (urlEnd > urlStart) {
                            val label = s.substring(i + 1, lb)
                            val url = s.substring(urlStart, urlEnd).trim()
                            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
                                out.append("<a href=\"").append(attrEsc(url)).append("\">")
                                    .append(inline(label)).append("</a>")
                            } else {
                                out.append(inline(label))
                            }
                            i = urlEnd + 1
                        } else {
                            out.append("["); i++
                        }
                    } else {
                        out.append("["); i++
                    }
                }
                c == '*' -> i = appendStarEmphasis(out, s, i)
                c == '_' -> i = appendUnderscore(out, s, i)
                c == '~' && i + 1 < n && s[i + 1] == '~' -> {
                    val k = s.indexOf("~~", i + 2)
                    if (k > i + 2) {
                        out.append("<s>").append(inline(s.substring(i + 2, k))).append("</s>")
                        i = k + 2
                    } else {
                        out.append("~"); i++
                    }
                }
                else -> {
                    out.append(esc(c.toString())); i++
                }
            }
        }
        return out.toString()
    }

    /** 处理 * / ** / *** 强调。返回新的游标位置。 */
    private fun appendStarEmphasis(out: StringBuilder, s: String, start: Int): Int {
        val n = s.length
        var run = 0
        while (start + run < n && s[start + run] == '*') run++
        val openOk = start == 0 || !isWordChar(s[start - 1])
        if (!openOk) {
            out.append("*".repeat(run))
            return start + run
        }
        if (run >= 3) {
            val k = s.indexOf("***", start + run)
            if (k > start + run) {
                out.append("<b><i>").append(inline(s.substring(start + run, k))).append("</i></b>")
                return k + 3
            }
        }
        if (run >= 2) {
            val k = s.indexOf("**", start + run)
            if (k > start + run) {
                out.append("<b>").append(inline(s.substring(start + run, k))).append("</b>")
                return k + 2
            }
            out.append("*".repeat(run))
            return start + run
        }
        // 单个 *
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
            out.append("<i>").append(inline(s.substring(start + 1, close))).append("</i>")
            return close + 1
        }
        out.append("*")
        return start + 1
    }

    /** 处理 _ 斜体 / __ 粗体（避免单词内下划线被误伤）。 */
    private fun appendUnderscore(out: StringBuilder, s: String, start: Int): Int {
        val n = s.length
        val openOk = start == 0 || !isWordChar(s[start - 1])
        if (!openOk) {
            out.append("_")
            return start + 1
        }
        // __粗体__
        if (start + 1 < n && s[start + 1] == '_') {
            val k = s.indexOf("__", start + 2)
            if (k > start + 2) {
                out.append("<b>").append(inline(s.substring(start + 2, k))).append("</b>")
                return k + 2
            }
            out.append("__")
            return start + 2
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
            out.append("<i>").append(inline(s.substring(start + 1, close))).append("</i>")
            return close + 1
        }
        out.append("_")
        return start + 1
    }

    // ---------- 转义与可见宽度 ----------

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun attrEsc(s: String): String =
        esc(s).replace("\"", "&quot;").replace("'", "&#39;")

    /** 计算去掉 Markdown 标记后的可见字符数，用于表格列宽对齐。 */
    private fun visibleLen(s: String): Int {
        var count = 0
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < n && s[i + 1] in escapable -> i += 2
                c == '`' -> {
                    val j = s.indexOf('`', i + 1)
                    if (j > i + 1) {
                        count += visibleLen(s.substring(i + 1, j)); i = j + 1
                    } else { i++ }
                }
                c == '[' -> {
                    val lb = s.indexOf(']', i + 1)
                    if (lb > i + 1 && s.startsWith("](", lb)) {
                        val urlEnd = s.indexOf(')', lb + 2)
                        if (urlEnd > lb + 2) {
                            count += visibleLen(s.substring(i + 1, lb)); i = urlEnd + 1
                        } else { count++; i++ }
                    } else { count++; i++ }
                }
                c == '*' || c == '_' || c == '~' -> i++
                else -> { count++; i++ }
            }
        }
        return count
    }
}
