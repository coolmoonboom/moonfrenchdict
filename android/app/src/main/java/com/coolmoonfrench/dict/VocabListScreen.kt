package com.coolmoonfrench.dict

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 待学习 / 待复习单词列表：左发音、中单词+音标、右进入详情；已掌握列表额外显示悬浮窗开关 */
@Composable
fun VocabListScreen(
    title: String,
    words: List<VocabEntry>,
    onOpen: (Int) -> Unit,
    onBack: () -> Unit,
    showFloatingToggle: Boolean = false
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "${words.size} 词",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(i) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    if (showFloatingToggle) {
                        // 悬浮窗开关：点击把该词加入悬浮窗队列并常驻屏幕
                        Switch(
                            checked = FloatingWindowState.visible && FloatingWindowState.isInQueue(e.word),
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (FloatingWindowControl.overlayPermissionGranted(context)) {
                                        FloatingWindowControl.add(context, e)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "需要悬浮窗权限，请在系统设置中允许「酷月法语」显示在其他应用上层",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        FloatingWindowControl.requestPermission(context)
                                    }
                                } else {
                                    FloatingWindowControl.remove(context, e.word)
                                }
                            },
                            modifier = Modifier.scaleSwitchSize()
                        )
                    }

                    IconButton(onClick = { onOpen(i) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "查看详情",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }
    }
}

/** 紧凑开关尺寸：悬浮窗开关保持行高一致，不放大可点击热区。 */
private fun Modifier.scaleSwitchSize(): Modifier =
    this.padding(horizontal = 2.dp)
