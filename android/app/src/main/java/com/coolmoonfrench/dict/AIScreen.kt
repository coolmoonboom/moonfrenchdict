package com.coolmoonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AIScreen(
    prefs: AIPreferences,
    onOpenSettings: () -> Unit,
    refreshKey: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 会话列表与当前会话 id 缓存为 state，避免每帧重复解析 SharedPreferences 中的大 JSON
    var conversations by remember { mutableStateOf(prefs.loadConversations()) }
    var activeConvId by remember { mutableStateOf(prefs.activeConversationId()) }
    var messages by remember { mutableStateOf(prefs.loadMessages()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var dataReady by remember { mutableStateOf(false) }
    var favVersion by remember { mutableStateOf(0) }
    var showConversations by remember { mutableStateOf(false) }
    var webSearchEnabled by remember { mutableStateOf(false) }
    var ocrLoading by remember { mutableStateOf(false) }
    var ocrError by remember { mutableStateOf<String?>(null) }
    var pendingAttachments by remember { mutableStateOf<List<AIAttachment>>(emptyList()) }
    var pendingAttachmentLoading by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // 首次进入：若无会话则自动新建；并确保 messages 与迁移/新建后的当前会话同步
    LaunchedEffect(Unit) {
        if (conversations.isEmpty()) {
            activeConvId = prefs.newConversation()
            conversations = prefs.loadConversations()
        } else if (activeConvId < 0) {
            activeConvId = conversations.first().id
            prefs.setActiveConversationId(activeConvId)
        }
        messages = prefs.loadMessages()
        dataReady = true
    }

    var currentConfig by remember { mutableStateOf(prefs.modelConfig) }
    val hasConfig = currentConfig.apiUrl.isNotBlank() && currentConfig.apiToken.isNotBlank() && currentConfig.modelName.isNotBlank()

    LaunchedEffect(refreshKey) {
        currentConfig = prefs.modelConfig
        conversations = prefs.loadConversations()
        activeConvId = prefs.activeConversationId()
        messages = prefs.loadMessages()
        dataReady = true
    }

    fun save(m: List<AIMessage>) {
        messages = m
        prefs.saveMessages(m)
    }

    fun startNewConversation() {
        activeConvId = prefs.newConversation()
        conversations = prefs.loadConversations()
        messages = emptyList()
        dataReady = true
        input = ""
        error = null
        loading = false
        showClearConfirm = false
    }

    fun sendQuestion(q: String, attachments: List<AIAttachment> = emptyList()) {
        if (q.isBlank() && attachments.isEmpty()) return
        if (loading) return

        // 首条消息自动命名会话标题
        val currentConv = conversations.firstOrNull { it.id == activeConvId }
        if (currentConv != null && currentConv.messages.isEmpty()) {
            val newTitle = (q.trim().take(30).ifBlank { attachments.firstOrNull()?.name ?: "新对话" })
            val updated = conversations.map { if (it.id == activeConvId) it.copy(title = newTitle) else it }
            prefs.saveConversations(updated)
            conversations = updated
        }

        // 拼接附件文本
        val content = if (attachments.isNotEmpty()) {
            val attachText = attachments.joinToString("\n\n") { a ->
                val info = when (a.type) {
                    "image" -> "[图片：${a.name}]"
                    else -> "[文件：${a.name}]"
                }
                if (a.extractedText.isNotBlank()) {
                    "$info 识别内容：\n${a.extractedText}"
                } else {
                    "$info（未提取到文字内容）"
                }
            }
            if (q.isNotBlank()) {
                q.trim() + "\n\n---\n" + attachText
            } else {
                "请分析以下内容：\n\n$attachText"
            }
        } else {
            q.trim()
        }

        val newList = messages + AIMessage(role = "user", content = content, attachments = attachments)
        save(newList)
        input = ""
        pendingAttachments = emptyList()
        loading = true
        error = null
        scope.launch {
            try {
                val reply = if (webSearchEnabled) {
                    val results = WebSearcher.search(q.trim())
                    val webContext = if (results.isNotEmpty()) {
                        results.joinToString("\n\n") { "- ${it.title}: ${it.snippet}（来源：${it.url}）" }
                    } else {
                        "（本次联网搜索未获取到结果）"
                    }
                    AIClient.chat(currentConfig, newList, webContext)
                } else {
                    AIClient.chat(currentConfig, newList)
                }
                save(newList + AIMessage(role = "assistant", content = reply))
            } catch (e: Exception) {
                error = "请求失败：${e.message?.take(120) ?: "未知错误"}"
            } finally {
                loading = false
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingAttachmentLoading = true
            scope.launch {
                try {
                    val attach = withContext(Dispatchers.IO) {
                        AttachmentExtractor.extract(context, uri, "image/*")
                    }
                    pendingAttachments = pendingAttachments + attach
                } catch (e: Exception) {
                    error = "图片处理失败：${e.message?.take(80) ?: "未知错误"}"
                } finally {
                    pendingAttachmentLoading = false
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingAttachmentLoading = true
            scope.launch {
                try {
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val attach = withContext(Dispatchers.IO) {
                        AttachmentExtractor.extract(context, uri, mime)
                    }
                    pendingAttachments = pendingAttachments + attach
                } catch (e: Exception) {
                    error = "文件处理失败：${e.message?.take(80) ?: "未知错误"}"
                } finally {
                    pendingAttachmentLoading = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏：标题 + 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showConversations = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "对话列表", modifier = Modifier.size(18.dp))
            }
            val currentTitle = conversations
                .firstOrNull { it.id == activeConvId }?.title
                ?: "AI 助手"
            Text(
                currentTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 2.dp)
            )
            if (currentConfig.modelName.isNotBlank()) {
                Text(
                    currentConfig.modelName,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            IconButton(onClick = { startNewConversation() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "新建对话", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "模型设置", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { if (messages.isNotEmpty()) showClearConfirm = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "清空对话", modifier = Modifier.size(18.dp))
            }
        }

        // 会话列表面板
        if (showConversations) {
            ConversationSheet(
                conversations = conversations,
                activeId = activeConvId,
                onDismiss = { showConversations = false },
                onSwitch = { id ->
                    prefs.setActiveConversationId(id)
                    activeConvId = id
                    messages = prefs.loadMessages()
                    showConversations = false
                },
                onNew = {
                    startNewConversation()
                    showConversations = false
                },
                onDelete = { id ->
                    prefs.deleteConversation(id)
                    activeConvId = prefs.activeConversationId()
                    conversations = prefs.loadConversations()
                    messages = prefs.loadMessages()
                }
            )
        }

        // 清空确认对话框
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("清空对话") },
                text = { Text("确定要清除所有对话记录吗？此操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        save(emptyList())
                        showClearConfirm = false
                    }) {
                        Text("确定清除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // 未配置模型提示
        if (!hasConfig) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("尚未配置大模型", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("点击右上角 ⚙ 进入模型设置，填写 API 地址、Token 和模型名称。", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("前往配置")
                    }
                }
            }
        }

        // 对话列表
        if (messages.isEmpty() && !loading) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "与 AI 助手对话，查词、学语法、翻译句子\n\n支持输入法语词、句子或中文问题",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(24.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            var favs by remember { mutableStateOf(prefs.loadAIFavorites().map { it.content }.toSet()) }
            LaunchedEffect(favVersion) {
                favs = prefs.loadAIFavorites().map { it.content }.toSet()
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(messages) { msg ->
                    AIBubble(
                        message = msg,
                        isFavorite = msg.content in favs,
                        onCopy = {
                            val clip = ClipData.newPlainText("ai", msg.content)
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, msg.content)
                            }
                            context.startActivity(Intent.createChooser(intent, "转发到"))
                        },
                        onToggleFavorite = {
                            if (prefs.isAIFavorite(msg.content)) {
                                prefs.removeAIFavoriteByContent(msg.content)
                            } else {
                                prefs.addAIFavorite(msg)
                            }
                            favVersion++
                        }
                    )
                }
                if (loading) {
                    item {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("思考中…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // 消息变化或加载状态变化时自动滚动到底部
        LaunchedEffect(messages.size, loading, dataReady, refreshKey) {
            if (dataReady && (messages.isNotEmpty() || loading)) {
                val target = if (loading) messages.size else messages.size - 1
                if (target >= 0) {
                    listState.scrollToItem(target)
                }
            }
        }

        // 错误提示
        if (error != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { error = null }) { Text("关闭", fontSize = 12.sp) }
            }
        }

        // 输入区
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 待发送附件预览
                if (pendingAttachments.isNotEmpty() || pendingAttachmentLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(pendingAttachments) { a ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (a.type == "image") Icons.Filled.Image else Icons.Filled.InsertDriveFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            a.name,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 80.dp)
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        IconButton(
                                            onClick = { pendingAttachments = pendingAttachments.filterNot { it == a } },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "移除 ${a.name}",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            if (pendingAttachmentLoading) {
                                item {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                        TextButton(
                            onClick = { pendingAttachments = emptyList() },
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("全清", fontSize = 10.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (hasConfig) "输入问题…" else "请先配置模型…", fontSize = 14.sp) },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        enabled = hasConfig
                    )
                    Spacer(Modifier.width(6.dp))
                    FilledTonalIconButton(
                        onClick = { imagePicker.launch("image/*") },
                        enabled = hasConfig && !pendingAttachmentLoading,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = "添加图片",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    FilledTonalIconButton(
                        onClick = {
                            filePicker.launch(arrayOf(
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                "text/plain"
                            ))
                        },
                        enabled = hasConfig && !pendingAttachmentLoading,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.InsertDriveFile,
                            contentDescription = "添加文件",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    FilledTonalIconButton(
                        onClick = { webSearchEnabled = !webSearchEnabled },
                        enabled = hasConfig,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (webSearchEnabled)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (webSearchEnabled)
                                MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            if (webSearchEnabled) Icons.Filled.Public else Icons.Filled.PublicOff,
                            contentDescription = if (webSearchEnabled) "联网已开启" else "开启联网搜索",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(
                        onClick = { sendQuestion(input, pendingAttachments) },
                        enabled = hasConfig && (input.isNotBlank() || pendingAttachments.isNotEmpty()) && !loading,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }
}

@Composable
private fun AIBubble(
    message: AIMessage,
    isFavorite: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.78f else 0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 附件展示
                if (message.attachments.isNotEmpty()) {
                    message.attachments.forEach { a ->
                        if (a.type == "image") {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                AsyncImage(
                                    model = a.localPath,
                                    contentDescription = a.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isUser) MaterialTheme.colorScheme.onPrimary
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        a.name, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        a.name, fontSize = 13.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                if (isUser) {
                    SelectionContainer {
                        Text(
                            message.content,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    SelectionContainer {
                        Markdown(
                            content = MarkdownSanitizer.sanitize(message.content),
                            imageTransformer = Coil3ImageTransformerImpl,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 操作栏：复制 / 转发 / 收藏
        Row(
            modifier = Modifier.padding(top = 2.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RowAction("复制", Icons.Filled.ContentCopy, onCopy)
            RowAction("转发", Icons.Filled.Share, onShare)
            TextButton(onClick = onToggleFavorite, modifier = Modifier.height(28.dp)) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    if (isFavorite) "已收藏" else "收藏",
                    fontSize = 11.sp,
                    color = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RowAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(28.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationSheet(
    conversations: List<AIConversation>,
    activeId: Long,
    onDismiss: () -> Unit,
    onSwitch: (Long) -> Unit,
    onNew: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "对话列表",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onNew) {
                    Icon(Icons.Filled.Add, contentDescription = "新建对话", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("新建对话", fontSize = 12.sp)
                }
            }

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无对话", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(0.6f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(conversations) { conv ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (conv.id == activeId) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { onSwitch(conv.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Chat,
                                contentDescription = null,
                                tint = if (conv.id == activeId)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    conv.title.ifBlank { "新对话" },
                                    fontSize = 12.sp,
                                    fontWeight = if (conv.id == activeId) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (conv.id == activeId)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "共 ${conv.messages.size} 条",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDelete(conv.id) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = "删除对话",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}