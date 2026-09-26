package com.coolmoonfrench.dict

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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

    /** 列表循环：按队列顺序「单词→例句」滚动播报，播完自动切下一词（已掌握列表循环用）。 */
    var listLoop by androidx.compose.runtime.mutableStateOf(false)

    /** 词卡模式：是否暂停播报（暂停播放按钮）。 */
    var paused by androidx.compose.runtime.mutableStateOf(false)

    /** 词卡模式：是否显示/朗读中文释义（收藏词播放为 true；已掌握列表默认为 false，避免剧透）。 */
    var revealMeaning by androidx.compose.runtime.mutableStateOf(false)

    /** 悬浮窗当前模式 */
    var mode by androidx.compose.runtime.mutableStateOf(FloatingMode.WORD)

    /**
     * 词卡模式是否锁定位置：锁定后窗口不可拖动，且整个窗口不再拦截任何触摸——
     * 即使悬浮窗遮住桌面应用，底层应用也能正常点击（点击穿透）。
     * 解锁通过通知栏「解锁悬浮窗」（锁定后窗口本身收不到点击，无法就地解锁）。
     */
    var locked by androidx.compose.runtime.mutableStateOf(false)

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
        // 新的「加词播放」交互隐含解锁：避免残留的锁定态让窗口继续穿透、点不到卡片。
        FloatingWindowState.locked = false
        if (!FloatingWindowState.visible || FloatingWindowState.mode != FloatingMode.WORD) {
            FloatingWindowState.mode = FloatingMode.WORD
            FloatingWindowState.queue.clear()
            FloatingWindowState.index = -1
            FloatingWindowState.visible = true
            FloatingWindowState.loopOne = false
            FloatingWindowState.listLoop = false
            FloatingWindowState.paused = false
            FloatingWindowState.revealMeaning = false
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

    /**
     * 开启「已掌握列表循环」：把整份列表作为悬浮窗队列，从 startIndex 起按顺序
     * 「单词→例句」播报并自动切下一词，悬浮窗同步显示当前词卡。
     */
    fun startListLoop(context: Context, words: List<VocabEntry>, startIndex: Int, revealMeaning: Boolean = false) {
        if (words.isEmpty()) return
        // 同 add：开始新一轮循环播放时清除锁定穿透态。
        FloatingWindowState.locked = false
        FloatingWindowState.mode = FloatingMode.WORD
        FloatingWindowState.queue.clear()
        FloatingWindowState.queue.addAll(words)
        FloatingWindowState.index = startIndex.coerceIn(0, words.size - 1)
        FloatingWindowState.loopOne = false
        FloatingWindowState.listLoop = true
        FloatingWindowState.paused = false
        FloatingWindowState.revealMeaning = revealMeaning
        FloatingWindowState.visible = true
        runCatching {
            context.startService(Intent(context, FloatingWindowService::class.java))
        }
    }

    /** 停止列表循环：停播但不关闭悬浮窗（窗口由用户自行关闭，或沿用原队列）。 */
    fun stopListLoop() {
        FloatingWindowState.listLoop = false
        FloatingWindowState.loopOne = false
        FloatingWindowState.paused = false
        FloatingWindowState.revealMeaning = false
        Espeak.stop()
        ChineseTts.stop()
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
        FloatingWindowState.listLoop = false
        FloatingWindowState.paused = false
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
class FloatingWindowService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val NOTIF_CHANNEL_ID = "floating_window"
        private const val NOTIF_ID = 4102

        /** 锁定（点击穿透）期间窗口收不到任何触摸，通知栏 action 是唯一操作入口。 */
        const val ACTION_UNLOCK = "com.coolmoonfrench.dict.action.FLOATING_UNLOCK"
        const val ACTION_CLOSE = "com.coolmoonfrench.dict.action.FLOATING_CLOSE"
    }

    private lateinit var wm: WindowManager
    private var overlay: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    // 悬浮窗的 ComposeView 不隶属于任何 Activity，必须自备 Lifecycle/ViewModelStore/SavedState，
    // 否则 Compose 在 onAttachedToWindow 时找不到 ViewTreeLifecycleOwner 会直接崩溃。
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreInstance = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreInstance
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /** 悬浮窗当前左上角坐标（`p.gravity = TOP or START`，x/y 即屏幕像素偏移）。 */
    fun windowX(): Int = params?.x ?: 0
    fun windowY(): Int = params?.y ?: 0

    /**
     * 把词卡窗口移动到绝对坐标（物理像素，`gravity = TOP or START`）。
     *
     * 拖动采用「锚定 + 手指屏幕绝对位移」算法：拖动开始那一帧记录锚点
     * （当时的窗口像素坐标 + 按下点在屏幕上的像素坐标 = 局部坐标 + 窗口坐标，
     * Compose 手势与窗口参数同为像素，全程不乘 density），之后每帧目标位置 =
     * 锚点窗口坐标 + [(按下点局部坐标 + 当前窗口坐标) -(按下点局部坐标 + 锚点窗口坐标)]。
     * 关键是把每帧窗口坐标换算回屏幕绝对坐标、再与锚点求差——实时修正窗口移动带来的
     * 参考系漂移，窗口精确跟手。若直接把局部位移累加到实时窗口坐标，窗口越追越偏，
     * 正是之前悬浮窗乱飘、脱手飞走的根因。
     */
    fun moveTo(x: Float, y: Float) {
        val p = params ?: return
        val v = overlay ?: return
        p.x = x.toInt()
        p.y = y.toInt()
        runCatching { wm.updateViewLayout(v, p) }
    }

    /**
     * 锁定时的点击穿透：加上 FLAG_NOT_TOUCHABLE 后窗口不拦截任何触摸，
     * 悬浮窗遮住桌面应用也能点到底层；解锁靠通知栏 action（见 onStartCommand）。
     */
    fun setTouchThrough(enabled: Boolean) {
        val p = params ?: return
        val v = overlay ?: return
        val mask = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val newFlags = if (enabled) p.flags or mask else p.flags and mask.inv()
        if (newFlags == p.flags) return
        p.flags = newFlags
        runCatching { wm.updateViewLayout(v, p) }
    }

    /** 锁定状态等变化后刷新通知栏按钮（增加/移除「解锁悬浮窗」）。 */
    fun refreshNotification() {
        if (FloatingWindowState.mode == FloatingMode.WORD) updateForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 锁定期间窗口点击穿透、收不到任何触摸，通知栏按钮是唯一入口：解锁 / 关闭。
        // 解锁只需翻转全局状态，词卡组合期的 LaunchedEffect(locked) 会负责恢复触摸并刷新通知。
        when (intent?.action) {
            ACTION_UNLOCK -> {
                FloatingWindowState.locked = false
                setTouchThrough(false)
                return START_STICKY
            }
            ACTION_CLOSE -> {
                FloatingWindowControl.stop(this)
                return START_NOT_STICKY
            }
        }
        if (overlay == null) showOverlay()
        updateForeground()
        return START_STICKY
    }

    /**
     * 词卡模式（已掌握循环）提升为前台服务，保证回到桌面后长时间不被系统回收；
     * 字幕模式的前台由 SubtitleCaptureService（mediaProjection）负责，这里撤下自己的通知避免重复。
     */
    private fun updateForeground() {
        if (FloatingWindowState.mode == FloatingMode.WORD) {
            runCatching {
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIF_ID, notification)
                }
            }
        } else {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        NOTIF_CHANNEL_ID,
                        "悬浮窗",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setContentTitle(if (FloatingWindowState.locked) "法语悬浮窗（已锁定·点击穿透）" else "法语悬浮窗")
            .setContentText(if (FloatingWindowState.locked) "悬浮窗已锁定，点击只会落在底层应用上；点此解锁" else "正在显示悬浮卡片并朗读…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
        // 锁定态下窗口不接受触摸，「解锁悬浮窗」必须常驻通知栏。
        if (FloatingWindowState.locked) {
            val unlock = PendingIntent.getService(
                this,
                1,
                Intent(this, FloatingWindowService::class.java).setAction(ACTION_UNLOCK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "解锁悬浮窗", unlock)
        }
        val close = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatingWindowService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(0, "关闭悬浮窗", close)
        return builder.build()
    }

    private fun showOverlay() {
        if (overlay != null) return
        val point = Point().also { wm.defaultDisplay.getRealSize(it) }
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val layoutFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        // 窗口宽高都自适应内容：背景透明后，窗口外框紧贴文字与控件，不会挡住底层应用。
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            layoutFlags,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = (point.x * 0.06f).toInt()
        p.y = (point.y * 0.10f).toInt()
        params = p

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
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
        ChineseTts.stop()
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        FloatingWindowState.visible = false
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
}

/** 悬浮窗内容：大词 + 小例句，锁定/设置交互，底部上一句/下一句/单句循环。 */
@OptIn(ExperimentalLayoutApi::class)
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

    // 锁定状态 / 设置面板（锁定提升到全局状态：配合通知栏解锁动作）
    val locked = FloatingWindowState.locked
    var settingsVisible by remember { mutableStateOf(false) }
    var tapTimes by remember { mutableStateOf<MutableList<Long>>(mutableListOf()) }
    val scope = rememberCoroutineScope()

    // 锁定 → 整窗点击穿透（FLAG_NOT_TOUCHABLE）并刷新通知栏按钮；解锁即恢复拦截。
    // 通知栏是锁定后的唯一解锁入口（窗口此时收不到点击），所以这里也要清掉展开的设置面板。
    LaunchedEffect(locked) {
        service.setTouchThrough(locked)
        service.refreshNotification()
        if (locked) settingsVisible = false
    }

    // 朗读：切词自动播一次「单词→例句」；单句循环则反复播当前词；
    // 列表循环则播完自动切下一词（切词会重启本效果继续播，形成连续循环）。
    // 收藏词（revealMeaning）无本地例句时，改读该词的中文释义。
    LaunchedEffect(word, FloatingWindowState.loopOne, FloatingWindowState.listLoop, FloatingWindowState.paused, FloatingWindowState.revealMeaning) {
        if (word.isEmpty() || FloatingWindowState.paused) return@LaunchedEffect
        while (true) {
            Espeak.speakAwait(word, deterministic = true)
            val ex = withContext(Dispatchers.IO) { VocabExamples.lookup(context, word) }
            if (ex != null) {
                Espeak.speakAwait(ex.fr)
            } else if (FloatingWindowState.revealMeaning) {
                val meaning = FloatingWindowState.current()?.meaning.orEmpty()
                // 只有中文释义才用中文语音朗读；英文兜底释义（如词典查无的残缺词）交给中文引擎
                // 只会读出一串英文，跳过朗读避免误导。
                if (meaning.isNotBlank() && hasChinese(meaning)) {
                    ChineseTts.ensureInitialized(context)
                    ChineseTts.speakAwait(meaning)
                }
            }
            if (FloatingWindowState.loopOne) continue
            if (FloatingWindowState.listLoop) {
                val before = FloatingWindowState.current()?.word
                FloatingWindowState.next()
                // 队列只有一个词时 next 不改变当前词，继续在本效果内循环，避免卡死。
                if (FloatingWindowState.current()?.word == before) continue
                break
            }
            break
        }
    }

    Box(modifier = Modifier.widthIn(max = 330.dp).padding(4.dp)) {
        // 主卡片：透明背景（仅文字与控件可见）+ 尺寸自适应内容
        Column(
            modifier = Modifier
                .widthIn(min = 180.dp, max = 330.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp)
                // 整卡手势：拖动（未锁定）/ 长按（设置面板）/ 三击关闭。
                // 【乱飘修复】拖动只累加「帧间屏幕位移」（positionChange），全程不再读取
                // 实时窗口坐标做加法：实时窗口坐标与 WM 实际生效位置存在 1-2 帧滞后，
                // 读它会引入正反馈，帧延迟 ≥2 时增益发散 → 窗口自激震荡乱飘、松手飞走。
                // Compose 指针坐标与 WindowManager 坐标同为物理像素，无需 density 换算。
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var longFired = false
                        var accX = down.position.x
                        var accY = down.position.y
                        var targetX = service.windowX().toFloat()
                        var targetY = service.windowY().toFloat()
                        val longPressJob = scope.launch {
                            delay(500)
                            if (!dragging) {
                                longFired = true
                                settingsVisible = !settingsVisible
                            }
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull {
                                it.id == down.id
                            } ?: break
                            when (event.type) {
                                PointerEventType.Move -> {
                                    if (!FloatingWindowState.locked && !longFired) {
                                        val d = change.positionChange()
                                        accX += d.x
                                        accY += d.y
                                        if (!dragging &&
                                            (abs(accX - down.position.x) > 8f ||
                                                abs(accY - down.position.y) > 8f)
                                        ) {
                                            dragging = true
                                            longPressJob.cancel()
                                        }
                                        if (dragging) {
                                            change.consume()
                                            targetX += d.x
                                            targetY += d.y
                                            service.moveTo(targetX, targetY)
                                        }
                                    }
                                }
                                PointerEventType.Release -> {
                                    val consumed = event.changes.any { it.isConsumed }
                                    if (!dragging && !longFired && !consumed) {
                                        val now = System.currentTimeMillis()
                                        tapTimes = (tapTimes + now)
                                            .filter { now - it <= 900 }.toMutableList()
                                        if (tapTimes.size >= 3) {
                                            tapTimes = mutableListOf()
                                            FloatingWindowControl.stop(context)
                                        }
                                    }
                                    longPressJob.cancel()
                                    break
                                }
                                else -> Unit
                            }
                        }
                    }
                }
        ) {
            // 顶部：单词 + 例句（原生字幕风格：白字，黑底随文字内容自适应，可在设置面板调节透明度）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = if (settings.floatBgAlpha <= 0f) 0.001f else settings.floatBgAlpha), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word.ifEmpty { "—" },
                        fontSize = (26 * settings.floatFontScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (word.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = FrenchIpa.wrap(word),
                            fontSize = (13 * settings.floatFontScale).sp,
                            color = Color.White.copy(alpha = 0.8f)
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
                                tint = Color.White,
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
                        color = Color.White
                    )
                    if (settings.floatShowTranslation) {
                        Text(
                            text = ex.second,
                            fontSize = (12 * settings.floatFontScale).sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                if (FloatingWindowState.revealMeaning) {
                    val meaning = entry?.meaning.orEmpty()
                    if (meaning.isNotBlank()) {
                        Text(
                            text = meaning,
                            fontSize = (13 * settings.floatFontScale).sp,
                            lineHeight = 18.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // 底部控制：锁（左下） + 上一句 / 暂停播放 / 下一句 / 单句循环
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    // 底栏黑底跟随词卡透明度等比缩放，保持原来的相对层次感
                    .background(Color.Black.copy(alpha = 0.55f * settings.floatBgAlpha), RoundedCornerShape(10.dp))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左下角常驻锁：单击锁定。锁定后整窗点击穿透（底层应用可直接点击），
                // 窗口自身收不到任何触摸，只能通过通知栏「解锁悬浮窗」解除。
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    FloatingWindowState.locked = true
                                    settingsVisible = false
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription =
                        if (locked) "已锁定·点击穿透（请在通知栏解锁）" else "未锁定（单击锁定并穿透点击）",
                        tint = if (locked) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = { FloatingWindowState.prev() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一句",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = {
                        FloatingWindowState.paused = !FloatingWindowState.paused
                        if (FloatingWindowState.paused) {
                            Espeak.stop()
                            ChineseTts.stop()
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (FloatingWindowState.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (FloatingWindowState.paused) "播放" else "暂停",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(
                    onClick = { FloatingWindowState.next() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一句",
                        tint = Color.White.copy(alpha = 0.75f),
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
                        else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 长按弹出的设置面板：翻译开关 / 语速 / 字号 / 关闭
        // 说明：面板参与窗口测量（不再用 matchParentSize 覆盖裁剪），窗口会随之变高，保证语速/字号完整可见。
        if (settingsVisible) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, end = 8.dp, start = 8.dp, bottom = 8.dp),
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
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
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
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
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
                            Text("透明度", fontSize = 13.sp)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AppSettings.FLOAT_BG_ALPHA_OPTIONS.forEach { a ->
                                    FilterChip(
                                        selected = settings.floatBgAlpha == a,
                                        onClick = { settings.updateFloatBgAlpha(a) },
                                        label = {
                                            Text(
                                                when (a) {
                                                    0f -> "透底"
                                                    1f -> "全黑"
                                                    else -> "${(a * 100).toInt()}%"
                                                },
                                                fontSize = 12.sp
                                            )
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

    // 是否跟随最新一句：仅当用户没有上滑回看时才自动滚到底部。
    // 不能直接用 canScrollForward 判断「已到底」，因为新追加一句后它会立刻变为可前滚，
    // 于是自动跟随永远失效。改为观察「最后一项是否可见」，并留 1 项容差吸收追加造成的瞬时偏移。
    var followTail by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }.collect { followTail = it }
    }
    LaunchedEffect(lines.size, partial) {
        val total = lines.size + if (partial.isNotEmpty()) 1 else 0
        if (total > 0 && followTail) {
            listState.animateScrollToItem(total - 1)
        }
    }

    var tapTimes by remember { mutableStateOf<MutableList<Long>>(mutableListOf()) }

    Column(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 380.dp)
            .padding(4.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            // 整个窗口三击关闭（字幕窗口无锁，直接关闭）
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        when (event.type) {
                            PointerEventType.Move -> {
                                if (abs(change.position.x - down.position.x) > 24f ||
                                    abs(change.position.y - down.position.y) > 24f
                                ) moved = true
                            }
                            PointerEventType.Release -> {
                                if (!moved && !event.changes.any { it.isConsumed }) {
                                    val now = System.currentTimeMillis()
                                    tapTimes = (tapTimes + now)
                                        .filter { now - it <= 900 }.toMutableList()
                                    if (tapTimes.size >= 3) {
                                        tapTimes = mutableListOf()
                                        SubtitleCaptureService.stop(context)
                                    }
                                }
                                break
                            }
                            else -> Unit
                        }
                    }
                }
            }
    ) {
        // 顶部标题栏：拖动移动悬浮窗；右上角关闭
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .pointerInput(Unit) {
                    // 与词卡相同的「帧间位移累加」方案：只累加 positionChange 的逐帧增量，
                    // 全程不读实时窗口坐标，避免参考系随窗口移动漂移导致乱飘。
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var accX = 0f
                        var accY = 0f
                        var targetX = service.windowX().toFloat()
                        var targetY = service.windowY().toFloat()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            when (event.type) {
                                PointerEventType.Move -> {
                                    val d = change.positionChange()
                                    accX += d.x
                                    accY += d.y
                                    if (!dragging && (abs(accX) > 6f || abs(accY) > 6f)) {
                                        dragging = true
                                    }
                                    if (dragging) {
                                        change.consume()
                                        service.moveTo(targetX + accX, targetY + accY)
                                    }
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
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { SubtitleCaptureService.stop(context) },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭字幕",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (lines.isEmpty() && partial.isEmpty()) {
            Text(
                text = "正在聆听系统声音…",
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(Color.Black, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(lines) { line ->
                    // 原生字幕风格：黑底白字，黑底随每句话内容自适应
                    Text(
                        text = line,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = Color.White,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .background(Color.Black, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (partial.isNotEmpty()) {
                    item {
                        // 识别中的半句：逐词蹦出，黑底随已识别的词增长，未出现的部分保持空白
                        Text(
                            text = partial,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .background(Color.Black, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}