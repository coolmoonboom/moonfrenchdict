package com.coolmoonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** AI 逐词解释结果 */
data class AIWordMeaning(
    val word: String,
    val pos: String,
    val meaning: String
)

/** 构造 AI 逐词解释的提示词，要求返回 JSON 数组 */
private fun buildAIAnalysisPrompt(sentence: String): String {
    return """
请分析下面法语句子中的每个词，返回一个 JSON 数组。数组每个元素对象包含三个字段：
- "word": 该词的原文词形（原形）
- "pos": 词性（如 nom、verbe、adjectif、préposition、adverbe、pronom、conjonction、article 等）
- "meaning": 中文释义（简洁，1-2 句）

要求：
1. 只返回 JSON 数组本身，不要用 Markdown 代码块包裹，不要有任何额外文字。
2. 覆盖句子中的所有实词和虚词，包括省音缩写（如 j' 拆成 je）。
3. meaning 用中文给出准确释义。

句子：$sentence
""".trimIndent()
}

/** 解析 AI 返回的逐词 JSON 数组（容错：剥离代码块、取第一个 [...]） */
private fun parseAIWords(reply: String): List<AIWordMeaning>? {
    val cleaned = reply.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```").trim()
    val start = cleaned.indexOf('[')
    val end = cleaned.lastIndexOf(']')
    if (start < 0 || end <= start) return null
    return try {
        val arr = JSONArray(cleaned.substring(start, end + 1))
        val list = mutableListOf<AIWordMeaning>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                AIWordMeaning(
                    word = o.optString("word", ""),
                    pos = o.optString("pos", ""),
                    meaning = o.optString("meaning", "")
                )
            )
        }
        list.filter { it.word.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentenceScreen(
    repository: DictRepository,
    translator: MyMemoryTranslator,
    conjugator: VerbConjugator,
    analyzer: SentenceAnalyzer,
    aiPrefs: AIPreferences? = null
) {
    var sentence by rememberSaveable { mutableStateOf("") }
    var analysis by remember { mutableStateOf<List<WordAnalysis>?>(null) }
    var sentenceTranslation by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var analyzeError by remember { mutableStateOf<String?>(null) }
    var favorited by remember { mutableStateOf(false) }
    var aiWords by remember { mutableStateOf<List<AIWordMeaning>?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 预热 Mimic 法语 TTS（幂等，非阻塞）
    LaunchedEffect(Unit) {
        Espeak.ensureInitialized(context)
    }

    fun doAnalyze(s: String) {
        sentence = s
        if (s.isBlank()) {
            analysis = null
            sentenceTranslation = null
            aiWords = null
            aiError = null
            analyzeError = null
            analyzing = false
            return
        }
        aiWords = null
        aiError = null
        analyzeError = null
        analyzing = true
        scope.launch {
            try {
                val result = analyzer.analyze(s)
                analysis = result
                val trans = translator.translate(s)
                sentenceTranslation = trans?.translatedText
                if (trans == null) {
                    analyzeError = "整句翻译失败，请检查网络后重试"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                analyzeError = "分析失败：${e.message?.take(120) ?: "未知错误"}"
            } finally {
                analyzing = false
            }
        }
    }

    /** AI 逐词解释：复用首页 AI（模型配置来自 AIPreferences） */
    fun doAIAnalyze(s: String) {
        if (s.isBlank()) {
            aiError = "请先输入要解释的法语句子"
            return
        }
        val prefs = aiPrefs ?: return
        val config = prefs.modelConfig
        if (config.apiUrl.isBlank() || config.apiToken.isBlank() || config.modelName.isBlank()) {
            aiError = "请先在 AI 设置中配置大模型"
            return
        }
        if (aiLoading) return
        aiLoading = true
        aiError = null
        scope.launch {
            try {
                val prompt = buildAIAnalysisPrompt(s)
                val reply = AIClient.chat(config, listOf(AIMessage("user", prompt)))
                aiWords = parseAIWords(reply)
                if (aiWords.isNullOrEmpty()) {
                    aiError = "AI 未返回有效的逐词结果"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aiError = "AI 分析失败：${e.message?.take(120) ?: "未知错误"}"
            } finally {
                aiLoading = false
            }
        }
    }

    LaunchedEffect(sentence) {
        favorited = aiPrefs?.isSentenceFavorite(sentence) ?: false
    }

    // 恢复配置变化前已分析的句子
    LaunchedEffect(Unit) {
        if (sentence.isNotBlank()) doAnalyze(sentence)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = sentence,
            onValueChange = { sentence = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            placeholder = { Text("输入法语句子") },
            maxLines = 3
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { doAnalyze(sentence) },
                modifier = Modifier.weight(1f),
                enabled = !analyzing
            ) {
                Text(if (analyzing) "分析中…" else "翻译并分析")
            }
            OutlinedButton(
                onClick = { doAIAnalyze(sentence) },
                modifier = Modifier.weight(1f),
                enabled = !aiLoading
            ) {
                Text(if (aiLoading) "AI 分析中…" else "AI 逐词解释")
            }
            if (sentence.isNotBlank()) {
                IconButton(
                    onClick = {
                        Espeak.ensureInitialized(context)
                                        Espeak.speakWithFeedback(context, sentence)
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "朗读整句",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            OutlinedButton(
                onClick = { doAnalyze("J'ai mangé une pomme dans la cuisine") },
                modifier = Modifier.weight(1f)
            ) {
                Text("示例")
            }
        }

        if (analyzing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val result = analysis
        if (result == null && aiWords == null && aiError == null && analyzeError == null && !aiLoading) return@Column

        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
            // 分析错误提示
            if (analyzeError != null) {
                item {
                    Text(
                        analyzeError!!,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
            // 整句翻译
            item {
                if (sentenceTranslation != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("整句翻译", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.width(8.dp))
                                Text("来源: MyMemory (联网)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                Spacer(Modifier.weight(1f))
                                if (aiPrefs != null) {
                                    IconButton(onClick = {
                                        if (favorited) {
                                            aiPrefs.removeSentenceFavorite(sentence)
                                            favorited = false
                                        } else {
                                            aiPrefs.addSentenceFavorite(sentence, sentenceTranslation ?: "")
                                            favorited = true
                                        }
                                    }) {
                                        Icon(
                                            if (favorited) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            contentDescription = "收藏句子",
                                            tint = if (favorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(sentenceTranslation!!, fontSize = 16.sp)
                        }
                    }
                }
            }
            // AI 逐词解释
            if (aiLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("AI 正在逐词解释…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (aiError != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            aiError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { aiError = null }) { Text("关闭", fontSize = 12.sp) }
                    }
                }
            }
            if (aiWords != null && aiWords!!.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AI 逐词解释", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("来源: 大模型", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
                items(aiWords!!) { aw ->
                    AIWordCard(aw)
                }
            }

            // 词性统计
            if (result != null) {
                item {
                    val counts = result.groupBy { it.pos }
                        .mapValues { it.value.size }
                        .filter { it.value > 0 }
                        .entries
                        .sortedByDescending { it.value }
                        .joinToString(" ") { "${it.key}${it.value}" }
                    Text(
                        "共${result.size}个词 ${if (counts.isNotEmpty()) "· $counts" else ""}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            // 逐词分析卡片
            if (result != null) {
                items(result) { wa ->
                    WordCard(wa, repository, conjugator, context)
                }
            }
            }
        }
    }
}

@Composable
private fun AIWordCard(aw: AIWordMeaning) {
    val cardContext = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        Espeak.ensureInitialized(cardContext)
                        Espeak.speakWithFeedback(cardContext, aw.word)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "朗读 ${aw.word}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(aw.word, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                if (aw.pos.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            aw.pos,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            if (aw.meaning.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    aw.meaning,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun WordCard(
    wa: WordAnalysis,
    repository: DictRepository,
    conjugator: VerbConjugator,
    context: Context
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 第一行：词形 + 词性 + 成分
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        Espeak.ensureInitialized(context)
                                        Espeak.speakWithFeedback(context, wa.surface)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "朗读 ${wa.surface}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    wa.surface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                if (wa.pos.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            wa.pos,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                if (wa.role.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            wa.role,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 11.sp
                        )
                    }
                }
                CopyButton(wa.surface, context)
            }

            // 缩写拆解
            if (wa.expansion.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${wa.surface}（缩写，展开为 ${wa.expansion}）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            // 原形 + 释义
            if (wa.infinitive.isNotEmpty() || wa.meaning.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "原形 ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (wa.infinitive.isNotEmpty()) {
                        Text(wa.infinitive, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Text(
                        wa.meaning,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 动词信息
            if (wa.tense.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "时态: ${wa.tense} · 人称: ${wa.person.ifEmpty { "—" }}",
                    fontSize = 12.sp
                )
            }
            if (wa.voice.isNotEmpty()) {
                Text(
                    "语态: ${wa.voice} · 助动词: ${wa.auxiliary.ifEmpty { "—" }}",
                    fontSize = 12.sp
                )
            }
            if (wa.notes.isNotEmpty()) {
                Text(wa.notes, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }

            if (wa.number.isNotEmpty()) {
                Text(
                    "数: ${wa.number}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            // 本地词库未收录提示
            if (!wa.inDictionary && wa.infinitive.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "本地词库未收录，可尝试在线查询…",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            // 同根词族 + 查词按钮
            val related = remember(wa.word) { repository.relatedWords(wa.word).take(4) }
            if (related.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "同根词族：${related.joinToString(" / ") { "${it.word} ${it.meaning.take(12)}" }}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 更多信息折叠
            if (expanded) {
                val similar = repository.similarWords(wa.word).take(5)
                if (similar.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "近义词/相近词：${similar.joinToString(" / ") { it.word }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                val conjug = if (wa.infinitive.isNotEmpty()) conjugator.conjugate(wa.infinitive) else null
                if (conjug != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "动词变位（直陈现在）：${conjug.present.take(3).joinToString(" / ")}…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            // 快捷查词
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (expanded) "收起" else "展开详情 / 查词", fontSize = 12.sp)
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
