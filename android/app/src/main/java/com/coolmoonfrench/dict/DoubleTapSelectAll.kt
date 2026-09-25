package com.coolmoonfrench.dict

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 双击输入框全选文本。
 *
 * 在 Initial 事件阶段监听、且**不消费**事件，因此不会影响输入框自身的单击定位光标、
 * 长按选择等行为；检测到两次快速点击后，把选区设为整段文本。
 */
@Composable
fun Modifier.selectAllOnDoubleTap(
    value: () -> TextFieldValue,
    onChange: (TextFieldValue) -> Unit
): Modifier = composed {
    var lastTap by remember { mutableStateOf(0L) }
    val current by rememberUpdatedState(value)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
            if (up != null) {
                val now = up.uptimeMillis
                if (now - lastTap < 350L) {
                    val v = current()
                    onChange(v.copy(selection = TextRange(0, v.text.length)))
                    lastTap = 0L
                } else {
                    lastTap = now
                }
            }
        }
    }
}

/**
 * 与 [OutlinedTextField] 用法一致、但**内部使用 [TextFieldValue]** 以支持双击全选的输入框。
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
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    var tf by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (value != tf.text) {
            tf = tf.copy(text = value, selection = TextRange(value.length))
        }
    }
    OutlinedTextField(
        value = tf,
        onValueChange = {
            tf = it
            onValueChange(it.text)
        },
        modifier = modifier.selectAllOnDoubleTap({ tf }, {
            tf = it
            onValueChange(it.text)
        }),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        maxLines = maxLines,
        placeholder = placeholder,
        label = label,
        trailingIcon = trailingIcon,
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
