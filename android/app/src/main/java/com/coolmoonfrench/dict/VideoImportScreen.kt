package com.coolmoonfrench.dict

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.coolmoonfrench.dict.room.VideoTextDatabase
import com.coolmoonfrench.dict.room.VideoTextRecord

/**
 * 视频转文字界面。
 *
 * 功能：
 * - 模型选择开关：小模型（内置，快速）/ 大模型（高精度，需下载且内存充足）
 * - 大模型的下载进度条与重试入口
 * - 视频文件选择（系统文档选择器）
 * - 识别中 loading 与识别结果预览
 * - 结果保存到 Room（来源 VIDEO、文件名、时间戳）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoImportScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 模型选择是否默认指向大模型（用户之前选择过才选中，否则小模型）
    var useLarge by remember { mutableStateOf(hadChosenLarge(context)) }
    var largeState by remember {
        mutableStateOf<VoskModelManager.LargeModelState>(VoskModelManager.LargeModelState.NotDownloaded)
    }

    // 内存充足标记（用于提示）
    val enoughMem = remember { VoskModelManager.hasEnoughMemory(context) }

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var savedId by remember { mutableStateOf(-1L) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            // 尝试解析文件名
            selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "video"
            resultText = ""
            errorMsg = ""
            savedId = -1
        }
    }

    // 打开界面时刷新大模型状态
    LaunchedEffect(Unit) {
        largeState = if (VoskModelManager.isLargeReady(context)) {
            VoskModelManager.LargeModelState.Ready
        } else {
            VoskModelManager.LargeModelState.NotDownloaded
        }
        useLarge = hadChosenLarge(context) && VoskModelManager.isLargeReady(context)
    }

    // 大模型下载任务
    var downloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val downloadInProgress = largeState is VoskModelManager.LargeModelState.Downloading

    fun startDownload() {
        if (downloadInProgress) return
        downloadJob?.cancel()
        errorMsg = ""
        // 清理上一次失败状态，避免残留失败文案
        largeState = VoskModelManager.LargeModelState.NotDownloaded
        downloadJob = scope.launch {
            try {
                VoskModelManager.downloadLargeModel(context) { state ->
                    largeState = state
                }
            } catch (e: Exception) {
                // downloadLargeModel 内部已回调 DownloadFailed；若在状态推送前就抛错，这里兜底
                if (largeState !is VoskModelManager.LargeModelState.DownloadFailed) {
                    largeState = VoskModelManager.LargeModelState.NotDownloaded
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        largeState = VoskModelManager.LargeModelState.NotDownloaded
    }

    fun startRecognition() {
        val uri = selectedUri ?: run {
            errorMsg = "请先选择视频文件"
            return
        }
        if (!enoughMem && useLarge) {
            errorMsg = "当前设备内存小于 4GB，不建议使用大模型，请切换小模型"
            return
        }
        if (useLarge && largeState !is VoskModelManager.LargeModelState.Ready) {
            errorMsg = "大模型尚未就绪，请先下载完成或切换到小模型"
            return
        }
        busy = true
        resultText = ""
        errorMsg = ""
        savedId = -1
        scope.launch {
            val res = VideoToText.processVideo(context, uri)
            busy = false
            res.fold(
                onSuccess = { text ->
                    resultText = text
                    // 保存到 Room
                    try {
                        val db = VideoTextDatabase.get(context)
                        val id = db.videoTextDao().insert(
                            VideoTextRecord(
                                source = "VIDEO",
                                fileName = selectedName,
                                text = text,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        savedId = id
                    } catch (e: Exception) {
                        errorMsg = "识别成功，但保存记录失败：${e.message}"
                    }
                },
                onFailure = { e ->
                    errorMsg = e.message ?: "识别失败"
                }
            )
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text("视频转文字", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------- 模型选择 ----------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("识别模型", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !useLarge,
                            onClick = {
                                useLarge = false
                                VoskModelManager.setModelChoice(context, large = false)
                            }
                        )
                        Column {
                            Text("小模型（内置，快速）", fontSize = 15.sp)
                            Text("无需网络，支持离线使用", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            enabled = enoughMem,
                            selected = useLarge,
                            onClick = {
                                useLarge = true
                                VoskModelManager.setModelChoice(context, large = true)
                            }
                        )
                        Column(Modifier.weight(1f)) {
                            Text("大模型（高精度，需下载）", fontSize = 15.sp)
                            Text("识别更准确，占空间约 1.4GB", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (!enoughMem) {
                        Text(
                            "当前设备内存不足 4GB，大模型不可用",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // 大模型下载区
                    if (useLarge) {
                        when (val st = largeState) {
                            is VoskModelManager.LargeModelState.Ready -> {
                                Text("大模型已就绪", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            is VoskModelManager.LargeModelState.Downloading -> {
                                LinearProgressIndicator(
                                    progress = { st.percent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("下载中：${st.percent}%  ${st.bytesRead / 1024 / 1024}/${st.totalBytes / 1024 / 1024} MB", fontSize = 12.sp)
                                TextButton(onClick = { cancelDownload() }) { Text("取消下载") }
                            }
                            is VoskModelManager.LargeModelState.DownloadFailed -> {
                                Text("下载失败：${st.message}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                Button(onClick = { startDownload() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("重试下载", fontSize = 13.sp)
                                }
                            }
                            is VoskModelManager.LargeModelState.NotDownloaded -> {
                                Button(onClick = { startDownload() }) {
                                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("下载大模型")
                                }
                            }
                        }
                    }
                }
            }

            // ---------- 视频选择 ----------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("选择视频", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    if (selectedUri != null) {
                        Text("已选择：$selectedName", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    OutlinedButton(
                        onClick = { videoPicker.launch(arrayOf("video/*")) },
                        enabled = !busy
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (selectedUri == null) "选择视频文件" else "重新选择")
                    }
                }
            }

            // ---------- 开始识别 ----------
            if (selectedUri != null && !busy) {
                Button(
                    onClick = { startRecognition() },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("开始识别")
                }
            }

            if (busy) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("识别中…视频越长耗时越久", fontSize = 14.sp)
                }
            }

            // ---------- 错误信息 ----------
            if (errorMsg.isNotEmpty()) {
                Text(
                    errorMsg,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ---------- 识别结果 ----------
            if (resultText.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("识别结果", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            if (savedId > 0) {
                                Text("已保存", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(
                            resultText,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(12.dp)
                        )
                        if (savedId > 0) {
                            Text(
                                "来源：VIDEO  时间：${
                                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date())
                                }",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 读取用户上次选择的模型模式（仅用于初始选中态，若大模型未就绪会被强制回落小模型） */
private fun hadChosenLarge(context: android.content.Context): Boolean {
    return VoskModelManager.getModelChoice(context) is VoskModelManager.ModelOption.Large
}