package com.coolmoonfrench.dict

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    repository: DictRepository,
    aiPrefs: AIPreferences,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "设置",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // 全局字体大小
            Text("全局字体大小", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("小", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = settings.fontScale,
                    onValueChange = { settings.updateFontScale(it) },
                    valueRange = 0.8f..1.6f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("大", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "当前：${((settings.fontScale * 100).roundToInt())}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ---------- 同步设置 ----------
            Text("同步设置", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))

            Text("同步间隔", fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("关", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = settings.syncIntervalHours.toFloat(),
                    onValueChange = { settings.updateSyncInterval(it.roundToInt()) },
                    valueRange = 0f..24f,
                    steps = 6,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("24h", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (settings.syncIntervalHours <= 0) "当前：关闭（仅手动同步）"
                else "当前：每 ${settings.syncIntervalHours} 小时自动同步",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SyncActions(
                context = LocalContext.current,
                repository = repository,
                aiPrefs = aiPrefs,
                settings = settings,
                onDismiss = onDismiss
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 深色模式开关
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("深色模式", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Switch(
                    checked = settings.darkModeEnabled,
                    onCheckedChange = { settings.updateDarkMode(it) }
                )
            }

            Text(
                "说明：深色模式开启后，App 始终使用深色配色；关闭后跟随系统。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 调试日志开关
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("调试日志（记录到 Documents）", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Switch(
                    checked = settings.debugLogEnabled,
                    onCheckedChange = { settings.updateDebugLog(it) }
                )
            }

            Text(
                "开启后，运行日志与闪退堆栈会写入手机 Documents/月球法语/logs 目录，方便排查问题。修复后建议关闭。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 朗读语速
            Text("朗读语速", fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("慢", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = settings.speechRate,
                    onValueChange = { settings.updateSpeechRate(it) },
                    valueRange = 0.75f..1.5f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("快", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "当前：${((settings.speechRate * 100).roundToInt())}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}
