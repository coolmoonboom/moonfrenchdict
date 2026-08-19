package com.moonfrench.dict

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerbGroupScreen(conjugator: VerbConjugator) {
    var selectedFamily by remember { mutableStateOf<String?>(null) }

    SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
        item {
            Text(
                "法语动词参考分组",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "法语动词按词尾与变位规律分为三组。第三组为不规则动词，可按词族记忆——同一词族的动词变位规律相同。",
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
        }

        // 第一组
        item {
            GroupCard(
                title = "第一组动词 (-er)",
                subtitle = "最庞大的组，绝大多数法语动词。词干 + 固定词尾即可变位，仅少数词干有拼写变化（-ger/-cer 及 è/é、双写）。",
                patterns = VerbGroups.firstGroupPatterns
            )
        }

        // 第二组
        item {
            GroupCard(
                title = "第二组动词 (-ir)",
                subtitle = "以 -ir 结尾、现在分词以 -issant 结尾的动词。词干 + -iss- 扩展 + 固定词尾。",
                patterns = VerbGroups.secondGroupPatterns
            )
        }

        // 第三组
        item {
            Text(
                "第三组动词（不规则）",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "最复杂的一组，多为高频动词，需逐一记忆。点击词族可查看全部动词。",
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(6.dp))
        }

        // 第三组词族列表
        items(VerbGroups.thirdGroupFamilies) { family ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            family.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            family.example,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        family.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    for ((v, meaning) in family.verbs) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                v,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                modifier = Modifier.width(140.dp)
                            )
                            Text(
                                meaning,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            // 查看变位按钮
                            TextButton(onClick = { selectedFamily = v }) {
                                Text("变位", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // 变位弹窗
    selectedFamily?.let { verb ->
        val c = conjugator.conjugate(verb)
        AlertDialog(
            onDismissRequest = { selectedFamily = null },
            title = { Text(verb) },
            text = {
                if (c == null) {
                    Text("无法生成变位")
                } else {
                    LazyColumn(modifier = Modifier.height(420.dp)) {
                        item { Text("现在分词：${c.participePresent}", fontSize = 13.sp) }
                        item { Text("过去分词：${c.participePasse}", fontSize = 13.sp) }
                        item { Text("副动词：${c.gerondif}", fontSize = 13.sp) }
                        item { Text("复合不定式：${c.infinitifPasse}", fontSize = 13.sp) }
                        item { Spacer(Modifier.height(6.dp)) }
                        item { SectionTitleInDialog("直陈式现在时") }
                        item { TenseLines(c.present) }
                        item { SectionTitleInDialog("未完成过去时") }
                        item { TenseLines(c.imparfait) }
                        item { SectionTitleInDialog("简单将来时") }
                        item { TenseLines(c.futurSimple) }
                        item { SectionTitleInDialog("虚拟式现在时") }
                        item { TenseLines(c.subjonctifPresent) }
                        item { SectionTitleInDialog("命令式") }
                        item { TenseLines(c.imperatif) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFamily = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun GroupCard(
    title: String,
    subtitle: String,
    patterns: List<VerbGroups.TensePattern>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            for (pattern in patterns) {
                Text(
                    pattern.tense,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(2.dp))
                for (rowForms in pattern.forms.chunked(3)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        for ((person, ending) in rowForms) {
                            Row(modifier = Modifier.weight(1f)) {
                                Text("$person ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Text(ending, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        repeat(3 - rowForms.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SectionTitleInDialog(title: String) {
    Text(
        title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun TenseLines(forms: List<String>) {
    val subjects = listOf("je", "tu", "il/elle/on", "nous", "vous", "ils/elles")
    for (i in 0 until 6) {
        if (i < forms.size && forms[i].isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(subjects[i], color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    modifier = Modifier.width(110.dp))
                Text(forms[i], fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
