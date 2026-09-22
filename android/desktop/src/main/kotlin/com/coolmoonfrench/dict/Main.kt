package com.coolmoonfrench.dict

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.system.exitProcess

private val SUBJECTS = listOf("je", "tu", "il/elle", "nous", "vous", "ils/elles")

private fun hasChinese(s: String): Boolean = s.any { it in '一'..'鿿' }

fun main(args: Array<String>) {
    if (args.contains("--selftest")) {
        runSelftest()
        return
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "法语词典 · 桌面预览"
        ) {
            App()
        }
    }
}

private fun runSelftest() {
    val repo = DictRepository()
    val t0 = System.currentTimeMillis()
    repo.ensureReady()
    val entry = repo.lookupExact("manger").firstOrNull() ?: error("lookupExact(manger) 失败")
    check(entry.meaning.isNotBlank()) { "lookupExact(manger) 无释义" }
    val conj = VerbConjugator().conjugate("manger") ?: error("变位引擎自检失败")
    check(conj.present.size == 6 && conj.present.first().isNotBlank()) { "变位结果异常" }
    val cands = kotlinx.coroutines.runBlocking { repo.searchVerbsByChinese("喜欢", 8) }
    println("lookup: ${entry.meaning}")
    println("conjugate: ${conj.present.joinToString(" | ")}")
    println("candidates(喜欢): ${cands.joinToString(", ") { it.infinitive }}")
    println("selftest OK in ${System.currentTimeMillis() - t0} ms")
    kotlin.system.exitProcess(0)
}

@Composable
private fun App() {
    val repository = remember { DictRepository() }
    val conjugator = remember { VerbConjugator() }

    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<VerbCandidate>>(emptyList()) }
    var coreMeaning by remember { mutableStateOf("") }
    var candError by remember { mutableStateOf<String?>(null) }
    var conj by remember { mutableStateOf<Conjugation?>(null) }
    var foundInfinitive by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf<DictEntry?>(null) }

    LaunchedEffect(query) {
        val q = query.trim()
        candidates = emptyList(); coreMeaning = ""; candError = null
        conj = null; foundInfinitive = null; entry = null
        if (q.isEmpty()) { loading = false; return@LaunchedEffect }
        loading = true
        try {
            delay(300)
            if (hasChinese(q)) {
                val res = ChineseVerbSearch.find(
                    query = q,
                    repository = repository,
                    conjugator = conjugator,
                    config = null,
                    onLocalReady = { local ->
                        candidates = local
                        if (local.isNotEmpty()) loading = false
                    }
                )
                candidates = res.candidates
                coreMeaning = res.coreMeaning
                candError = res.aiError
            } else {
                entry = repository.lookupExact(q).firstOrNull()
                var c = conjugator.conjugate(q)
                if (c == null) {
                    val found = conjugator.findInfinitive(q)
                    if (found != null) {
                        foundInfinitive = found
                        c = conjugator.conjugate(found)
                    }
                }
                conj = c
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            candError = "查询失败：${e.message?.take(100) ?: "未知错误"}"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("输入法语动词（原形或变体）或中文含义") },
            singleLine = true
        )
        if (query.isBlank()) {
            Text(
                "输入法语动词查看变位，或输入中文（如「喜欢」「我喜欢你」）匹配候选动词。",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            if (loading && candidates.isEmpty() && conj == null && entry == null) {
                Text(
                    "正在查询…",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (coreMeaning.isNotBlank()) {
                Text(
                    "识别为核心动词：$coreMeaning",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            candError?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            candidates.forEach { c -> CandidateCard(c) }

            if (!hasChinese(query.trim())) {
                entry?.let { e ->
                    SimpleCard {
                        Column {
                            Text(e.word, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(e.meaning, fontSize = 14.sp)
                        }
                    }
                }
                conj?.let { c ->
                    SimpleCard {
                        Column {
                            val display = if (foundInfinitive != null) {
                                "${query.trim()} → ${c.infinitive}"
                            } else {
                                c.infinitive
                            }
                            Text(display, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "第${c.group}组 · 助动词 ${c.auxiliary} · 过去分词 ${c.participePasse}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("直陈式现在时", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            SUBJECTS.forEachIndexed { i, subj ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                    Text(
                                        subj,
                                        Modifier.width(70.dp),
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(c.present.getOrElse(i) { "" }, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        androidx.compose.foundation.layout.Box(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun CandidateCard(c: VerbCandidate) {
    SimpleCard {
        Column {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
    }
}
