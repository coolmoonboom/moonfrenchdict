package com.moonfrench.dict

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert

class MainActivity : ComponentActivity() {

    private lateinit var repository: DictRepository
    private lateinit var translator: MyMemoryTranslator
    private lateinit var conjugator: VerbConjugator
    private lateinit var analyzer: SentenceAnalyzer
    private lateinit var morphology: MorphologyAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = DictRepository(this)
        translator = MyMemoryTranslator()
        conjugator = VerbConjugator()
        analyzer = SentenceAnalyzer(repository, conjugator)
        morphology = MorphologyAnalyzer()

        var loaded by mutableStateOf(false)

        setContent {
            FrenchDictTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!loaded) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text("加载词典中…")
                        }
                        LaunchedEffect(Unit) {
                            repository.load()
                            loaded = true
                        }
                    } else {
                        MainTabs(repository, translator, conjugator, analyzer, morphology)
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
    morphology: MorphologyAnalyzer
) {
    var selected by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("法语词典") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selected == 0,
                    onClick = { selected = 0 },
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("查词") }
                )
                NavigationBarItem(
                    selected = selected == 1,
                    onClick = { selected = 1 },
                    icon = { Icon(Icons.Filled.SwapVert, contentDescription = null) },
                    label = { Text("动词变位") }
                )
                NavigationBarItem(
                    selected = selected == 2,
                    onClick = { selected = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("动词分组") }
                )
                NavigationBarItem(
                    selected = selected == 3,
                    onClick = { selected = 3 },
                    icon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                    label = { Text("句子分析") }
                )
                NavigationBarItem(
                    selected = selected == 4,
                    onClick = { selected = 4 },
                    icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    label = { Text("收藏") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                0 -> LookupScreen(repository, translator, conjugator, morphology)
                1 -> ConjugationScreen(conjugator, repository, translator, morphology)
                2 -> VerbGroupScreen(conjugator)
                3 -> SentenceScreen(repository, translator, conjugator, analyzer)
                4 -> FavoritesScreen(repository)
            }
        }
    }
}
