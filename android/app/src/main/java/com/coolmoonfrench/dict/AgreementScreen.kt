package com.coolmoonfrench.dict

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 配合页：输入任意法语词（代词/形容词/名词/冠词等），展示该词的阴阳单复全形态，
 * 每个形态尽量带本地例句。形态表本地生成；底部「动词派生(AI)」另用大模型把动词
 * 展开成分词 / 动作名词 / 施动者 / 形容词 / 副词的词形网络。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgreementScreen(repository: DictRepository, aiPrefs: AIPreferences) {
    var query by rememberSaveable { mutableStateOf("") }
    var paradigm by remember { mutableStateOf<AgreementParadigm?>(null) }
    var examples by remember { mutableStateOf<Map<String, Pair<String, String>>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var notFound by remember { mutableStateOf(false) }
    // 动词派生（AI）：查到动词时自动预填派生输入框
    var deriveQuery by rememberSaveable { mutableStateOf("") }
    var deriveResult by remember { mutableStateOf<VerbDerivationResult?>(null) }
    var deriveLoading by remember { mutableStateOf(false) }
    var deriveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 预热法语 TTS（幂等，非阻塞）
    LaunchedEffect(Unit) {
        Speech.ensureInitialized(context)
    }

    fun runSearch(q: String) {
        query = q
        error = null
        notFound = false
        examples = emptyMap()
        if (q.isBlank()) {
            paradigm = null
            return
        }
        val curated = AgreementData.findCurated(q)
        if (curated != null) {
            paradigm = curated
            return
        }
        scope.launch {
            val entry = withContext(Dispatchers.IO) { repository.lookupExact(q).firstOrNull() }
            val result = when (AgreementData.posKind(entry)) {
                "noun" -> AgreementData.nounParadigm(q, entry)
                "adj" -> AgreementData.adjectiveParadigm(q, entry)
                "verb" -> null
                else -> {
                    // 动词或未知：尝试按名词兜底
                    if (entry != null) AgreementData.nounParadigm(q, entry) else null
                }
            }
            if (result != null) {
                // 形态无内联例句时用本地词库例句补齐
                val ex = withContext(Dispatchers.IO) {
                    result.forms.mapNotNull { f ->
                        val key = f.form.split(" ", limit = 2).lastOrNull() ?: f.form
                        VocabExamples.lookup(context, key)?.let { e -> f.form to (e.fr to e.zh) }
                    }.toMap()
                }
                paradigm = result
                examples = ex
                notFound = false
            } else {
                val kind = AgreementData.posKind(entry)
                paradigm = null
                notFound = true
                if (kind == "verb") {
                    // 动词自动预填到下方「动词派生(AI)」输入框
                    deriveQuery = q.trim()
                    deriveResult = VerbDerivation.cached(q)
                }
                error = if (kind == "verb") "本页上方只查阴阳单复数形态；动词请看下方「动词派生 (AI)」。" else "未找到该词的性数配合形态，请检查拼写。"
            }
        }
    }

    fun doDerive() {
        val v = deriveQuery.trim()
        if (v.isBlank() || deriveLoading) return
        val config = aiPrefs.modelConfig
        if (!IpaService.isConfigured(config)) {
            deriveError = "尚未配置 AI 模型，请先在 AI 设置中配置。"
            return
        }
        deriveLoading = true
        deriveError = null
        deriveResult = VerbDerivation.cached(v)
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { VerbDerivation.generate(config, v) }.getOrNull()
            }
            deriveLoading = false
            if (r == null) {
                if (deriveResult == null) deriveError = "AI 未能生成派生表，请稍后重试。"
            } else {
                deriveResult = r
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectableOutlinedTextField(
                value = query,
                onValueChange = { runSearch(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入任意法语词（celui / vieux / chat…）") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Button(onClick = { runSearch(query) }) {
                Text("查形态")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)
        ) {
            item {
                Text(
                    "阴阳单复全形态：输入任意法语词（代词、形容词、名词、冠词等），列出该词的全部性数配合形式，每个形态附本地例句。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (query.isBlank()) {
                item {
                    Text(
                        "示例：" +
                            "查「celui」→ celui（阳单）/ celle（阴单）/ ceux（阳复）/ celles（阴复）；" +
                            "查「vieux」→ vieux / vieil / vieille / vieilles；" +
                            "查「cheval」→ le cheval / les chevaux；" +
                            "查「je」或「il」→ 人称代词性数。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }

            error?.let { msg ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            paradigm?.let { p ->
                // 手工范式：直接展示全部形态
                item {
                    Text(
                        p.key,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        "${p.category}  ·  ${p.description}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                if (p.isCurated) {
                    items(p.forms) { f ->
                        FormRow(f.form, f.label, f.note, f.example, f.exampleZh)
                    }
                } else {
                    // 生成范式：本地例句补齐（无例句时只显示词形）
                    items(p.forms) { f ->
                        val ex = examples[f.form]
                        FormRow(f.form, f.label, f.note, ex?.first.orEmpty(), ex?.second.orEmpty())
                    }
                }
            }

            // ---- 动词派生（AI）：动词 → 分词 / 动作名词 / 施动者 / 形容词 / 副词 ----
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("动词派生 (AI)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "输入动词原形，AI 展开整张词形网络：现在/过去分词与复合时态、" +
                                "动作名词（含「le+动词原形」古体与现代派生名词对比）、施动者名词、" +
                                "形容词（-ant 令人… 对 过去分词 感到…）、副词（-ment）等。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SelectableOutlinedTextField(
                                value = deriveQuery,
                                onValueChange = { deriveQuery = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("法语动词原形（étudier / finir…）") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { doDerive() }, enabled = !deriveLoading) {
                                Text(if (deriveLoading) "生成中…" else "生成")
                            }
                        }
                        deriveError?.let { msg ->
                            Text(
                                msg,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            deriveResult?.let { d ->
                if (d.participePresent.isNotBlank() || d.gerondif.isNotBlank() ||
                    d.ppMasculin.isNotBlank() || d.exempleComposeFr.isNotBlank()
                ) {
                    item { DerivHead("分词与复合时态") }
                }
                if (d.participePresent.isNotBlank()) {
                    item {
                        SpeakableRow(
                            d.participePresent,
                            "现在分词（表主动/进行；可名词化指执行者：l'${d.participePresent} 正在做…的人）",
                            ""
                        )
                    }
                }
                if (d.gerondif.isNotBlank()) {
                    item { SpeakableRow(d.gerondif, "副动词（en + 现在分词，表同时/伴随动作）", "") }
                }
                if (d.ppMasculin.isNotBlank()) {
                    item {
                        SpeakableRow(
                            "le ${d.ppMasculin} · la ${d.ppFeminin} · les ${d.ppMasculinPluriel} / ${d.ppFemininPluriel}",
                            "过去分词（表被动/完成）：做形容词须与所修饰名词性数配合",
                            "助动词 ${d.auxiliaire}"
                        )
                    }
                }
                if (d.exempleComposeFr.isNotBlank()) {
                    item { SpeakableRow(d.exempleComposeFr, d.exempleComposeZh, "复合过去时") }
                }
                if (d.exemplePqpFr.isNotBlank()) {
                    item { SpeakableRow(d.exemplePqpFr, d.exemplePqpZh, "愈过去时") }
                }
                if (d.nomsAction.isNotEmpty()) {
                    item { DerivHead("动词 → 名词（动作/结果名词）") }
                    items(d.nomsAction) { e -> DerivEntryRow(e) }
                }
                if (d.nomsAgent.isNotEmpty()) {
                    item { DerivHead("动词 → 名词（施动者）") }
                    items(d.nomsAgent) { e -> DerivEntryRow(e) }
                }
                if (d.adjectifs.isNotEmpty()) {
                    item { DerivHead("动词 → 形容词") }
                    items(d.adjectifs) { e -> DerivEntryRow(e) }
                }
                if (d.adverbes.isNotEmpty()) {
                    item { DerivHead("动词 → 副词") }
                    items(d.adverbes) { e -> DerivEntryRow(e) }
                }
                if (d.notes.isNotBlank()) {
                    item {
                        Text(
                            "提示：${d.notes}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DerivHead(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

/** 法语行 + 中文行 + 可选用法标签，附朗读按钮。 */
@Composable
private fun SpeakableRow(fr: String, zh: String, note: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fr,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (note.isNotEmpty()) {
                    Text(
                        note,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                IconButton(onClick = {
                    Speech.ensureInitialized(context)
                    Speech.speakWithFeedback(context, fr.replace('/', ' ').trim())
                }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "朗读 $fr",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (zh.isNotEmpty()) {
                Text(
                    zh,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DerivEntryRow(e: DerivativeEntry) {
    val zh = if (e.note.isNotBlank()) "${e.zh}  ·  ${e.note}" else e.zh
    SpeakableRow(e.form, zh.ifBlank { e.note }, e.label)
}

@Composable
private fun FormRow(form: String, label: String, note: String, exampleFr: String, exampleZh: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    form,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                val ipa = FrenchIpa.wrap(form.split(" ").lastOrNull().orEmpty())
                if (ipa.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(ipa, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    label,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                if (note.isNotEmpty()) {
                    Text(
                        note,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        Speech.ensureInitialized(context)
                        Speech.speakWithFeedback(context, form.split(" ").lastOrNull().orEmpty())
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "朗读 $form",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (exampleFr.isNotEmpty()) {
                Text(
                    exampleFr,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (exampleZh.isNotEmpty()) {
                Text(
                    exampleZh,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}