package com.coolmoonfrench.dict

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 云端登录区（侧滑栏顶部）：选择网盘 → 输入账号/应用密码 → 登录；已登录可查看账号并退出。 */
@Composable
fun CloudLoginSection(
    context: Context,
    repository: DictRepository,
    aiPrefs: AIPreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val provider = remember { NutsCloudProvider(context) }
    var loggedIn by remember { mutableStateOf(provider.isConfigured()) }
    var account by remember { mutableStateOf(provider.account() ?: "") }
    var pwd by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 头像占位符：登录后显示坚果云首字母
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (loggedIn && account.isNotBlank()) account.first().uppercase()
                    else "?",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (loggedIn) "坚果云" else "未登录",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    if (loggedIn && account.isNotBlank()) account else "同步到云端",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!loggedIn) {
            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text("坚果云账号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pwd,
                onValueChange = { pwd = it },
                label = { Text("应用密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "应用密码需在坚果云网页「安全选项→应用管理」中创建，不是登录密码。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (account.isBlank() || pwd.isBlank()) {
                        error = "请输入账号与应用密码"
                        return@Button
                    }
                    busy = true
                    error = null
                    scope.launch(Dispatchers.IO) {
                        provider.saveCredentials(account, pwd)
                        val ok = provider.validate()
                        withContext(Dispatchers.Main) {
                            busy = false
                            if (ok) {
                                loggedIn = true
                                Toast.makeText(context, "坚果云登录成功", Toast.LENGTH_SHORT).show()
                            } else {
                                provider.clearCredentials()
                                error = "登录失败：账号/应用密码无效或网络异常"
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("登录")
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            Button(
                onClick = {
                    busy = true
                    scope.launch(Dispatchers.IO) {
                        val ok = withContext(Dispatchers.IO) { provider.validate() }
                        withContext(Dispatchers.Main) {
                            busy = false
                            Toast.makeText(
                                context,
                                if (ok) "连接正常" else "连接失败，请检查网络",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("检查连接") }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    provider.clearCredentials()
                    loggedIn = false
                    account = ""
                    pwd = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** 同步操作区（设置→同步设置）：手动同步 / 回滚到上一个版本 / 合并云端与本地。 */
@Composable
fun SyncActions(
    context: Context,
    repository: DictRepository,
    aiPrefs: AIPreferences,
    settings: AppSettings,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val provider = remember { NutsCloudProvider(context) }
    val manager = remember {
        SyncManager(context, repository, aiPrefs, provider)
    }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var rollbackEnabled by remember { mutableStateOf(manager.latestSnapshotFile() != null) }
    var cloudAvailable by remember { mutableStateOf(provider.isConfigured()) }

    fun runSync(block: () -> Boolean, okMsg: String, errMsg: String) {
        if (busy) return
        busy = true
        status = null
        scope.launch(Dispatchers.IO) {
            val ok = runCatching { block() }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                busy = false
                if (ok) {
                    status = okMsg
                    rollbackEnabled = manager.latestSnapshotFile() != null
                    cloudAvailable = provider.isConfigured()
                    onDismiss()
                } else {
                    status = errMsg
                }
            }
        }
    }

    if (!cloudAvailable) {
        Text(
            "请先在左侧菜单顶部登录云盘（坚果云）。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }

    Button(
        onClick = { runSync({ manager.pushToCloud() }, "已上传到云端", "上传失败") },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) { Text("立即同步（上传到云端）") }

    Button(
        onClick = { runSync({ manager.mergeCloudAndLocal() }, "合并完成", "合并失败") },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) { Text("合并云端与本地") }

    OutlinedButton(
        onClick = { runSync({ manager.rollbackToLastSnapshot() }, "已回滚到上一个版本", "无可回滚的版本") },
        enabled = !busy && rollbackEnabled,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) { Text("回滚到上一个版本") }

    Text(
        if (rollbackEnabled) "已保存 ${manager.snapshotsWithin3Days()} 份近 3 天快照" else "暂无本地快照",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
    if (manager.lastSyncTime() > 0) {
        val t = manager.lastSyncTime()
        val d = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(t)
        Text("上次同步：$d", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (busy) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("处理中…", fontSize = 12.sp)
        }
    }
    status?.let {
        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}