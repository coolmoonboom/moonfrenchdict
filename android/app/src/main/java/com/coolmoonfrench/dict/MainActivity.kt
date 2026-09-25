package com.coolmoonfrench.dict

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Notes
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_EXTRACT_SUBTITLES = "com.coolmoonfrench.dict.action.EXTRACT_SUBTITLES"
    }

    private lateinit var repository: DictRepository
    private lateinit var translator: MyMemoryTranslator
    private lateinit var conjugator: VerbConjugator
    private lateinit var analyzer: SentenceAnalyzer
    private lateinit var morphology: MorphologyAnalyzer
    private lateinit var aiPrefs: AIPreferences

    /** 字幕授权弹窗返回时会触发 onResume，此标志用于避免刚启动的字幕悬浮窗被立刻关闭。 */
    private var suppressFloatingAutoClose = false

    /** MediaProjection 授权回调：成功后启动字幕捕获前台服务。 */
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            suppressFloatingAutoClose = true
            SubtitleCaptureService.start(this, result.resultCode, data)
        } else {
            Toast.makeText(this, "已取消系统音频捕获授权", Toast.LENGTH_SHORT).show()
        }
    }

    /** 回到 App 时自动关闭悬浮窗（词卡 / 字幕）。 */
    override fun onResume() {
        super.onResume()
        if (suppressFloatingAutoClose) {
            suppressFloatingAutoClose = false
            return
        }
        if (FloatingWindowState.visible) {
            when (FloatingWindowState.mode) {
                FloatingMode.WORD -> FloatingWindowControl.stop(this)
                FloatingMode.SUBTITLE -> SubtitleCaptureService.stop(this)
            }
        }
    }

    /** 快捷方式 / 界面按钮触发的「提取在线视频字幕」入口。 */
    private fun beginSubtitleExtraction() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "系统内部音频捕获需要 Android 10 及以上", Toast.LENGTH_LONG).show()
            return
        }
        if (!FloatingWindowControl.overlayPermissionGranted(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限，再重新提取字幕", Toast.LENGTH_LONG).show()
            FloatingWindowControl.requestPermission(this)
            return
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mpm == null) {
            Toast.makeText(this, "当前设备不支持屏幕/音频捕获", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { projectionLauncher.launch(mpm.createScreenCaptureIntent()) }
            .onFailure { Toast.makeText(this, "无法发起捕获授权：${it.message}", Toast.LENGTH_LONG).show() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_EXTRACT_SUBTITLES) {
            window.decorView.post { beginSubtitleExtraction() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = DictRepository(this)
        translator = MyMemoryTranslator()
        conjugator = VerbConjugator()
        analyzer = SentenceAnalyzer(repository, conjugator)
        morphology = MorphologyAnalyzer()
        aiPrefs = AIPreferences(this)

        CrashLogger.init(this)

        var loaded by mutableStateOf(false)

        setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context).build()
            }
            val settings = remember { AppSettings(applicationContext) }
            CrashLogger.enabled = settings.debugLogEnabled
            CrashLogger.log(applicationContext, "启动", "onCreate -> setContent")
            val darkTheme = if (settings.darkModeEnabled) true else isSystemInDarkTheme()
            FrenchDictTheme(
                darkTheme = darkTheme,
                fontScale = settings.fontScale
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!loaded) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("加载词典中…")
                        }
                        LaunchedEffect(Unit) {
                            // 只等数据库就绪（首次拷贝 assets / 打开 DB），快速进入界面
                            repository.ensureReady()
                            // 并行预热法语 TTS，避免进入界面后仍显示"正在初始化"
                            Speech.setSpeechRate(settings.speechRate)
                            Speech.ensureInitialized(applicationContext)
                            loaded = true
                            // 模糊搜索内存索引放到首屏之后后台构建，不阻塞启动
                            repository.ensureIndexInBackground()
                        }
                    } else {
                        MainTabs(
                            repository, translator, conjugator, analyzer, morphology, settings, aiPrefs,
                            onExtractSubtitles = { beginSubtitleExtraction() }
                        )
                    }
                }
            }
        }

        // 从桌面快捷方式进入：直接发起字幕捕获授权
        if (intent?.action == ACTION_EXTRACT_SUBTITLES) {
            window.decorView.post { beginSubtitleExtraction() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabs(
    repository: DictRepository,
    translator: MyMemoryTranslator,
    conjugator: VerbConjugator,
    analyzer: SentenceAnalyzer,
    morphology: MorphologyAnalyzer,
    settings: AppSettings,
    aiPrefs: AIPreferences,
    onExtractSubtitles: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showGrammar by remember { mutableStateOf(false) }
    var showGrammarLearn by remember { mutableStateOf(false) }
    var showPronouns by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }
    var showAISettings by remember { mutableStateOf(false) }
    var aiRefreshKey by remember { mutableStateOf(0) }
    var showQuestionTypes by remember { mutableStateOf(false) }
    var showVideoImport by remember { mutableStateOf(false) }
    var showVoiceChat by remember { mutableStateOf(false) }
    var showCalNum by remember { mutableStateOf(false) }
    var showPhonetics by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 自动同步：开启同步间隔且已登录时按间隔合并云端与本地（登录/开启后无需重启即可生效）
    val syncCtx = LocalContext.current
    LaunchedEffect(Unit) {
        val prov = NutsCloudProvider(syncCtx)
        val mgr = SyncManager(syncCtx, repository, aiPrefs, prov)
        while (true) {
            val h = settings.syncIntervalHours
            if (h > 0 && prov.isConfigured()) {
                // 网络与打包必须在 IO 线程执行，主线程调用会触发 NetworkOnMainThreadException
                withContext(Dispatchers.IO) { mgr.mergeCloudAndLocal() }
                delay(h * 60L * 60L * 1000L)
            } else {
                // 未开启自动同步或未登录：每分钟检查一次，登录/开启后即可开始同步
                delay(60L * 1000L)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp)
            ) {
                // 登录区 + 功能列表放在可滚动列中，避免内容超出屏幕后底部条目无法触达
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 顶部：云端登录区（坚果云 WebDAV，阿里/百度预留）
                    CloudLoginSection(
                        context = LocalContext.current,
                        repository = repository,
                        aiPrefs = aiPrefs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 中部：功能按钮
                    DrawerItem(
                        icon = Icons.Filled.Star,
                        label = "收藏",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showFavorites = true
                        }
                    )
                    DrawerItem(
                        icon = Icons.Filled.History,
                        label = "历史查词",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showHistory = true
                        }
                    )
                    DrawerItem(
                        icon = Icons.Filled.School,
                        label = "语法练习",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showGrammar = true
                        }
                    )
                    DrawerItem(
                        icon = Icons.Filled.Book,
                        label = "语法学习",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showGrammarLearn = true
                        }
                    )
                    DrawerItem(
                        icon = Icons.Filled.TextFields,
                        label = "所有代词",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showPronouns = true
                        }
                    )
                    DrawerItem(
                        icon = Icons.Filled.RecordVoiceOver,
                        label = "字母音标表",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showPhonetics = true
                        }
                    )
                    DrawerItem(
                        icon = Icons.AutoMirrored.Filled.Help,
                        label = "问句类型",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showQuestionTypes = true
                        }
                    )
                    DrawerItem(
                        icon = Icons.Filled.VideoLibrary,
                        label = "视频转文字",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showVideoImport = true
                        }
                    )
                    DrawerItem(
                        icon = Icons.Filled.Today,
                        label = "日历和数字",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showCalNum = true
                        }
                    )
                    // 口语对话入口暂时隐藏（功能代码保留，恢复时取消注释即可）
                    // DrawerItem(
                    //     icon = Icons.Filled.Mic,
                    //     label = "口语对话",
                    //     onClick = {
                    //         scope.launch { drawerState.close() }
                    //         showVoiceChat = true
                    //     }
                    // )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 底部：设置按钮固定在底部，始终可见可点
                DrawerItem(
                    icon = Icons.Filled.Settings,
                    label = "设置",
                    onClick = {
                        scope.launch { drawerState.close() }
                        showSettings = true
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                // 极简紧凑顶栏：仅一个小的汉堡菜单按钮
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "菜单", modifier = Modifier.size(22.dp))
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selected == 0,
                        onClick = { selected = 0 },
                        icon = { Icon(Icons.Filled.SmartToy, contentDescription = null) },
                        label = { Text("AI") }
                    )
                    NavigationBarItem(
                        selected = selected == 1,
                        onClick = { selected = 1 },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("查词") }
                    )
                    NavigationBarItem(
                        selected = selected == 2,
                        onClick = { selected = 2 },
                        icon = { Icon(Icons.Filled.SwapVert, contentDescription = null) },
                        label = { Text("变位") }
                    )
                    NavigationBarItem(
                        selected = selected == 3,
                        onClick = { selected = 3 },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("分组") }
                    )
                    NavigationBarItem(
                        selected = selected == 4,
                        onClick = { selected = 4 },
                        icon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                        label = { Text("句子") }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Box(modifier = Modifier.fillMaxSize().alpha(if (selected == 0) 1f else 0f).zIndex(if (selected == 0) 1f else 0f)) {
                    AIScreen(prefs = aiPrefs, onOpenSettings = { showAISettings = true }, refreshKey = aiRefreshKey)
                }
                Box(modifier = Modifier.fillMaxSize().alpha(if (selected == 1) 1f else 0f).zIndex(if (selected == 1) 1f else 0f)) {
                    LookupScreen(repository, translator, conjugator, morphology, settings, aiPrefs)
                }
                Box(modifier = Modifier.fillMaxSize().alpha(if (selected == 2) 1f else 0f).zIndex(if (selected == 2) 1f else 0f)) {
                    ConjugationScreen(conjugator, repository, translator, morphology, aiPrefs)
                }
                Box(modifier = Modifier.fillMaxSize().alpha(if (selected == 3) 1f else 0f).zIndex(if (selected == 3) 1f else 0f)) {
                    VerbGroupScreen(conjugator)
                }
                Box(modifier = Modifier.fillMaxSize().alpha(if (selected == 4) 1f else 0f).zIndex(if (selected == 4) 1f else 0f)) {
                    SentenceScreen(repository, translator, conjugator, analyzer, aiPrefs)
                }
            }
        }
    }

    // 系统返回键处理（全面屏手势/返回键统一走这里）。
    // 注意：必须声明在各覆盖层之前，这样覆盖层内部更细一级的 BackHandler 后注册、优先生效，
    // 返回手势会逐层回退（如 收藏详情 -> 收藏列表 -> 主界面），而不是直接退出整页。
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = showFavorites) { showFavorites = false }
    BackHandler(enabled = showAISettings) {
        showAISettings = false
        aiRefreshKey++
    }
    BackHandler(enabled = showHistory) { showHistory = false }
    BackHandler(enabled = showGrammar) { showGrammar = false }
    BackHandler(enabled = showGrammarLearn) { showGrammarLearn = false }
    BackHandler(enabled = showPronouns) { showPronouns = false }
    BackHandler(enabled = showPhonetics) { showPhonetics = false }
    BackHandler(enabled = showQuestionTypes) { showQuestionTypes = false }
    BackHandler(enabled = showVideoImport) { showVideoImport = false }
    BackHandler(enabled = showVoiceChat) { showVoiceChat = false }
    BackHandler(enabled = showCalNum) { showCalNum = false }

    // 收藏（双栏：AI收藏 + 单词句子收藏）
    if (showFavorites) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            SidebarFavoritesScreen(
                prefs = aiPrefs,
                repository = repository,
                onBack = { showFavorites = false }
            )
        }
    }

    // AI 模型设置
    if (showAISettings) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AISettingsScreen(
                prefs = aiPrefs,
                onBack = {
                    showAISettings = false
                    aiRefreshKey++
                }
            )
        }
    }

    // 历史查词覆盖层
    if (showHistory) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            HistoryScreen(
                repository = repository,
                settings = settings,
                onBack = { showHistory = false },
                onWordClick = { word ->
                    showHistory = false
                    selected = 1
                    // 通知查词界面填入该词
                    pendingLookupWord = word
                }
            )
        }
    }

    // 语法练习
    if (showGrammar) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            GrammarPracticeScreen(onBack = { showGrammar = false })
        }
    }

    // 日历和数字（星期 / 月份 / 相对日期 / 数字系统 / 数学符号读法）
    if (showCalNum) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            CalendarNumbersScreen(onBack = { showCalNum = false })
        }
    }

    // 语法学习（词法 / 动词 / 句法，内部自行处理逐级返回）
    if (showGrammarLearn) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            GrammarScreen(onExit = { showGrammarLearn = false })
        }
    }

    // 所有代词
    if (showPronouns) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            PronounsScreen(onBack = { showPronouns = false })
        }
    }

    // 字母音标表
    if (showPhonetics) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            PhoneticsScreen(onBack = { showPhonetics = false })
        }
    }

    // 问句类型
    if (showQuestionTypes) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            QuestionTypeScreen(
                prefs = aiPrefs,
                onOpenSettings = {
                    showQuestionTypes = false
                    showAISettings = true
                },
                onBack = { showQuestionTypes = false }
            )
        }
    }

    // 视频转文字
    if (showVideoImport) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            VideoImportScreen(
                onBack = { showVideoImport = false },
                onExtractSubtitles = onExtractSubtitles
            )
        }
    }

    // 口语对话（语音输入 -> 直接发送 -> 本地 TTS 播报，可随时打断）
    if (showVoiceChat) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            VoiceChatScreen(
                aiPrefs = aiPrefs,
                onBack = { showVoiceChat = false }
            )
        }
    }

    // 设置弹窗
    if (showSettings) {
        SettingsSheet(settings = settings, repository = repository, aiPrefs = aiPrefs, onDismiss = { showSettings = false })
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

// 用于从历史点击传词到查词界面的全局状态
var pendingLookupWord: String? by mutableStateOf(null)