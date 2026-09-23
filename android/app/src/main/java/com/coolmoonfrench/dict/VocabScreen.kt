package com.coolmoonfrench.dict

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/** 出题方向 */
private enum class VocabDir(val label: String) {
    F2C("法→中"),
    C2F("中→法"),
    MIX("混合")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabScreen(mode: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    val book = remember { VocabData.load(context) }

    val isVerbs = mode == VocabData.MODE_VERBS
    val prefsName = if (isVerbs) "vocab_prefs_v" else "vocab_prefs_w"
    val prefs = remember { context.applicationContext.getSharedPreferences(prefsName, 0) }

    var levels by remember { mutableStateOf(prefs.getStringSet("levels", emptySet()) ?: emptySet()) }
    var themes by remember { mutableStateOf(prefs.getStringSet("themes", emptySet()) ?: emptySet()) }
    var direction by remember {
        mutableStateOf(VocabDir.entries.getOrElse(prefs.getInt("dir", VocabDir.MIX.ordinal)) { VocabDir.MIX })
    }

    var inQuiz by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<Pair<VocabSrs, List<VocabEntry>>?>(null) }

    fun saveSelection() {
        prefs.edit()
            .putStringSet("levels", levels)
            .putStringSet("themes", themes)
            .putInt("dir", direction.ordinal)
            .apply()
    }

    if (!inQuiz) {
        VocabSetupView(
            context = context,
            book = book,
            title = if (isVerbs) "背动词" else "背单词",
            verbMode = isVerbs,
            levels = levels,
            onToggleLevel = { l ->
                levels = levels.toMutableSet().apply { if (l in this) remove(l) else add(l) }
                saveSelection()
            },
            themes = themes,
            onToggleTheme = { t ->
                themes = themes.toMutableSet().apply { if (t in this) remove(t) else add(t) }
                saveSelection()
            },
            onClearFilters = {
                levels = emptySet()
                themes = emptySet()
                saveSelection()
            },
            direction = direction,
            onDirectionChange = {
                direction = it
                saveSelection()
            },
            onResetProgress = {
                VocabSrs(context, VocabSrs.bookId(isVerbs, levels, themes)).reset()
                Toast.makeText(context, "已重置本书记忆进度", Toast.LENGTH_SHORT).show()
            },
            onStart = {
                val p = book.pool(isVerbs, levels, themes)
                if (p.size < 4) {
                    Toast.makeText(context, "当前词书词量不足，请放宽筛选条件", Toast.LENGTH_SHORT).show()
                    return@VocabSetupView
                }
                val s = VocabSrs(context, VocabSrs.bookId(isVerbs, levels, themes))
                val q = s.buildSession(p, DAILY_NEW)
                if (q.isEmpty()) {
                    Toast.makeText(context, "今日待学词已全部完成，明天再来吧", Toast.LENGTH_SHORT).show()
                    return@VocabSetupView
                }
                session = s to q
                inQuiz = true
            },
            onBack = onExit
        )
        return
    }

    val cur = session
    if (cur == null) {
        inQuiz = false
        return
    }

    VocabQuizContent(
        mode = mode,
        srs = cur.first,
        initialQueue = cur.second,
        pool = book.pool(isVerbs, levels, themes),
        all = book.entries,
        direction = direction,
        onBackToSetup = { inQuiz = false },
        onExit = onExit
    )
}

/** 答题/结果视图：独立组合位置，保证每次进入状态从零开始 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VocabQuizContent(
    mode: Int,
    srs: VocabSrs,
    initialQueue: List<VocabEntry>,
    pool: List<VocabEntry>,
    all: List<VocabEntry>,
    direction: VocabDir,
    onBackToSetup: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current

    var queue by remember { mutableStateOf(initialQueue) }
    var index by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf<VocabQuestion?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var longPressed by remember { mutableIntStateOf(-1) }
    var example by remember { mutableStateOf<VocabExamples.Example?>(null) }
    var finished by remember { mutableStateOf(false) }

    val target = queue.getOrNull(index)

    // 载入当前题目
    LaunchedEffect(target) {
        if (target == null) {
            finished = true
            return@LaunchedEffect
        }
        val toFront = when (direction) {
            VocabDir.F2C -> true
            VocabDir.C2F -> false
            VocabDir.MIX -> Random.nextBoolean()
        }
        question = VocabQuiz.build(target, pool, all, Random.Default, toFront)
        selected = null
        longPressed = -1
        example = null
    }

    val current = question

    // 加载 AI 例句（仅法→中方向展示）
    LaunchedEffect(current?.target?.word, current?.frenchToFront) {
        val w = current?.target?.word ?: return@LaunchedEffect
        if (!current.frenchToFront || VocabExamples.isConfigured(context).not()) {
            example = null
            return@LaunchedEffect
        }
        VocabExamples.cached(context, w)?.let {
            example = it
            return@LaunchedEffect
        }
        val g = VocabExamples.generate(context, w)
        if (g != null) example = g
    }

    fun advance() {
        val ni = index + 1
        if (ni < queue.size) index = ni else finished = true
    }

    fun answer(i: Int) {
        if (selected != null || current == null) return
        selected = i
        if (i == current.answerIndex) {
            correctCount++
            srs.record(current.target.word, true)
        } else {
            srs.record(current.target.word, false)
        }
    }

    // 答对自动进入下一题
    LaunchedEffect(selected) {
        if (selected != null && current != null && selected == current.answerIndex) {
            delay(900)
            advance()
        }
    }

    if (finished) {
        val stats = srs.progress(pool)
        VocabResultView(
            mode = mode,
            correctCount = correctCount,
            total = queue.size,
            learned = stats[0],
            mastered = stats[1],
            onRestart = {
                val q = srs.buildSession(pool, DAILY_NEW)
                if (q.isEmpty()) {
                    Toast.makeText(context, "今日待学词已全部完成，明天再来吧", Toast.LENGTH_SHORT).show()
                    return@VocabResultView
                }
                queue = q
                index = 0
                correctCount = 0
                question = null
                selected = null
                longPressed = -1
                example = null
                finished = false
            },
            onBackToSetup = onBackToSetup,
            onExit = onExit
        )
        return
    }

    if (queue.isEmpty()) {
        onBackToSetup()
        return
    }

    BackHandler { onBackToSetup() }

    val answered = selected != null
    val isCorrect = answered && selected == current?.answerIndex

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToSetup) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回选书")
            }
            Text(
                if (mode == VocabData.MODE_VERBS) "背动词" else "背单词",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "答对 $correctCount",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        if (current != null) {
            LinearProgressIndicator(
                progress = { (index + 1f) / queue.size },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Text(
                "第 ${index + 1} 题 / 共 ${queue.size} 题",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 顶部目标卡片：法→中显示法语词+IPA+例句，中→法显示中文词义
                TargetCard(
                    question = current,
                    example = example,
                    onSpeakWord = {
                        Espeak.speakWithFeedback(context, current.target.word, deterministic = true)
                    },
                    onSpeakExample = {
                        val fr = example?.fr ?: return@TargetCard
                        Speech.ensureInitialized(context)
                        Speech.speakWithFeedback(context, fr)
                    }
                )

                // 选项 a/b/c/d
                current.options.forEachIndexed { oi, option ->
                    val isThisCorrect = oi == current.answerIndex
                    val isThisSelected = oi == selected

                    val bgColor = when {
                        !answered -> MaterialTheme.colorScheme.surfaceVariant
                        isThisCorrect -> MaterialTheme.colorScheme.tertiaryContainer
                        isThisSelected -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }

                    val letter = ('a' + oi).toString()
                    // 中→法：长按或作答后显示该选项的中文释义
                    val showZh = current.frenchToFront.not() &&
                        (answered || (oi == longPressed))
                    val expandedZh = if (showZh) option.entry.meaning else ""

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .combinedClickable(
                                enabled = !answered,
                                onClick = { answer(oi) },
                                onLongClick = { longPressed = if (longPressed == oi) -1 else oi }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        answered && isThisCorrect -> Color(0xFF2E7D32)
                                        answered && isThisSelected -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                letter,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (answered && (isThisCorrect || isThisSelected)) Color.White
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.text, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            if (expandedZh.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    expandedZh,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 中→法：法语选项带发音按钮
                        if (current.frenchToFront.not()) {
                            IconButton(onClick = {
                                Espeak.speakWithFeedback(context, option.entry.word, deterministic = true)
                            }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "朗读",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 答错：提示正确项 + 下一题按钮
                if (answered && !isCorrect) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "正确答案是 ${('a' + current.answerIndex)}. ${current.answerText}",
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { advance() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (index == queue.size - 1) "查看结果" else "下一题")
                            }
                        }
                    }
                }

                if (answered && isCorrect) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "回答正确，自动进入下一题…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** 顶部目标 + 示例句卡片 */
@Composable
private fun TargetCard(
    question: VocabQuestion,
    example: VocabExamples.Example?,
    onSpeakWord: () -> Unit,
    onSpeakExample: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    question.prompt,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onSpeakWord,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "朗读单词",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (question.frenchToFront) {
                val ipa = FrenchIpa.wrap(question.target.word)
                if (ipa.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        ipa,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            example?.let { ex ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                ex.fr,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(ex.zh, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onSpeakExample, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "朗读例句",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 选书页 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabSetupView(
    context: android.content.Context,
    book: VocabBook,
    title: String,
    verbMode: Boolean,
    levels: Set<String>,
    onToggleLevel: (String) -> Unit,
    themes: Set<String>,
    onToggleTheme: (String) -> Unit,
    onClearFilters: () -> Unit,
    direction: VocabDir,
    onDirectionChange: (VocabDir) -> Unit,
    onResetProgress: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val poolSize = remember(levels, themes, verbMode) {
        book.pool(verbMode, levels, themes).size
    }
    val progress = remember(levels, themes, verbMode) {
        VocabSrs(
            context,
            VocabSrs.bookId(verbMode, levels, themes)
        ).progress(book.pool(verbMode, levels, themes))
    }
    var confirmReset by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "${book.entries.size} 词",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "选择难度、主题组成你的单词书。答错的词会按艾宾浩斯曲线复现，更换单词书则重新排期。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            Text("难度（不选=全部）", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VocabData.LEVELS.forEach { lvl ->
                    FilterChip(
                        selected = lvl in levels,
                        onClick = { onToggleLevel(lvl) },
                        label = { Text(lvl) }
                    )
                }
            }

            Text("主题（不选=全部主题）", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                book.themes.forEach { t ->
                    FilterChip(
                        selected = t.id in themes,
                        onClick = { onToggleTheme(t.id) },
                        label = { Text(t.zh) }
                    )
                }
            }
            if (themes.isNotEmpty() || levels.isNotEmpty()) {
                TextButton(onClick = onClearFilters) {
                    Text("清除筛选（选择全部）")
                }
            }

            Text("出题方向", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VocabDir.entries.forEach { d ->
                    FilterChip(
                        selected = d == direction,
                        onClick = { onDirectionChange(d) },
                        label = { Text(d.label) }
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "本词书共 $poolSize 词",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "已学 ${progress[0]} / 已掌握 ${progress[1]}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = poolSize >= 4
            ) {
                Text("开始练习", fontSize = 16.sp)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmReset = true }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重置本书记忆进度")
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("重置记忆进度") },
            text = { Text("将清空当前单词书内所有词的复习排期，重新开始，确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onResetProgress()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("取消") }
            }
        )
    }
}

/** 练习结果页 */
@Composable
private fun VocabResultView(
    mode: Int,
    correctCount: Int,
    total: Int,
    learned: Int,
    mastered: Int,
    onRestart: () -> Unit,
    onBackToSetup: () -> Unit,
    onExit: () -> Unit
) {
    val title = if (mode == VocabData.MODE_VERBS) "背动词" else "背单词"
    val percent = if (total > 0) correctCount * 100 / total else 0
    val head = when {
        percent >= 90 -> "太棒了！"
        percent >= 70 -> "非常不错！"
        percent >= 50 -> "继续加油！"
        else -> "还需努力！"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(head, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("答对 $correctCount / $total 题", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "本书累计：已学 $learned 词 · 已掌握 $mastered 词",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("再来一轮")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBackToSetup, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("更换单词书")
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onExit) {
            Text("返回语法学习")
        }
    }
}

private const val DAILY_NEW = 15