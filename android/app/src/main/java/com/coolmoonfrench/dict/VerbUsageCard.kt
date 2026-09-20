package com.coolmoonfrench.dict

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 动词用法卡片：句型（~高亮接续介词~、**宾语占位**）+ 中文说明 + 例句。
 * 在变位页与动词分组页弹窗中复用。
 */
@Composable
fun VerbUsageCard(patterns: List<UsagePattern>, modifier: Modifier = Modifier) {
    if (patterns.isEmpty()) return
    val highlight = MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("句型用法", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            for (p in patterns) {
                Text(
                    renderPattern(p.pattern, highlight),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (p.note.isNotBlank()) {
                    Text(p.note, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for (ex in p.examples) {
                    Text("• ${ex.fr}", fontSize = 12.sp)
                    Text("　${ex.zh}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 把句型字符串渲染为带高亮/加粗的 AnnotatedString。
 *  ~..~ ：高亮接续介词（à/de/que 等），主题色加粗；
 *  **..** ：宾语/名词占位，常规加粗。 */
private fun renderPattern(pattern: String, highlight: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < pattern.length) {
            val tilde = pattern.indexOf('~', i)
            val bold = pattern.indexOf("**", i)
            when {
                tilde == -1 && bold == -1 -> {
                    append(pattern.substring(i))
                    break
                }
                bold == -1 || (tilde != -1 && tilde < bold) -> {
                    append(pattern.substring(i, tilde))
                    val end = pattern.indexOf('~', tilde + 1)
                    if (end == -1) {
                        append(pattern.substring(tilde))
                        break
                    }
                    withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) {
                        append(pattern.substring(tilde + 1, end))
                    }
                    i = end + 1
                }
                else -> {
                    append(pattern.substring(i, bold))
                    val end = pattern.indexOf("**", bold + 2)
                    if (end == -1) {
                        append(pattern.substring(bold))
                        break
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(pattern.substring(bold + 2, end))
                    }
                    i = end + 2
                }
            }
        }
    }