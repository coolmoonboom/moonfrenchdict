package com.moonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val TENSE_LABELS = listOf(
    "直陈式现在时" to "Présent",
    "未完成过去时" to "Imparfait",
    "简单将来时" to "Futur simple",
    "简单过去时" to "Passé simple",
    "条件式现在时" to "Conditionnel",
    "虚拟式现在时" to "Subjonctif",
    "命令式" to "Impératif",
    "复合过去时" to "Passé composé",
    "愈过去时" to "Plus-que-parfait",
    "先将来时" to "Futur antérieur",
    "条件式过去时" to "Conditionnel passé",
    "虚拟式过去时" to "Subjonctif passé"
)

private val SUBJECTS = listOf("je", "tu", "il/elle", "nous", "vous", "ils/elles")
private val PRONOM_SUBJECTS = listOf("je me", "tu te", "il se", "nous nous", "vous vous", "ils se")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConjugationScreen(
    conjugator: VerbConjugator,
    repository: DictRepository,
    translator: MyMemoryTranslator,
    morphology: MorphologyAnalyzer
) {
    var query by remember { mutableStateOf("") }
    var conj by remember { mutableStateOf<Conjugation?>(null) }
    var passive by remember { mutableStateOf<Conjugation?>(null) }
    var pronominalConj by remember { mutableStateOf<Conjugation?>(null) }
    var breakdown by remember { mutableStateOf<WordBreakdown?>(null) }
    var meaning by remember { mutableStateOf("") }
    var onlineMeaning by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var foundInfinitive by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun doSearch(q: String) {
        query = q
        onlineMeaning = null
        if (q.isBlank()) {
            conj = null; passive = null; pronominalConj = null
            breakdown = null; meaning = ""; error = null; foundInfinitive = null
            return
        }
        // 先尝试直接变位（输入即原形）
        var c = conjugator.conjugate(q)
        var infinitive = q
        if (c == null) {
            // 反向查找：任意变体 → 原形
            val found = conjugator.findInfinitive(q)
            if (found != null) {
                infinitive = found
                foundInfinitive = found
                c = conjugator.conjugate(found)
            }
        } else {
            foundInfinitive = null
        }

        if (c != null) {
            conj = c
            passive = if (c.infinitive == "être") null else conjugator.passive(c)
            val isPronominalInput = infinitive.trim().lowercase().startsWith("se ") || infinitive.trim().lowercase().startsWith("s'")
            pronominalConj = if (isPronominalInput) c else conjugator.pronominal(c)
            breakdown = morphology.analyze(infinitive, repository)
            meaning = repository.lookupExact(c.infinitive).firstOrNull()?.meaning ?: ""
            error = null
        } else {
            conj = null; passive = null; pronominalConj = null
            breakdown = null; meaning = ""
            foundInfinitive = null
            error = "未找到该动词的变位，请检查拼写"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { doSearch(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入法语动词（原形或变体）") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Button(onClick = { /* doSearch 已在 onValueChange 触发 */ }) {
                Text("变位")
            }
        }

        if (error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        loading = true
                        scope.launch {
                            onlineMeaning = translator.translate(query)?.translatedText
                            loading = false
                        }
                    }) {
                        Text(if (loading) "翻译中…" else "联网查词")
                    }
                    onlineMeaning?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it)
                    }
                }
            }
            return@Column
        }

        conj?.let { c ->
            // 自动补中文释义
            LaunchedEffect(c.infinitive) {
                if (onlineMeaning == null && meaning.isNotEmpty() && !hasChinese(meaning)) {
                    onlineMeaning = translator.translate(c.infinitive)?.translatedText
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // 动词卡片
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(c.infinitive, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    if (foundInfinitive != null) {
                                        Text("→ 原形：${c.infinitive}", fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    if (meaning.isNotEmpty()) {
                                        Text(meaning, fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    }
                                    onlineMeaning?.let {
                                        Text("中文（联网）：$it", fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text("第${c.group}组", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("助动词：${c.auxiliary}", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                                    Text("过去分词：${c.participePasse}", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("过去分词需与主语性数配合", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
                        }
                    }
                }

                // 单词拆解
                breakdown?.let { bd ->
                    if (!bd.isEmpty) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("构词拆解", fontWeight = FontWeight.Medium, fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        for (p in bd.parts) {
                                            Surface(
                                                shape = MaterialTheme.shapes.small,
                                                color = when (p.kind) {
                                                    MorphKind.PREFIX -> MaterialTheme.colorScheme.primaryContainer
                                                    MorphKind.ROOT -> MaterialTheme.colorScheme.tertiaryContainer
                                                    MorphKind.SUFFIX -> MaterialTheme.colorScheme.secondaryContainer
                                                }
                                            ) {
                                                Text(p.text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                            Text(p.meaning, fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 非人称形式
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("动词词族形式", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            NonFiniteRow("现在分词", "Participe présent", c.participePresent, "作形容词或动名词，表正在进行的动作", context)
                            NonFiniteRow("副动词", "Gérondif", "en ${c.participePresent}", "表同时进行的方式/条件", context)
                            NonFiniteRow("复合不定式", "Infinitif passé", c.infinitifPasse, "表已完成动作的不定式", context)
                            NonFiniteRow("副动词过去式", "Gérondif passé", c.gerondifPasse, "表已完成的伴随动作", context)
                        }
                    }
                }

                // 三按钮切换
                item {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("主动语态", "被动语态", "代动词语态").forEachIndexed { i, label ->
                            FilterChip(
                                selected = selectedTab == i,
                                onClick = { selectedTab = i },
                                label = { Text(label, fontSize = 13.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // 根据选中 Tab 显示对应时态
                when (selectedTab) {
                    0 -> { // 主动语态
                        item { SectionTitle("主动语态") }
                        ActiveTenses(c, context)
                    }
                    1 -> { // 被动语态
                        passive?.let { pc ->
                            item { SectionTitle("被动语态（être + 过去分词）") }
                            PassiveTenses(pc, context)
                        } ?: item {
                            Text("该动词无法构成被动语态（être 自身）", modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    2 -> { // 代动词语态
                        pronominalConj?.let { pc ->
                            item { SectionTitle("代动词语态（se + 动词）") }
                            PronominalTenses(pc, context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun NonFiniteRow(label: String, french: String, value: String, desc: String, context: Context) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("$label $french", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(value, fontSize = 14.sp)
            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        CopyButton(value, context)
    }
}

@Composable
private fun CopyButton(text: String, context: Context) {
    IconButton(onClick = {
        val clip = ClipData.newPlainText("text", text)
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }) {
        Text("复制", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TenseRow(tenseCn: String, tenseFr: String, forms: List<String>, subjects: List<String> = SUBJECTS, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$tenseCn $tenseFr", fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                CopyButton(forms.joinToString("\n"), context)
            }
            Spacer(Modifier.height(4.dp))
            val allEmpty = forms.all { it.isBlank() }
            if (allEmpty) {
                Text("无人称动词：仅使用第三人称单数（il）形式", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (i in 0 until 6) {
                if (i < forms.size && forms[i].isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text(
                            subjects.getOrElse(i) { subjects.last() },
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                            modifier = Modifier.width(90.dp)
                        )
                        Text(forms[i], fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (forms.size > 6 && forms[6].isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("无人称形式：${forms[6]}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun LazyListScope.ActiveTenses(c: Conjugation, context: Context) {
    item { TenseRow("直陈式现在时", "Présent", c.present, context = context) }
    item { TenseRow("未完成过去时", "Imparfait", c.imparfait, context = context) }
    item { TenseRow("简单将来时", "Futur simple", c.futurSimple, context = context) }
    item { TenseRow("简单过去时", "Passé simple", c.passeSimple, context = context) }
    item { TenseRow("条件式现在时", "Conditionnel", c.conditionnel, context = context) }
    item { TenseRow("虚拟式现在时", "Subjonctif", c.subjonctifPresent, context = context) }
    item { TenseRow("命令式", "Impératif", c.imperatif, context = context) }

    item { Spacer(Modifier.height(4.dp)) }
    item {
        Text("复合时态", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    }
    item { TenseRow("复合过去时", "Passé composé", compoundPresent(c), context = context) }
    item { TenseRow("愈过去时", "Plus-que-parfait", compoundImparfait(c), context = context) }
    item { TenseRow("先将来时", "Futur antérieur", compoundFutur(c), context = context) }
    item { TenseRow("条件式过去时", "Conditionnel passé", compoundConditionnel(c), context = context) }
    item { TenseRow("虚拟式过去时", "Subjonctif passé", compoundSubjonctif(c), context = context) }
}

private fun LazyListScope.PassiveTenses(pc: Conjugation, context: Context) {
    item { TenseRow("直陈式现在时", "Présent", pc.present, context = context) }
    item { TenseRow("未完成过去时", "Imparfait", pc.imparfait, context = context) }
    item { TenseRow("简单将来时", "Futur simple", pc.futurSimple, context = context) }
    item { TenseRow("简单过去时", "Passé simple", pc.passeSimple, context = context) }
    item { TenseRow("条件式现在时", "Conditionnel", pc.conditionnel, context = context) }
    item { TenseRow("虚拟式现在时", "Subjonctif", pc.subjonctifPresent, context = context) }
    item { TenseRow("命令式", "Impératif", pc.imperatif, context = context) }

    item { Spacer(Modifier.height(4.dp)) }
    item {
        Text("复合时态（被动）", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    }
    item { TenseRow("复合过去时", "Passé composé", compoundPassive(pc), context = context) }
    item { TenseRow("愈过去时", "Plus-que-parfait", compoundImparfaitPassive(pc), context = context) }
    item { TenseRow("先将来时", "Futur antérieur", compoundFuturPassive(pc), context = context) }
    item { TenseRow("条件式过去时", "Conditionnel passé", compoundConditionnelPassive(pc), context = context) }
}

private fun LazyListScope.PronominalTenses(pc: Conjugation, context: Context) {
    item { TenseRow("直陈式现在时", "Présent", pc.present, PRONOM_SUBJECTS, context) }
    item { TenseRow("未完成过去时", "Imparfait", pc.imparfait, PRONOM_SUBJECTS, context) }
    item { TenseRow("简单将来时", "Futur simple", pc.futurSimple, PRONOM_SUBJECTS, context) }
    item { TenseRow("简单过去时", "Passé simple", pc.passeSimple, PRONOM_SUBJECTS, context) }
    item { TenseRow("条件式现在时", "Conditionnel", pc.conditionnel, PRONOM_SUBJECTS, context) }
    item { TenseRow("虚拟式现在时", "Subjonctif", pc.subjonctifPresent, PRONOM_SUBJECTS, context) }
    item { TenseRow("命令式", "Impératif", pc.imperatif, context = context) }

    item { Spacer(Modifier.height(4.dp)) }
    item {
        Text("复合时态（代动词）", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    }
    item { TenseRow("复合过去时", "Passé composé", compoundPresent(pc), PRONOM_SUBJECTS, context) }
    item { TenseRow("愈过去时", "Plus-que-parfait", compoundImparfait(pc), PRONOM_SUBJECTS, context) }
    item { TenseRow("先将来时", "Futur antérieur", compoundFutur(pc), PRONOM_SUBJECTS, context) }
    item { TenseRow("条件式过去时", "Conditionnel passé", compoundConditionnel(pc), PRONOM_SUBJECTS, context) }
    item { TenseRow("虚拟式过去时", "Subjonctif passé", compoundSubjonctif(pc), PRONOM_SUBJECTS, context) }
}

// 复合时态生成函数
private fun compoundPresent(c: Conjugation): List<String> {
    val aux = if (c.auxiliary == "être") VerbConjugator.êtreConj.present else VerbConjugator.avoirConj.present
    return aux.map { "$it ${c.participePasse}" }
}
private fun compoundImparfait(c: Conjugation): List<String> {
    val aux = if (c.auxiliary == "être") VerbConjugator.êtreConj.imparfait else VerbConjugator.avoirConj.imparfait
    return aux.map { "$it ${c.participePasse}" }
}
private fun compoundFutur(c: Conjugation): List<String> {
    val aux = if (c.auxiliary == "être") VerbConjugator.êtreConj.futurSimple else VerbConjugator.avoirConj.futurSimple
    return aux.map { "$it ${c.participePasse}" }
}
private fun compoundConditionnel(c: Conjugation): List<String> {
    val aux = if (c.auxiliary == "être") VerbConjugator.êtreConj.conditionnel else VerbConjugator.avoirConj.conditionnel
    return aux.map { "$it ${c.participePasse}" }
}
private fun compoundSubjonctif(c: Conjugation): List<String> {
    val aux = if (c.auxiliary == "être") VerbConjugator.êtreConj.subjonctifPresent else VerbConjugator.avoirConj.subjonctifPresent
    return aux.map { "$it ${c.participePasse}" }
}
private fun compoundPassive(pc: Conjugation): List<String> {
    return VerbConjugator.avoirConj.present.map { "$it été ${pc.participePasse}" }
}
private fun compoundImparfaitPassive(pc: Conjugation): List<String> {
    return VerbConjugator.avoirConj.imparfait.map { "$it été ${pc.participePasse}" }
}
private fun compoundFuturPassive(pc: Conjugation): List<String> {
    return VerbConjugator.avoirConj.futurSimple.map { "$it été ${pc.participePasse}" }
}
private fun compoundConditionnelPassive(pc: Conjugation): List<String> {
    return VerbConjugator.avoirConj.conditionnel.map { "$it été ${pc.participePasse}" }
}