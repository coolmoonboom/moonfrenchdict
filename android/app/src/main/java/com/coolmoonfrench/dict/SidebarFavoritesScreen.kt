package com.coolmoonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SidebarFavoritesScreen(
    prefs: AIPreferences,
    repository: DictRepository,
    onBack: () -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var selectedFavId by remember { mutableStateOf<Long?>(null) }

    // 系统返回键：先关闭收藏详情，再返回上一级
    BackHandler(enabled = selectedFavId != null) { selectedFavId = null }

    if (selectedFavId != null) {
        AIFavoriteDetailScreen(
            prefs = prefs,
            favoriteId = selectedFavId!!,
            onBack = { selectedFavId = null },
            onChanged = {}
        )
        return
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
            Text("收藏", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // 双栏 Tab
        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) {
                Text("AI 收藏", modifier = Modifier.padding(vertical = 12.dp))
            }
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) {
                Text("单词·句子收藏", modifier = Modifier.padding(vertical = 12.dp))
            }
        }

        when (tabIndex) {
            0 -> AIFavoritesTab(prefs, onOpen = { selectedFavId = it })
            1 -> WordSentenceFavoritesTab(prefs, repository)
        }
    }
}

@Composable
private fun AIFavoritesTab(prefs: AIPreferences, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    var favorites by remember { mutableStateOf(prefs.loadAIFavorites()) }
    var refresh by remember { mutableStateOf(0) }

    fun reload() {
        favorites = prefs.loadAIFavorites()
        refresh++
    }

    LaunchedEffect(refresh) { reload() }

    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无 AI 收藏。在 AI 对话中点击 ☆ 收藏消息。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) {
        items(favorites) { fav ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { onOpen(fav.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (fav.role == "user")
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        if (fav.role == "user") "问：" else "答：",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        fav.content,
                        fontSize = 14.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = {
                            val clip = ClipData.newPlainText("ai", fav.content)
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                        }
                        TextButton(onClick = {
                            prefs.removeAIFavorite(fav.id)
                            reload()
                        }) {
                            Text("移除", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordSentenceFavoritesTab(prefs: AIPreferences, repository: DictRepository) {
    val context = LocalContext.current
    var wordFavs by remember { mutableStateOf(repository.loadFavorites()) }
    var sentenceFavs by remember { mutableStateOf(prefs.loadSentenceFavorites()) }
    var refresh by remember { mutableStateOf(0) }

    // 预热 Mimic 法语 TTS（幂等，非阻塞）
    LaunchedEffect(Unit) {
        Espeak.ensureInitialized(context)
    }

    fun reload() {
        wordFavs = repository.loadFavorites()
        sentenceFavs = prefs.loadSentenceFavorites()
        refresh++
    }

    LaunchedEffect(refresh) { reload() }

    val hasWords = wordFavs.isNotEmpty()
    val hasSentences = sentenceFavs.isNotEmpty()

    if (!hasWords && !hasSentences) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无收藏。可在查词界面或句子分析中收藏。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 句子收藏
        if (hasSentences) {
            Text(
                "句子收藏",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            sentenceFavs.forEach { saved ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(saved.sentence, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        if (saved.translation.isNotBlank()) {
                            Text(
                                saved.translation,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = {
                                Espeak.ensureInitialized(context)
                                if (!Espeak.speak(saved.sentence)) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "朗读失败：${Espeak.lastError() ?: "未知错误"}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "朗读", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = {
                                val clip = ClipData.newPlainText("sentence", "${saved.sentence}\n${saved.translation}")
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                            }
                            TextButton(onClick = {
                                prefs.removeSentenceFavorite(saved.sentence)
                                reload()
                            }) {
                                Text("移除", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 单词收藏
        if (hasWords) {
            Text(
                "单词收藏",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).padding(top = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            wordFavs.forEach { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.word, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(
                                entry.meaning.take(60),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            Espeak.ensureInitialized(context)
                            if (!Espeak.speak(entry.word)) {
                                android.widget.Toast.makeText(
                                    context,
                                    "朗读失败：${Espeak.lastError() ?: "未知错误"}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "朗读 ${entry.word}", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = {
                            val clip = ClipData.newPlainText("word", "${entry.word}\n${entry.meaning}")
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                        }
                        TextButton(onClick = {
                            repository.removeFavorite(entry.word)
                            reload()
                        }) {
                            Text("移除", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}