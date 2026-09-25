package com.coolmoonfrench.dict

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 单词列表：左发音、中单词+音标、右进入详情。
 * 已掌握列表额外支持多选（长按进入选择），右上角提供「全选 / 反选 / 播放」，
 * 点「播放」把所选单词作为队列交给悬浮窗开始列表循环播报。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VocabListScreen(
    title: String,
    words: List<VocabEntry>,
    onOpen: (Int) -> Unit,
    onBack: () -> Unit,
    enableFloatingPlay: Boolean = false
) {
    val context = LocalContext.current
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    fun exitSelection() {
        selectionMode = false
        selected.clear()
    }

    fun playSelected() {
        val picked = words.filter { selected.contains(it.word) }
        if (picked.isEmpty()) return
        if (!FloatingWindowControl.overlayPermissionGranted(context)) {
            Toast.makeText(
                context,
                "需要悬浮窗权限，请在系统设置中允许「酷月法语」显示在其他应用上层",
                Toast.LENGTH_LONG
            ).show()
            FloatingWindowControl.requestPermission(context)
            return
        }
        FloatingWindowControl.startListLoop(context, picked, 0)
        exitSelection()
    }

    BackHandler { if (selectionMode) exitSelection() else onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))

            if (enableFloatingPlay && selectionMode) {
                TextButton(onClick = {
                    selected.clear()
                    selected.addAll(words.map { it.word })
                }) { Text("全选", fontSize = 13.sp) }
                TextButton(onClick = {
                    val all = words.map { it.word }
                    val inverted = all.filterNot { selected.contains(it) }
                    selected.clear()
                    selected.addAll(inverted)
                }) { Text("反选", fontSize = 13.sp) }
                TextButton(
                    onClick = { playSelected() },
                    enabled = selected.isNotEmpty()
                ) { Text("播放", fontSize = 13.sp) }
            } else {
                Text(
                    if (selectionMode) "已选 ${selected.size}/${words.size}" else "${words.size} 词",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }

        if (words.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无单词",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(words) { i, e ->
                val isSelected = selected.contains(e.word)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) {
                                    if (isSelected) selected.remove(e.word) else selected.add(e.word)
                                } else {
                                    onOpen(i)
                                }
                            },
                            onLongClick = {
                                if (enableFloatingPlay && !selectionMode) {
                                    selectionMode = true
                                    if (!selected.contains(e.word)) selected.add(e.word)
                                }
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (!selected.contains(e.word)) selected.add(e.word)
                                } else {
                                    selected.remove(e.word)
                                }
                            }
                        )
                    }

                    IconButton(onClick = {
                        Espeak.speakWithFeedback(context, e.word, deterministic = true)
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "朗读",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.word, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            val ipa = FrenchIpa.wrap(e.word)
                            if (ipa.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    ipa,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (e.meaning.isNotBlank()) {
                            Text(
                                e.meaning,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    if (!selectionMode) {
                        IconButton(onClick = { onOpen(i) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "查看详情",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }
    }
}
