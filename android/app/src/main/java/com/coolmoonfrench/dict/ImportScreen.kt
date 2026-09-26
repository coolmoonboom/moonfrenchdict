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
    }

    private fun buildPrompt(content: String): String = """
        你是法语词典编辑。用户提供了下面的内容，请把它整理成可导入单词收藏的词条清单。
        用户提供的内容：
        $content
        要求：
        1. 只输出一个 JSON 数组，不要任何解释，不要代码块标记。
        2. 每个数组元素格式：
           {"word":"单词原形","pos":"词性简称(如 n.m. n.f. adj. v.t. adv. 等)","ipa":"/标准IPA音标/","meaning":"简洁中文释义","example":"含该词的地道法语例句","example_zh":"例句的中文翻译"}
        3. word 必须保留法语重音符号（é è ê à ç î ô û ù 等），禁止写成无重音形式；内容里是动词变位时 word 填不定式原形。
        4. 若内容是一个词或短语，输出含一个元素的数组；若是一段文本，提取其中值得收藏的词条，最多 20 个。
        5. example 与 example_zh 必须完整；无法给出可靠例句时两个字段都填空字符串。
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

    /** 组装收藏展示用释义：词性 + 中文释义 + 音标 + 例句 + 中文例句。 */
    fun buildMeaning(w: ImportedWord): String {
        val sb = StringBuilder()
        val pos = w.pos.trim().removePrefix("【").removeSuffix("】")
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

    /** 单次 AI 调用的词条数，控制上下文规模、避免长输出被截断。 */
    const val CHUNK_SIZE = 12

    /** 返回整理好的词条；调用方按返回的 word 与原收藏匹配写回。无配置/失败返回空。 */
    suspend fun refine(config: AIModelConfig, words: List<Pair<String, String>>): List<ImportedWord> {
        if (words.isEmpty() || !IpaService.isConfigured(config)) return emptyList()
        val list = words.joinToString("\n") { (w, m) -> "$w\t$m" }
        val reply = try {
            AIClient.chat(config, listOf(AIMessage("user", buildPrompt(list))))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        // 只保留义项确为中文的结果：个别条目没整理好就保留原义，不做破坏性覆盖。
        return ImportWordParser.parse(reply).filter { hasChinese(it.meaning) }
    }

    private fun buildPrompt(list: String): String = """
        你是法语词典编辑。下面是用户收藏里的「单词<TAB>当前释义」清单，
        部分释义是英文、速记或格式杂乱，请逐条改写成统一的中文标准词条。
        清单内容：
        $list
        要求：
        1. 只输出一个 JSON 数组，不要任何解释，不要代码块标记。
        2. 每个元素格式：
           {"word":"与输入单词逐字一致","pos":"词性简称(n.m. n.f. adj. v.t. v.i. adv. v.phr. contraction 等)","ipa":"/标准IPA音标/","meaning":"简洁中文释义(必须是中文)","example":"含该词的地道法语例句","example_zh":"例句的中文翻译"}
        3. word 原样保留输入内容：短语（如 aller faire）、缩合（如 Qu'on）都要原样保留，禁止改写成单个动词原形。
        4. meaning 必须是中文；原义是英文的准确翻译过来，禁止臆造。
        5. 无法给出可靠例句时 example 与 example_zh 留空字符串。
        6. 输入每行输出一条，顺序与输入一致，不要合并、删减或新增。
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
            val result = withContext(Dispatchers.IO) {
                runCatching { ImportWordParser.recognize(config, content) }
                    .getOrElse { emptyList() }
            }
            loading = false
            if (result.isEmpty()) {
                error = "AI 未能识别出有效词条，请检查粘贴内容或稍后重试。"
            } else {
                words = result
                recognized = true
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
                    "粘贴任意法语内容（单词、短语或一段文本），AI 会识别出词性、音标、例句与中文例句，可修正后一次性导入「收藏-单词」，与收藏词一样支持选择与播放。",
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
                        Text("AI 正在识别词条…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
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

@Composable
private fun ImportedWordCard(w: ImportedWord) {
    var word by remember { mutableStateOf(w.word) }
    var pos by remember { mutableStateOf(w.pos) }
    var ipa by remember { mutableStateOf(w.ipa) }
    var meaning by remember { mutableStateOf(w.meaning) }
    var example by remember { mutableStateOf(w.example) }
    var exampleZh by remember { mutableStateOf(w.exampleZh) }

    fun syncWord(v: String) {
        word = v
        w.word = v
    }

    fun syncPos(v: String) {
        pos = v
        w.pos = v
    }

    fun syncIpa(v: String) {
        ipa = v
        w.ipa = v
    }

    fun syncMeaning(v: String) {
        meaning = v
        w.meaning = v
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = word,
                    onValueChange = ::syncWord,
                    modifier = Modifier.weight(1.2f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                )
                OutlinedTextField(
                    value = pos,
                    onValueChange = ::syncPos,
                    modifier = Modifier.weight(0.8f),
                    singleLine = true,
                    placeholder = { Text("词性") }
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = ipa,
                onValueChange = ::syncIpa,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("音标 /…/") }
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = meaning,
                onValueChange = ::syncMeaning,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                placeholder = { Text("中文释义") }
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
