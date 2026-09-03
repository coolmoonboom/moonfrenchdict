package com.coolmoonfrench.dict

/**
 * 极简 Markdown -> HTML 转换器。
 * 供气泡内的系统 TextView(Html.fromHtml) 复用：长按文本时能弹出系统级文本选择菜单，
 * 同时保留粗体/斜体/代码/标题/列表/引用/链接等基本排版。
 */
object MarkdownToHtml {

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun inline(s: String): String {
        var t = s
        t = t.replace(Regex("`([^`]+)`")) { "<code>${esc(it.groupValues[1])}</code>" }
        t = t.replace(Regex("\\*\\*(.+?)\\*\\*")) { "<b>${inline(it.groupValues[1])}</b>" }
        t = t.replace(Regex("(?<![*])[*_]([^*_]+)[*_](?![*_])")) { "<i>${inline(it.groupValues[1])}</i>" }
        t = t.replace(Regex("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)")) {
            "<a href=\"${esc(it.groupValues[2])}\">${inline(it.groupValues[1])}</a>"
        }
        return t
    }

    fun convert(md: String): String {
        if (md.isBlank()) return ""
        val lines = md.split("\n")
        val out = StringBuilder()
        var inCode = false
        var inUl = false
        var inOl = false
        var inQuote = false

        fun closeLists() {
            if (inUl) { out.append("</ul>"); inUl = false }
            if (inOl) { out.append("</ol>"); inOl = false }
        }

        for (raw in lines) {
            val line = raw.trim()

            if (line.startsWith("```")) {
                if (inCode) {
                    out.append("</code></pre>")
                    inCode = false
                } else {
                    closeLists()
                    if (inQuote) { out.append("</blockquote>"); inQuote = false }
                    out.append("<pre><code>")
                    inCode = true
                }
                continue
            }
            if (inCode) {
                out.append(esc(raw)).append('\n')
                continue
            }

            if (line.isEmpty()) {
                closeLists()
                if (inQuote) { out.append("</blockquote>"); inQuote = false }
                continue
            }

            // 表格：转成紧凑纯文本，避免横向溢出
            if (line.startsWith("|") && line.endsWith("|")) {
                val cells = line.trim('|').split("|").map { it.trim() }
                val isSep = cells.all { it.matches(Regex(":?-{2,}:?")) }
                if (!isSep && cells.any { it.isNotBlank() }) {
                    out.append(cells.joinToString("   ")).append("<br/>")
                }
                continue
            }

            val heading = Regex("^(#{1,6})\\s+(.*)").find(line)
            if (heading != null) {
                closeLists()
                if (inQuote) { out.append("</blockquote>"); inQuote = false }
                val level = heading.groupValues[1].length
                out.append("<h$level>").append(inline(heading.groupValues[2])).append("</h$level>")
                continue
            }

            if (Regex("^([-*_])\\1{2,}\\s*$").matches(line)) {
                closeLists()
                out.append("<hr/>")
                continue
            }

            if (line.startsWith(">")) {
                if (!inQuote) {
                    closeLists()
                    out.append("<blockquote>")
                    inQuote = true
                }
                out.append(inline(line.drop(1).trim())).append("<br/>")
                continue
            }

            val ul = Regex("^[-*+]\\s+(.*)").find(line)
            if (ul != null) {
                if (inOl) { out.append("</ol>"); inOl = false }
                if (!inUl) { out.append("<ul>"); inUl = true }
                out.append("<li>").append(inline(ul.groupValues[1])).append("</li>")
                continue
            }

            val ol = Regex("^\\d+\\.\\s+(.*)").find(line)
            if (ol != null) {
                if (inUl) { out.append("</ul>"); inUl = false }
                if (!inOl) { out.append("<ol>"); inOl = true }
                out.append("<li>").append(inline(ol.groupValues[1])).append("</li>")
                continue
            }

            closeLists()
            if (inQuote) { out.append("</blockquote>"); inQuote = false }
            out.append("<p>").append(inline(raw)).append("</p>")
        }

        if (inCode) out.append("</code></pre>")
        closeLists()
        if (inQuote) out.append("</blockquote>")
        return out.toString()
    }

    /** 用户纯文本消息转 HTML（转义 + 换行） */
    fun plainTextToHtml(s: String): String =
        "<p>" + esc(s).replace("\n", "<br/>") + "</p>"
}
