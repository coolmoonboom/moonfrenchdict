package com.coolmoonfrench.dict

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

const val QUIZ_COUNT = 10

private fun buildQuiz(bank: List<QuizQuestion>): List<QuizQuestion> {
    val picked = bank.shuffled().take(minOf(QUIZ_COUNT, bank.size))
    return picked.map { q -> q.copy(options = q.options.shuffled()) }
}

@Composable
fun QuizScreen(category: QuizCategory, onBack: () -> Unit) {
    val bank = remember(category) { getQuestions(category) }

    var questions by remember { mutableStateOf(buildQuiz(bank)) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }

    if (finished) {
        QuizResultScreen(
            category = category,
            score = score,
            total = questions.size,
            onRestart = {
                questions = buildQuiz(bank)
                currentIndex = 0
                score = 0
                selected = null
                finished = false
            },
            onBack = onBack
        )
        return
    }

    val current = questions.getOrNull(currentIndex)
    if (current == null) {
        onBack()
        return
    }

    val answered = selected != null
    val isCorrect = selected == current.correct
    val isLast = currentIndex == questions.size - 1

    // 答对时自动跳到下一题（最后一题跳到结果页）
    LaunchedEffect(selected) {
        if (answered && isCorrect) {
            delay(900)
            if (isLast) {
                finished = true
            } else {
                currentIndex++
                selected = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(category.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "得分 $score",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        // 进度
        LinearProgressIndicator(
            progress = { (currentIndex + 1f) / questions.size },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Text(
            "第 ${currentIndex + 1} 题 / 共 ${questions.size} 题",
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
            // 题目
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Text(
                    current.question,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // 选项
            current.options.forEach { option ->
                val isThisCorrect = option == current.correct
                val isThisSelected = option == selected

                val bgColor = when {
                    !answered -> MaterialTheme.colorScheme.surfaceVariant
                    isThisCorrect -> MaterialTheme.colorScheme.tertiaryContainer
                    isThisSelected -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val fgColor = when {
                    !answered -> MaterialTheme.colorScheme.onSurface
                    isThisCorrect -> MaterialTheme.colorScheme.onTertiaryContainer
                    isThisSelected -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .clickable(enabled = !answered) { selected = option }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (answered && isThisCorrect) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "正确",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (answered && isThisSelected) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "错误",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(option, fontSize = 16.sp, color = fgColor)
                }
            }

            // 答错后显示解释 + 下一题按钮
            if (answered && !isCorrect) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "解释",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            current.explanation,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Button(
                    onClick = {
                        if (isLast) {
                            finished = true
                        } else {
                            currentIndex++
                            selected = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(if (isLast) "查看结果" else "下一题")
                }
            }

            // 答对时轻提示
            if (answered && isCorrect) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
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

@Composable
private fun QuizResultScreen(
    category: QuizCategory,
    score: Int,
    total: Int,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
    val percent = if (total > 0) score * 100 / total else 0
    val title = when {
        percent >= 90 -> "太棒了！"
        percent >= 70 -> "非常不错！"
        percent >= 50 -> "继续加油！"
        else -> "还需努力！"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(category.title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "答对 $score / $total 题",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("重新练习")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("返回菜单")
        }
    }
}