package com.coolmoonfrench.dict

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
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
fun HistoryScreen(
    repository: DictRepository,
    settings: AppSettings,
    onBack: () -> Unit,
    onWordClick: (String) -> Unit
) {
    var history by remember { mutableStateOf(repository.loadHistory()) }
    var ttsReady by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("历史查词", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                repository.clearHistory()
                history = emptyList()
            }) {
                Icon(Icons.Filled.Delete, contentDescription = "清空", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("清空", fontSize = 13.sp)
            }
        }

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无查词历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .clickable { onWordClick(entry.word) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.word, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                Text(
                                    entry.meaning.take(50),
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
                        }
                    }
                }
            }
        }
    }
}
