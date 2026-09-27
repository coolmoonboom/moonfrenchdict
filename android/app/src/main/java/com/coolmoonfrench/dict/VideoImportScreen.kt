package com.coolmoonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
    onBack: () -> Unit,
    onExtractSubtitles: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AIPreferences(context) }

    // 识别模型选择（sherpa-onnx 两方案）；未下载时识别静默使用内置轻模型
    var asrEngine by remember { mutableStateOf(AsrModelManager.selected(context)) }
    var asrState by remember { mutableStateOf(AsrModelManager.initialState(context, asrEngine)) }

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var savedId by remember { mutableStateOf(-1L) }

    // 当前识别结果是否已收藏；以及收藏列表（用于展示/移除）
    var isFav by remember { mutableStateOf(false) }
    var favList by remember { mutableStateOf(prefs.loadVideoTextFavorites()) }

    // 二级页面：收藏文字查看页 / 在线视频字幕历史 / 识别结果全文弹窗
    var showFavViewer by remember { mutableStateOf(false) }
    var showSubtitleSessions by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }

    fun reloadFavorites() {
        favList = prefs.loadVideoTextFavorites()
        isFav = resultText.isNotEmpty() && prefs.isVideoTextFavorite(resultText)
    }

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

    // 大模型下载任务
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var pausedHint by remember { mutableStateOf(false) }

    // 本地 vosk 大模型 zip 导入
    var voskLargeReady by remember { mutableStateOf(VoskModelManager.isLargeReady(context)) }
    var voskZipBusy by remember { mutableStateOf(false) }
    var voskJob by remember { mutableStateOf<Job?>(null) }
    val voskImportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !voskZipBusy) {
            errorMsg = ""
            voskZipBusy = true
            voskJob = scope.launch {
                val res = runCatching {
                    VoskModelManager.importLargeModel(context, uri) { }
                }
                voskJob = null
                voskZipBusy = false
                if (res.isSuccess) {
                    VoskModelManager.setModelChoice(context, large = true)
                    voskLargeReady = VoskModelManager.isLargeReady(context)
                    android.widget.Toast.makeText(
                        context, "本地模型导入完成：FR/Whisper 未就绪时识别将自动使用它",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    errorMsg = "本地模型导入失败：${res.exceptionOrNull()?.message ?: "文件无效"}"
                }
            }
        }
    }

    // 离开界面（含退后台）自动暂停正在进行的下载，半截文件保留供续传
    DisposableEffect(Unit) {
        onDispose {
            AsrModelManager.pauseDownload()
            voskJob?.cancel()
        }
    }

    // 打开界面 / 切换引擎 / 任务结束时刷新模型状态（暂停态保留进度提示）
    LaunchedEffect(asrEngine, downloadJob) {
        if (downloadJob == null && !pausedHint) {
            asrState = AsrModelManager.initialState(context, asrEngine)
        }
    }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }
    val downloadInProgress = downloadJob != null ||
        asrState is AsrModelManager.State.Downloading ||
        asrState is AsrModelManager.State.Installing

    fun startAsrDownload() {
        if (downloadInProgress) return
        errorMsg = ""
        pausedHint = false
        asrState = AsrModelManager.State.Downloading(0, 0, 0)
        downloadJob = scope.launch {
            try {
                if (asrEngine == AsrModelManager.Engine.FR) {
                    AsrModelManager.downloadFr(context) {
                        asrState = it
                        pausedHint = it is AsrModelManager.State.Paused
                    }
                } else {
                    AsrModelManager.downloadWhisper(context) {
                        asrState = it
                        pausedHint = it is AsrModelManager.State.Paused
                    }
                }
            } catch (e: Exception) {
                if (asrState !is AsrModelManager.State.Failed) {
                    asrState = AsrModelManager.State.NotDownloaded
                }
            } finally {
                downloadJob = null
            }
        }
    }

    fun cancelAsrDownload() {
        downloadJob?.cancel()
        downloadJob = null
        asrState = AsrModelManager.initialState(context, asrEngine)
    }

    fun selectAsrEngine(engine: AsrModelManager.Engine) {
        if (downloadInProgress) return
        asrEngine = engine
        AsrModelManager.setSelected(context, engine)
        asrState = AsrModelManager.initialState(context, engine)
    }

    fun uninstallAsrModel(engine: AsrModelManager.Engine) {
        downloadJob?.cancel()
        downloadJob = null
        AsrModelManager.deleteModel(context, engine)
        asrState = AsrModelManager.initialState(context, asrEngine)
    }

    fun startRecognition() {
        val uri = selectedUri ?: run {
            errorMsg = "请先选择视频文件"
            return
        }
        // 识别前实时校验内存（Whisper 模型加载需要约 3GB 可用内存）
        if (asrEngine == AsrModelManager.Engine.WHISPER &&
            AsrModelManager.isWhisperReady(context) &&
            !AsrModelManager.hasEnoughMemoryForWhisper(context)
        ) {
            errorMsg = "当前设备内存不足（可用内存 < 3GB），请切换法语快速模型"
            return
        }
        busy = true
        resultText = ""
        errorMsg = ""
        savedId = -1
        isFav = false
        recognitionJob?.cancel()
        recognitionJob = scope.launch {
            val res = VideoToText.processVideo(context, uri)
            if (!isActive) return@launch
            busy = false
            recognitionJob = null
            res.fold(
                onSuccess = { text ->
                    resultText = text
                    isFav = prefs.isVideoTextFavorite(text)
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

    /**
     * 界面进入后台（ON_STOP）时取消进行中的识别与下载：
     * 大模型识别本身是 CPU/内存密集的原生循环，若用户切后台仍继续跑，会在回前台时造成
     * 内存压力下的卡死。取消后 busy 复位，回到前台可重新开始识别。
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                recognitionJob?.cancel()
                recognitionJob = null
                downloadJob?.cancel()
                downloadJob = null
                if (busy) {
                    busy = false
                    errorMsg = "识别已取消（切到后台终止），请回到前台重新识别"
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ---------- 二级页面：收藏文字查看 ----------
    if (showFavViewer) {
        VideoTextFavoritesScreen(prefs = prefs, onBack = {
            showFavViewer = false
            reloadFavorites()
        })
        return
    }

    // ---------- 二级页面：在线视频字幕历史 ----------
    if (showSubtitleSessions) {
        SubtitleSessionsScreen(prefs = prefs, onBack = { showSubtitleSessions = false })
        return
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
            // ---------- 识别模型 ----------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("识别模型", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(
                        "未下载模型时，默认使用内置轻量识别，无需下载。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AsrEngineRow(
                        title = "法语快速模型（约 52MB）",
                        subtitle = "纯法语优化，识别速度快，适合日常使用",
                        selected = asrEngine == AsrModelManager.Engine.FR,
                        state = if (asrEngine == AsrModelManager.Engine.FR) asrState
                            else if (AsrModelManager.isFrReady(context)) AsrModelManager.State.Ready else AsrModelManager.State.NotDownloaded,
                        busy = downloadInProgress,
                        warning = null,
                        onSelect = { selectAsrEngine(AsrModelManager.Engine.FR) },
                        onDownload = { startAsrDownload() },
                        onCancel = { cancelAsrDownload() },
                        onPause = { AsrModelManager.pauseDownload() },
                        onUninstall = { uninstallAsrModel(AsrModelManager.Engine.FR) }
                    )
                    AsrEngineRow(
                        title = "Whisper 高精度模型（约 1GB）",
                        subtitle = "Whisper large-v3-turbo，识别更准，输出法语 + 中文双语",
                        selected = asrEngine == AsrModelManager.Engine.WHISPER,
                        state = if (asrEngine == AsrModelManager.Engine.WHISPER) asrState
                            else if (AsrModelManager.isWhisperReady(context)) AsrModelManager.State.Ready else AsrModelManager.State.NotDownloaded,
                        busy = downloadInProgress,
                        warning = if (asrEngine == AsrModelManager.Engine.WHISPER &&
                            AsrModelManager.isWhisperReady(context) &&
                            !AsrModelManager.hasEnoughMemoryForWhisper(context)
                        ) "当前可用内存低于 3GB，识别可能卡顿" else null,
                        onSelect = { selectAsrEngine(AsrModelManager.Engine.WHISPER) },
                        onDownload = { startAsrDownload() },
                        onCancel = { cancelAsrDownload() },
                        onPause = { AsrModelManager.pauseDownload() },
                        onUninstall = { uninstallAsrModel(AsrModelManager.Engine.WHISPER) }
                    )
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("本地模型（vosk 大模型 zip）", fontSize = 15.sp)
                            Text(
                                if (voskLargeReady) "已导入；选择模型未就绪时识别自动使用它"
                                else "已有 vosk 法语模型 zip 包可直接导入，免去下载",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (voskZipBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("导入中…", fontSize = 12.sp)
                        } else {
                            TextButton(onClick = {
                                voskImportPicker.launch(
                                    arrayOf("application/zip", "application/octet-stream")
                                )
                            }) {
                                Text(if (voskLargeReady) "重新导入" else "导入本地模型", fontSize = 13.sp)
                            }
                            if (voskLargeReady) {
                                TextButton(onClick = {
                                    VoskModelManager.deleteLargeModel(context)
                                    voskLargeReady = false
                                }) {
                                    Text("卸载", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
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

            // ---------- 在线视频字幕 ----------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("在线视频字幕", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(
                        "无需选择文件：点下面的按钮后，App 会捕获系统内部播放的声音（非扬声器外放），用本地模型实时转成字幕显示在悬浮窗上。适合观看在线视频时使用。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onExtractSubtitles() },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("提取在线视频字幕")
                        }
                        OutlinedButton(
                            onClick = { showSubtitleSessions = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("字幕记录")
                        }
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
                            IconButton(onClick = {
                                val clip = ClipData.newPlainText("video_text", resultText)
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(clip)
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "复制", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                isFav = if (isFav) {
                                    prefs.removeVideoTextFavorite(resultText)
                                    false
                                } else {
                                    prefs.addVideoTextFavorite(resultText, selectedName)
                                    true
                                }
                                reloadFavorites()
                            }) {
                                Icon(
                                    if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = if (isFav) "取消收藏" else "收藏",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isFav) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            resultText,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showResultDialog = true }
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

            // ---------- 收藏的文字（改为按钮进入独立查看页，释放主界面空间） ----------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("收藏的文字", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(
                        "查看已收藏的识别文本与字幕，点击条目可查看全文",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showFavViewer = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("收藏的文字（${favList.size}）")
                    }
                }
            }
        }
    }

    // ---------- 识别结果全文弹窗 ----------
    if (showResultDialog && resultText.isNotEmpty()) {
        FullTextDialog(text = resultText, title = "识别结果", onDismiss = { showResultDialog = false })
    }
}

/**
 * 识别模型单行选项：单选 + 名称/说明 + 下载状态与操作（下载/进度/取消/重试/卸载）。
 */
@Composable
private fun AsrEngineRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    state: AsrModelManager.State,
    busy: Boolean,
    warning: String?,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect, enabled = !busy)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp)
                Text(
                    if (state is AsrModelManager.State.Ready) "已下载，可用" else subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state is AsrModelManager.State.Ready) {
                TextButton(onClick = onUninstall, enabled = !busy) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("卸载", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (warning != null) {
            Text(warning, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        when (state) {
            is AsrModelManager.State.NotDownloaded -> {
                Button(
                    onClick = onDownload,
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("下载", fontSize = 13.sp)
                }
            }
            is AsrModelManager.State.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                val total = state.totalBytes
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (total > 0) "下载中 ${state.percent}%（${state.bytesRead / 1024 / 1024}/${total / 1024 / 1024} MB）"
                        else "下载中 ${state.bytesRead / 1024 / 1024} MB",
                        fontSize = 12.sp
                    )
                    if (selected) {
                        TextButton(onClick = onPause) { Text("暂停", fontSize = 12.sp) }
                        TextButton(onClick = onCancel) { Text("取消下载", fontSize = 12.sp) }
                    }
                }
            }
            is AsrModelManager.State.Installing -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("正在安装模型…", fontSize = 12.sp)
                }
            }
            is AsrModelManager.State.Failed -> {
                Text("下载失败：${state.message}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = onDownload,
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("重试", fontSize = 13.sp)
                }
            }
            is AsrModelManager.State.Paused -> {
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("已暂停 ${state.percent}%，再次开始将断点续传", fontSize = 12.sp)
                    if (selected) {
                        Button(
                            onClick = onDownload,
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text("继续", fontSize = 13.sp) }
                    }
                }
            }
            is AsrModelManager.State.Ready -> Unit
        }
    }
}
