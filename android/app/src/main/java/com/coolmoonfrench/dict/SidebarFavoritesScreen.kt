package com.coolmoonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        // 四个分类 Tab
        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) {
                Text("AI 收藏", modifier = Modifier.padding(vertical = 12.dp))
            }
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) {
                Text("单词", modifier = Modifier.padding(vertical = 12.dp))
            }
            Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }) {
                Text("句子", modifier = Modifier.padding(vertical = 12.dp))
            }
            Tab(selected = tabIndex == 3, onClick = { tabIndex = 3 }) {
                Text("视频转文字", modifier = Modifier.padding(vertical = 12.dp))
            }
        }

        when (tabIndex) {
            0 -> AIFavoritesTab(prefs, onOpen = { selectedFavId = it })
            1 -> WordFavoritesTab(repository, prefs)
            2 -> SentenceFavoritesTab(prefs)
            3 -> VideoTextFavoritesTab(prefs)
        }
    }
}

@Composable
private fun AIFavoritesTab(prefs: AIPreferences, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    var favorites by remember { mutableStateOf(prefs.loadAIFavorites()) }

    fun reload() {
        favorites = prefs.loadAIFavorites()
    }

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordFavoritesTab(repository: DictRepository, aiPrefs: AIPreferences) {
    val context = LocalContext.current
    var wordFavs by remember { mutableStateOf(repository.loadFavorites()) }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var refining by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()

    // 预热 Mimic 法语 TTS（幂等，非阻塞），同时刷新收藏（词书新增收藏后重进可见）
    LaunchedEffect(Unit) {
        Speech.ensureInitialized(context)
        wordFavs = repository.loadFavorites()
    }

    fun exitSelection() {
        selectionMode = false
        selected.clear()
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    if (wordFavs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无单词收藏。可在查词界面点击 ☆ 收藏单词。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        return
    }

    if (refining) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("整理收藏") },
            text = { Text("正在把所选 ${selected.size} 个词发给 AI，逐条改写为中/英/音标/例句统一格式…") },
            confirmButton = {}
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 多选模式顶部操作条：全选 / 复制 / 整理 / 播放 / 移除 / 取消
        if (selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已选 ${selected.size}/${wordFavs.size}", fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    selected.clear()
                    selected.addAll(wordFavs.map { it.word })
                }) { Text("全选", fontSize = 13.sp) }
                TextButton(
                    onClick = {
                        // 批量复制：按列表顺序，每个单词内容（单词 + 释义）之间用回车分隔，不加头尾
                        val picked = wordFavs.filter { selected.contains(it.word) }
                        val text = picked.joinToString("\n") { "${it.word}\n${it.meaning}" }
                        if (text.isNotEmpty()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("words", text))
                            Toast.makeText(context, "已复制 ${picked.size} 个单词", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = selected.isNotEmpty()
                ) { Text("复制", fontSize = 13.sp) }
                TextButton(
                    onClick = {
                        // 整理：把所选词的「原词 + 当前释义」交给 AI，逐条改写成标准中文词条后
                        // 覆盖写回收藏；AI 未返回中文结果的词保留原义，不做破坏性覆盖。
                        val config = aiPrefs.modelConfig
                        if (!IpaService.isConfigured(config)) {
                            Toast.makeText(context, "尚未配置 AI 模型，请先在 AI 设置中配置", Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        val picked = wordFavs.filter { selected.contains(it.word) }
                        if (picked.isEmpty()) return@TextButton
                        refining = true
                        uiScope.launch {
                            var done = 0
                            picked.chunked(FavoriteRefiner.CHUNK_SIZE).forEach { chunk ->
                                val results = withContext(Dispatchers.IO) {
                                    runCatching {
                                        FavoriteRefiner.refine(config, chunk.map { it.word to it.meaning })
                                    }.getOrElse { emptyList() }
                                }
                                // AI 偶发改写大小写/空格：按小写原词回配收藏键，未命中则用返回词原文。
                                val keyIndex = chunk.associateBy { it.word.lowercase() }
                                results.forEach { r ->
                                    val key = keyIndex[r.word.lowercase()]?.word ?: r.word
                                    val meaning = ImportWordParser.buildMeaning(r)
                                    if (meaning.isNotBlank()) {
                                        repository.addFavorite(key, meaning)
                                        done++
                                    }
                                }
                            }
                            refining = false
                            Toast.makeText(
                                context,
                                if (done > 0) "已整理 $done/${picked.size} 个词的中文义项"
                                else "AI 本次未能整理出有效中文义项",
                                Toast.LENGTH_LONG
                            ).show()
                            if (done > 0) {
                                wordFavs = repository.loadFavorites()
                                exitSelection()
                            }
                        }
                    },
                    enabled = selected.isNotEmpty() && !refining
                ) { Text(if (refining) "整理中…" else "整理", fontSize = 13.sp) }
                TextButton(
                    onClick = {
                        // 播放：把所选单词交给悬浮窗，按列表顺序循环播报（与已掌握列表一致）
                        val picked = wordFavs
                            .filter { selected.contains(it.word) }
                            .map { VocabEntry(it.word, it.pos, "", it.meaning, false) }
                        if (picked.isEmpty()) return@TextButton
                        if (!FloatingWindowControl.overlayPermissionGranted(context)) {
                            Toast.makeText(
                                context,
                                "需要悬浮窗权限，请在系统设置中允许「酷月法语」显示在其他应用上层",
                                Toast.LENGTH_LONG
                            ).show()
                            FloatingWindowControl.requestPermission(context)
                            return@TextButton
                        }
                        FloatingWindowControl.startListLoop(context, picked, 0, revealMeaning = true)
                        exitSelection()
                    },
                    enabled = selected.isNotEmpty()
                ) { Text("播放", fontSize = 13.sp) }
                TextButton(
                    onClick = {
                        selected.toList().forEach { repository.removeFavorite(it) }
                        wordFavs = repository.loadFavorites()
                        exitSelection()
                    },
                    enabled = selected.isNotEmpty()
                ) { Text("移除", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { exitSelection() }) { Text("取消", fontSize = 13.sp) }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            items(wordFavs, key = { it.word }) { entry ->
                val isSelected = selected.contains(entry.word)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) {
                                    if (isSelected) selected.remove(entry.word)
                                    else selected.add(entry.word)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    if (!selected.contains(entry.word)) selected.add(entry.word)
                                }
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 多选模式：每词左侧出现勾选框
                        if (selectionMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!selected.contains(entry.word)) selected.add(entry.word)
                                    } else {
                                        selected.remove(entry.word)
                                    }
                                }
                            )
                        }
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
                            Speech.ensureInitialized(context)
                            Speech.speakWithFeedback(context, entry.word)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "朗读 ${entry.word}", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = {
                            val clip = ClipData.newPlainText("word", "${entry.word}\n${entry.meaning}")
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SentenceFavoritesTab(prefs: AIPreferences) {
    val context = LocalContext.current
    var sentenceFavs by remember { mutableStateOf(prefs.loadSentenceFavorites()) }

    // 预热 Mimic 法语 TTS（幂等，非阻塞）
    LaunchedEffect(Unit) {
        Speech.ensureInitialized(context)
    }

    if (sentenceFavs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无句子收藏。可在句子分析界面点击 ☆ 收藏句子。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) {
        items(sentenceFavs) { saved ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
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
                            Speech.ensureInitialized(context)
                                    Speech.speakWithFeedback(context, saved.sentence)
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
                            sentenceFavs = prefs.loadSentenceFavorites()
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
private fun VideoTextFavoritesTab(prefs: AIPreferences) {
    val context = LocalContext.current
    var videoFavs by remember { mutableStateOf(prefs.loadVideoTextFavorites()) }

    if (videoFavs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无视频转文字收藏。可在视频转文字识别结果中点击 ☆ 收藏。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) {
        items(videoFavs) { fav ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        fav.text,
                        fontSize = 14.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (fav.fileName.isNotBlank()) {
                        Text(
                            "来源：${fav.fileName}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = {
                            val clip = ClipData.newPlainText("video_text", fav.text)
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                        }
                        TextButton(onClick = {
                            prefs.removeVideoTextFavorite(fav.text)
                            videoFavs = prefs.loadVideoTextFavorites()
                        }) {
                            Text("移除", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}