package com.coolmoonfrench.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共享 Markdown 解析模型（收藏详情表格 + 图片/PDF 导出）的鲁棒性测试。
 */
class MarkdownModelTest {

    // ---------- 行内样式 ----------

    @Test
    fun `bold italic code strike and link parse into styled spans`() {
        val spans = MarkdownInline.parse("**gras** *italique* `code` ~~barre~~ [lien](https://a.com)")
        val byText = spans.associateBy { it.text }
        assertTrue(byText.getValue("gras").bold)
        assertTrue(byText.getValue("italique").italic)
        assertTrue(byText.getValue("code").code)
        assertTrue(byText.getValue("barre").strike)
        assertEquals("https://a.com", byText.getValue("lien").link)
    }

    @Test
    fun `triple emphasis is both bold and italic`() {
        val spans = MarkdownInline.parse("***les deux***")
        assertEquals(1, spans.size)
        assertTrue(spans[0].bold)
        assertTrue(spans[0].italic)
        assertEquals("les deux", spans[0].text)
    }

    @Test
    fun `underscore emphasis outside words`() {
        val spans = MarkdownInline.parse("_oui_ et __non__")
        val byText = spans.associateBy { it.text }
        assertTrue(byText.getValue("oui").italic)
        assertTrue(byText.getValue("non").bold)
    }

    @Test
    fun `plain text strips all markers`() {
        val spans = MarkdownInline.parse("**Bonjour** le `monde`")
        assertEquals("Bonjour le monde", MarkdownInline.plainText(spans))
    }

    @Test
    fun `image syntax keeps alt text without bang marker`() {
        val plain = MarkdownInline.plainText(MarkdownInline.parse("![图片](/data/user/0/x/img.png)"))
        assertEquals("图片", plain)
        assertFalse(plain.contains("!"))
    }

    @Test
    fun `relative link keeps label without link target`() {
        val spans = MarkdownInline.parse("[voir](note.md)")
        assertEquals("voir", MarkdownInline.plainText(spans))
        assertNull(spans.first { it.text == "voir" }.link)
    }

    @Test
    fun `backslash escape keeps literal marker`() {
        assertEquals("a*b", MarkdownInline.plainText(MarkdownInline.parse("a\\*b")))
    }

    // ---------- 表格拆分 ----------

    @Test
    fun `split row handles surrounding pipes and escaped pipe`() {
        assertEquals(listOf("a", "b", "c"), MarkdownTables.splitRow("| a | b | c |"))
        assertEquals(listOf("a|b", "c"), MarkdownTables.splitRow("| a\\|b | c |"))
    }

    @Test
    fun `separator row detection`() {
        assertTrue(MarkdownTables.isSeparatorRow(listOf("---", ":---:", "---:")))
        assertFalse(MarkdownTables.isSeparatorRow(listOf("Français", "中文")))
    }

    @Test
    fun `aligns are derived from separator row`() {
        assertEquals(
            listOf(MdAlign.LEFT, MdAlign.CENTER, MdAlign.RIGHT),
            MarkdownTables.alignsOf(listOf(":---", ":---:", "---:"))
        )
    }

    // ---------- 表格块 ----------

    @Test
    fun `long french sentence is preserved in full without truncation`() {
        val md = "| Français | 中文 |\n" +
            "| Combien coûte-t-il de faire un gâteau dans la rue ? | 在街上做一个蛋糕要多少钱？ |"
        val table = MarkdownBlocks.parse(md).filterIsInstance<MdBlock.Table>().single()
        assertEquals(2, table.header.size)
        assertEquals(1, table.rows.size)
        assertEquals("Français", MarkdownInline.plainText(table.header[0]))
        assertEquals(
            "Combien coûte-t-il de faire un gâteau dans la rue ?",
            MarkdownInline.plainText(table.rows[0][0])
        )
    }

    @Test
    fun `table parses with separator and alignment`() {
        val md = "| A | B |\n| :-- | --: |\n| 1 | 2 |"
        val table = MarkdownBlocks.parse(md).filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf(MdAlign.LEFT, MdAlign.RIGHT), table.aligns)
        assertEquals(1, table.rows.size)
    }

    @Test
    fun `table with bold header cell keeps styling`() {
        val md = "| **Mot** | Sens |\n| --- | --- |\n| rue | 街道 |"
        val table = MarkdownBlocks.parse(md).filterIsInstance<MdBlock.Table>().single()
        assertTrue(table.header[0].first().bold)
    }

    // ---------- 块级结构 ----------

    @Test
    fun `headings paragraphs lists quote code and rule`() {
        val md = listOf(
            "# Titre",
            "",
            "Un paragraphe.",
            "",
            "- premier",
            "- deuxieme",
            "",
            "1. un",
            "2. deux",
            "",
            "> citation",
            "",
            "```kotlin",
            "val x = 1",
            "```",
            "",
            "---"
        ).joinToString("\n")
        val blocks = MarkdownBlocks.parse(md)
        assertTrue(blocks.any { it is MdBlock.Heading && it.level == 1 })
        assertTrue(blocks.any { it is MdBlock.Paragraph })
        assertTrue(blocks.any { it is MdBlock.BulletList && it.items.size == 2 })
        assertTrue(blocks.any { it is MdBlock.OrderedList && it.items.size == 2 })
        assertTrue(blocks.any { it is MdBlock.Quote })
        assertTrue(blocks.any { it is MdBlock.CodeBlock })
        assertTrue(blocks.any { it is MdBlock.Rule })
    }

    @Test
    fun `ordered list keeps its start number`() {
        val blocks = MarkdownBlocks.parse("3. trois\n4. quatre")
        val list = blocks.filterIsInstance<MdBlock.OrderedList>().single()
        assertEquals(3, list.start)
        assertEquals(2, list.items.size)
    }

    @Test
    fun `paragraph soft line breaks are merged`() {
        val blocks = MarkdownBlocks.parse("ligne un\nligne deux")
        val p = blocks.filterIsInstance<MdBlock.Paragraph>().single()
        assertEquals("ligne un ligne deux", MarkdownInline.plainText(p.spans))
    }

    @Test
    fun `code fence content is not parsed as markdown`() {
        val blocks = MarkdownBlocks.parse("```\n**pas gras**\n```")
        val code = blocks.filterIsInstance<MdBlock.CodeBlock>().single()
        assertEquals(listOf("**pas gras**"), code.lines)
    }

    @Test
    fun `blank markdown yields no blocks`() {
        assertTrue(MarkdownBlocks.parse("   \n  ").isEmpty())
        assertTrue(MarkdownBlocks.parse("").isEmpty())
    }
}
