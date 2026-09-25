package com.coolmoonfrench.dict

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** 出题方向 */
private enum class VocabDir(val label: String) {
    F2C("法→中"),
    C2F("中→法"),
    MIX("混合")
}

/** 页面路由 */
private sealed class VocabRoute {
    object Home : VocabRoute()
    class WordList(val words: List<VocabEntry>, val title: String, val showFloatingToggle: Boolean = false) : VocabRoute()
    class Detail(val words: List<VocabEntry>, val index: Int, val listTitle: String) : VocabRoute()
    object ReviewDays : VocabRoute()
    class Quiz(val fixed: List<VocabEntry>?, val title: String, val startIndex: Int = 0) : VocabRoute()
}

/**
 * 中途退出后的「继续上次学习」存档：每本单词书一条，记录队列词序与已答到的位置。
 * 答题进度（SRS 排期）在退出前已逐题落库，续学时从存档位置继续即可。
 */
private object VocabResumeStore {
    data class Resume(val words: List<String>, val index: Int)

    private fun prefs(context: android.content.Context) =
        context.applicationContext.getSharedPreferences("vocab_resume", 0)

    fun save(context: android.content.Context, key: String, words: List<String>, index: Int) {
        val obj = JSONObject()
            .put("words", JSONArray(words))
            .put("i", index)
        prefs(context).edit().putString(key, obj.toString()).apply()
    }

    fun clear(context: android.content.Context, key: String) =
        prefs(context).edit().remove(key).apply()

    fun load(context: android.content.Context, key: String): Resume? = runCatching {
        val raw = prefs(context).getString(key, null) ?: return null
        val obj = JSONObject(raw)
        val arr = obj.getJSONArray("words")
        val words = (0 until arr.length()).map { arr.getString(it) }
        Resume(words, obj.optInt("i", 0))
    }.getOrNull()
        ?.takeIf { it.words.isNotEmpty() && it.index in it.words.indices }
}

@Composable
fun VocabScreen(mode: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    val book = remember { VocabData.load(context) }
    val isVerbs = mode == VocabData.MODE_VERBS
    val title = if (isVerbs) "背动词" else "背单词"

    val prefsName = if (isVerbs) "vocab_prefs_v" else "vocab_prefs_w"
    val prefs = remember { context.applicationContext.getSharedPreferences(prefsName, 0) }

    var levelId by remember { mutableStateOf(prefs.getString("level", VocabData.ALL) ?: VocabData.ALL) }
    var direction by remember {
        mutableStateOf(VocabDir.entries.getOrElse(prefs.getInt("dir", VocabDir.MIX.ordinal)) { VocabDir.MIX })
    }

    var route by remember { mutableStateOf<VocabRoute>(VocabRoute.Home) }
    var refresh by remember { mutableIntStateOf(0) }

    val srs = remember(levelId) { VocabSrs(context, VocabSrs.bookKey(isVerbs, levelId)) }
    val pool = remember(levelId) { book.pool(isVerbs, levelId) }

    when (val r = route) {
        is VocabRoute.Home -> {
            VocabHomeView(
                context = context,
                book = book,
                title = title,
                verbMode = isVerbs,
                levelId = levelId,
                direction = direction,
                pool = pool,
                srs = srs,
                refreshKey = refresh,
                onLevelChange = {
                    levelId = it
                    prefs.edit().putString("level", it).apply()
                },
                onDirectionChange = {
                    direction = it
                    prefs.edit().putInt("dir", it.ordinal).apply()
                },
                onOpenWords = { words, t -> route = VocabRoute.WordList(words, t) },
                onStart = { route = VocabRoute.Quiz(null, if (isVerbs) "背动词" else "背单词") },
                onContinue = { entries, i ->
                    route = VocabRoute.Quiz(entries, if (isVerbs) "背动词" else "背单词", i)
                },
                onForgetResume = {
                    VocabResumeStore.clear(context, VocabSrs.bookKey(isVerbs, levelId))
                    refresh++
                },
                onReview = { route = VocabRoute.ReviewDays },
                onMastered = { words -> route = VocabRoute.WordList(words, "已掌握", true) },
                onBack = onExit
            )
        }

        is VocabRoute.ReviewDays -> {
            VocabReviewDaysScreen(
                pool = pool,
                srs = srs,
                refreshKey = refresh,
                onOpenDay = { words, label -> route = VocabRoute.WordList(words, label) },
                onStart = { words, label -> route = VocabRoute.Quiz(words, label) },
                onBack = { route = VocabRoute.Home }
            )
        }

        is VocabRoute.WordList -> {
            VocabListScreen(
                title = r.title,
                words = r.words,
                onOpen = { i -> route = VocabRoute.Detail(r.words, i, r.title) },
                onBack = { route = VocabRoute.Home },
                showFloatingToggle = r.showFloatingToggle
            )
        }

        is VocabRoute.Detail -> {
            VocabDetailScreen(
                words = r.words,
                initialIndex = r.index,
                onNavigate = { i -> route = VocabRoute.Detail(r.words, i, r.listTitle) },
                onBack = { route = VocabRoute.WordList(r.words, r.listTitle) }
            )
        }

        is VocabRoute.Quiz -> {
            VocabQuizScreen(
                title = r.title,
                srs = srs,
                pool = pool,
                all = book.base(isVerbs),
                direction = direction,
                newLimit = srs.dailyPlan(),
                fixed = r.fixed,
                startIndex = r.startIndex,
                resumeKey = VocabSrs.bookKey(isVerbs, levelId),
                onFinish = {
                    refresh++
                    route = VocabRoute.Home
                }
            )
        }
    }
}

// ------------------------------------------------------------------ 单词书首页

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabHomeView(
    context: android.content.Context,
    book: VocabBook,
    title: String,
    verbMode: Boolean,
    levelId: String,
    direction: VocabDir,
    pool: List<VocabEntry>,
    srs: VocabSrs,
    refreshKey: Int,
    onLevelChange: (String) -> Unit,
    onDirectionChange: (VocabDir) -> Unit,
    onOpenWords: (List<VocabEntry>, String) -> Unit,
    onStart: () -> Unit,
    onContinue: (List<VocabEntry>, Int) -> Unit,
    onForgetResume: () -> Unit,
    onReview: () -> Unit,
    onMastered: (List<VocabEntry>) -> Unit,
    onBack: () -> Unit
) {
    val stats = remember(pool, refreshKey) { srs.stats(pool) }
    var plan by remember(levelId) { mutableIntStateOf(srs.dailyPlan()) }

    // 存在未完成的旧会话（词仍全部在本书中才有效）
    val resume = remember(refreshKey, levelId) {
        VocabResumeStore.load(context, VocabSrs.bookKey(verbMode, levelId))
            ?.let { r ->
                val byWord = book.base(verbMode).associateBy { it.word }
                val entries = r.words.mapNotNull { byWord[it] }
                if (entries.size == r.words.size) r to entries else null
            }
    }
    // 第一轮：按计划展示未学新词数；第二轮（无未学词）：按计划展示重学批次
    val newTodo = if (plan > 0) {
        if (stats.notStarted > 0) minOf(stats.notStarted, plan) else minOf(stats.learning + stats.mastered, plan)
    } else 0
    val dueTodo = stats.due
    // 全部单词都学过一遍 → 书名加「（第二轮）」
    val bookTitle = if (stats.notStarted == 0 && stats.learned > 0) {
        book.labelOf(levelId) + "（第二轮）"
    } else {
        book.labelOf(levelId)
    }

    var showBooks by remember { mutableStateOf(false) }
    var showPlan by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "${book.base(verbMode).size} 词",
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
            // 当前单词书 + 更换
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("当前单词书", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                bookTitle,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        OutlinedButton(onClick = { showBooks = true }) { Text("更换") }
                    }

                    Spacer(Modifier.height(14.dp))
                    ThreeColorBar(stats)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendDot(Color(0xFF2E7D32), "已掌握 ${stats.mastered}")
                        LegendDot(Color(0xFFE6A700), "学习中 ${stats.learning}")
                        LegendDot(MaterialTheme.colorScheme.outline, "未学习 ${stats.notStarted}")
                    }
                }
            }

            // 待学习新词 / 待复习
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigCountCard(
                    modifier = Modifier.weight(1f),
                    count = newTodo,
                    label = "待学习新词",
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = {
                        onOpenWords(srs.newWords(pool).take(if (plan > 0) plan else Int.MAX_VALUE), "待学习新词")
                    }
                )
                BigCountCard(
                    modifier = Modifier.weight(1f),
                    count = dueTodo,
                    label = "待复习",
                    accent = Color(0xFFE6A700),
                    onClick = onReview
                )
                BigCountCard(
                    modifier = Modifier.weight(1f),
                    count = stats.mastered,
                    label = "已掌握",
                    accent = Color(0xFF2E7D32),
                    onClick = { onMastered(srs.masteredWords(pool)) }
                )
            }

            // 每日计划
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showPlan = true }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("每日学习计划", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (plan > 0) "每天背 $plan 个新词" else "未设置（点按设置）",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("调整", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            // 出题方向
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

            resume?.let { (saved, entries) ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    onClick = { onContinue(entries, saved.index) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("继续上次学习", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "已答 ${saved.index} / ${entries.size} 题，从第 ${saved.index + 1} 题继续",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onForgetResume) {
                            Text("放弃", fontSize = 13.sp)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val q = srs.buildSession(pool, plan)
                    if (q.isEmpty()) {
                        Toast.makeText(context, "今日待学词已全部完成，明天再来吧", Toast.LENGTH_SHORT).show()
                    } else {
                        onStart()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = pool.size >= 4 && (dueTodo > 0 || newTodo > 0)
            ) {
                Text("开始学习", fontSize = 17.sp)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmReset = true }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重置本书进度")
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showBooks) {
        AlertDialog(
            onDismissRequest = { showBooks = false },
            title = { Text("更换单词书") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val opts = listOf(VocabData.ALL to "全部词汇") +
                        book.levels.map { it.id to it.label }
                    opts.forEach { (id, label) ->
                        val count = book.pool(verbMode, id).size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onLevelChange(id)
                                    showBooks = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = id == levelId, onClick = {
                                onLevelChange(id)
                                showBooks = false
                            })
                            Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
                            Text(
                                "$count 词",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBooks = false }) { Text("关闭") }
            }
        )
    }

    if (showPlan) {
        AlertDialog(
            onDismissRequest = { showPlan = false },
            title = { Text("每日学习计划") },
            text = {
                Column {
                    Text("设置每天要背的新词数量。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    listOf(0, 10, 20, 30, 50, 100).forEach { n ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    plan = n
                                    srs.setDailyPlan(n)
                                    showPlan = false
                                }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = n == plan, onClick = {
                                plan = n
                                srs.setDailyPlan(n)
                                showPlan = false
                            })
                            Text(if (n == 0) "不设置" else "每天 $n 个", fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPlan = false }) { Text("取消") } }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("重置记忆进度") },
            text = { Text("将清空当前单词书内所有词的复习排期，重新开始，确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    srs.reset()
                    onForgetResume()
                    confirmReset = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("取消") }
            }
        )
    }
}

// ------------------------------------------------------------------ 待复习（按天选择）

@Composable
private fun VocabReviewDaysScreen(
    pool: List<VocabEntry>,
    srs: VocabSrs,
    refreshKey: Int,
    onOpenDay: (List<VocabEntry>, String) -> Unit,
    onStart: (List<VocabEntry>, String) -> Unit,
    onBack: () -> Unit
) {
    val groups = remember(pool, refreshKey) { srs.reviewGroups(pool) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    BackHandler { onBack() }

    val chosen = remember(groups, selected) {
        groups.filter { it.day in selected }.flatMap { it.words }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("待复习", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "${groups.sumOf { it.words.size }} 词",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有学过的单词，先去背一些吧",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            return@Column
        }

        Text(
            "选择要复习的学习日（可多选），然后开始复习。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(groups) { g ->
                val checked = g.day in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = {
                        selected = if (checked) selected - g.day else selected + g.day
                    })
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenDay(g.words, dayLabel(g.day)) }
                    ) {
                        Text(dayLabel(g.day), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${g.words.size} 词" + if (g.due > 0) " · 到期 ${g.due}" else " · 未到期",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onOpenDay(g.words, dayLabel(g.day)) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "查看当天单词",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }

        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = { onStart(chosen, "待复习") },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(50.dp)
            ) {
                Text(if (chosen.isEmpty()) "开始复习" else "开始复习（${chosen.size} 词）", fontSize = 17.sp)
            }
        }
    }
}

/** 复习日标签：今天 / 昨天 / M月d日 */
private fun dayLabel(day: Long): String {
    val today = System.currentTimeMillis() / 86_400_000L
    return when (day) {
        today -> "今天"
        today - 1 -> "昨天"
        else -> java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA)
            .format(java.util.Date(day * 86_400_000L))
    }
}

/** 三色进度条：已掌握 / 学习中 / 未学习 */
@Composable
private fun ThreeColorBar(stats: VocabStats) {
    val total = stats.total.coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        if (stats.mastered > 0) {
            Box(
                Modifier
                    .weight(stats.mastered.toFloat() / total)
                    .fillMaxHeight()
                    .background(Color(0xFF2E7D32))
            )
        }
        if (stats.learning > 0) {
            Box(
                Modifier
                    .weight(stats.learning.toFloat() / total)
                    .fillMaxHeight()
                    .background(Color(0xFFE6A700))
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BigCountCard(
    modifier: Modifier,
    count: Int,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$count",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ------------------------------------------------------------------ 答题

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VocabQuizScreen(
    title: String,
    srs: VocabSrs,
    pool: List<VocabEntry>,
    all: List<VocabEntry>,
    direction: VocabDir,
    newLimit: Int,
    fixed: List<VocabEntry>?,
    resumeKey: String,
    startIndex: Int = 0,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    // 队列随机洗牌：每次开始/再来一轮的题目顺序都不同
    val initialQueue = remember(fixed) { (fixed ?: srs.buildSession(pool, newLimit)).shuffled() }

    var queue by remember { mutableStateOf(initialQueue) }
    var index by remember {
        mutableIntStateOf(startIndex.coerceAtLeast(0).coerceAtMost(initialQueue.size - 1))
    }
    var correctCount by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf<VocabQuestion?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var longPressed by remember { mutableIntStateOf(-1) }
    var example by remember { mutableStateOf<VocabExamples.Example?>(null) }
    var finished by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    val repository = remember { DictRepository(context) }
    var fav by remember { mutableStateOf(false) }

    val target = queue.getOrNull(index)

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
        showDetails = false
        fav = target.word.isNotEmpty() && repository.isFavorite(target.word)
    }

    val current = question

    // 当前单词自动发音：开始背、切到下一题都会朗读
    LaunchedEffect(current?.target?.word) {
        val w = current?.target?.word ?: return@LaunchedEffect
        Espeak.speakWithFeedback(context, w, deterministic = true)
    }

    // 本地例句（离线资产，不调用 AI）
    LaunchedEffect(current?.target?.word) {
        val w = current?.target?.word ?: return@LaunchedEffect
        example = withContext(Dispatchers.IO) { VocabExamples.lookup(context, w) }
    }

    fun advance() {
        val ni = index + 1
        if (ni < queue.size) index = ni else finished = true
    }

    // 续学存档：队列/位置变化即持久化；背完自动清除
    LaunchedEffect(queue, index) {
        VocabResumeStore.save(context, resumeKey, queue.map { it.word }, index)
    }
    LaunchedEffect(finished) {
        if (finished) VocabResumeStore.clear(context, resumeKey)
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

    /** 已掌握当前词：标记为已掌握并跳到下一题 */
    fun master() {
        if (selected != null || current == null) return
        srs.master(current.target.word)
        advance()
    }

    LaunchedEffect(selected) {
        if (selected != null && current != null && selected == current.answerIndex) {
            advance()
        }
    }

    if (finished) {
        // 结果页（含「太棒了」）必须自己接管返回键：
        // 否则此阶段答题页的 BackHandler 已随组合移除，返回会穿透到语法页，出现概率性退出到软件主页
        BackHandler { onFinish() }
        val stats = srs.stats(pool)
        VocabResultView(
            title = title,
            correctCount = correctCount,
            total = queue.size,
            learned = stats.learned,
            mastered = stats.mastered,
            onRestart = {
                val q = (fixed ?: srs.buildSession(pool, newLimit)).shuffled()
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
            onBackToSetup = onFinish
        )
        return
    }

    if (queue.isEmpty()) {
        onFinish()
        return
    }

    BackHandler { onFinish() }

    val answered = selected != null
    val isCorrect = answered && selected == current?.answerIndex

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onFinish) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "答对 $correctCount",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        if (current != null) {
            // 选择题界面元素整体下移约 3 厘米（1cm ≈ 63dp）
            Spacer(Modifier.height(189.dp))
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
                TargetCard(
                    question = current,
                    example = example,
                    showExample = current.frenchToFront || answered,
                    showDetails = showDetails,
                    onToggleDetails = { showDetails = !showDetails },
                    onSpeakWord = {
                        Espeak.speakWithFeedback(context, current.target.word, deterministic = true)
                    },
                    onSpeakExample = {
                        val fr = example?.fr ?: return@TargetCard
                        Speech.ensureInitialized(context)
                        Speech.speakWithFeedback(context, fr)
                    }
                )

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
                    // 中→法：显示选项词义；法→中：长按选项显示对应的法语词
                    val expandedZh = when {
                        !current.frenchToFront && (answered || oi == longPressed) -> option.entry.meaning
                        current.frenchToFront && oi == longPressed -> option.entry.word
                        else -> ""
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .combinedClickable(
                                enabled = !answered,
                                onClick = { answer(oi) },
                                onLongClick = { longPressed = if (longPressed == oi) -1 else oi }
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
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
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (answered && (isThisCorrect || isThisSelected)) Color.White
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.text, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                            if (expandedZh.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    expandedZh,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (current.frenchToFront.not()) {
                            IconButton(onClick = {
                                Espeak.speakWithFeedback(context, option.entry.word, deterministic = true)
                            }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "朗读",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (!answered) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (fav) repository.removeFavorite(current.target.word)
                                else repository.addFavorite(current.target.word)
                                fav = !fav
                            },
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Icon(
                                if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (fav) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (fav) "已收藏" else "收藏", fontSize = 15.sp)
                        }
                        OutlinedButton(
                            onClick = { master() },
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Text("已掌握", fontSize = 15.sp)
                        }
                    }
                }

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

/** 顶部目标 + 示例句卡片。法→中模式下点按单词可展开音标/词性/含义等详情。 */
@Composable
private fun TargetCard(
    question: VocabQuestion,
    example: VocabExamples.Example?,
    showExample: Boolean,
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
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
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (question.frenchToFront) Modifier.clickable { onToggleDetails() }
                            else Modifier
                        )
                )
                IconButton(onClick = onSpeakWord, modifier = Modifier.size(36.dp)) {
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
                Spacer(Modifier.height(4.dp))
                Text(
                    if (showDetails) "点按单词收起详情" else "点按单词查看释义 / 词性 / 例句",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
                if (showDetails) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            DetailLine("音标", FrenchIpa.wrap(question.target.word).ifBlank { "—" })
                            DetailLine("词性", question.target.pos.ifBlank { "—" })
                            DetailLine("含义", question.target.meaning.ifBlank { "—" })
                        }
                    }
                }
            }

            if (showExample) example?.let { ex ->
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

/** 详情面板中的一行「标签 + 内容」。 */
@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        Text(
            value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 练习结果页 */
@Composable
private fun VocabResultView(
    title: String,
    correctCount: Int,
    total: Int,
    learned: Int,
    mastered: Int,
    onRestart: () -> Unit,
    onBackToSetup: () -> Unit
) {
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
            Text("返回单词书")
        }
    }
}
