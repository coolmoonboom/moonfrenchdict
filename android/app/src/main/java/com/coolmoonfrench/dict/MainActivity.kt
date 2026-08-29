package com.coolmoonfrench.dict

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Notes
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape

class MainActivity : ComponentActivity() {

    private lateinit var repository: DictRepository
    private lateinit var translator: MyMemoryTranslator
    private lateinit var conjugator: VerbConjugator
    private lateinit var analyzer: SentenceAnalyzer
    private lateinit var morphology: MorphologyAnalyzer
    private lateinit var aiPrefs: AIPreferences

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
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("加载词典中…")
                        }
                        LaunchedEffect(Unit) {
                            repository.load()
                            loaded = true
                        }
                    } else {
                        MainTabs(
                            repository, translator, conjugator, analyzer, morphology, settings, aiPrefs
                        )
                    }
                }
            }
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
    aiPrefs: AIPreferences
) {
    var selected by rememberSaveable { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showGrammar by remember { mutableStateOf(false) }
    var showPronouns by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }
    var showAISettings by remember { mutableStateOf(false) }
    var aiRefreshKey by remember { mutableStateOf(0) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp)
            ) {
                // 顶部：头像占位符 + 登录按钮
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("?",
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { /* 登录功能预留 */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("登录")
                    }
                }

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
                    icon = Icons.Filled.TextFields,
                    label = "所有代词",
                    onClick = {
                        scope.launch { drawerState.close() }
                        showPronouns = true
                    }
                )

                Spacer(Modifier.weight(1f))

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 底部：设置按钮
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
                    LookupScreen(repository, translator, conjugator, morphology, settings)
                }
                Box(modifier = Modifier.fillMaxSize().alpha(if (selected == 2) 1f else 0f).zIndex(if (selected == 2) 1f else 0f)) {
                    ConjugationScreen(conjugator, repository, translator, morphology)
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

    // 所有代词
    if (showPronouns) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            PronounsScreen(onBack = { showPronouns = false })
        }
    }

    // 系统返回键处理：二级界面优先关闭，抽屉打开时先关闭抽屉
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = showFavorites) { showFavorites = false }
    BackHandler(enabled = showAISettings) {
        showAISettings = false
        aiRefreshKey++
    }
    BackHandler(enabled = showHistory) { showHistory = false }
    BackHandler(enabled = showGrammar) { showGrammar = false }
    BackHandler(enabled = showPronouns) { showPronouns = false }

    // 设置弹窗
    if (showSettings) {
        SettingsSheet(settings = settings, onDismiss = { showSettings = false })
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