package com.coolmoonfrench.dict

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model

/**
 * 口语对话：长按麦克风 → Vosk 离线识别 → 直接发送 → AI 回复并用本地 TTS 播报。
 *
 * 交互要点：
 * - 长按麦克风说话，松开即把识别文本直接发送（不经过输入框）。
 * - AI 回复期间长按麦克风会**打断**：停止播报并作废正在进行的请求，随后新消息的回答会接续。
 * - 全程离线识别，只有 AI 回复需要联网（复用 App 的 AI 配置）。
 */

internal data class VoiceLine(
    val user: String,
    val ai: String = "",
    val status: String = "loading" // loading | ok | error | interrupted
)

private const val VOICE_SYSTEM_PROMPT = """Tu es un partenaire de conversation en français pour un apprenant chinois.
Règles:
- Réponds toujours en français avec des phrases courtes et naturelles (1 à 3 phrases maximum).
- Adopte un ton oral, chaleureux et détendu, comme une vraie conversation entre deux personnes.
- Pas de longues explications, pas de listes, pas de cours de grammaire.
- Termine chaque réponse par une question simple pour relancer la conversation.
- Si le message contient une faute évidente, propose très brièvement la forme correcte, puis continue."""

/** 取最近若干轮对话构造请求历史，避免上下文过长。 */
internal fun buildHistory(lines: List<VoiceLine>, currentIndex: Int): List<AIMessage> {
    val msgs = mutableListOf<AIMessage>()
    val start = maxOf(0, currentIndex - 11)
    for (i in start..currentIndex) {
        val line = lines[i]
        msgs.add(AIMessage(role = "user", content = line.user))
        if (line.status == "ok" && line.ai.isNotBlank()) {
            msgs.add(AIMessage(role = "assistant", content = line.ai))
        }
    }
    return msgs
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatScreen(
    aiPrefs: AIPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val listState = rememberLazyListState()

    val lines = remember { mutableStateListOf<VoiceLine>() }
    var model by remember { mutableStateOf<Model?>(null) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var phase by remember { mutableStateOf("loading") } // loading | error | idle
    var liveText by remember { mutableStateOf("") }
    var aiThinking by remember { mutableStateOf(false) }
    var aiSpeaking by remember { mutableStateOf(false) }
    var gen by remember { mutableStateOf(0) }

    val asrHolder = remember { arrayOfNulls<StreamingAsr>(1) }
    var asrActive by remember { mutableStateOf(false) }
    var speakPoll by remember { mutableStateOf<Job?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "需要麦克风权限才能进行语音对话", Toast.LENGTH_LONG).show()
        }
    }

    // 预热 Piper/系统 TTS，避免第一次播报等待
    LaunchedEffect(Unit) { Espeak.ensureInitialized(context) }

    LaunchedEffect(reloadKey) {
        phase = "loading"
        modelError = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dir = VoskModelManager.currentModelDir(context)
                    ?: VoskModelManager.ensureSmallModel(context)
                Model(dir.absolutePath)
            }
        }
        result.onSuccess {
            model = it
            phase = "idle"
        }.onFailure {
            modelError = "离线语音模型加载失败：${it.message ?: "未知错误"}"
            phase = "error"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { asrHolder[0]?.cancel() }
            asrHolder[0] = null
            speakPoll?.cancel()
            Speech.stop()
            runCatching { model?.close() }
        }
    }

    fun stopAi() {
        gen += 1
        speakPoll?.cancel()
        speakPoll = null
        if (aiThinking) {
            val i = lines.indexOfLast { it.status == "loading" }
            if (i >= 0) lines[i] = lines[i].copy(status = "interrupted")
        }
        aiThinking = false
        aiSpeaking = false
        Speech.stop()
    }

    fun speakReply(text: String, myGen: Int) {
        aiSpeaking = true
        Espeak.speakWithFeedback(context, text)
        speakPoll?.cancel()
        speakPoll = scope.launch {
            delay(900)
            var guard = 0
            while (gen == myGen && Espeak.isPlaying() && guard < 1200) {
                delay(150)
                guard++
            }
            if (gen == myGen) aiSpeaking = false
        }
    }

    fun sendMessage(text: String) {
        val config = aiPrefs.modelConfig
        if (config.apiUrl.isBlank() || config.apiToken.isBlank() || config.modelName.isBlank()) {
            Toast.makeText(context, "请先在「设置 - AI」中填写模型配置", Toast.LENGTH_LONG).show()
            return
        }
        lines.add(VoiceLine(user = text))
        val idx = lines.lastIndex
        val myGen = gen + 1
        gen = myGen
        aiThinking = true
        scope.launch {
            try {
                val history = buildHistory(lines, idx)
                val reply = AIClient.chat(
                    config,
                    history,
                    systemPromptOverride = VOICE_SYSTEM_PROMPT
                )
                if (gen != myGen) return@launch
                aiThinking = false
                if (reply.isBlank()) {
                    lines[idx] = lines[idx].copy(status = "error")
                } else {
                    lines[idx] = lines[idx].copy(ai = reply, status = "ok")
                    speakReply(reply, myGen)
                }
            } catch (e: CancellationException) {
                // 被新的发言打断：保持静默
            } catch (e: Exception) {
                if (gen == myGen) {
                    aiThinking = false
                    lines[idx] = lines[idx].copy(status = "error")
                }
            }
        }
    }

    fun beginRecording() {
        if (phase != "idle") {
            if (phase == "loading") Toast.makeText(context, "语音模型加载中，请稍候", Toast.LENGTH_SHORT).show()
            else Toast.makeText(context, modelError ?: "语音模型不可用", Toast.LENGTH_LONG).show()
            return
        }
        if (asrHolder[0] != null) return
        val m = model ?: return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        stopAi()
        liveText = ""
        val asr = StreamingAsr(m)
        asrHolder[0] = asr
        asrActive = true
        asr.start(object : StreamingAsr.Listener {
            override fun onPartial(text: String) {
                mainHandler.post { if (asrHolder[0] === asr) liveText = text }
            }

            override fun onError(message: String) {
                mainHandler.post {
                    if (asrHolder[0] === asr) {
                        asrHolder[0] = null
                        asrActive = false
                        liveText = ""
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    fun finishRecording() {
        val asr = asrHolder[0] ?: return
        asrHolder[0] = null
        asrActive = false
        val text = try {
            asr.stop()
        } catch (e: Exception) {
            ""
        }
        liveText = ""
        if (text.isBlank()) {
            Toast.makeText(context, "没有听清，请再说一次", Toast.LENGTH_SHORT).show()
            return
        }
        sendMessage(text)
    }

    val onPressStart by rememberUpdatedState { beginRecording() }
    val onPressEnd by rememberUpdatedState { finishRecording() }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("口语对话", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "长按麦克风说法语，松开直接发送",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (lines.isNotEmpty()) {
                    TextButton(onClick = {
                        stopAi()
                        lines.clear()
                    }) { Text("清空") }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (lines.isEmpty() && phase == "idle") {
                    item {
                        Text(
                            "点击下方麦克风开始一段法语对话。AI 会用简短的法语回应并朗读出来，你可以随时长按麦克风打断它。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
                itemsIndexed(lines) { index, line ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 300.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    line.user,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        VoiceAiBubble(
                            line = line,
                            thinking = aiThinking && index == lines.lastIndex && line.status == "loading",
                            speaking = aiSpeaking && index == lines.lastIndex && line.status == "ok",
                            onStopSpeak = { stopAi() }
                        )
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val status = when {
                        phase == "loading" -> "正在加载离线语音模型…"
                        phase == "error" -> modelError ?: "语音模型不可用"
                        asrActive -> if (liveText.isBlank()) "正在聆听…（松开结束）" else liveText
                        aiThinking -> "AI 正在思考…"
                        aiSpeaking -> "AI 正在说话（长按麦克风可打断）"
                        else -> "长按麦克风开始说话"
                    }
                    Text(
                        status,
                        fontSize = 13.sp,
                        color = if (asrActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        asrActive -> MaterialTheme.colorScheme.error
                                        phase == "idle" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            onPressStart()
                                            tryAwaitRelease()
                                            onPressEnd()
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "长按说话",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    if (phase == "error") {
                        TextButton(onClick = { reloadKey++ }) { Text("重试加载模型") }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceAiBubble(
    line: VoiceLine,
    thinking: Boolean,
    speaking: Boolean,
    onStopSpeak: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            when {
                line.status == "loading" || thinking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("思考中…", fontSize = 14.sp)
                }
                line.status == "error" -> Text(
                    "回复失败，请检查网络或 AI 配置后重试",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error
                )
                line.status == "interrupted" -> Text(
                    "（已打断）",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Column {
                    Text(line.ai, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (speaking) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "播报中 · 点击停止",
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = { onStopSpeak() })
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
