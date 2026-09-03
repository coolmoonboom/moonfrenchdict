package com.coolmoonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Html
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 一条问句转换结果：query 为用户输入，reply 为 AI 返回的三种疑问形式 Markdown，failed 为失败提示 */
private data class QTItem(
    val query: String,
    val reply: String? = null,
    val failed: String? = null
)

/** 构造问句转换提示词：要求把任意法语句子转成三种疑问形式 */
private fun buildQuestionPrompt(query: String): String {
    return """
你是法语老师。请把下面这句法语改写成三种常见的疑问表达，逐行给出、行与行之间留一个空行，只输出三个句子，不要讲解、不要多余文字：
1. 一般疑问句（陈述语序，不倒装）：句子
2. est-ce que 疑问句：句子
3. 主谓倒装疑问句：句子

要求：保持原意，若原句本身带疑问词（qui/que/où/quand/comment/pourquoi/combien/quel 等）也要保留；三种形式都用同一个疑问词对应的正确语序。每行开头保留「1.」「2.」「3.」序号。

原句：$query
""".trimIndent()
}

@Composable
fun QuestionTypeScreen(
    prefs: AIPreferences,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<QTItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentConfig by remember { mutableStateOf(prefs.modelConfig) }
    val hasConfig = currentConfig.apiUrl.isNotBlank() &&
        currentConfig.apiToken.isNotBlank() &&
        currentConfig.modelName.isNotBlank()

    val listState = rememberLazyListState()

    // 新结果加入后滚动到底部
    LaunchedEffect(items.size, loading) {
        if (items.isNotEmpty()) {
            listState.scrollToItem(items.size - 1)
        }
    }

    fun convert(q: String) {
        if (q.isBlank() || loading) return
        val config = currentConfig
        if (config.apiUrl.isBlank() || config.apiToken.isBlank() || config.modelName.isBlank()) {
            error = "请先在 AI 设置中配置大模型"
            return
        }
        val query = q.trim()
        input = ""
        error = null
        items = items + QTItem(query = query)
        loading = true
        scope.launch {
            try {
                val prompt = buildQuestionPrompt(query)
                val reply = AIClient.chat(config, listOf(AIMessage("user", prompt)))
                items = items.dropLast(1) + QTItem(query = query, reply = reply.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                items = items.dropLast(1) + QTItem(
                    query = query,
                    failed = e.message?.take(120) ?: "未知错误"
                )
            } finally {
                loading = false
            }
        }
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
            Text(
                "问句类型",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(Icons.Filled.SmartToy, contentDescription = "AI 设置")
            }
        }

        // 说明卡
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                "输入任意法语问句，AI 会同时生成三种疑问形式：一般疑问句（陈述语序）、est-ce que 疑问句、主谓倒装疑问句。需先在右上角配置大模型。",
                modifier = Modifier.padding(12.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))

        // 未配置模型：给出引导
        if (!hasConfig) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("尚未配置大模型", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("问句转换依赖首页的 AI 能力，请先配置模型 API。", fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("前往 AI 设置")
                        }
                    }
                }
            }
            return@Column
        }

        // 对话区
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            if (items.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "输入一句法语问句，即可看到三种疑问形式",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            itemsIndexed(items, key = { index, _ -> index }) { _, item ->
                QuestionTypeItem(
                    item = item,
                    onCopy = {
                        val copyText = item.reply ?: item.query
                        val clip = ClipData.newPlainText("qt", copyText)
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
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
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("转换中…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (error != null) {
                item {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        // 底部输入区
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
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
                    placeholder = { Text("输入法语句子，如：Tu aimes le café ?", fontSize = 14.sp) },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = { convert(input) },
                    enabled = input.isNotBlank() && !loading,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "转换")
                }
            }
        }
    }
}

/** 单条问句转换结果卡片：上方原句，下方 AI 三种疑问形式（系统选择菜单 + Markdown 渲染） */
@Composable
private fun QuestionTypeItem(
    item: QTItem,
    onCopy: () -> Unit
) {
    val fontScaleOverride = LocalDensity.current.fontScale / LocalContext.current.resources.configuration.fontScale

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // 原句（用户侧）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            SelectableMarkdownTextView(
                html = MarkdownToHtml.plainTextToHtml(item.query),
                contentColor = MaterialTheme.colorScheme.onPrimary.toArgb(),
                fontScaleOverride = fontScaleOverride
            )
        }

        Spacer(Modifier.height(4.dp))

        // 结果（AI 侧）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when {
                item.failed != null -> {
                    Text(
                        "转换失败：${item.failed}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                item.reply != null -> {
                    SelectableMarkdownTextView(
                        html = MarkdownToHtml.convert(MarkdownSanitizer.sanitize(item.reply)),
                        contentColor = MaterialTheme.colorScheme.onSurface.toArgb(),
                        fontScaleOverride = fontScaleOverride
                    )
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("转换中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 操作栏
        Row(modifier = Modifier.padding(top = 2.dp, start = 8.dp)) {
            TextButton(onClick = onCopy, modifier = Modifier.height(28.dp)) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(if (item.reply != null) "复制结果" else "复制原句", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 可选中、支持 Markdown 的 TextView 气泡（与首页 AI 气泡一致，长按弹系统文本选择菜单） */
@Composable
private fun SelectableMarkdownTextView(
    html: String,
    contentColor: Int,
    fontScaleOverride: Float
) {
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
                setLineSpacing(0f, 1.2f)
                setPadding(0, 0, 0, 0)
            }
        },
        update = { tv ->
            tv.setTextColor(contentColor)
            tv.textSize = 15f * fontScaleOverride
            tv.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            tv.setLinkTextColor(contentColor)
            tv.layoutParams = tv.layoutParams?.apply {
                width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}