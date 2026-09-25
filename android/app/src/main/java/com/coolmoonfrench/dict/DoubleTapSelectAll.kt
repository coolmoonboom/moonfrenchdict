package com.coolmoonfrench.dict

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * 双击回调：在 Initial 事件阶段监听。第一次点击放行（不影响输入框单击定位光标、
 * 长按选择）；检测到第二次快速点击后，**消费**这次手势并触发 [onDoubleTap]，
 * 从而阻止输入框自身的「双击选词」覆盖我们设置的整段选区。
 */
@Composable
fun Modifier.onDoubleTap(onDoubleTap: () -> Unit): Modifier = composed {
    var lastTap by remember { mutableStateOf(0L) }
    val current by rememberUpdatedState(onDoubleTap)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val now = down.uptimeMillis
            val isDouble = now - lastTap in 1..300L
            lastTap = if (isDouble) 0L else now
            if (isDouble) {
                down.consume()
                current()
                waitForUpOrCancellation(pass = PointerEventPass.Initial)?.consume()
            } else {
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
            }
        }
    }
}

/**
 * 与 [OutlinedTextField] 用法一致、但内部使用 [TextFieldValue] 的输入框，支持：
 * - 双击全选并弹出「复制 / 剪切 / 粘贴」菜单；
 * - 右侧一键清除（X）按钮（[showClear] 且文本非空且可编辑时显示）。
 * 对外仍以 String 读写，调用方无需改动原有状态与逻辑。
 */
@Composable
fun SelectableOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    placeholder: @Composable (() -> Unit)? = null,
    label: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = LocalTextStyle.current,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    showClear: Boolean = true
) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    var tf by remember { mutableStateOf(TextFieldValue(value)) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (value != tf.text) {
            tf = tf.copy(text = value, selection = TextRange(value.length))
        }
    }

    fun commit(v: TextFieldValue) {
        tf = v
        onValueChange(v.text)
    }

    /** 有选区时取选区文本，否则取全文。 */
    fun selectedOrAll(): String {
        val s = tf.selection
        return if (!s.collapsed) tf.text.substring(s.min, s.max) else tf.text
    }

    // 菜单打开时判断剪贴板是否有内容；用 hasPrimaryClip 避免触发系统的剪贴板读取提示。
    val canPaste = remember(menuExpanded) {
        runCatching { clipboard.hasPrimaryClip() }.getOrDefault(false)
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = tf,
            onValueChange = { commit(it) },
            modifier = Modifier.fillMaxWidth().onDoubleTap {
                tf = tf.copy(selection = TextRange(0, tf.text.length))
                menuExpanded = true
            },
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            maxLines = maxLines,
            placeholder = placeholder,
            label = label,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingIcon?.invoke()
                    if (showClear && tf.text.isNotEmpty() && enabled && !readOnly) {
                        IconButton(
                            onClick = {
                                commit(tf.copy(text = "", selection = TextRange(0)))
                            }
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清除输入",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            leadingIcon = leadingIcon,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = textStyle,
            shape = shape,
            colors = colors
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("复制") },
                enabled = tf.text.isNotEmpty(),
                onClick = {
                    val text = selectedOrAll()
                    if (text.isNotEmpty()) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
                    }
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("剪切") },
                enabled = tf.text.isNotEmpty() && enabled && !readOnly,
                onClick = {
                    val s = tf.selection
                    val text = selectedOrAll()
                    if (text.isNotEmpty()) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
                    }
                    val newText = if (!s.collapsed) tf.text.removeRange(s.min, s.max) else ""
                    commit(tf.copy(text = newText, selection = TextRange(newText.length)))
                    menuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("粘贴") },
                enabled = canPaste && enabled && !readOnly,
                onClick = {
                    val paste = runCatching {
                        clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                    }.getOrDefault("")
                    if (paste.isNotEmpty()) {
                        val s = tf.selection
                        val newText = tf.text.replaceRange(s.min, s.max, paste)
                        commit(tf.copy(text = newText, selection = TextRange(s.min + paste.length)))
                    }
                    menuExpanded = false
                }
            )
        }
    }
}
