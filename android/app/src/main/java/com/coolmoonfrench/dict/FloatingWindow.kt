package com.coolmoonfrench.dict

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** 悬浮窗模式：单词卡（已掌握循环）或实时字幕。 */
enum class FloatingMode { WORD, SUBTITLE }

/** 悬浮窗全局状态：词队列、当前索引与交互状态（由已掌握列表与悬浮窗共同读写）。 */
object FloatingWindowState {
    val queue = androidx.compose.runtime.mutableStateListOf<VocabEntry>()
    var index by androidx.compose.runtime.mutableIntStateOf(-1)
    var visible by androidx.compose.runtime.mutableStateOf(false)
    var loopOne by androidx.compose.runtime.mutableStateOf(false)

    /** 悬浮窗当前模式 */
    var mode by androidx.compose.runtime.mutableStateOf(FloatingMode.WORD)

    /** 字幕模式：已识别完成的句子（可上下滚动查看上一句） */
    val subtitleLines = androidx.compose.runtime.mutableStateListOf<String>()

    /** 字幕模式：当前正在识别、尚未成句的实时文本 */
    var subtitlePartial by androidx.compose.runtime.mutableStateOf("")

    /** 字幕模式：顶部标题（如「在线视频字幕」） */
    var subtitleLabel by androidx.compose.runtime.mutableStateOf("")

    fun current(): VocabEntry? = if (index in queue.indices) queue[index] else null

    fun isInQueue(word: String): Boolean = queue.any { it.word == word }

    fun next() {
        if (queue.isNotEmpty()) index = (index + 1) % queue.size
    }

    fun prev() {
        if (queue.isNotEmpty()) index = (index - 1 + queue.size) % queue.size
    }
}

/** 悬浮窗服务控制：队列增删与服务启停。 */
object FloatingWindowControl {

    fun overlayPermissionGranted(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /** 引导用户前往系统悬浮窗授权页。 */
    fun requestPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    /** 把词加入悬浮队列并显示（服务未启动则先启动）。 */
    fun add(context: Context, entry: VocabEntry) {
        if (!FloatingWindowState.visible || FloatingWindowState.mode != FloatingMode.WORD) {
            FloatingWindowState.mode = FloatingMode.WORD
            FloatingWindowState.queue.clear()
            FloatingWindowState.index = -1
            FloatingWindowState.visible = true
            FloatingWindowState.loopOne = false
            context.startService(Intent(context, FloatingWindowService::class.java))
        }
        val i = FloatingWindowState.queue.indexOfFirst { it.word == entry.word }
        if (i < 0) {
            FloatingWindowState.queue.add(entry)
            FloatingWindowState.index = FloatingWindowState.queue.size - 1
        } else {
            FloatingWindowState.index = i
        }
    }

    /** 从队列移除词；队列清空则停止服务。 */
    fun remove(context: Context, word: String) {
        val i = FloatingWindowState.queue.indexOfFirst { it.word == word }
        if (i < 0) return
        val idx = FloatingWindowState.index
        FloatingWindowState.queue.removeAt(i)
        if (FloatingWindowState.queue.isEmpty()) {
            stop(context)
        } else {
            if (idx > i) FloatingWindowState.index = idx - 1
            FloatingWindowState.index =
                FloatingWindowState.index.coerceIn(0, FloatingWindowState.queue.size - 1)
        }
    }

    /** 关闭悬浮窗并停止服务。 */
    fun stop(context: Context) {
        FloatingWindowState.visible = false
        FloatingWindowState.index = -1
        FloatingWindowState.queue.clear()
        FloatingWindowState.loopOne = false
        runCatching {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }
}

/** 字幕悬浮窗控制：显示字幕模式、追加识别文本、关闭。 */
object SubtitleWindowControl {

    /** 打开字幕悬浮窗（清空上一轮字幕）。 */
    fun show(context: Context, label: String) {
        FloatingWindowState.mode = FloatingMode.SUBTITLE
        FloatingWindowState.subtitleLabel = label
        FloatingWindowState.subtitleLines.clear()
        FloatingWindowState.subtitlePartial = ""
        FloatingWindowState.visible = true
        runCatching { context.startService(Intent(context, FloatingWindowService::class.java)) }
    }

    /** 追加一句已完成的字幕。 */
    fun appendLine(line: String) {
        val t = line.trim()
        FloatingWindowState.subtitlePartial = ""
        if (t.isEmpty()) return
        FloatingWindowState.subtitleLines.add(t)
        if (FloatingWindowState.subtitleLines.size > 300) {
            FloatingWindowState.subtitleLines.removeAt(0)
        }
    }

    /** 更新实时识别中的半句文本（成句后由 appendLine 清空）。 */
    fun updatePartial(text: String) {
        FloatingWindowState.subtitlePartial = text.trim()
    }

    /** 关闭字幕悬浮窗。 */
    fun hide(context: Context) {
        FloatingWindowState.subtitleLines.clear()
        FloatingWindowState.subtitlePartial = ""
        FloatingWindowState.visible = false
        runCatching {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }
}

/** 悬浮窗服务：在 WindowManager 上挂载一个 ComposeView，常驻于任何界面之上。 */
class FloatingWindowService : Service() {

    private lateinit var wm: WindowManager
    private var overlay: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    /** 移动悬浮窗（拖动时由 Compose 手势回调）。 */
    fun moveBy(dx: Float, dy: Float) {
        val p = params ?: return
        val v = overlay ?: return
        p.x += dx.toInt()
        p.y += dy.toInt()
        runCatching { wm.updateViewLayout(v, p) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlay == null) showOverlay()
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlay != null) return
        val point = Point().also { wm.defaultDisplay.getRealSize(it) }
        val width = (point.x * 0.92f).toInt()
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val layoutFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        val p = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            layoutFlags,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = (point.x - width) / 2
        p.y = (point.y * 0.08f).toInt()
        params = p

        val view = ComposeView(this).apply {
            setContent {
                FrenchDictTheme {
                    when (FloatingWindowState.mode) {
                        FloatingMode.WORD -> FloatingWordWindow(service = this@FloatingWindowService)
                        FloatingMode.SUBTITLE -> FloatingSubtitleWindow(service = this@FloatingWindowService)
                    }
                }
            }
        }
        wm.addView(view, p)
        overlay = view
    }

    override fun onDestroy() {
        Espeak.stop()
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        FloatingWindowState.visible = false
        super.onDestroy()
    }
}

/** 悬浮窗内容：大词 + 小例句，锁定/设置交互，底部上一句/下一句/单句循环。 */
@Composable
private fun FloatingWordWindow(service: FloatingWindowService) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context.applicationContext) }
    val entry = FloatingWindowState.current()
    val word = entry?.word.orEmpty()

    var example by remember(word) {
        mutableStateOf<Pair<String, String>?>(null)
    }
    LaunchedEffect(word) {
        example = if (word.isEmpty()) null
        else withContext(Dispatchers.IO) {
            VocabExamples.lookup(context, word)?.let { it.fr to it.zh }
        }
    }

    // 锁定 / 锁图标 / 设置面板（悬浮窗局部状态，服务重启即复位）
    var locked by remember { mutableStateOf(false) }
    var lockIconVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var tapTimes by remember { mutableStateOf<MutableList<Long>>(mutableListOf()) }
    val scope = rememberCoroutineScope()

    fun handleTap() {
        if (locked) {
            val now = System.currentTimeMillis()
            tapTimes = (tapTimes + now).filter { now - it <= 700 }.toMutableList()
            if (tapTimes.size >= 3) {
                locked = false
                lockIconVisible = false
                tapTimes = mutableListOf()
            }
        } else {
            lockIconVisible = !lockIconVisible
        }
    }

    // 朗读：切词自动播一次「单词→例句」；单句循环开启后连续播报当前词
    LaunchedEffect(word, FloatingWindowState.loopOne) {
        if (word.isEmpty()) return@LaunchedEffect
        while (true) {
            Espeak.speakAwait(word, deterministic = true)
            val ex = withContext(Dispatchers.IO) { VocabExamples.lookup(context, word) }
            if (ex != null) Espeak.speakAwait(ex.fr)
            if (!FloatingWindowState.loopOne) break
        }
    }

    Box(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        // 主卡片：单词 + 例句 + 底部控制
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
                // 整卡手势：拖动（未锁定）/ 单击（锁图标/三击解锁）/ 长按（设置面板）
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var longFired = false
                        var lastPos = down.position
                        val longPressJob = scope.launch {
                            delay(500)
                            if (!dragging) {
                                longFired = true
                                settingsVisible = !settingsVisible
                                lockIconVisible = false
                            }
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull {
                                it.id == down.id
                            } ?: break
                            when (event.type) {
                                PointerEventType.Move -> {
                                    if (!locked && !longFired) {
                                        val delta = change.position - lastPos
                                        val total = change.position - down.position
                                        if (!dragging &&
                                            (abs(total.x) > 8f || abs(total.y) > 8f)
                                        ) {
                                            dragging = true
                                            longPressJob.cancel()
                                        }
                                        if (dragging) {
                                            change.consume()
                                            service.moveBy(delta.x, delta.y)
                                        }
                                    }
                                    lastPos = change.position
                                }
                                PointerEventType.Release -> {
                                    val consumed = event.changes.any { it.isConsumed }
                                    if (!dragging && !longFired && !consumed) handleTap()
                                    longPressJob.cancel()
                                    break
                                }
                                else -> Unit
                            }
                        }
                    }
                }
        ) {
            // 顶部：单词 + 例句
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word.ifEmpty { "—" },
                        fontSize = (26 * settings.floatFontScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = if (word.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (word.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = FrenchIpa.wrap(word),
                            fontSize = (13 * settings.floatFontScale).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (word.isNotEmpty()) {
                        IconButton(onClick = {
                            Espeak.speakWithFeedback(context, word, deterministic = true)
                        }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "朗读单词",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                example?.let { ex ->
                    Text(
                        text = ex.first,
                        fontSize = (14 * settings.floatFontScale).sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (settings.floatShowTranslation) {
                        Text(
                            text = ex.second,
                            fontSize = (12 * settings.floatFontScale).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 底部控制：锁图标（左下） + 上一句 / 下一句 / 单句循环（中央）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 锁图标：单击窗口后出现；点它锁定位置；锁定中显示实心锁
                if (lockIconVisible) {
                    IconButton(onClick = {
                        locked = true
                        lockIconVisible = false
                    }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "锁定位置",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (locked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "已锁定（三击解锁）",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp).padding(start = 16.dp)
                    )
                }
                if (lockIconVisible || locked) {
                    Spacer(Modifier.width(8.dp))
                }
                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = { FloatingWindowState.prev() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一句",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { FloatingWindowState.next() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一句",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // 单句循环按钮紧挨下一曲
                IconButton(
                    onClick = { FloatingWindowState.loopOne = !FloatingWindowState.loopOne },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = "单句循环",
                        tint = if (FloatingWindowState.loopOne) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 长按弹出的设置面板：翻译开关 / 语速 / 字号 / 关闭
        if (settingsVisible) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = 40.dp, end = 8.dp, start = 8.dp, bottom = 8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("悬浮窗设置", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {
                                settingsVisible = false
                            }, modifier = Modifier.size(26.dp)) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "关闭面板",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.width(190.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("显示翻译", fontSize = 13.sp)
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = settings.floatShowTranslation,
                                    onCheckedChange = { settings.updateFloatShowTranslation(it) },
                                    modifier = Modifier.scale(0.6f)
                                )
                            }
                            Text("语速", fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                AppSettings.SPEECH_RATE_OPTIONS.forEach { rate ->
                                    FilterChip(
                                        selected = settings.speechRate == rate,
                                        onClick = { settings.updateSpeechRate(rate) },
                                        label = {
                                            Text(
                                                if (rate % 1f == 0f) rate.toInt().toString()
                                                else rate.toString(),
                                                fontSize = 12.sp
                                            )
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("字号", fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0.8f, 1f, 1.2f, 1.4f).forEach { s ->
                                    FilterChip(
                                        selected = settings.floatFontScale == s,
                                        onClick = { settings.updateFloatFontScale(s) },
                                        label = {
                                            Text("x$s", fontSize = 12.sp)
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "关闭悬浮窗",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        FloatingWindowControl.stop(context)
                                    }
                                    .padding(vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 字幕悬浮窗：只展示实时识别文字，可上下滚动查看上一句，
 * 无上一句/下一句/循环等控制；顶部标题栏可拖动，右上角关闭。
 */
@Composable
private fun FloatingSubtitleWindow(service: FloatingWindowService) {
    val context = LocalContext.current
    val lines = FloatingWindowState.subtitleLines
    val partial = FloatingWindowState.subtitlePartial
    val listState = rememberLazyListState()

    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    LaunchedEffect(lines.size, partial) {
        val total = lines.size + if (partial.isNotEmpty()) 1 else 0
        if (total > 0 && atBottom) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // 顶部标题栏：拖动移动悬浮窗；右上角关闭
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var lastPos = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            when (event.type) {
                                PointerEventType.Move -> {
                                    val delta = change.position - lastPos
                                    if (!dragging && (abs(delta.x) > 4f || abs(delta.y) > 4f)) {
                                        dragging = true
                                    }
                                    if (dragging) {
                                        change.consume()
                                        service.moveBy(delta.x, delta.y)
                                    }
                                    lastPos = change.position
                                }
                                PointerEventType.Release -> break
                                else -> Unit
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = FloatingWindowState.subtitleLabel.ifEmpty { "实时字幕" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { SubtitleCaptureService.stop(context) },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭字幕",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (lines.isEmpty() && partial.isEmpty()) {
            Text(
                text = "正在聆听系统声音…",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                if (partial.isNotEmpty()) {
                    item {
                        Text(
                            text = partial,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}