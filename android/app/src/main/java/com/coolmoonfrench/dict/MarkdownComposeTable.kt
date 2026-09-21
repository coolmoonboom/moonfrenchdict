package com.coolmoonfrench.dict

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.markdownComponents

/**
 * 用可自动换行的表格组件替换 markdown 库自带的表格。
 *
 * 库自带的 `MarkdownTable` 对单元格使用 `TextOverflow.Ellipsis`，遇到法语长句
 * （如 `Combien coûte-t-il de faire un gâteau dans la rue ?`）会在屏幕宽度不足时
 * 被省略成 `Combien coûte-t…`。这里改为内容按列宽自动折行、完整展示。
 */
internal fun markdownWithWrappingTables() = markdownComponents(
    table = { model -> MarkdownTableView(model.content) }
)

@Composable
internal fun MarkdownTableView(text: String) {
    val table = remember(text) {
        MarkdownBlocks.parse(text).filterIsInstance<MdBlock.Table>().firstOrNull()
    }
    if (table == null) {
        Text(text.trim())
        return
    }
    val colCount = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (colCount == 0) return

    val colors = MaterialTheme.colorScheme
    val border = colors.outlineVariant
    val weights = remember(table) {
        List(colCount) { c ->
            val head = table.header.getOrNull(c)?.let { MarkdownInline.plainText(it).length } ?: 0
            val body = table.rows.maxOfOrNull { row ->
                row.getOrNull(c)?.let { MarkdownInline.plainText(it).length } ?: 0
            } ?: 0
            maxOf(head, body, 4).toFloat()
        }
    }
    val rowModifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
    val linkColor = colors.primary

    Column(modifier = Modifier.fillMaxWidth().border(1.dp, border)) {
        Row(modifier = rowModifier.background(colors.surfaceVariant)) {
            for (c in 0 until colCount) {
                TableCell(
                    spans = table.header.getOrNull(c).orEmpty(),
                    align = table.aligns.getOrNull(c),
                    bold = true,
                    linkColor = linkColor,
                    modifier = Modifier.weight(weights[c])
                )
                if (c < colCount - 1) ColumnDivider(border)
            }
        }
        HorizontalDivider(color = border)
        table.rows.forEachIndexed { r, row ->
            Row(modifier = rowModifier) {
                for (c in 0 until colCount) {
                    TableCell(
                        spans = row.getOrNull(c).orEmpty(),
                        align = table.aligns.getOrNull(c),
                        bold = false,
                        linkColor = linkColor,
                        modifier = Modifier.weight(weights[c])
                    )
                    if (c < colCount - 1) ColumnDivider(border)
                }
            }
            if (r < table.rows.lastIndex) HorizontalDivider(color = border)
        }
    }
}

@Composable
private fun TableCell(
    spans: List<MdSpan>,
    align: MdAlign?,
    bold: Boolean,
    linkColor: Color,
    modifier: Modifier
) {
    Box(modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(
            text = spansToAnnotated(spans, bold, linkColor),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = align.toTextAlign(),
            softWrap = true
        )
    }
}

@Composable
private fun ColumnDivider(color: Color) {
    Box(Modifier.width(1.dp).fillMaxHeight().background(color))
}

private fun spansToAnnotated(spans: List<MdSpan>, bold: Boolean, linkColor: Color): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        withStyle(
            SpanStyle(
                fontWeight = if (s.bold || bold) FontWeight.Bold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
                fontFamily = if (s.code) FontFamily.Monospace else null,
                textDecoration = when {
                    s.strike && s.link != null ->
                        TextDecoration.combine(listOf(TextDecoration.LineThrough, TextDecoration.Underline))
                    s.strike -> TextDecoration.LineThrough
                    s.link != null -> TextDecoration.Underline
                    else -> null
                },
                color = if (s.link != null) linkColor else Color.Unspecified
            )
        ) {
            append(s.text)
        }
    }
}

private fun MdAlign?.toTextAlign(): TextAlign = when (this) {
    MdAlign.CENTER -> TextAlign.Center
    MdAlign.RIGHT -> TextAlign.End
    else -> TextAlign.Start
}
