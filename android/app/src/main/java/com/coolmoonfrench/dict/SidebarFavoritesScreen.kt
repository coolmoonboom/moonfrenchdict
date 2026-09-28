package com.coolmoonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.imePadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarFavoritesScreen(
    prefs: AIPreferences,
    repository: DictRepository,
    onBack: () -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var selectedFavId by remember { mutableStateOf<Long?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showBatchAiSettings by remember { mutableStateOf(false) }

    // 长按「单词」标签 → 打开 AI 识别导入界面；界面内「去配置 AI」再进入批量专用模型设置
    if (showImport) {
        if (showBatchAiSettings) {
            AISettingsScreen(prefs = prefs, onBack = { showBatchAiSettings = false }, batch = true)
            return
        }
        ImportScreen(
            prefs = prefs,
            repository = repository,
            onBack = { showImport = false },
            onOpenSettings = { showBatchAiSettings = true }
        )
        return
    }

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

        // 四个分类 Tab；长按「单词」标签直接进入 AI 识别导入界面
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("AI 收藏", "单词", "句子", "视频转文字").forEachIndexed { idx, label ->
                val sel = tabIndex == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .combinedClickable(
                            onClick = { tabIndex = idx },
                            onLongClick = { if (idx == 1) { tabIndex = 1; showImport = true } }
                        )
                ) {
                    Text(
                        label,
                        color = if (sel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    if (sel) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
        Text(
            "长按「单词」标签可进入 AI 识别导入",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp)
        )
        HorizontalDivider()

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
                        MarkdownSanitizer.previewPlain(fav.content),
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
    var editTarget by remember { mutableStateOf<DictEntry?>(null) }
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
            text = { Text("正在获取内容…（${selected.size} 个词）") },
            confirmButton = {}
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 多选模式顶部操作条：全选 / 复制 / 整理 / 播放 / 移除 / 取消
        if (selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已选 ${selected.size}/${wordFavs.size}", fontSize = 13.sp, maxLines = 1)
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
                        val config = aiPrefs.effectiveBatchConfig
                        if (!IpaService.isConfigured(config)) {
                            Toast.makeText(context, "尚未配置 AI 模型，请先在 AI 设置中配置", Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        val picked = wordFavs.filter { selected.contains(it.word) }
                        if (picked.isEmpty()) return@TextButton
                        refining = true
                        uiScope.launch {
                            // 两遍整理：常规批量后，本轮没整出中文的词（多为英文词典位）
                            // 再按 5 词小批喂 AI 重试；AI 乱改词头的返回直接丢弃不写新键。
                            val okKeys = mutableSetOf<String>()
                            suspend fun pass(words: List<Pair<String, String>>) {
                                val results = runCatching {
                                    FavoriteRefiner.refine(config, words)
                                }.getOrElse { emptyList() }
                                val keyIndex = words.associateBy {
                                    FavoriteMeaning.normalizeWordKey(it.first).lowercase()
                                }
                                results.forEach { r ->
                                    val orig = keyIndex[FavoriteMeaning.normalizeWordKey(r.word).lowercase()]?.first
                                        ?: return@forEach
                                    val rm = ImportWordParser.buildMeaning(r)
                                    if (rm.isNotBlank()) withContext(Dispatchers.IO) {
                                        repository.addFavorite(orig, rm)
                                        okKeys.add(FavoriteMeaning.normalizeWordKey(orig).lowercase())
                                    }
                                }
                            }
                            val failed = withContext(Dispatchers.IO) {
                                picked.chunked(FavoriteRefiner.CHUNK_SIZE).forEach { chunk ->
                                    pass(chunk.map { it.word to it.meaning })
                                }
                                val stubborn = picked.filter {
                                    FavoriteMeaning.normalizeWordKey(it.word).lowercase() !in okKeys
                                }
                                stubborn.chunked(5).forEach { group ->
                                    pass(group.map { it.word to it.meaning })
                                }
                                picked.filter {
                                    FavoriteMeaning.normalizeWordKey(it.word).lowercase() !in okKeys
                                }.map { it.word }
                            }
                            refining = false
                            val done = picked.size - failed.size
                            val msg = when {
                                done == 0 -> "AI 本次未能整理出有效中文义项"
                                failed.isEmpty() -> "已整理 $done/${picked.size} 个词的中文义项"
                                else -> {
                                    val shown = failed.take(4).joinToString("、")
                                    val more = if (failed.size > 4) " 等 ${failed.size} 个" else ""
                                    "已整理 $done/${picked.size}；暂未中文化 ${failed.size} 个: $shown$more"
                                }
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
            itemsIndexed(wordFavs, key = { index, entry -> entry.word + "#" + index }) { _, entry ->
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
                            // 只显示词性+中文义项；音标/例句只进悬浮窗，不占列表行
                            Text(
                                FavoriteMeaning.parse(entry.meaning).gloss(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { editTarget = entry }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑 ${entry.word}", modifier = Modifier.size(16.dp))
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

        // 编辑弹窗：可改单词、词性、音标、中文释义与法语/中文例句（例句仅悬浮窗展示朗读）
        editTarget?.let { target ->
            FavoriteWordEditDialog(
                entry = target,
                onDismiss = { editTarget = null },
                onSave = { edited ->
                    val w = edited.word.trim()
                    val rm = ImportWordParser.buildMeaning(edited)
                    if (w.isNotEmpty() && rm.isNotBlank()) {
                        repository.addFavorite(w, rm)
                        if (FavoriteMeaning.normalizeWordKey(w) !=
                            FavoriteMeaning.normalizeWordKey(target.word)
                        ) {
                            repository.removeFavorite(target.word)
                        }
                        wordFavs = repository.loadFavorites()
                        Toast.makeText(context, "已保存修改", Toast.LENGTH_SHORT).show()
                    }
                    editTarget = null
                }
            )
        }
    }
}

/** 收藏词条编辑器：与 AI 识别预览同一套字段口径，保存后覆盖写回原收藏键。 */
@Composable
private fun FavoriteWordEditDialog(
    entry: DictEntry,
    onDismiss: () -> Unit,
    onSave: (ImportedWord) -> Unit
) {
    val w = remember(entry.word) {
        val parsed = FavoriteMeaning.parse(entry.meaning)
        ImportedWord(
            word = entry.word,
            pos = parsed.pos.ifBlank { entry.pos },
            ipa = parsed.ipa,
            meaning = parsed.zh,
            example = parsed.exampleFr,
            exampleZh = parsed.exampleZh
        )
    }
    var head by remember { mutableStateOf(ImportWordParser.formatHead(w.word, w.pos, w.ipa, w.meaning)) }
    var example by remember { mutableStateOf(w.example) }
    var exampleZh by remember { mutableStateOf(w.exampleZh) }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                Text("编辑词条", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("单词｜词性｜音标｜释义", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = head,
                    onValueChange = {
                        head = it
                        val p = ImportWordParser.splitHead(it)
                        w.word = p.word; w.pos = p.pos; w.ipa = p.ipa; w.meaning = p.meaning
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    placeholder = { Text("bannir｜v.t.｜/ba.niʁ/｜封禁，驱逐") }
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it; w.example = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    placeholder = { Text("法语例句（仅悬浮窗展示朗读）") }
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = exampleZh,
                    onValueChange = { exampleZh = it; w.exampleZh = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    placeholder = { Text("例句中文翻译（仅悬浮窗展示朗读）") }
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onSave(w) }) { Text("保存") }
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