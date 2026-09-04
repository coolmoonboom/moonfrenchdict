package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MarkdownToHtml 回归测试：
 * 核心不变量是「合法 Markdown 不把原始符号漏到 HTML 上」——
 * 凡是成对的 ** * _ ` ~ 以及表格的 | 都必须被消费，不能出现在输出里。
 */
class MarkdownToHtmlTest {

    /** 去掉全部 HTML 标签后得到的纯文本（近似屏幕可见内容，&nbsp;、<br/> 还原为空格/换行）。 */
    private fun visible(html: String): String =
        html.replace("&nbsp;", " ")
            .replace(Regex("<br/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")

    // ---------- 报障截图回归 ----------

    @Test
    fun frenchSnippet_noRawAsterisksRemain() {
        val md = """
            **Courante/orale**
            *Comment ça va ?*
            *Comment va-t-on ?*
        """.trimIndent()
        val html = MarkdownToHtml.convert(md)
        // 三行没有空行：应合并为同一个 <p>（软换行），不能出现原始星号
        assertFalse("bold 标记泄漏", html.contains("**"))
        assertFalse("斜体标记泄漏", html.contains("*Comment"))
        assertTrue(html.contains("<b>Courante/orale</b>"))
        assertTrue(html.contains("<i>Comment ça va ?</i>"))
        assertTrue(html.contains("<i>Comment va-t-on ?</i>"))
        // 软换行合并进一个段落
        assertEquals(1, Regex("<p>").findAll(html).count())
        assertFalse(html.contains("？</p>"))
    }

    @Test
    fun tableCells_areInlinedAndNoRawPipes() {
        val md = """
            | 语域 | 用法 | 例句 |
            | --- | --- | --- |
            | **Courante** | *Quelque chose* | C'est bon |
            | Soutenue | littéraire | 少见 |
        """.trimIndent()
        val html = MarkdownToHtml.convert(md)
        assertTrue(html.startsWith("<tt>"))
        assertTrue(html.endsWith("</tt>"))
        assertTrue("表头加粗缺失", html.contains("<b>Courante</b>"))
        assertTrue(html.contains("<i>Quelque&nbsp;chose</i>"))
        assertTrue("行间用 <br/> 分隔", html.contains("<br/>"))
        assertTrue("对齐空格需用 &nbsp; 保留", html.contains("&nbsp;"))
        assertFalse("管道符残留", html.contains("|"))
        assertFalse("分隔行残留", html.contains("---"))
    }

    @Test
    fun mixedParagraphAndBlocks_renderCleanly() {
        val md = """
            ## 语法讲解

            **Courante/orale** 表示口语。
            这里的 *Comment ça va ?* 很常用。

            - 第一点：**日常**
            - 第二点：*口吻*

            > 引用一句话

            代码 `bonjour` 和 **加粗** 都要正常。
        """.trimIndent()
        val html = MarkdownToHtml.convert(md)
        val text = visible(html)
        assertFalse(html.contains("**"))
        assertFalse(html.contains("|"))
        assertTrue(html.contains("<h2>语法讲解</h2>"))
        assertTrue(html.contains("<ul><li>"))
        assertTrue(html.contains("<li>第一点：<b>日常</b></li>"))
        assertTrue(html.contains("<code>bonjour</code>"))
        assertTrue(html.contains("<blockquote>引用一句话</blockquote>"))
        assertFalse("段落被拆成多段", text.contains("表示口语。这里的"))
    }

    // ---------- 段落 / 软换行 ----------

    @Test
    fun singleNewline_isSoftBreak_singleParagraph() {
        val html = MarkdownToHtml.convert("第一行很长被截断的内容\n继续第二行内容")
        assertEquals(1, Regex("<p>").findAll(html).count())
        assertTrue(html.contains("<p>第一行很长被截断的内容 继续第二行内容</p>"))
    }

    @Test
    fun blankLine_opensNewParagraph() {
        val html = MarkdownToHtml.convert("第一段\n\n第二段")
        assertEquals(2, Regex("<p>").findAll(html).count())
    }

    // ---------- 行内格式 ----------

    @Test
    fun emphasis_allVariantsRender() {
        val md = "**b** *i* _i2_ ***bi*** ~~del~~ `c`"
        val html = MarkdownToHtml.convert(md)
        assertFalse(html.contains("**"))
        assertFalse(html.contains("<i>_"))
        assertTrue(html.contains("<b>b</b>"))
        assertTrue(html.contains("<i>i</i>"))
        assertTrue(html.contains("<i>i2</i>"))
        assertTrue(html.contains("<b><i>bi</i></b>"))
        assertTrue(html.contains("<s>del</s>"))
        assertTrue(html.contains("<code>c</code>"))
    }

    @Test
    fun underscores_insideWords_areLeftAlone() {
        val html = MarkdownToHtml.convert("file_name_test 正常")
        assertFalse(html.contains("<i>"))
        assertTrue(html.contains("file_name_test 正常"))
    }

    @Test
    fun asteriskInsideWord_isNotEmphasis() {
        val html = MarkdownToHtml.convert("2 * 3 = 6")
        assertFalse(html.contains("<i>"))
        assertTrue(visible(html).contains("2 * 3 = 6"))
    }

    @Test
    fun unmatchedMarkers_renderAsLiteralText() {
        // 没有成对闭合的星号应原样显示，绝不吞字
        val html = MarkdownToHtml.convert("只说了一个 * 星号")
        assertEquals("只说了一个 * 星号", visible(html).trim())
    }

    @Test
    fun htmlInput_isEscaped_notExecuted() {
        val html = MarkdownToHtml.convert("<b>危险</b> <script>alert(1)</script>")
        assertFalse("原始 <b> 应被转义", html.contains("<b>危险</b>"))
        assertTrue(html.contains("&lt;b&gt;"))
    }

    @Test
    fun link_rendersAsAnchor() {
        val html = MarkdownToHtml.convert("看[这里](https://example.com/a?b=1&c=2)就行")
        assertTrue(html.contains("<a href=\"https://example.com/a?b=1&amp;c=2\">"))
        assertTrue(html.contains(">这里</a>"))
    }

    @Test
    fun backslashEscapes_punctLiterally() {
        val html = MarkdownToHtml.convert("\\*不是斜体\\*")
        assertFalse(html.contains("<i>"))
        assertTrue(visible(html).trim().contains("*不是斜体*"))
    }

    // ---------- 块级 ----------

    @Test
    fun heading_list_quote_code_hr() {
        val md = """
            # H1

            正文

            ---

            1. 第一
            2. 第二

            ```
            val x = "**不动**"
            ```
        """.trimIndent()
        val html = MarkdownToHtml.convert(md)
        assertTrue(html.contains("<h1>H1</h1>"))
        assertTrue(html.contains("<hr/>"))
        assertTrue(html.contains("<ol><li>第一</li><li>第二</li></ol>"))
        assertTrue(html.contains("<code>"))
        // 代码块内不再执行行内转换
        assertTrue(html.contains("**不动**"))
        // 代码块之外不得出现裸星号
        val outsideCode = html.replace(Regex("<code>[\\s\\S]*?</code>"), "")
        assertFalse("代码块外出现裸星号: $outsideCode", outsideCode.contains("**"))
    }

    @Test
    fun nestedOrderedMixedAndListContinuation() {
        val md = "- 苹果\n- 香蕉，很长很长的第二行继续\n  还是续行\n\n然后是一段正文。"
        val html = MarkdownToHtml.convert(md)
        assertTrue(html.contains("<ul>"))
        assertEquals(2, Regex("<li>").findAll(html).count())
        assertTrue(html.contains("还是续行"))
        assertTrue(html.contains("<p>然后是一段正文。</p>"))
    }

    @Test
    fun emptyInput_returnsEmpty() {
        assertEquals("", MarkdownToHtml.convert(""))
        assertEquals("", MarkdownToHtml.convert("   \n\n  "))
    }

    @Test
    fun plainTextToHtml_escapesAndBreaksLines() {
        val html = MarkdownToHtml.plainTextToHtml("a < b\n\nc & d")
        assertEquals("<p>a &lt; b<br/><br/>c &amp; d</p>", html)
    }

    // ---------- 表格对齐 ----------

    @Test
    fun table_columnsAreAligned_byVisibleWidth() {
        val md = """
            | **Mot** | Sens |
            | --- | --- |
            | bonjour | Bonjour est **long** |
            | soir | Soir |
        """.trimIndent()
        val html = MarkdownToHtml.convert(md)
        assertTrue(html.startsWith("<tt>"))
        assertTrue(html.endsWith("</tt>"))
        // 表头加粗
        assertTrue(html.contains("<b>Mot"))
        // 三行的列头（第 2 列）起始位置对齐：去标签后的三行文本应完全一致于预期
        val lines = visible(html).split('\n').map { it.trimEnd() }
        assertEquals(
            listOf(
                "Mot      Sens",
                "bonjour  Bonjour est long",
                "soir     Soir"
            ),
            lines
        )
        assertFalse(html.contains("**"))
        assertFalse(html.contains("|"))
    }

    @Test
    fun table_pipeInsideCell_escapedBackslash() {
        val md = "| A \\| B | C |\n| --- | --- |\n| x | y |"
        val html = MarkdownToHtml.convert(md)
        assertFalse(html.contains("\\|"))
        assertTrue(visible(html).contains("A | B"))
    }

    @Test
    fun multiBlockOutput_containsNoLeakedMarkers() {
        val samples = listOf(
            "a **b** c",
            "*a* `x` _y_ [z](https://x.y)",
            "- **list**\n- *item*",
            "| h1 | h2 |\n| --- | --- |\n| **b** | *i* |",
            "# 标题\n\n**段落** 内容\n\n> 引用 *强调*",
            "**a**\nb\n\nc"
        )
        for (s in samples) {
            val html = MarkdownToHtml.convert(s)
            val outsideCode = html.replace(Regex("<code>.*?</code>"), "")
            assertFalse("marker 泄漏: $s -> $html", outsideCode.contains("**"))
            assertFalse("漏 |: $s -> $html", outsideCode.contains("|"))
        }
    }
}
