package com.coolmoonfrench.dict

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GrammarPracticeScreen(onBack: () -> Unit) {
    var selectedCategory by remember { mutableStateOf<QuizCategory?>(null) }

    // 系统返回键：先关闭答题界面，再返回上一级
    BackHandler(enabled = selectedCategory != null) { selectedCategory = null }

    if (selectedCategory != null) {
        // 五、答题界面
        QuizScreen(
            category = selectedCategory!!,
            onBack = { selectedCategory = null }
        )
    } else {
        // 一、子菜单界面
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("语法练习", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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
                    "选择练习类型，每题会从题库随机抽 10 道选择题",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )

                CategoryItem(Icons.Filled.AccessTime, QuizCategory.TENSE.title, "时态与时态用法", "识别现在时、未完成过去时、复合过去时、将来时等") {
                    selectedCategory = QuizCategory.TENSE
                }
                CategoryItem(Icons.Filled.Book, QuizCategory.WORD.title, "常用单词与反义词", "高频词汇、词义辨析、反义词与同义词") {
                    selectedCategory = QuizCategory.WORD
                }
                CategoryItem(Icons.Filled.CollectionsBookmark, QuizCategory.PREPOSITION.title, "介词用法", "à / de / en / dans / pour 等的搭配与位置") {
                    selectedCategory = QuizCategory.PREPOSITION
                }
                CategoryItem(Icons.Filled.Link, QuizCategory.CONJUNCTION.title, "连接词与连词", "et / mais / parce que / si / bien que 等") {
                    selectedCategory = QuizCategory.CONJUNCTION
                }
                CategoryItem(Icons.AutoMirrored.Filled.DirectionsRun, QuizCategory.ADVERB.title, "副词", "时间、地点、方式、程度副词及构词法") {
                    selectedCategory = QuizCategory.ADVERB
                }
                CategoryItem(Icons.Filled.Spellcheck, QuizCategory.PRONOUN.title, "全部代词", "人称、COD/COI、所有格、指示、疑问、关系、副代词(en/y)、泛指、中性代词") {
                    selectedCategory = QuizCategory.PRONOUN
                }

                Spacer(Modifier.height(12.dp))
            }
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
                Text(title, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}