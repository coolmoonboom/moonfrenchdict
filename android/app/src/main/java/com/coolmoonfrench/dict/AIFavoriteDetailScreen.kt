package com.coolmoonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIFavoriteDetailScreen(
    prefs: AIPreferences,
    favoriteId: Long,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var favorite by remember { mutableStateOf(prefs.getAIFavorite(favoriteId)) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(favorite?.content ?: "") }
    var notice by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    if (favorite == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("收藏不存在或已删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // 系统返回键：编辑状态先取消编辑，否则返回上一级
    BackHandler(enabled = editing) {
        draft = favorite!!.content
        editing = false
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val path = prefs.copyImageToStorage(context, uri)
            if (path != null) {
                draft += "\n![图片]($path)\n"
                notice = "已插入图片"
            } else {
                notice = "图片保存失败"
            }
        }
    }

    fun copyContent() {
        val clip = ClipData.newPlainText("ai", favorite!!.content)
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        notice = "已复制"
    }

    fun saveEdit() {
        val newContent = draft.trim()
        if (newContent.isBlank()) {
            notice = "内容不能为空"
            return
        }
        prefs.updateAIFavorite(favoriteId, newContent)
        favorite = prefs.getAIFavorite(favoriteId)
        editing = false
        notice = "已保存"
        onChanged()
    }

    fun shareImage() {
        scope.launch {
            exporting = true
            notice = null
            val ok = withContext(Dispatchers.IO) {
                try {
                    val bmp = AIExportHelper.renderTextToBitmap(favorite!!.content)
                    val file = File(context.cacheDir, "fav_${favoriteId}_img.png")
                    file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    true
                } catch (e: Exception) { false }
            }
            exporting = false
            if (ok) {
                AIExportHelper.shareFile(context, File(context.cacheDir, "fav_${favoriteId}_img.png"), "image/png", "分享图片")
            } else {
                notice = "生成图片失败"
            }
        }
    }

    fun sharePdf() {
        scope.launch {
            exporting = true
            notice = null
            val ok = withContext(Dispatchers.IO) {
                try {
                    val bmp = AIExportHelper.renderTextToBitmap(favorite!!.content)
                    val file = File(context.cacheDir, "fav_${favoriteId}.pdf")
                    AIExportHelper.bitmapToPdf(bmp, file)
                } catch (e: Exception) { false }
            }
            exporting = false
            if (ok) {
                AIExportHelper.shareFile(context, File(context.cacheDir, "fav_${favoriteId}.pdf"), "application/pdf", "分享 PDF")
            } else {
                notice = "生成 PDF 失败"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editing) "编辑收藏" else "收藏详情",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!editing) {
                        IconButton(onClick = { copyContent() }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
                        }
                        IconButton(onClick = { shareImage() }, enabled = !exporting) {
                            Icon(Icons.Filled.Share, contentDescription = "分享图片")
                        }
                        IconButton(onClick = { sharePdf() }, enabled = !exporting) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "分享PDF")
                        }
                        IconButton(onClick = { draft = favorite!!.content; editing = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
            if (exporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (editing) {
                // 编辑模式
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "支持 Markdown 语法，可用「插入图片」添加图片",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.height(34.dp)) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("插入图片", fontSize = 12.sp)
                        }
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        placeholder = { Text("输入内容…") },
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { editing = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { saveEdit() }) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("保存")
                        }
                    }
                }
            } else {
                // 展示模式：Markdown 渲染
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // 类型标签
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (favorite!!.role == "user") "问：" else "答：",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Markdown(
                        content = MarkdownSanitizer.sanitize(favorite!!.content),
                        imageTransformer = Coil3ImageTransformerImpl
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            }

            // 底部提示
            notice?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                ) {
                    Text(msg, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp)
                }
            }
        }
    }
}