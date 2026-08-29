package com.coolmoonfrench.dict

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AISettingsScreen(
    prefs: AIPreferences,
    onBack: () -> Unit
) {
    var interfaceType by remember { mutableStateOf(prefs.modelConfig.interfaceType) }
    var apiUrl by remember { mutableStateOf(prefs.modelConfig.apiUrl) }
    var apiToken by remember { mutableStateOf(prefs.modelConfig.apiToken) }
    var modelName by remember { mutableStateOf(prefs.modelConfig.modelName) }
    var notes by remember { mutableStateOf(prefs.modelConfig.notes) }
    var showToken by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 模型选择弹窗
    if (showModelDialog && models.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("选择模型") },
            text = {
                Column {
                    models.forEach { model ->
                        TextButton(
                            onClick = {
                                modelName = model
                                showModelDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(model, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) { Text("取消") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("添加模型", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // 接口格式
            SectionLabel("接口格式")
            SegmentedControl(
                options = AIInterfaceType.OPTIONS,
                selected = interfaceType,
                onSelect = { interfaceType = it }
            )

            // 模型 API 地址
            SectionLabel("模型 API 地址")
            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it; errorMsg = null },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://api.openai.com/v1", fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            HelperText(
                "实际请求 ${apiUrl.trim().trimEnd('/')}/chat/completions" +
                        (if (interfaceType == AIInterfaceType.ANTHROPIC) "（Anthropic 为 /messages）" else "")
            )

            // API Token
            SectionLabel("API Token")
            OutlinedTextField(
                value = apiToken,
                onValueChange = { apiToken = it; errorMsg = null },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("请输入 API Token", fontSize = 14.sp) },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showToken) "隐藏" else "显示"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
            )

            // 模型名称
            SectionLabel("模型名称")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it; errorMsg = null },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("如 deepseek-chat", fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { /* dismiss keyboard */ })
                )
                Button(
                    onClick = {
                        if (apiUrl.isBlank() || apiToken.isBlank()) {
                            errorMsg = "请先填写 API 地址和 Token"
                            return@Button
                        }
                        loading = true
                        errorMsg = null
                        scope.launch {
                            try {
                                val cfg = AIModelConfig(interfaceType, apiUrl, apiToken, modelName, notes)
                                models = AIClient.listModels(cfg)
                                if (models.isEmpty()) {
                                    errorMsg = "拉取成功但列表为空，请手动填写模型名称"
                                } else {
                                    showModelDialog = true
                                }
                            } catch (e: Exception) {
                                errorMsg = "拉取失败：${e.message?.take(100) ?: "未知错误"}。请按服务商文档手动填写模型名称。"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.height(56.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onTertiary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("拉取列表", fontSize = 13.sp)
                }
            }
            HelperText("输入 API Token 后可拉取可用模型列表选择；拉取失败时按服务商文档手动填写。")

            // 备注
            SectionLabel("备注（选填）")
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("模型展示名，如「我的 DeepSeek」", fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            // 高级配置
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("高级配置", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    if (showAdvanced) "展开" else "展开",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (showAdvanced) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("支持的接口格式：", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("• OpenAI Chat：追加 /chat/completions，补全对话", fontSize = 12.sp)
                        Text("• OpenAI Responses：追加 /responses，响应格式", fontSize = 12.sp)
                        Text("• Anthropic：追加 /messages，需 x-api-key", fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("拉取模型列表请求：GET {base}/models", fontSize = 12.sp)
                    }
                }
            }

            // 错误提示
            if (errorMsg != null) {
                Text(
                    errorMsg!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // 检查并保存
            Button(
                onClick = {
                    if (apiUrl.isBlank() || apiToken.isBlank() || modelName.isBlank()) {
                        errorMsg = "请填写 API 地址、Token 和模型名称"
                        return@Button
                    }
                    loading = true
                    errorMsg = null
                    scope.launch {
                        try {
                            val cfg = AIModelConfig(interfaceType, apiUrl, apiToken, modelName, notes)
                            // 验证：拉取模型列表测试连通性
                            AIClient.listModels(cfg)
                            prefs.modelConfig = cfg
                            onBack()
                        } catch (e: Exception) {
                            errorMsg = "验证失败：${e.message?.take(100) ?: "未知错误"}。请检查配置后重试。"
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onTertiary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("检查并保存", fontSize = 16.sp)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun HelperText(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, lineHeight = 16.sp)
}

@Composable
private fun SegmentedControl(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (label, value) ->
            val isSel = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}