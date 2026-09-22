package com.coolmoonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SUBJECTS = listOf("je", "tu", "il/elle", "nous", "vous", "ils/elles")
private val PRONOM_SUBJECTS = listOf("je me", "tu te", "il se", "nous nous", "vous vous", "ils se")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConjugationScreen(
    conjugator: VerbConjugator,
    repository: DictRepository,
    translator: MyMemoryTranslator,
    morphology: MorphologyAnalyzer,
    aiPrefs: AIPreferences
) {
    var query by rememberSaveable { mutableStateOf("") }
    var conj by remember { mutableStateOf<Conjugation?>(null) }
    var passive by remember { mutableStateOf<Conjugation?>(null) }
    var pronominalConj by remember { mutableStateOf<Conjugation?>(null) }
    var breakdown by remember { mutableStateOf<WordBreakdown?>(null) }
    var meaning by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var foundInfinitive by remember { mutableStateOf<String?>(null) }
    var aiInfo by remember { mutableStateOf<AiWordInfo?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var chineseMode by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<VerbCandidate>>(emptyList()) }
    var coreMeaning by remember { mutableStateOf("") }
    var candError by remember { mutableStateOf<String?>(null) }
    var selectedFromCandidates by remember { mutableStateOf(false) }
    var lastChineseQuery by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 预热法语 TTS（幂等，非阻塞）
    LaunchedEffect(Unit) {
        Speech.ensureInitialized(context)
    }

    // 预热中文动词候选索引（幂等，后台执行）
    LaunchedEffect(Unit) {
        repository.prewarmChineseVerbIndex()
    }

    /** 用大模型查询单词的中文释义与音标；带取消，保证新输入能立即打断旧请求。 */
    fun runAiWordSearch(word: String) {
        val config = aiPrefs.modelConfig
        if (!IpaService.isConfigured(config)) {
            aiError = "未配置 AI 模型，无法在线查询"
            return
        }
        searchJob?.cancel()
        aiLoading = true
        aiError = null
        searchJob = scope.launch {
            try {
                aiInfo = AiWordSearch.search(config, word)
                if (aiInfo == null) aiError = "未查询到该词的释义"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aiError = "查询失败：${e.message?.take(100) ?: "未知错误"}"
            } finally {
                aiLoading = false
            }
        }
    }

    fun doSearch(q: String) {
        query = q
        // 取消上一轮在线查询，避免旧请求返回后覆盖新结果
        searchJob?.cancel()
        searchJob = null
        loading = false
        aiInfo = null
        aiError = null
        aiLoading = false
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

    /** 中文查询：防抖 300ms 后生成候选，新输入会取消旧请求。 */
    fun scheduleChineseSearch(q: String) {
        searchJob?.cancel()
        searchJob = null
        loading = false
        aiLoading = false
        aiInfo = null
        aiError = null
        conj = null; passive = null; pronominalConj = null
        breakdown = null; meaning = ""; error = null; foundInfinitive = null
        candidates = emptyList()
        coreMeaning = ""
        candError = null
        if (q.isBlank()) return
        loading = true
        searchJob = scope.launch {
            try {
                delay(300)
                val res = ChineseVerbSearch.find(
                    query = q,
                    repository = repository,
                    conjugator = conjugator,
                    config = aiPrefs.modelConfig,
                    onLocalReady = { local ->
                        // 本地候选先行展示，AI 结果稍后合并刷新
                        candidates = local
                        if (local.isNotEmpty()) loading = false
                    }
                )
                candidates = res.candidates
                coreMeaning = res.coreMeaning
                candError = res.aiError
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                candError = "查询失败：${e.message?.take(100) ?: "未知错误"}"
            } finally {
                loading = false
            }
        }
    }

    /** 点选候选：改为展示该不定式的完整变位。 */
    fun selectCandidate(infinitive: String) {
        searchJob?.cancel()
        searchJob = null
        selectedFromCandidates = true
        chineseMode = false
        query = infinitive
        doSearch(infinitive)
    }

    /** 从变位结果返回候选列表：直接复用已有候选，避免重新加载。 */
    fun backToCandidates() {
        searchJob?.cancel()
        searchJob = null
        selectedFromCandidates = false
        chineseMode = true
        query = lastChineseQuery
        if (candidates.isEmpty()) scheduleChineseSearch(lastChineseQuery)
    }

    /** 统一输入入口：中文走候选模式，其余走现有法语查询路径。 */
    fun onQueryChange(q: String) {
        query = q
        if (hasChinese(q)) {
            chineseMode = true
            selectedFromCandidates = false
            lastChineseQuery = q
            scheduleChineseSearch(q)
        } else {
            chineseMode = false
            selectedFromCandidates = false
            lastChineseQuery = ""
            candidates = emptyList()
            coreMeaning = ""
            candError = null
            doSearch(q)
        }
    }

    // 恢复配置变化前已查询的动词
    LaunchedEffect(Unit) {
        if (query.isNotBlank()) onQueryChange(query)
    }

    // 返回手势拦截：变位详情回候选列表，候选态清空查询回初始态，避免直接退出应用
    BackHandler(enabled = chineseMode || selectedFromCandidates) {
        if (selectedFromCandidates) {
            backToCandidates()
        } else {
            onQueryChange("")
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
                onValueChange = { onQueryChange(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入法语动词（原形或变体）或中文含义") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Button(onClick = { onQueryChange(query) }) {
                Text("变位")
            }
        }

        if (chineseMode && !selectedFromCandidates) {
            ChineseCandidatePanel(
                query = query,
                loading = loading,
                candidates = candidates,
                coreMeaning = coreMeaning,
                error = candError,
                aiConfigured = IpaService.isConfigured(aiPrefs.modelConfig),
                onSelect = { selectCandidate(it) },
                onRetry = { onQueryChange(query) }
            )
            return@Column
        }

        if (error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { runAiWordSearch(query) },
                        enabled = !aiLoading
                    ) {
                        Text(if (aiLoading) "AI 查询中…" else "AI 查词")
                    }
                    aiInfo?.let { info ->
                        Spacer(Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { Espeak.speakWithFeedback(context, info.word, deterministic = true) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "朗读 ${info.word}",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(info.word, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    if (info.ipa.isNotBlank()) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(info.ipa, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("AI 释义", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(info.meaning, fontSize = 14.sp)
                            }
                        }
                    }
                    if (aiError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(aiError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
            return@Column
        }

        conj?.let { c ->
            // 词典无中文释义时用 AI 补全（生僻动词）
            LaunchedEffect(c.infinitive) {
                aiInfo = null
                aiError = null
                if (!hasChinese(meaning)) {
                    runAiWordSearch(c.infinitive)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (selectedFromCandidates) {
                    item {
                        TextButton(
                            onClick = { backToCandidates() },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text("‹ 返回候选")
                        }
                    }
                }
                // 动词卡片
                item {
                    val displayForm = if (foundInfinitive != null) query.trim() else c.infinitive
                    val displayIpa = FrenchIpa.wrap(displayForm)
                    val infinitiveIpa = FrenchIpa.wrap(c.infinitive)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        Speech.ensureInitialized(context)
                                        Speech.speakWithFeedback(context, displayForm)
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "朗读 $displayForm",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(displayForm, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    if (displayIpa.isNotEmpty()) {
                                        Text("音标 $displayIpa", fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (foundInfinitive != null) {
                                        Text(
                                            "原形：${c.infinitive}" + if (infinitiveIpa.isNotEmpty()) "  $infinitiveIpa" else "",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    when {
                                        meaning.isNotEmpty() -> Text(meaning, fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                        aiLoading -> Text("中文释义查询中……", fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                        aiInfo != null -> Text(
                                            "AI：${aiInfo!!.meaning}" + if (aiInfo!!.ipa.isNotBlank()) "  ${aiInfo!!.ipa}" else "",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                        aiError != null -> Text(aiError!!, fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
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

                // 句型用法
                VerbUsages.patternsOf(c.infinitive).takeIf { it.isNotEmpty() }?.let { patterns ->
                    item {
                        VerbUsageCard(patterns, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp))
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

                // 语态 / 所有发音切换
                item {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("主动", "被动", "代动词", "所有发音").forEachIndexed { i, label ->
                            FilterChip(
                                selected = selectedTab == i,
                                onClick = { selectedTab = i },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // 根据选中 Tab 显示对应内容
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
                    3 -> { // 所有发音
                        AllPronunciations(c, context)
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
    item { TenseRow("虚拟式现在时", "Subjonctif présent", c.subjonctifPresent, context = context) }
    item { TenseRow("虚拟式未完成过去时", "Subjonctif imparfait", c.subjonctifImparfait, context = context) }
    item { TenseRow("命令式", "Impératif", c.imperatif, context = context) }

    item { Spacer(Modifier.height(4.dp)) }
    item {
        Text("复合时态", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    }
    item { TenseRow("复合过去时", "Passé composé", compoundPresent(c), context = context) }
    item { TenseRow("愈过去时", "Plus-que-parfait", compoundImparfait(c), context = context) }
    item { TenseRow("先过去时", "Passé antérieur", compoundPasseSimple(c), context = context) }
    item { TenseRow("先将来时", "Futur antérieur", compoundFutur(c), context = context) }
    item { TenseRow("条件式过去时", "Conditionnel passé", compoundConditionnel(c), context = context) }
    item { TenseRow("虚拟式过去时", "Subjonctif passé", compoundSubjonctif(c), context = context) }
    item { TenseRow("虚拟式愈过去时", "Subjonctif plus-que-parfait", compoundSubjonctifImparfait(c), context = context) }
}

private fun LazyListScope.PassiveTenses(pc: Conjugation, context: Context) {
    item { TenseRow("直陈式现在时", "Présent", pc.present, context = context) }
    item { TenseRow("未完成过去时", "Imparfait", pc.imparfait, context = context) }
    item { TenseRow("简单将来时", "Futur simple", pc.futurSimple, context = context) }
    item { TenseRow("简单过去时", "Passé simple", pc.passeSimple, context = context) }
    item { TenseRow("条件式现在时", "Conditionnel", pc.conditionnel, context = context) }
    item { TenseRow("虚拟式现在时", "Subjonctif présent", pc.subjonctifPresent, context = context) }
    item { TenseRow("虚拟式未完成过去时", "Subjonctif imparfait", pc.subjonctifImparfait, context = context) }
    item { TenseRow("命令式", "Impératif", pc.imperatif, context = context) }

    item { Spacer(Modifier.height(4.dp)) }
    item {
        Text("复合时态（被动）", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    }
    item { TenseRow("复合过去时", "Passé composé", compoundPassive(pc), context = context) }
    item { TenseRow("愈过去时", "Plus-que-parfait", compoundImparfaitPassive(pc), context = context) }
    item { TenseRow("先过去时", "Passé antérieur", compoundPasseSimplePassive(pc), context = context) }
    item { TenseRow("先将来时", "Futur antérieur", compoundFuturPassive(pc), context = context) }
    item { TenseRow("条件式过去时", "Conditionnel passé", compoundConditionnelPassive(pc), context = context) }
}

private fun LazyListScope.PronominalTenses(pc: Conjugation, context: Context) {
    item { TenseRow("直陈式现在时", "Présent", pc.present, PRONOM_SUBJECTS, context) }
    item { TenseRow("未完成过去时", "Imparfait", pc.imparfait, PRONOM_SUBJECTS, context) }
    item { TenseRow("简单将来时", "Futur simple", pc.futurSimple, PRONOM_SUBJECTS, context) }
    item { TenseRow("简单过去时", "Passé simple", pc.passeSimple, PRONOM_SUBJECTS, context) }
    item { TenseRow("条件式现在时", "Conditionnel", pc.conditionnel, PRONOM_SUBJECTS, context) }
    item { TenseRow("虚拟式现在时", "Subjonctif présent", pc.subjonctifPresent, PRONOM_SUBJECTS, context) }
    item { TenseRow("虚拟式未完成过去时", "Subjonctif imparfait", pc.subjonctifImparfait, PRONOM_SUBJECTS, context) }
    item { TenseRow("命令式", "Impératif", pc.imperatif, context = context) }

    item { Spacer(Modifier.height(4.dp)) }
    item {
        Text("复合时态（代动词）", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
    }
    item { TenseRow("复合过去时", "Passé composé", compoundPresent(pc), PRONOM_SUBJECTS, context) }
    item { TenseRow("愈过去时", "Plus-que-parfait", compoundImparfait(pc), PRONOM_SUBJECTS, context) }
    item { TenseRow("先过去时", "Passé antérieur", compoundPasseSimple(pc), PRONOM_SUBJECTS, context) }
    item { TenseRow("先将来时", "Futur antérieur", compoundFutur(pc), PRONOM_SUBJECTS, context) }
    item { TenseRow("条件式过去时", "Conditionnel passé", compoundConditionnel(pc), PRONOM_SUBJECTS, context) }
    item { TenseRow("虚拟式过去时", "Subjonctif passé", compoundSubjonctif(pc), PRONOM_SUBJECTS, context) }
    item { TenseRow("虚拟式愈过去时", "Subjonctif plus-que-parfait", compoundSubjonctifImparfait(pc), PRONOM_SUBJECTS, context) }
}

// ---------------------------------------------------------------------------
// 所有发音（第四页）
// ---------------------------------------------------------------------------

@Composable
private fun PronGroupRow(group: PronGroup, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { Espeak.speakWithFeedback(context, group.forms.first(), deterministic = true) }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "朗读 ${group.forms.first()}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("/${group.ipa}/", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(3.dp))
                Text(
                    group.forms.joinToString("、"),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        Espeak.speakWithFeedback(context, group.forms.first(), deterministic = true)
                    }
                )
            }
        }
    }
}

private fun LazyListScope.AllPronunciations(c: Conjugation, context: Context) {
    item {
        SectionTitle("所有发音（按读音分组）")
    }
    item {
        Text(
            "同一行的拼写形式读音相同。点击 🔊 可朗读该组读音（本地法语语音）。",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
    }
    val groups = Pronunciations.groupsOf(c)
    if (groups.isEmpty()) {
        item {
            Text("暂无可发音的单词形式", modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        items(groups) { g -> PronGroupRow(g, context) }
    }
}

// 复合时态生成函数
internal fun compoundPresent(c: Conjugation): List<String> {
    val aux = if (c.auxiliary == "être") VerbConjugator.êtreConj.present else VerbConjugator.avoirConj.present
    return aux.map { "$it ${c.participePasse}" }
}
internal fun compoundImparfait(c: Conjugation): List<String> {
    val aux = if (c.auxiliary == "être") VerbConjugator.êtreConj.imparfait else VerbConjugator.avoirConj.imparfait
    return aux.map { "$it ${c.participePasse}" }
}
internal fun compoundFutur(c: Conjugation): List<String> {
    val aux = if (c.auxiliary == "être") VerbConjugator.êtreConj.futurSimple else VerbConjugator.avoirConj.futurSimple
    return aux.map { "$it ${c.participePasse}" }
}
internal fun compoundConditionnel(c: Conjugation): List<String> {
    val aux = if (c.auxiliary == "être") VerbConjugator.êtreConj.conditionnel else VerbConjugator.avoirConj.conditionnel
    return aux.map { "$it ${c.participePasse}" }
}
internal fun compoundSubjonctif(c: Conjugation): List<String> {
    val aux = if (c.auxiliary == "être") VerbConjugator.êtreConj.subjonctifPresent else VerbConjugator.avoirConj.subjonctifPresent
    return aux.map { "$it ${c.participePasse}" }
}
internal fun compoundPasseSimple(c: Conjugation): List<String> {
    val aux = if (c.auxiliary == "être") VerbConjugator.êtreConj.passeSimple else VerbConjugator.avoirConj.passeSimple
    return aux.map { "$it ${c.participePasse}" }
}
internal fun compoundSubjonctifImparfait(c: Conjugation): List<String> {
    val aux = if (c.auxiliary == "être") VerbConjugator.êtreConj.subjonctifImparfait else VerbConjugator.avoirConj.subjonctifImparfait
    return aux.map { "$it ${c.participePasse}" }
}
internal fun compoundPassive(pc: Conjugation): List<String> {
    return VerbConjugator.avoirConj.present.map { "$it été ${pc.participePasse}" }
}
internal fun compoundImparfaitPassive(pc: Conjugation): List<String> {
    return VerbConjugator.avoirConj.imparfait.map { "$it été ${pc.participePasse}" }
}
internal fun compoundFuturPassive(pc: Conjugation): List<String> {
    return VerbConjugator.avoirConj.futurSimple.map { "$it été ${pc.participePasse}" }
}
internal fun compoundConditionnelPassive(pc: Conjugation): List<String> {
    return VerbConjugator.avoirConj.conditionnel.map { "$it été ${pc.participePasse}" }
}
internal fun compoundPasseSimplePassive(pc: Conjugation): List<String> {
    return VerbConjugator.avoirConj.passeSimple.map { "$it été ${pc.participePasse}" }
}

/** 中文查询候选面板：加载 / 空结果 / 候选列表三态。 */
@Composable
private fun ChineseCandidatePanel(
    query: String,
    loading: Boolean,
    candidates: List<VerbCandidate>,
    coreMeaning: String,
    error: String?,
    aiConfigured: Boolean,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit
) {
    when {
        loading && candidates.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在匹配候选动词…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        candidates.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "未找到与「$query」匹配的法语动词",
                    textAlign = TextAlign.Center
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                if (!aiConfigured) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "配置 AI 模型可获得更准确的中文候选",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("重试") }
            }
        }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        "「$query」的候选动词",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (coreMeaning.isNotBlank()) {
                        Text(
                            "识别为核心动词：$coreMeaning",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (error != null) {
                        Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            items(candidates) { c ->
                VerbCandidateCard(c, onSelect)
            }
        }
    }
}

/** 单个候选动词卡片：不定式 + IPA + 词性 + 中文释义 + 例句，点击查看变位。 */
@Composable
private fun VerbCandidateCard(c: VerbCandidate, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onSelect(c.infinitive) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    Speech.ensureInitialized(context)
                    Speech.speakWithFeedback(context, c.infinitive)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "朗读 ${c.infinitive}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.infinitive, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (c.ipa.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(c.ipa, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    if (c.pos.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                c.pos,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                if (c.meaning.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(c.meaning, fontSize = 14.sp)
                }
                if (c.example.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(c.example, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("变位 ›", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
