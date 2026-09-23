package com.coolmoonfrench.dict

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 单词详情：词形+音标、收藏、全部义项、本地例句、上一曲/播放/下一曲 */
@Composable
fun VocabDetailScreen(
    words: List<VocabEntry>,
    initialIndex: Int,
    onNavigate: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DictRepository(context) }
    val scope = rememberCoroutineScope()

    val index = initialIndex.coerceIn(0, (words.size - 1).coerceAtLeast(0))
    val entry = words.getOrNull(index)
    val word = entry?.word.orEmpty()

    var meanings by remember { mutableStateOf<List<String>>(emptyList()) }
    var example by remember { mutableStateOf<VocabExamples.Example?>(null) }
    var fav by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(word) {
        fav = word.isNotEmpty() && repository.isFavorite(word)
        meanings = withContext(Dispatchers.IO) {
            repository.lookupExact(word).map { it.meaning }.filter { it.isNotBlank() }.distinct()
        }
        example = if (word.isEmpty()) null
        else withContext(Dispatchers.IO) { VocabExamples.lookup(context, word) }
    }

    fun speakWord() {
        if (word.isNotEmpty()) Espeak.speakWithFeedback(context, word, deterministic = true)
    }

    fun playAll() {
        scope.launch {
            speakWord()
            delay(1400)
            val ex = example ?: return@launch
            Speech.ensureInitialized(context)
            Speech.speakWithFeedback(context, ex.fr)
        }
    }

    if (entry == null) {
        onBack()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "${index + 1} / ${words.size}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.word,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val ipa = FrenchIpa.wrap(entry.word)
                    if (ipa.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(ipa, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = {
                    if (fav) repository.removeFavorite(entry.word) else repository.addFavorite(entry.word)
                    fav = !fav
                }) {
                    Icon(
                        if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "收藏",
                        tint = if (fav) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { speakWord() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "朗读单词",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 全部义项
            Text("释义", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (meanings.isEmpty()) {
                Text(entry.meaning, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            } else {
                meanings.forEach { m ->
                    Text(m, fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // 例句
            Text("例句", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val ex = example
            if (ex == null) {
                Text(
                    "暂无例句",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ex.fr, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(2.dp))
                            Text(ex.zh, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            Speech.ensureInitialized(context)
                            Speech.speakWithFeedback(context, ex.fr)
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "朗读例句",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // 底部：上一曲 / 播放 / 下一曲
        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (index > 0) onNavigate(index - 1) },
                    enabled = index > 0
                ) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一个", modifier = Modifier.size(32.dp))
                }
                FilledIconButton(
                    onClick = { playAll() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放", modifier = Modifier.size(32.dp))
                }
                IconButton(
                    onClick = { if (index < words.size - 1) onNavigate(index + 1) },
                    enabled = index < words.size - 1
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一个", modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}
