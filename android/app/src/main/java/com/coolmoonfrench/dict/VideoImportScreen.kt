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

    // 模型选择是否默认指向大模型（用户之前选择过才选中，否则小模型）
    var useLarge by remember { mutableStateOf(hadChosenLarge(context)) }
    var largeState by remember {
        mutableStateOf<VoskModelManager.LargeModelState>(VoskModelManager.LargeModelState.NotDownloaded)
    }

    // 内存是否满足大模型要求（可用内存可能随时间变化，每次识别前都重新校验）
    var enoughMem by remember { mutableStateOf(VoskModelManager.hasEnoughMemory(context)) }

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

    // 卸载大模型确认弹窗
    var confirmUninstall by remember { mutableStateOf(false) }

    // 打开界面时刷新大模型状态
    LaunchedEffect(Unit) {
        enoughMem = VoskModelManager.hasEnoughMemory(context)
        largeState = if (VoskModelManager.isLargeReady(context)) {
            VoskModelManager.LargeModelState.Ready
        } else {
            VoskModelManager.LargeModelState.NotDownloaded
        }
        useLarge = VoskModelManager.userPrefersLarge(context) && VoskModelManager.isLargeReady(context)
    }

    // 大模型下载任务
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }
    val downloadInProgress = largeState is VoskModelManager.LargeModelState.Downloading ||
        largeState is VoskModelManager.LargeModelState.Installing ||
        downloadJob != null

    fun afterModelStateChange(state: VoskModelManager.LargeModelState) {
        largeState = state
        if (state is VoskModelManager.LargeModelState.Ready) {
            // 下载/导入完成后：内存可能有变化，重刷内存标记；并确认偏好项已同步为大模型
            enoughMem = VoskModelManager.hasEnoughMemory(context)
            VoskModelManager.setModelChoice(context, large = true)
        }
    }

    fun startDownload() {
        if (downloadInProgress) return
        downloadJob?.cancel()
        errorMsg = ""
        // 清理上一次失败状态，避免残留失败文案
        largeState = VoskModelManager.LargeModelState.NotDownloaded
        downloadJob = scope.launch {
            try {
                VoskModelManager.downloadLargeModel(context) { state ->
                    afterModelStateChange(state)
                }
            } catch (e: Exception) {
                // downloadLargeModel 内部已回调 DownloadFailed；若在状态推送前就抛错，这里兜底
                if (largeState !is VoskModelManager.LargeModelState.DownloadFailed) {
                    largeState = VoskModelManager.LargeModelState.NotDownloaded
                }
            } finally {
                downloadJob = null
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        largeState = VoskModelManager.LargeModelState.NotDownloaded
    }

    // 导入自选模型（本地 zip）
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            errorMsg = ""
            downloadJob?.cancel()
            largeState = VoskModelManager.LargeModelState.Installing
            downloadJob = scope.launch {
                try {
                    VoskModelManager.importLargeModel(context, uri) { state ->
                        afterModelStateChange(state)
                    }
                } catch (e: Exception) {
                    if (largeState !is VoskModelManager.LargeModelState.DownloadFailed) {
                        largeState = VoskModelManager.LargeModelState.NotDownloaded
                    }
                } finally {
                    downloadJob = null
                }
            }
        }
    }

    fun startRecognition() {
        val uri = selectedUri ?: run {
            errorMsg = "请先选择视频文件"
            return
        }
        // 识别前实时校验一次内存，避免用缓存的误判
        enoughMem = VoskModelManager.hasEnoughMemory(context)
        if (!enoughMem && useLarge) {
            errorMsg = "当前设备内存不足（可用内存 < 2GB），请切换小模型"
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
                            selected = useLarge,
                            onClick = {
                                useLarge = true
                                VoskModelManager.setModelChoice(context, large = true)
                            }
                        )
                        Column(Modifier.weight(1f)) {
                            Text("大模型（高精度）", fontSize = 15.sp)
                            Text(
                                if (VoskModelManager.isLargeReady(context))
                                    "已就绪，可用内存 ≥2GB 时识别更准确"
                                else
                                    "识别更准确，占空间约 1.4GB，需下载或导入",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (useLarge && !enoughMem) {
                        Text(
                            "当前可用内存不足 2GB，建议切换小模型以免卡顿/崩溃",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (useLarge && enoughMem && !VoskModelManager.isLargeReady(context)) {
                        Text(
                            "模型自理：可下载官方大模型，或导入本地自选模型 zip",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 大模型下载/导入/卸载区
                    if (useLarge) {
                        when (val st = largeState) {
                            is VoskModelManager.LargeModelState.Ready -> {
                                Text("大模型已就绪", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                                        enabled = !downloadInProgress
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("替换/导入模型", fontSize = 13.sp)
                                    }
                                    TextButton(
                                        onClick = { confirmUninstall = true },
                                        enabled = !downloadInProgress
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("卸载", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            is VoskModelManager.LargeModelState.Downloading -> {
                                LinearProgressIndicator(
                                    progress = { st.percent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("下载中：${st.percent}%  ${st.bytesRead / 1024 / 1024}/${st.totalBytes / 1024 / 1024} MB", fontSize = 12.sp)
                                TextButton(onClick = { cancelDownload() }) { Text("取消下载") }
                            }
                            is VoskModelManager.LargeModelState.Installing -> {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Text("正在解压模型…", fontSize = 12.sp)
                            }
                            is VoskModelManager.LargeModelState.DownloadFailed -> {
                                Text("下载/导入失败：${st.message}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { startDownload() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("重试下载", fontSize = 13.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("导入本地 zip", fontSize = 13.sp)
                                    }
                                }
                            }
                            is VoskModelManager.LargeModelState.NotDownloaded -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { startDownload() }) {
                                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("下载大模型")
                                    }
                                    OutlinedButton(
                                        onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) }
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("导入自选模型")
                                    }
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

    // ---------- 卸载大模型确认弹窗 ----------
    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("卸载大模型") },
            text = { Text("将删除已下载的大模型文件（约 1.4GB），释放存储空间。删除后如需高精度识别可重新下载或导入。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    // 删除模型文件并回落小模型
                    VoskModelManager.deleteLargeModel(context)
                    largeState = VoskModelManager.LargeModelState.NotDownloaded
                    useLarge = false
                    VoskModelManager.setModelChoice(context, large = false)
                    errorMsg = ""
                }) {
                    Text("确认卸载", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 读取用户上次选择的模型模式（仅用于初始选中态，若大模型未就绪会被强制回落小模型） */
private fun hadChosenLarge(context: android.content.Context): Boolean {
    return VoskModelManager.getModelChoice(context) is VoskModelManager.ModelOption.Large
}