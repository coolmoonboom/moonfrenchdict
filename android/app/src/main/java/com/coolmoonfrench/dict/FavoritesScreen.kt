package com.coolmoonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun FavoritesScreen(repository: DictRepository) {
    var favorites by remember { mutableStateOf<List<DictEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val saved = repository.loadFavorites()
        favorites = saved
        loaded = true
    }

    // 初始化 Mimic 法语 TTS（幂等，非阻塞）；轮询等待就绪
    LaunchedEffect(Unit) {
        Espeak.ensureInitialized(context)
        while (true) {
            ttsReady = Espeak.isReady()
            if (ttsReady) break
            delay(250)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "收藏的单词",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无收藏。在查词界面点击 ☆ 收藏 保存单词。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp))
            }
            return@Column
        }

        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(favorites) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.word, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                Text(
                                    entry.meaning.take(60),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (!ttsReady) {
                                        Espeak.ensureInitialized(context)
                                        android.widget.Toast.makeText(
                                            context,
                                            "语音引擎" + (Espeak.lastError()?.let { "：$it" } ?: "正在初始化，请稍候"),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } else if (!Espeak.speak(entry.word)) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "朗读失败：${Espeak.lastError() ?: "未知错误"}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "朗读 ${entry.word}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            CopyButton(entry.word, context)
                            TextButton(onClick = {
                                repository.removeFavorite(entry.word)
                                favorites = repository.loadFavorites()
                            }) {
                                Text("移除", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
    }
    }
}

@Composable
private fun CopyButton(text: String, context: Context) {
    IconButton(onClick = {
        val clip = ClipData.newPlainText("text", text)
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }) {
        Text("复制", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
    }
}
