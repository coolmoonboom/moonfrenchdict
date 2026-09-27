package com.coolmoonfrench.dict

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 判断释义是否包含中文 */
internal fun hasChinese(s: String): Boolean = s.any { it in '\u4e00'..'\u9fff' }

/**
 * 查词前归一化输入：把不间断空格/全角空格/零宽字符换成普通空格，
 * 去掉首尾空白并合并连续空格。避免「单词后多打一个空格」导致精确匹配失败、
 * 进而丢失词性/释义/词根拆解等内容。
 */
internal fun normalizeLookupQuery(raw: String): String =
    raw.replace('\u00A0', ' ')
        .replace('\u3000', ' ')
        .replace('\u200B', ' ')
        .trim()
        .replace(Regex("\\s+"), " ")

/** 省音/缩合形式：如 d'eau = de + eau，l'application = le/la + application */
internal data class Contraction(val surface: String, val prefix: String, val base: String)

private val CONTRACTION_PREFIXES = mapOf(
    "d" to "de", "l" to "le/la", "j" to "je", "m" to "me", "n" to "ne",
    "t" to "tu", "s" to "se", "c" to "ce", "qu" to "que",
    "jusqu" to "jusque", "lorsqu" to "lorsque", "quoiqu" to "quoique", "presqu" to "presque"
)

/** 识别冠词/介词与后词的省音缩合，如 d'eau、l'application、qu'il */
internal fun detectContraction(text: String): Contraction? {
    val t = text.trim()
    val idx = t.indexOfFirst { it == '\'' || it == '’' }
    if (idx <= 0 || idx >= t.length - 1) return null
    val prefix = t.substring(0, idx).lowercase()
    val base = t.substring(idx + 1).trim()
    val full = CONTRACTION_PREFIXES[prefix] ?: return null
    if (base.isEmpty()) return null
    return Contraction(t, full, base)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupScreen(
    repository: DictRepository,
    translator: MyMemoryTranslator,
    conjugator: VerbConjugator,
    morphology: MorphologyAnalyzer,
    settings: AppSettings,
    aiPrefs: AIPreferences,
    active: Boolean = false
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<DictEntry?>(null) }
    var similar by remember { mutableStateOf<List<DictEntry>>(emptyList()) }
    var related by remember { mutableStateOf<List<DictEntry>>(emptyList()) }
    var derived by remember { mutableStateOf<List<DictEntry>>(emptyList()) }
    var prefixSuggestions by remember { mutableStateOf<List<DictEntry>>(emptyList()) }
    var onlineResult by remember { mutableStateOf<MyMemoryTranslator.TranslateResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var translateError by remember { mutableStateOf<String?>(null) }
    var expansion by remember { mutableStateOf<String?>(null) }
    var breakdown by remember { mutableStateOf<WordBreakdown?>(null) }
    // 中文输入：翻译成法语后的结果与状态
    var zhToFr by remember { mutableStateOf<MyMemoryTranslator.TranslateResult?>(null) }
    var zhTranslating by remember { mutableStateOf(false) }
    var zhError by remember { mutableStateOf<String?>(null) }
    // 实际用于法语查询的词（中文输入时为翻译结果，法语输入时即输入本身）
    var frenchTerm by remember { mutableStateOf("") }
    // 省音缩合信息（如 d'eau → de + eau）
    var contractionSurface by remember { mutableStateOf<String?>(null) }
    var contractionPrefix by remember { mutableStateOf("") }
    var contractionBase by remember { mutableStateOf("") }
    var favoriteWords by remember { mutableStateOf(emptySet<String>()) }
    var showBatchAiSettings by remember { mutableStateOf(false) }
    // AI 查询：手动点「AI 查询」按钮，或本地词库未命中时自动触发；
    // 走批量识别管线，能容错残缺/错拼写法并还原词头，产出结构化多词条结果。
    var aiLookup by remember { mutableStateOf<List<ImportedWord>?>(null) }
    var aiLookupLoading by remember { mutableStateOf(false) }
    var aiLookupError by remember { mutableStateOf<String?>(null) }
    var aiLookupAuto by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 预热 Mimic 法语 TTS（幂等，非阻塞）
    LaunchedEffect(Unit) {
        Speech.ensureInitialized(context)
    }

    // 防抖：每次输入取消上一次未完成的搜索，避免卡顿
    var searchJob by remember { mutableStateOf<Job?>(null) }

    /** 用已确定是法语的词执行原有查询流程（精确匹配 / 缩合 / 近似 / 词根 / 派生 / 拆解） */
    suspend fun searchFrench(raw: String) {
        val fr = normalizeLookupQuery(raw)
        if (fr.isEmpty()) return
        val exact = withContext(Dispatchers.IO) { repository.lookupExact(fr) }
        val contraction = if (exact.isEmpty()) detectContraction(fr) else null
        if (exact.isNotEmpty()) {
            withContext(Dispatchers.IO) { repository.addHistory(fr) }
            val first = exact.first()
            val verb = withContext(Dispatchers.IO) { conjugator.isVerb(first.word) }
            // 近似词/词根词/词形分析均走后台
            val sim = withContext(Dispatchers.IO) { repository.similarWords(fr) }
            val rel = withContext(Dispatchers.IO) { repository.relatedWords(fr) }
            val der = withContext(Dispatchers.IO) { repository.derivedWords(fr) }
            val bd = withContext(Dispatchers.IO) { morphology.analyze(fr, repository) }
            withContext(Dispatchers.Main) {
                selected = first
                contractionSurface = null
                expansion = if (verb) "动词原形：${first.word}" else null
                similar = sim
                related = rel
                derived = der
                breakdown = bd
            }
        } else if (contraction != null) {
            // 缩合形式：解析出后词并查询，同时保留缩合形式用于音标（含连诵 ‿）
            val baseExact = withContext(Dispatchers.IO) { repository.lookupExact(contraction.base) }
            val first = baseExact.firstOrNull()
            val verb = first?.let { withContext(Dispatchers.IO) { conjugator.isVerb(it.word) } } ?: false
            val sim = withContext(Dispatchers.IO) { repository.similarWords(contraction.base) }
            val rel = withContext(Dispatchers.IO) { repository.relatedWords(contraction.base) }
            val der = withContext(Dispatchers.IO) { repository.derivedWords(contraction.base) }
            val bd = withContext(Dispatchers.IO) { morphology.analyze(contraction.base, repository) }
            withContext(Dispatchers.Main) {
                selected = first
                contractionSurface = contraction.surface
                contractionPrefix = contraction.prefix
                contractionBase = contraction.base
                expansion = if (verb) "动词原形：${first.word}" else null
                similar = sim
                related = rel
                derived = der
                breakdown = bd
            }
        } else {
            val sim = withContext(Dispatchers.IO) { repository.similarWords(fr) }
            val rel = withContext(Dispatchers.IO) { repository.relatedWords(fr) }
            val der = withContext(Dispatchers.IO) { repository.derivedWords(fr) }
            val bd = withContext(Dispatchers.IO) { morphology.analyze(fr, repository) }
            val verb = withContext(Dispatchers.IO) { conjugator.isVerb(fr) }
            withContext(Dispatchers.Main) {
                selected = null
                contractionSurface = null
                similar = sim
                related = rel
                derived = der
                breakdown = bd
                expansion = if (verb) "动词原形：$fr" else null
            }
        }
    }

    fun doSearch(raw: String) {
        query = raw
        val q = normalizeLookupQuery(raw)
        onlineResult = null
        expansion = null
        breakdown = null
        contractionSurface = null
        contractionPrefix = ""
        contractionBase = ""
        aiLookup = null
        aiLookupLoading = false
        aiLookupError = null
        loading = false
        translateError = null
        zhError = null
        searchJob?.cancel()
        if (q.isBlank()) {
            selected = null
            similar = emptyList()
            related = emptyList()
            derived = emptyList()
            prefixSuggestions = emptyList()
            frenchTerm = ""
            zhToFr = null
            zhTranslating = false
            return
        }
        if (hasChinese(q)) {
            // 中文输入：先翻译成法语，再按原有流程查询法语结果
            frenchTerm = ""
            zhToFr = null
            zhTranslating = true
            searchJob = scope.launch {
                val res = TranslationAssist.zhToFr(q, aiPrefs, translator)
                val fr = res?.translatedText?.trim()
                withContext(Dispatchers.Main) {
                    zhTranslating = false
                    zhToFr = res
                    frenchTerm = fr.orEmpty()
                    if (res == null) zhError = "中文翻译法语失败，请检查网络或 AI 配置"
                }
                if (!fr.isNullOrBlank()) searchFrench(fr)
            }
        } else {
            zhToFr = null
            zhTranslating = false
            frenchTerm = q
            searchJob = scope.launch { searchFrench(q) }
        }
    }

    // 加载收藏列表，并恢复配置变化前已查询的内容
    LaunchedEffect(Unit) {
        favoriteWords = repository.loadFavorites().map { it.word }.toSet()
        if (query.isNotBlank()) doSearch(query)
    }

    // 无精确匹配时的前缀建议：异步加载，避免阻塞 UI
    LaunchedEffect(frenchTerm) {
        val q = frenchTerm.trim()
        if (q.isBlank() || hasChinese(q) || selected != null) {
            prefixSuggestions = emptyList()
            return@LaunchedEffect
        }
        prefixSuggestions = withContext(Dispatchers.IO) { repository.lookupPrefix(q, 10) }
    }

    // 本地词库未命中时自动切入 AI 查询（含拼写容错与词头还原），替代旧的简版查词
    LaunchedEffect(frenchTerm, selected, contractionSurface) {
        aiLookup = null
        aiLookupError = null
        aiLookupLoading = false
        if (selected != null || contractionSurface != null) return@LaunchedEffect
        val term = frenchTerm.trim()
        val config = aiPrefs.effectiveBatchConfig
        if (term.isEmpty() || hasChinese(term) || !IpaService.isConfigured(config)) return@LaunchedEffect
        delay(500)
        if (selected != null) return@LaunchedEffect
        aiLookupAuto = true
        aiLookupLoading = true
        val res = withContext(Dispatchers.IO) {
            runCatching { ImportWordParser.recognize(config, term) }.getOrElse { emptyList() }
        }
        aiLookupLoading = false
        if (res.isEmpty()) aiLookupError = "AI 未能识别出「$term」的有效词条" else aiLookup = res
    }

    // 响应从历史查词界面点选的单词
    LaunchedEffect(pendingLookupWord) {
        val w = pendingLookupWord
        if (w != null) {
            pendingLookupWord = null
            doSearch(w)
        }
    }

    // 收藏查重：与库内所有键做归一比较（变音符/大小写/空白差异都视为同一个词）
    fun isFavored(word: String): Boolean {
        val key = FavoriteMeaning.normalizeWordKey(word)
        return favoriteWords.any { it == word || FavoriteMeaning.normalizeWordKey(it) == key }
    }

    // AI 结果收藏：已收藏（哪怕旧内容是英文）直接用 AI 结果覆盖替换，缺例句的也借此补齐
    fun replaceFavoriteWithFeedback(word: String, meaning: String) {
        val existed = isFavored(word)
        val norm = FavoriteMeaning.normalizeWordKey(word)
        if (existed) {
            // 清掉可能的孪生旧键，只保留 AI 词头一条
            favoriteWords.filter { FavoriteMeaning.normalizeWordKey(it) == norm }
                .forEach { repository.removeFavorite(it) }
        }
        repository.addFavorite(word, meaning)
        favoriteWords = favoriteWords + word
        Toast.makeText(
            context,
            if (existed) "已替换原收藏内容" else "加入收藏成功",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun addFavoriteWithFeedback(word: String, meaning: String) {
        if (isFavored(word)) {
            Toast.makeText(context, "已经收藏过了", Toast.LENGTH_SHORT).show()
        } else {
            repository.addFavorite(word, meaning)
            favoriteWords = favoriteWords + word
            Toast.makeText(context, "加入收藏成功", Toast.LENGTH_SHORT).show()
        }
    }

    // 手动点「AI 查询」：无视本地命中，直接把当前输入交给 AI 识别查询
    fun runAiLookup() {
        val term = frenchTerm.trim()
        if (term.isEmpty()) return
        if (aiLookupLoading) return
        val config = aiPrefs.effectiveBatchConfig
        if (!IpaService.isConfigured(config)) {
            Toast.makeText(context, "请先在 AI 设置中配置模型", Toast.LENGTH_SHORT).show()
            return
        }
        // 输入为大批量法语词（>50 个）：查词界面原地转接批量导入流程
        if (ImportWordParser.countImportableFrenchWords(term) > 50) {
            aiLookupAuto = false
            aiLookup = null
            aiLookupError = null
            aiLookupLoading = true
            scope.launch {
                val n = runBatchImport(config, term, repository)
                aiLookupLoading = false
                Toast.makeText(
                    context,
                    if (n > 0) "已批量识别并导入 $n 个单词到「收藏-单词」" else "批量导入未成功，请稍后重试",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        aiLookupAuto = false
        aiLookup = null
        aiLookupError = null
        aiLookupLoading = true
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { ImportWordParser.recognize(config, term) }.getOrElse { emptyList() }
            }
            aiLookupLoading = false
            if (res.isEmpty()) aiLookupError = "AI 未能识别出「$term」的有效词条" else aiLookup = res
        }
    }

    if (showBatchAiSettings) {
        AISettingsScreen(prefs = aiPrefs, onBack = { showBatchAiSettings = false }, batch = true)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索栏：输入框 + 导入按钮（进入界面自动聚焦并弹出输入法）
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectableOutlinedTextField(
                value = query,
                onValueChange = { doSearch(it) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                placeholder = { Text("输入法语单词或中文") },
                singleLine = true,
                requestFocusKey = if (active) true else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showBatchAiSettings = true })
                    }
            ) {
                OutlinedButton(onClick = { runAiLookup() }) {
                    Text("AI 查询")
                }
            }
        }
        Text(
            "批量导入请转到并长按「收藏-单词」",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
        )

        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                // 中文 → 法语翻译结果（仅中文输入时出现）
                if (zhTranslating) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在翻译为法语…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                } else if (zhToFr != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("中文 → 法语", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "来源: ${zhToFr!!.source}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            Speech.ensureInitialized(context)
                                            Speech.speakWithFeedback(context, zhToFr!!.translatedText)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "朗读法语译文",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    zhToFr!!.translatedText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(Modifier.height(2.dp))
                                IpaLine(
                                    target = zhToFr!!.translatedText,
                                    aiPrefs = aiPrefs,
                                    textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else if (zhError != null) {
                    item {
                        Text(
                            zhError!!,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }

                // 缩合形式（如 d'eau → de + eau）：展示缩合词形与含连诵的音标
                if (contractionSurface != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            Speech.ensureInitialized(context)
                                            Speech.speakWithFeedback(context, contractionSurface!!)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "朗读缩合形式",
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        contractionSurface!!,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                IpaLine(
                                    target = contractionSurface!!,
                                    aiPrefs = aiPrefs,
                                    textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "缩合：$contractionPrefix + $contractionBase",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // 无对应词条时的输入音标（法语输入）；AI 词条卡已包含音标时不再重复展示
                if (selected == null && zhToFr == null && contractionSurface == null &&
                    aiLookup == null && !aiLookupLoading &&
                    frenchTerm.isNotBlank() && !hasChinese(frenchTerm)
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            Speech.ensureInitialized(context)
                                            Speech.speakWithFeedback(context, frenchTerm)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "朗读输入词",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        frenchTerm,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                IpaLine(
                                    target = frenchTerm,
                                    aiPrefs = aiPrefs,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                // AI 查询结果（手动触发或本地未命中自动触发）：结构化多词条卡
                if (aiLookupLoading && selected == null && frenchTerm.isNotBlank() &&
                    !hasChinese(frenchTerm) && contractionSurface == null
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "正在获取内容…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                if (aiLookupError != null && aiLookup == null && selected == null &&
                    frenchTerm.isNotBlank() && !hasChinese(frenchTerm) && contractionSurface == null
                ) {
                    item {
                        Text(
                            aiLookupError!!,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                aiLookup?.let { words ->
                    item {
                        Text(
                            if (aiLookupAuto) "本地词库未收录 · AI 查询：" else "AI 查询结果：",
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    itemsIndexed(words, key = { i, _ -> i }) { _, w ->
                        AiLookupCard(
                            word = w,
                            context = context,
                            aiPrefs = aiPrefs,
                            favored = isFavored(w.word),
                            onFavorite = { replaceFavoriteWithFeedback(w.word, ImportWordParser.buildMeaning(w)) }
                        )
                    }
                }

                // 主词条
                if (selected != null) {
                    val entry = selected!!
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            Speech.ensureInitialized(context)
                                        Speech.speakWithFeedback(context, entry.word)
                                        }
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "朗读",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Text(
                                        text = entry.word,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    // 收藏星标：紧挨最大的词；未收藏为描边主题色，已收藏为黄色实心
                                    val starFav = isFavored(entry.word)
                                    IconButton(
                                        onClick = {
                                            if (starFav) {
                                                repository.removeFavorite(entry.word)
                                                val norm = FavoriteMeaning.normalizeWordKey(entry.word)
                                                favoriteWords = favoriteWords.filter {
                                                    FavoriteMeaning.normalizeWordKey(it) != norm
                                                }.toSet()
                                                Toast.makeText(context, "已取消收藏", Toast.LENGTH_SHORT).show()
                                            } else {
                                                addFavoriteWithFeedback(entry.word, entry.meaning)
                                            }
                                        },
                                        modifier = Modifier.size(60.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (starFav) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            contentDescription = if (starFav) "取消收藏" else "收藏",
                                            tint = if (starFav) Color(0xFFFFC107) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (entry.pos.isNotEmpty()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                entry.pos,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                IpaLine(
                                    target = entry.word,
                                    aiPrefs = aiPrefs,
                                    textColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = entry.meaning,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (expansion != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        expansion!!,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    CopyButton(entry.word, context)
                                }
                            }
                        }
                    }

                    // 自动翻译兜底（本地无中文时）：优先 AI，未配置回退 MyMemory
                    item {
                        LaunchedEffect(entry.word) {
                            if (onlineResult == null && !hasChinese(entry.meaning)) {
                                val config = aiPrefs.effectiveBatchConfig
                                val aiConfigured = config.apiUrl.isNotBlank() &&
                                    config.apiToken.isNotBlank() && config.modelName.isNotBlank()
                                val result = withContext(Dispatchers.IO) {
                                    if (aiConfigured) {
                                        val prompt = "请将法语单词或短语翻译为简洁准确的中文释义。" +
                                            "仅输出中文翻译结果，不要添加解释或原文。" +
                                            "单词：${entry.word}" +
                                            if (entry.en.isNotBlank()) "\n英文参考释义：${entry.en}" else ""
                                        val reply = try {
                                            AIClient.chat(
                                                config,
                                                listOf(AIMessage("user", prompt))
                                            )
                                        } catch (_: Exception) { null }
                                        if (reply != null && reply.isNotBlank()) {
                                            MyMemoryTranslator.TranslateResult(
                                                translatedText = reply.trim(),
                                                source = "AI(${config.modelName})"
                                            )
                                        } else null
                                    } else null
                                } ?: withContext(Dispatchers.IO) {
                                    val en = translator.translate(entry.word, "fr|en")
                                    if (en != null) {
                                        val zh = translator.translate(en.translatedText, "en|zh-CN")
                                        zh
                                    } else null
                                }
                                if (onlineResult == null && result != null) {
                                    onlineResult = result
                                }
                            }
                        }
                    }

                    if (onlineResult != null) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("中文释义", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "来源: ${onlineResult!!.source} (联网)",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(onlineResult!!.translatedText, fontSize = 16.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "释义来自联网翻译，仅供参考；可将其添加到本地词条。",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    } else if (loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                Text("翻译中…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                    } else if (translateError != null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    translateError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // 词根拆解
                    breakdown?.let { bd ->
                        if (!bd.isEmpty) {
                            item {
                                Text(
                                    "词根拆解",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 3.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        for (p in bd.parts) {
                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                                Text(
                                                    text = when (p.kind) {
                                                        MorphKind.PREFIX -> "前缀"
                                                        MorphKind.ROOT -> "词根"
                                                        MorphKind.SUFFIX -> "后缀"
                                                    } + " · ${p.text}",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.width(140.dp)
                                                )
                                                Text(
                                                    p.meaning,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 在线翻译按钮（手动刷新）
                    item {
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                loading = true
                                translateError = null
                                scope.launch {
                                    try {
                                        val en = translator.translate(entry.word, "fr|en")
                                        if (en != null) {
                                            onlineResult = translator.translate(en.translatedText, "en|zh-CN")
                                        }
                                        if (onlineResult == null) {
                                            translateError = "联网释义失败，请检查网络后重试"
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        translateError = "联网释义失败：${e.message ?: "未知错误"}"
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            enabled = !loading
                        ) {
                            Text(if (loading) "翻译中…" else "重新联网释义")
                        }
                    }
                }

                // 疑似变体 / 相近单词
                if (similar.isNotEmpty()) {
                    item {
                        Text(
                            "疑似变体 / 相近单词",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(similar) { e ->
                                Card(
                                    onClick = { selected = e; query = e.word; frenchTerm = e.word; zhToFr = null; expansion = null; onlineResult = null; breakdown = morphology.analyze(e.word, repository) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(e.word, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                            if (e.pos.isNotEmpty()) {
                                                Spacer(Modifier.width(4.dp))
                                                Text(e.pos, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                            }
                                        }
                                        Text(
                                            e.meaning.take(18),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 同词根词
                if (related.isNotEmpty()) {
                    item {
                        Text(
                            "同词根词",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(related) { e ->
                                Card(
                                    onClick = { selected = e; query = e.word; frenchTerm = e.word; zhToFr = null; onlineResult = null; breakdown = morphology.analyze(e.word, repository) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(e.word, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                            if (e.pos.isNotEmpty()) {
                                                Spacer(Modifier.width(4.dp))
                                                Text(e.pos, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                            }
                                        }
                                        Text(
                                            e.meaning.take(18),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 派生词
                if (derived.isNotEmpty()) {
                    item {
                        Text(
                            "派生词",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(derived) { e ->
                                Card(
                                    onClick = { selected = e; query = e.word; frenchTerm = e.word; zhToFr = null; onlineResult = null; breakdown = morphology.analyze(e.word, repository) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(e.word, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                            if (e.pos.isNotEmpty()) {
                                                Spacer(Modifier.width(4.dp))
                                                Text(e.pos, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                            }
                                        }
                                        Text(
                                            e.meaning.take(18),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 无精确匹配时的建议列表
                if (selected == null && frenchTerm.isNotBlank() && similar.isEmpty() && related.isEmpty() && derived.isEmpty()) {
                    if (prefixSuggestions.isNotEmpty()) {
                        item {
                            Text(
                                "建议",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        items(prefixSuggestions) { entry ->
                            Card(
                                onClick = {
                                    selected = entry
                                    query = entry.word
                                    frenchTerm = entry.word
                                    zhToFr = null
                                    onlineResult = null
                                    searchJob?.cancel()
                                    searchJob = scope.launch {
                                        val sim = withContext(Dispatchers.IO) { repository.similarWords(entry.word) }
                                        val rel = withContext(Dispatchers.IO) { repository.relatedWords(entry.word) }
                                        val der = withContext(Dispatchers.IO) { repository.derivedWords(entry.word) }
                                        val bd = withContext(Dispatchers.IO) { morphology.analyze(entry.word, repository) }
                                        withContext(Dispatchers.Main) {
                                            similar = sim
                                            related = rel
                                            derived = der
                                            breakdown = bd
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(entry.word, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                    Text(
                                        entry.meaning.take(40),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    } else if (aiLookup == null && !aiLookupLoading && aiLookupError == null) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("未找到匹配，正在尝试联网释义…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    LaunchedEffect(frenchTerm) {
                                        loading = true
                                        translateError = null
                                        try {
                                            val en = translator.translate(frenchTerm, "fr|en")
                                            if (en != null) {
                                                onlineResult = translator.translate(en.translatedText, "en|zh-CN")
                                            }
                                            if (onlineResult == null) {
                                                translateError = "联网释义失败，请检查网络后重试"
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            translateError = "联网释义失败：${e.message ?: "未知错误"}"
                                        } finally {
                                            loading = false
                                        }
                                    }
                                    if (loading) {
                                        Text("翻译中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (translateError != null) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            translateError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 13.sp
                                        )
                                    }
                                    if (onlineResult != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Card(modifier = Modifier.padding(horizontal = 24.dp)) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text("联网释义", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "来源: ${onlineResult!!.source} (联网)",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    onlineResult!!.translatedText,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Button(onClick = {
                                            loading = true
                                            translateError = null
                                            scope.launch {
                                                try {
                                                    val en = translator.translate(frenchTerm, "fr|en")
                                                    if (en != null) {
                                                        onlineResult = translator.translate(en.translatedText, "en|zh-CN")
                                                    }
                                                    if (onlineResult == null) {
                                                        translateError = "联网释义失败，请检查网络后重试"
                                                    }
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    translateError = "联网释义失败：${e.message ?: "未知错误"}"
                                                } finally {
                                                    loading = false
                                                }
                                            }
                                        }) {
                                            Text("重新翻译")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * AI 查询产出的词条卡：词头 + 朗读 + 词性徽章 + 音标 + 中文释义 + 例句/翻译 + 收藏与复制。
 * 收藏按钮带查重：已收藏提示「已经收藏过了」，未收藏写入后提示「加入收藏成功」。
 */
@Composable
private fun AiLookupCard(
    word: ImportedWord,
    context: Context,
    aiPrefs: AIPreferences,
    favored: Boolean,
    onFavorite: () -> Unit
) {
    val pos = word.pos.ifBlank { DictEntry.extractPos(word.meaning) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        Speech.ensureInitialized(context)
                        Speech.speakWithFeedback(context, word.word)
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "朗读",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = word.word,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                if (pos.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            pos,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            IpaLine(
                target = word.word,
                aiPrefs = aiPrefs,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                fontSize = 15.sp,
                override = word.ipa
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = word.meaning,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (word.example.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = word.example,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )
                if (word.exampleZh.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = word.exampleZh,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (favored) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (favored) "已收藏" else "收藏",
                    tint = if (favored) Color(0xFFFFC107) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onFavorite() }
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "释义来源：AI",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                CopyButton(word.word, context)
            }
        }
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
