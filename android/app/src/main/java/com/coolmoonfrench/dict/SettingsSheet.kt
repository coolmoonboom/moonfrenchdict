package com.coolmoonfrench.dict

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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

            // 查词记录最大数量
            Text("查词记录最大数量", fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("10", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = settings.historyLimit.toFloat(),
                    onValueChange = { settings.updateHistoryLimit(it.roundToInt()) },
                    valueRange = 10f..100f,
                    steps = 8,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("100", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "当前：${settings.historyLimit} 条",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
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
        }
    }
}
