package com.coolmoonfrench.dict

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * 在 ComposeView 的指定位置（窗口坐标）派发一次长按手势，尽力唤起系统原生文本选择工具条。
 * Compose 不支持编程式直接弹出工具条，只能模拟长按；坐标命中失败时用户仍可手动长按唤出。
 */
private fun dispatchLongPress(view: View, x: Float, y: Float) {
    val downTime = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
    view.dispatchTouchEvent(down)
    view.postDelayed({
        val up = MotionEvent.obtain(
            downTime, downTime + 500, MotionEvent.ACTION_UP, x, y, 0
        )
        view.dispatchTouchEvent(up)
    }, 500)
}

/**
 * 与 [OutlinedTextField] 用法一致、但内部使用 [TextFieldValue] 的输入框，支持：
 * - 双击全选（单行词框选整词，多行句框选全句），自动聚焦并弹出输入法；
 * - 右侧一键清除（X）按钮（[showClear] 且文本非空且可编辑时显示）。
 * 文本选择的复制 / 剪切 / 粘贴统一使用系统原生长按文本选择工具条，不再自绘菜单。
 * 对外仍以 String 读写，调用方无需改动原有状态与逻辑。
 *
 * @param focusRequester 外部聚焦器（可空，内部默认持有）。
 * @param requestFocusKey 当该值从 null 变为非 null 时，自动聚焦并弹出输入法，
 *   用于「点开界面即开始搜索」。聚焦成功后再弹输入法，避免键盘弹出但无光标。
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
    showClear: Boolean = true,
    focusRequester: FocusRequester? = null,
    requestFocusKey: Any? = null
) {
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    val internalFocusRequester = remember { FocusRequester() }
    val fr = focusRequester ?: internalFocusRequester
    var tf by remember { mutableStateOf(TextFieldValue(value)) }
    var fieldBounds by remember { mutableStateOf<Rect?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(value) {
        if (value != tf.text) {
            tf = tf.copy(text = value, selection = TextRange(value.length))
        }
    }

    // 外部要求聚焦（进入界面自动弹输入法）：先聚焦再弹键盘，确保光标在位。
    LaunchedEffect(requestFocusKey) {
        if (requestFocusKey != null) {
            fr.requestFocus()
            delay(120)
            keyboard?.show()
        }
    }

    fun commit(v: TextFieldValue) {
        tf = v
        onValueChange(v.text)
    }

    // 双击全选：选中全部文本，聚焦并弹出输入法，再尽力唤起系统原生文本选择工具条。
    fun selectAllAndFocus() {
        tf = tf.copy(selection = TextRange(0, tf.text.length))
        fr.requestFocus()
        scope.launch {
            delay(120)
            keyboard?.show()
        }
        val b = fieldBounds
        if (b != null) {
            val x = b.left + b.width / 2f
            val y = b.top + b.height / 2f
            view.postDelayed({ dispatchLongPress(view, x, y) }, 300)
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { fieldBounds = it.boundsInWindow() }
    ) {
        OutlinedTextField(
            value = tf,
            onValueChange = { commit(it) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fr)
                .onDoubleTap(::selectAllAndFocus),
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
    }
}
