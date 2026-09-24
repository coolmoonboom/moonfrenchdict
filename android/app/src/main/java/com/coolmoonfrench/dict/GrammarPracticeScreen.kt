package com.coolmoonfrench.dict

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 选中的练习：标题 + 题库 */
private data class StartedQuiz(val title: String, val bank: List<QuizQuestion>)

@Composable
fun GrammarPracticeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val genCtx = remember {
        GrammarGenCtx(VocabData.load(context), VerbConjugator())
    }

    var levelId by rememberSaveable { mutableStateOf("A1") }
    var started by remember { mutableStateOf<StartedQuiz?>(null) }

    BackHandler(enabled = started != null) { started = null }

    val current = started
    if (current != null) {
        QuizScreen(
            title = current.title,
            bank = current.bank,
            onBack = { started = null }
        )
        return
    }

    val level = GrammarCatalog.level(levelId)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("语法练习", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(GrammarCatalog.levels) { lv ->
                FilterChip(
                    selected = lv.id == levelId,
                    onClick = { levelId = lv.id },
                    label = { Text(lv.label) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${level.label} 阶段板块，每题模块从题库随机抽 10 道选择题",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            level.modules.forEach { module ->
                CategoryItem(module.icon, module.title, "", module.desc) {
                    started = StartedQuiz(module.title, module.build(genCtx))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Text(
                "六大板块综合练习（不分级别）",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            CategoryItem(Icons.Filled.AccessTime, "时态练习", "", "综合时态与时态用法") {
                started = StartedQuiz(QuizCategory.TENSE.title, getQuestions(QuizCategory.TENSE))
            }
            CategoryItem(Icons.Filled.Book, "单词练习", "", "高频词汇、词义辨析、反义与同义") {
                started = StartedQuiz(QuizCategory.WORD.title, getQuestions(QuizCategory.WORD))
            }
            CategoryItem(Icons.Filled.CollectionsBookmark, "介词练习", "", "à / de / en / dans / pour 的搭配") {
                started = StartedQuiz(QuizCategory.PREPOSITION.title, getQuestions(QuizCategory.PREPOSITION))
            }
            CategoryItem(Icons.Filled.Link, "连接词练习", "", "et / mais / parce que / bien que 等") {
                started = StartedQuiz(QuizCategory.CONJUNCTION.title, getQuestions(QuizCategory.CONJUNCTION))
            }
            CategoryItem(Icons.AutoMirrored.Filled.DirectionsRun, "副词练习", "", "时间地点方式程度副词与构词") {
                started = StartedQuiz(QuizCategory.ADVERB.title, getQuestions(QuizCategory.ADVERB))
            }
            CategoryItem(Icons.Filled.Spellcheck, "代词练习", "", "人称、C/COI、关系、en/y 等全部代词") {
                started = StartedQuiz(QuizCategory.PRONOUN.title, getQuestions(QuizCategory.PRONOUN))
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CategoryItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(2.dp))
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
