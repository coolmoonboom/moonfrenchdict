package com.coolmoonfrench.dict

import java.util.regex.Pattern

object MarkdownSanitizer {

    private val separatorRow = Pattern.compile("^\\s*\\|?[\\s:\\-|+_.\\t]*\\s*$")
    private val isBlockStart = Pattern.compile("^(\\s{0,3}(>|#|[-+*]\\s|\\d+\\.\\s|```|\\|)|[-_*]{3,}|\\[x?\\]\\s)")

    /**
     * 规范化 AI 生成的 Markdown，修复导致表格/引用块渲染失败的不规范格式：
     * 1. 以 `|` 开头的表格块若缺少分隔行，自动补一行（列数与表头一致）。
     * 2. 引用块 `>` 之后若紧跟非引用内容且无空行，插入空行，避免后续文本被吞入引用块。
     */
    fun sanitize(md: String): String {
        if (md.isBlank()) return md
        val lines = md.split("\n")
        val out = mutableListOf<String>()
        var inCodeFence = false
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                out.add(line)
                inCodeFence = !inCodeFence
                i++
                continue
            }
            if (inCodeFence) {
                out.add(line)
                i++
                continue
            }

            if (line.trimStart().startsWith("|")) {
                val block = mutableListOf(line)
                var j = i + 1
                while (j < lines.size) {
                    val l = lines[j]
                    if (l.isBlank()) break
                    if (l.trimStart().startsWith("|")) {
                        block.add(l); j++
                    } else break
                }
                val needsSeparator = block.size < 2 || !isSeparatorRow(block[1])
                if (needsSeparator) {
                    out.add(block[0])
                    out.add(buildSeparator(block[0]))
                    for (k in 1 until block.size) out.add(block[k])
                } else {
                    out.addAll(block)
                }
                i = j
                continue
            }

            if (line.startsWith(">")) {
                out.add(line)
                var j = i + 1
                var addedBlank = false
                while (j < lines.size && lines[j].isBlank()) {
                    out.add(lines[j]); j++
                }
                if (j < lines.size) {
                    val next = lines[j]
                    val isQuoteContinuation = next.startsWith(">")
                    val isIndented = next.startsWith(" ") || next.startsWith("\t")
                    val startsNewBlock = isBlockStart.matcher(next).find()
                    if (!isQuoteContinuation && !isIndented && !startsNewBlock && !addedBlank) {
                        out.add("")
                        addedBlank = true
                    }
                }
                i = j
                continue
            }

            out.add(line)
            if (i + 1 < lines.size && lines[i + 1].trimStart().startsWith("|")) {
                out.add("")
            }
            i++
        }
        return out.joinToString("\n")
    }

    private fun isSeparatorRow(line: String): Boolean {
        if (!separatorRow.matcher(line).matches()) return false
        return line.contains('-')
    }

    private fun buildSeparator(header: String): String {
        val trimmed = header.trim().trimStart('|').trimEnd('|').trim()
        val columns = if (trimmed.isEmpty()) 1 else trimmed.split("|").size
        val cols = if (columns >= 1) columns else 1
        return (1..cols).joinToString("|", prefix = "|", postfix = "|") { "---" }
    }
}
