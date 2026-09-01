package com.coolmoonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupScreen(
    repository: DictRepository,
    translator: MyMemoryTranslator,
    conjugator: VerbConjugator,
    morphology: MorphologyAnalyzer,
    settings: AppSettings,
    aiPrefs: AIPreferences
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
    var favoriteWords by remember { mutableStateOf(emptySet<String>()) }
    var ttsReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 初始化 Mimic 法语 TTS（幂等，非阻塞）；轮询等待就绪
    LaunchedEffect(Unit) {
        Espeak.ensureInitialized(context)
        while (true) {
            ttsReady = Espeak.isReady()
            if (ttsReady) break
            delay(250)
        }
    }

    // 防抖：每次输入取消上一次未完成的搜索，避免卡顿
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun doSearch(q: String) {
        query = q
        onlineResult = null
        expansion = null
        breakdown = null
        loading = false
        translateError = null
        searchJob?.cancel()
        if (q.isBlank()) {
            selected = null
            similar = emptyList()
            related = emptyList()
            derived = emptyList()
            return
        }
        searchJob = scope.launch {
            // 后台线程执行全部查询，避免阻塞 UI
            val exact = withContext(Dispatchers.IO) { repository.lookupExact(q) }
            if (exact.isNotEmpty()) {
                withContext(Dispatchers.IO) { repository.addHistory(q, settings.historyLimit) }
                val first = exact.first()
                val verb = withContext(Dispatchers.IO) { conjugator.isVerb(first.word) }
                withContext(Dispatchers.Main) {
                    selected = first
                    expansion = if (verb) "动词原形：${first.word}" else null
                }
                // 近似词/词根词/词形分析均走后台
                val sim = withContext(Dispatchers.IO) { repository.similarWords(q) }
                val rel = withContext(Dispatchers.IO) { repository.relatedWords(q) }
                val der = withContext(Dispatchers.IO) { repository.derivedWords(q) }
                val bd = withContext(Dispatchers.IO) { morphology.analyze(q, repository) }
                withContext(Dispatchers.Main) {
                    similar = sim
                    related = rel
                    derived = der
                    breakdown = bd
                }
            } else {
                val sim = withContext(Dispatchers.IO) { repository.similarWords(q) }
                val rel = withContext(Dispatchers.IO) { repository.relatedWords(q) }
                val der = withContext(Dispatchers.IO) { repository.derivedWords(q) }
                val bd = withContext(Dispatchers.IO) { morphology.analyze(q, repository) }
                val verb = withContext(Dispatchers.IO) { conjugator.isVerb(q) }
                withContext(Dispatchers.Main) {
                    selected = null
                    similar = sim
                    related = rel
                    derived = der
                    breakdown = bd
                    expansion = if (verb) "动词原形：$q" else null
                }
            }
        }
    }

    // 加载收藏列表，并恢复配置变化前已查询的内容
    LaunchedEffect(Unit) {
        favoriteWords = repository.loadFavorites().map { it.word }.toSet()
        if (query.isNotBlank()) doSearch(query)
    }

    // 无精确匹配时的前缀建议：异步加载，避免阻塞 UI
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isBlank() || selected != null) {
            prefixSuggestions = emptyList()
            return@LaunchedEffect
        }
        prefixSuggestions = withContext(Dispatchers.IO) { repository.lookupPrefix(q, 10) }
    }

    // 响应从历史查词界面点选的单词
    LaunchedEffect(pendingLookupWord) {
        val w = pendingLookupWord
        if (w != null) {
            pendingLookupWord = null
            doSearch(w)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { doSearch(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            placeholder = { Text("输入法语单词") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
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
                                            if (!ttsReady) {
                                                Espeak.ensureInitialized(context)
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "语音引擎" + (Espeak.lastError()?.let { "：$it" } ?: "正在初始化，请稍候"),
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            } else if (!Espeak.speak(entry.word)) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "朗读失败：${Espeak.lastError() ?: "未知错误"}",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
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
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
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
                                val config = aiPrefs.modelConfig
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
                                    onClick = { selected = e; query = e.word; expansion = null; onlineResult = null; breakdown = morphology.analyze(e.word, repository) },
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
                                    onClick = { selected = e; query = e.word; onlineResult = null; breakdown = morphology.analyze(e.word, repository) },
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
                                    onClick = { selected = e; query = e.word; onlineResult = null; breakdown = morphology.analyze(e.word, repository) },
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
                if (selected == null && query.isNotBlank() && similar.isEmpty() && related.isEmpty() && derived.isEmpty()) {
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
                    } else {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("未找到匹配，正在尝试联网释义…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    LaunchedEffect(query) {
                                        loading = true
                                        translateError = null
                                        try {
                                            val en = translator.translate(query, "fr|en")
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
                                                    val en = translator.translate(query, "fr|en")
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

                // 收藏按钮
                if (selected != null) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        val isFav = favoriteWords.contains(selected!!.word)
                        OutlinedButton(
                            onClick = {
                                val w = selected!!.word
                                if (isFav) {
                                    repository.removeFavorite(w)
                                    favoriteWords = repository.loadFavorites().map { it.word }.toSet()
                                } else {
                                    repository.addFavorite(w)
                                    favoriteWords = favoriteWords + w
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(if (isFav) "★ 已收藏" else "☆ 收藏")
                        }
                    }
                }
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
