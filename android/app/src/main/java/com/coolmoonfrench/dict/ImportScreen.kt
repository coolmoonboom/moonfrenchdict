package com.coolmoonfrench.dict

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** AI 识别出的可导入词条（预览阶段允许编辑修正）。 */
data class ImportedWord(
    var word: String = "",
    var pos: String = "",
    var ipa: String = "",
    var meaning: String = "",
    var example: String = "",
    var exampleZh: String = ""
)

/** 用已配置的大模型把用户粘贴的内容识别成「词性 + 音标 + 中文释义 + 例句 + 中文例句」词条列表。 */
object ImportWordParser {

    /** 单批送入 AI 的字符预算：太长容易被截断输出，太短批次过多。 */
    private const val CHUNK_CHARS = 600
    private const val CHUNK_LINES = 24

    suspend fun recognize(config: AIModelConfig, content: String): List<ImportedWord> {
        val c = content.trim()
        if (c.isEmpty()) return emptyList()
        if (!IpaService.isConfigured(config)) return emptyList()
        val reply = try {
            AIClient.chat(config, listOf(AIMessage("user", buildPrompt(c))))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        return parse(reply)
            .filter { hasChinese(it.meaning) }
            .map { it.copy(pos = normalizePos(it.pos)) }
    }

    /**
     * 把长文本按行切成若干批（每批不超过字符/行数预算），供调用方逐批识别后再合并。
     * 单行超预算时单独成批，保证不丢内容。
     */
    internal fun chunkLines(content: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var lines = 0
        content.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val over = buf.length + line.length + 1 > CHUNK_CHARS || lines >= CHUNK_LINES
            if (over && buf.isNotEmpty()) {
                out += buf.toString().trimEnd()
                buf.setLength(0)
                lines = 0
            }
            buf.appendLine(line)
            lines++
        }
        if (buf.isNotBlank()) out += buf.toString().trimEnd()
        return out
    }

    /** 同一词的多次识别合并：保留义项更完整的一条。 */
    internal fun merge(words: List<ImportedWord>): List<ImportedWord> {
        val map = LinkedHashMap<String, ImportedWord>()
        words.forEach { w ->
            if (w.word.isBlank()) return@forEach
            val key = w.word.lowercase().trim()
            val old = map[key]
            if (old == null || score(w) > score(old)) map[key] = w
        }
        return map.values.toList()
    }

    private fun score(w: ImportedWord): Int =
        w.meaning.length + w.ipa.length + w.example.length

    /** 「单词｜词性｜音标｜释义」一体化编辑框的展示格式（四段恒定，空段留位）。 */
    internal fun formatHead(word: String, pos: String, ipa: String, meaning: String): String {
        var ipaOut = ipa.trim().removeSurrounding("[").removeSurrounding("]")
        if (ipaOut.isNotEmpty() && !ipaOut.startsWith("/")) ipaOut = "/$ipaOut/"
        return listOf(word.trim(), pos.trim().removePrefix("【").removeSuffix("】"), ipaOut, meaning.trim())
            .joinToString("｜")
    }

    private val POS_HINT = Regex(
        "^(n\\.?|v\\.?|adj|adv|prep|pron|art|det|interj|loc|num|contraction|感叹|缩合|缩写|拟声|象声)",
        RegexOption.IGNORE_CASE
    )

    /** 解析「单词｜词性｜音标｜释义」：≥4 段按位次取；3 段按内容识别音标/词性；≤2 段视为词+释义。 */
    internal fun splitHead(v: String): ImportedWord {
        val parts = v.split('｜', '|').map { it.trim() }
        val word = parts.getOrElse(0) { "" }
        var pos = ""
        var ipa = ""
        var meaning = ""
        when {
            parts.size >= 4 -> {
                pos = parts[1]
                ipa = parts[2]
                meaning = parts.drop(3).joinToString("｜")
            }
            parts.size == 3 -> {
                val second = parts[1]
                val third = parts[2]
                when {
                    isIpa(second) -> { ipa = second; meaning = third }
                    isIpa(third) -> { pos = second; ipa = third }
                    POS_HINT.containsMatchIn(second) -> { pos = second; meaning = third }
                    else -> { pos = second; meaning = third }
                }
            }
            parts.size == 2 -> {
                val s = parts[1]
                if (isIpa(s)) ipa = s else meaning = s
            }
        }
        val ipaClean = ipa.removeSurrounding("[").removeSurrounding("]")
            .let { if (it.isNotEmpty() && !it.startsWith("/")) "/$it/" else it }
        return ImportedWord(
            word = word,
            pos = pos.removePrefix("【").removeSuffix("】"),
            ipa = ipaClean,
            meaning = meaning
        )
    }

    private fun isIpa(s: String): Boolean =
        s.startsWith("/") || (
            s.isNotEmpty() &&
                !s.any { it.code in 0x4E00..0x9FFF } &&
                s.any { "θðʃʒɲŋœøæɑɔəɛœɥˈˌ".contains(it) }
            )

    private fun buildPrompt(content: String): String = """
        你是法语词典编辑。用户提供的内容可能是一份单词表、一段文本或混排笔记，里面会夹杂
        序号、分类标题、emoji、中文词性注释与备注、人名、结尾提问句等无关内容。
        用户提供的内容：
        $content
        要求：
        1. 只提取内容中的法语词条/短语；自动过滤所有非词条内容（序号、标题、emoji、
           纯中文句子、人名列表、提问/寒暄句等），不要为它们生成词条。
        2. 内容里的法语词条必须全部输出，禁止截断或只挑重点；本批最多 24 个词条。
        3. word 逐字保留输入中的词/短语形式，包括冠词与全部重音符号：la tête、le but、
           l'objectif、la chaîne、à chaque fois que、tomber sur qqn、pas trop de la team
           这类带冠词或介词的短语一律照原样，不得改写、拆分、合并或去掉冠词；
           动词变位形式才还原为不定式原形。
        4. 若输入已给出词性和中文释义（含括注、魁北克用法备注等），原义必须完整保留进
           meaning，你负责补全缺失的词性、音标、例句；不要丢备注信息。
        5. 每个数组元素格式：
           {"word":"原词","pos":"词性简称(n.m. n.f. v.t. v.i. adj. adv. loc.adv. loc.verb. interj. contraction 等)","ipa":"/标准IPA音标/","meaning":"中文释义(可含备注)","example":"含该词的地道法语例句","example_zh":"例句的中文翻译"}
        6. example 与 example_zh 必须完整；无法给出可靠例句时两个字段都填空字符串。
        7. 只输出一个 JSON 数组，不要任何解释，不要代码块标记。
    """.trimIndent()

    internal fun parse(reply: String): List<ImportedWord> {
        val cleaned = reply.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        return try {
            val arr = JSONArray(cleaned.substring(start, end + 1))
            val result = mutableListOf<ImportedWord>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val w = o.optString("word", "").trim()
                if (w.isEmpty()) continue
                result += ImportedWord(
                    word = w,
                    pos = o.optString("pos", "").trim(),
                    ipa = o.optString("ipa", "").trim(),
                    meaning = o.optString("meaning", "").trim(),
                    example = o.optString("example", "").trim(),
                    exampleZh = o.optString("example_zh", "").trim()
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 词性英文写法归一为标准缩写，杜绝【verb】这类英文标记进收藏。 */
    internal fun normalizePos(pos: String): String {
        val p = pos.trim().removePrefix("【").removeSuffix("】").trim()
        if (p.isEmpty()) return ""
        val key = p.lowercase().replace(".", "").replace("·", "")
        return when (key) {
            "verb", "verbs", "verbe", "v", "vir", "vt", "vi", "vpr", "vimp" -> "v."
            "noun", "noms", "nom" -> "n."
            "adjective", "adjectifs", "adjectif", "adj" -> "adj."
            "adverb", "adverbs", "adverbe", "adv" -> "adv."
            "pronoun", "pronoms", "pronom", "pron" -> "pron."
            "preposition", "prepositions", "prep" -> "prep."
            "conjunction", "conjonction", "conj" -> "conj."
            "interjection", "interjections", "interj" -> "interj."
            "article", "articles", "art" -> "art."
            "participle", "participles", "participe" -> "v."
            "contraction", "contractions", "contract" -> "contraction"
            "prefix", "prefixes", "pref" -> "préf."
            "suffix", "suffixes", "suff" -> "suff."
            "numeral", "numeraux", "num" -> "num."
            "expression", "locution", "loc" -> "loc."
            else -> p
        }
    }

    /** 组装收藏展示用释义：词性 + 中文释义 + 音标 + 例句 + 中文例句。 */
    fun buildMeaning(w: ImportedWord): String {
        val sb = StringBuilder()
        val pos = normalizePos(w.pos)
        if (pos.isNotEmpty() || w.meaning.isNotBlank()) {
            sb.append(if (pos.isNotEmpty()) "【$pos】" else "").append(w.meaning.trim())
        }
        if (w.ipa.isNotBlank()) sb.append("\n音标 ${w.ipa.trim()}")
        if (w.example.isNotBlank()) sb.append("\n例句：${w.example.trim()}")
        if (w.exampleZh.isNotBlank()) sb.append("\n中文：${w.exampleZh.trim()}")
        return sb.toString()
    }
}

/** 收藏批量整理：把「单词 + 旧释义」交给 AI 统一改写为标准中文词条格式。 */
object FavoriteRefiner {

    /** 单次 AI 调用的词条数；批次小才不容易被模型偷懒或截断。 */
    const val CHUNK_SIZE = 10

    /** 一批最多尝试次数：模型偶尔整批返回英文/空结果，自动重试一次。 */
    private const val MAX_ATTEMPTS = 2

    /** 返回整理好的词条（义项必为中文）；调用方按返回的 word 与原收藏匹配写回。 */
    suspend fun refine(config: AIModelConfig, words: List<Pair<String, String>>): List<ImportedWord> {
        if (words.isEmpty() || !IpaService.isConfigured(config)) return emptyList()
        val list = words.joinToString("\n") { (w, m) -> "$w\t$m" }
        repeat(MAX_ATTEMPTS) { attempt ->
            val reply = try {
                AIClient.chat(config, listOf(AIMessage("user", buildPrompt(list, attempt > 0))))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@repeat
            }
            // 义项必须是中文才算有效；全英文回声/半成品直接丢弃并触发重试。
            val usable = ImportWordParser.parse(reply)
                .filter { hasChinese(it.meaning) }
                .map { it.copy(pos = ImportWordParser.normalizePos(it.pos)) }
            if (usable.isNotEmpty()) return usable
        }
        return emptyList()
    }

    private fun buildPrompt(list: String, retry: Boolean): String = """
        你是法语词典编辑。下面是用户收藏里的「单词<TAB>当前释义」清单，
        释义可能是英文、词形标记、速记或格式杂乱。请把每一条改写成统一格式的中文词条。
        ${if (retry) "注意：上一次你输出了英文释义，被整批拒绝了。这次 meaning 里一个英文句子都不许出现。\n" else ""}清单内容：
        $list
        【输出格式】只输出一个 JSON 数组，不要解释、不要代码块标记。每个元素：
        {"word":"与输入单词逐字一致","pos":"词性","ipa":"/标准IPA/","meaning":"中文释义","example":"地道法语例句","example_zh":"例句中文翻译"}
        【统一规范】
        1. meaning 必须是简体中文（法语例证词可夹用），格式：核心释义；有补充再写「；短语：…；备注：…」。
           禁止输出英文释义整句，禁止把输入的英文原文照抄回来。
        2. pos 只能用这些标准缩写：n.m. n.f. v.t. v.i. v. adj. adv. loc.adv. loc.verb. loc. pron.
           prep. conj. interj. art. num. contraction préf. suff.；禁止 verb、noun、adjective 等英文写法。
        3. 输入若是变位/分词等形式（如 Ferais、émis），word 保持输入原样，
           meaning 开头先说明词形来源再给中文义，例：「faire 的现在条件式第一/二人称单数：会做、做」。
        4. 输入里已有的中文说明与备注（魁北克口语、用法括注等）必须完整保留进 meaning。
        5. 输入每行输出一条、顺序一致，一单词不落；无法给出可靠例句时
           example 与 example_zh 留空字符串。
    """.trimIndent()
}

/** AI 批量导入收藏：粘贴内容 → AI 识别 → 可编辑预览 → 写入单词收藏。 */
@Composable
fun ImportScreen(
    prefs: AIPreferences,
    repository: DictRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var words by remember { mutableStateOf<List<ImportedWord>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("AI 正在识别词条…") }
    var error by remember { mutableStateOf<String?>(null) }
    var recognized by remember { mutableStateOf(false) }
    val config = prefs.modelConfig
    val configured = IpaService.isConfigured(config)

    BackHandler { onBack() }

    fun doRecognize() {
        if (content.isBlank()) return
        if (!configured) {
            error = "尚未配置 AI 模型，请先在设置中配置后再识别。"
            return
        }
        loading = true
        error = null
        words = emptyList()
        recognized = false
        scope.launch {
            // 长文按行分批送 AI（单批过长输出易被截断），逐批识别后按单词合并去重。
            val chunks = ImportWordParser.chunkLines(content)
            val collected = mutableListOf<ImportedWord>()
            var failedChunks = 0
            chunks.forEachIndexed { i, chunk ->
                loadingText = if (chunks.size > 1) {
                    "AI 正在识别 第 ${i + 1}/${chunks.size} 批…"
                } else {
                    "AI 正在识别词条…"
                }
                val result = withContext(Dispatchers.IO) {
                    runCatching { ImportWordParser.recognize(config, chunk) }
                        .getOrElse { emptyList() }
                }
                if (result.isEmpty()) failedChunks++ else collected += result
            }
            val result = ImportWordParser.merge(collected)
            loading = false
            if (result.isEmpty()) {
                error = "AI 未能识别出有效词条，请检查粘贴内容或稍后重试。"
            } else {
                words = result
                recognized = true
                if (failedChunks > 0 && chunks.size > 1) {
                    error = "有 $failedChunks/${chunks.size} 批识别失败，其余 ${result.size} 个词条已列出，可再次识别补全。"
                }
            }
        }
    }

    fun doImport() {
        val good = words.filter { it.word.isNotBlank() }
        if (good.isEmpty()) return
        var count = 0
        good.forEach { w ->
            val word = w.word.trim()
            if (word.isNotEmpty()) {
                repository.addFavorite(word, ImportWordParser.buildMeaning(w))
                count++
            }
        }
        Toast.makeText(context, "已导入 $count 个单词到收藏", Toast.LENGTH_LONG).show()
        onBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("导入收藏", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            item {
                Text(
                    "粘贴任意内容（整份单词表、一段文本或混排笔记均可）：AI 自动过滤序号、中文注释、标题、人名等无关内容，批量识别其中全部法语词/短语，补全词性、音标、中文释义与例句翻译；长内容自动分批识别。可修正后一次性导入「收藏-单词」。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SelectableOutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("粘贴法语内容…") },
                    maxLines = 8,
                    showClear = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!configured) {
                        Text(
                            "未配置 AI 模型",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                        TextButton(onClick = onOpenSettings) { Text("去配置 AI") }
                    } else {
                        Button(
                            onClick = ::doRecognize,
                            enabled = content.isNotBlank() && !loading
                        ) {
                            Text(if (loading) "识别中…" else "AI 识别")
                        }
                    }
                }
            }

            if (loading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(loadingText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }

            if (error != null) {
                item {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            if (recognized && words.isNotEmpty()) {
                item {
                    Text(
                        "识别到 ${words.size} 个词条，可直接修改后导入：",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                items(words) { w ->
                    ImportedWordCard(w)
                }
                item {
                    Button(
                        onClick = ::doImport,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Text("导入到收藏-单词")
                    }
                }
            }
        }
    }
}

/**
 * 可编辑词条卡。空间有限，压缩成 3 个框：
 * ①「单词｜词性｜音标｜释义」合并为一框（竖线分段，编辑后按位拆回内部字段）；
 * ② 法语例句；③ 例句中文翻译。
 */
@Composable
private fun ImportedWordCard(w: ImportedWord) {
    var head by remember {
        mutableStateOf(ImportWordParser.formatHead(w.word, w.pos, w.ipa, w.meaning))
    }
    var example by remember { mutableStateOf(w.example) }
    var exampleZh by remember { mutableStateOf(w.exampleZh) }

    fun syncHead(v: String) {
        head = v
        val p = ImportWordParser.splitHead(v)
        w.word = p.word
        w.pos = p.pos
        w.ipa = p.ipa
        w.meaning = p.meaning
    }

    fun syncExample(v: String) {
        example = v
        w.example = v
    }

    fun syncExampleZh(v: String) {
        exampleZh = v
        w.exampleZh = v
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                "单词｜词性｜音标｜释义",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = head,
                onValueChange = ::syncHead,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                placeholder = { Text("bannir｜v.t.｜/ba.niʁ/｜封禁，驱逐") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = example,
                onValueChange = ::syncExample,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                placeholder = { Text("法语例句") }
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = exampleZh,
                onValueChange = ::syncExampleZh,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                placeholder = { Text("例句中文翻译") }
            )
        }
    }
}
