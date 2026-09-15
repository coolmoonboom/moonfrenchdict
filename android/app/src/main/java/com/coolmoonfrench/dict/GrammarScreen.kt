package com.coolmoonfrench.dict

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GrammarScreen(onExit: () -> Unit) {
    var categoryIndex by remember { mutableStateOf<Int?>(null) }
    var topicIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    // 预热 Mimic 法语 TTS（幂等，非阻塞）
    LaunchedEffect(Unit) {
        Espeak.ensureInitialized(context)
    }

    fun goUp() {
        when {
            topicIndex != null -> topicIndex = null
            categoryIndex != null -> categoryIndex = null
            else -> onExit()
        }
    }

    // 系统返回键：主题 → 分类 → 首页 → 退出，逐级返回
    BackHandler { goUp() }

    val catIndex = categoryIndex
    val topIndex = topicIndex
    when {
        catIndex == null -> GrammarHome(
            onBack = onExit,
            onOpenCategory = { categoryIndex = it }
        )
        topIndex == null -> GrammarCategoryView(
            category = GrammarContent.categories[catIndex],
            onBack = { categoryIndex = null },
            onOpenTopic = { topicIndex = it }
        )
        else -> GrammarTopicView(
            topic = GrammarContent.categories[catIndex].topics[topIndex],
            onBack = { topicIndex = null }
        )
    }
}

/** 统一顶栏：返回按钮 + 标题/法文副标题 + 尾部说明 */
@Composable
private fun GrammarHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!trailing.isNullOrBlank()) {
            Text(
                trailing,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    }
}

private val categoryIcons: List<ImageVector> = listOf(
    Icons.Filled.TextFields,
    Icons.Filled.SwapVert,
    Icons.AutoMirrored.Filled.Notes
)

/** 首页：三大板块入口 */
@Composable
private fun GrammarHome(
    onBack: () -> Unit,
    onOpenCategory: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        GrammarHeader(title = "语法学习", onBack = onBack, trailing = "共 ${GrammarContent.categories.size} 板块")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "按词法、动词、句法三大板块系统学习法语语法。点进板块选择主题，即可查看讲解、规则与例句。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }

            itemsIndexed(GrammarContent.categories) { index, category ->
                CategoryEntryCard(
                    icon = categoryIcons.getOrElse(index) { Icons.Filled.TextFields },
                    title = category.title,
                    fr = category.fr,
                    intro = category.intro,
                    count = category.topics.size,
                    onClick = { onOpenCategory(index) }
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun CategoryEntryCard(
    icon: ImageVector,
    title: String,
    fr: String,
    intro: String,
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
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
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text(fr, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.height(2.dp))
                Text(intro, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "$count 讲",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** 分类页：主题列表 */
@Composable
private fun GrammarCategoryView(
    category: GrammarCategory,
    onBack: () -> Unit,
    onOpenTopic: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        GrammarHeader(
            title = category.title,
            subtitle = category.fr,
            onBack = onBack,
            trailing = "共 ${category.topics.size} 讲"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    category.intro,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }

            itemsIndexed(category.topics) { index, topic ->
                TopicEntryCard(
                    index = index + 1,
                    topic = topic,
                    onClick = { onOpenTopic(index) }
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun TopicEntryCard(
    index: Int,
    topic: GrammarTopic,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    Text(
                        index.toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(topic.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(topic.fr, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(3.dp))
                Text(
                    topic.summary,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 主题详情页：讲解、规则、例句 */
@Composable
private fun GrammarTopicView(
    topic: GrammarTopic,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        GrammarHeader(title = topic.title, subtitle = topic.fr, onBack = onBack)

        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            topic.summary,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(topic.sections) { section ->
                    SectionBlock(section)
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun SectionBlock(section: GrammarSection) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!section.heading.isNullOrBlank()) {
            Text(
                section.heading,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
        }

        if (!section.text.isNullOrBlank()) {
            Text(
                section.text,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(6.dp))
        }

        section.bullets.forEach { bullet ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    "•",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(14.dp)
                )
                Text(
                    bullet,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        section.examples.forEach { example ->
            Spacer(Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            example.fr,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            example.zh,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            Espeak.ensureInitialized(context)
                            Espeak.speakWithFeedback(context, example.fr)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
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

        if (!section.note.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "提示",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        section.note,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
