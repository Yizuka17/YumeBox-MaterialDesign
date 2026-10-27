/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * Copyright (c) YumeYucca 2025 - Present
 */

package com.github.yumelira.yumebox.presentation.component

import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DefaultTextFieldMargin = DpSize(16.dp, 16.dp)
private val DefaultTextFieldRadius = 16.dp

/**
 * Miuix TextField chrome with a platform [EditText] as its input node. Keeping the host view as
 * an EditText is what allows OEM TextView hooks to supply the selection handles and action menu.
 */
@Composable
fun OemTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme
    val hasText = value.text.isNotEmpty()
    val labelVisible = label.isNotEmpty() && !(useLabelAsPlaceholder && hasText)
    val labelFloating = labelVisible && hasText
    val borderWidth by animateDpAsState(if (focused) 2.dp else 0.dp, label = "oem_text_field_border")
    val borderColor by animateColorAsState(
        if (focused) colors.primary else colors.secondaryContainer,
        label = "oem_text_field_border_color",
    )
    val labelOffset by animateDpAsState(
        if (labelFloating) -DefaultTextFieldMargin.height / 2 else 0.dp,
        label = "oem_text_field_label_offset",
    )
    val labelSize by animateDpAsState(
        if (labelFloating) 10.dp else 17.dp,
        label = "oem_text_field_label_size",
    )

    Box(
        modifier =
            modifier
                .squircleBackground(colors.secondaryContainer, DefaultTextFieldRadius)
                .squircleBorder({ borderWidth }, { borderColor }, DefaultTextFieldRadius),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.invoke()
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(
                            start = if (leadingIcon == null) DefaultTextFieldMargin.width else 0.dp,
                            end = if (trailingIcon == null) DefaultTextFieldMargin.width else 0.dp,
                            top = DefaultTextFieldMargin.height,
                            bottom = DefaultTextFieldMargin.height,
                        ),
                contentAlignment = Alignment.TopStart,
            ) {
                if (labelVisible) {
                    Text(
                        text = label,
                        fontSize = labelSize.value.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSecondaryContainer,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.offset(y = labelOffset),
                    )
                }
                PlatformEditText(
                    value = value,
                    onValueChange = onValueChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .offset(y = if (labelFloating) DefaultTextFieldMargin.height / 2 else 0.dp),
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    keyboardOptions = keyboardOptions,
                    onImeAction = onImeAction,
                    textColor = colors.onSurface,
                    onFocusChanged = { focused = it },
                )
            }
            trailingIcon?.invoke()
        }
    }
}

@Composable
fun OemTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    OemTextField(
        value = fieldValue,
        onValueChange = { updated ->
            fieldValue = updated
            onValueChange(updated.text)
        },
        modifier = modifier,
        label = label,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        onImeAction = onImeAction,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

/** Miuix-compatible search capsule; callers retain ownership of the leading/trailing controls. */
@Composable
fun OemSearchInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    inputModifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    onImeAction: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = modifier.background(colors.secondaryContainer, CircleShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.invoke()
            PlatformEditText(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).heightIn(min = 45.dp).then(inputModifier),
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                onImeAction = onImeAction,
                textColor = colors.onSurface,
                textStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 17.sp),
            )
            trailingIcon?.invoke()
        }
    }
}

@Composable
private fun PlatformEditText(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean,
    maxLines: Int,
    keyboardOptions: KeyboardOptions,
    onImeAction: (() -> Unit)?,
    textColor: Color,
    textStyle: TextStyle = TextStyle(fontSize = 17.sp),
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnImeAction by rememberUpdatedState(onImeAction)
    val currentOnFocusChanged by rememberUpdatedState(onFocusChanged)

    AndroidView(
        factory = { context ->
            EditText(context).apply {
                background = null
                setPadding(0, 0, 0, 0)
                includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL
                isSingleLine = singleLine
                setMaxLines(maxLines)
                inputType = keyboardOptions.keyboardType.toInputType(singleLine)
                imeOptions = keyboardOptions.imeAction.toEditorInfoAction()
                setTextColor(textColor.toArgb())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textStyle.fontSize.value)
                setText(value.text)
                setSelection(value.selection.end.coerceIn(0, value.text.length))
                setOnFocusChangeListener { _, hasFocus -> currentOnFocusChanged(hasFocus) }
                setOnEditorActionListener { _, _, _ ->
                    currentOnImeAction?.invoke()
                    currentOnImeAction != null
                }
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

                        override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

                        override fun afterTextChanged(text: android.text.Editable?) {
                            val updatedText = text?.toString().orEmpty()
                            currentOnValueChange(
                                TextFieldValue(
                                    text = updatedText,
                                    selection = TextRange(selectionStart.coerceIn(0, updatedText.length), selectionEnd.coerceIn(0, updatedText.length)),
                                )
                            )
                        }
                    }
                )
            }
        },
        modifier = modifier,
        update = { editText ->
            editText.isEnabled = enabled
            editText.isFocusable = enabled && !readOnly
            editText.isFocusableInTouchMode = enabled && !readOnly
            editText.isCursorVisible = enabled && !readOnly
            val inputType = keyboardOptions.keyboardType.toInputType(singleLine)
            if (editText.inputType != inputType) editText.inputType = inputType
            if (editText.isSingleLine != singleLine) editText.isSingleLine = singleLine
            if (editText.maxLines != maxLines) editText.maxLines = maxLines
            val imeOptions = keyboardOptions.imeAction.toEditorInfoAction()
            if (editText.imeOptions != imeOptions) editText.imeOptions = imeOptions
            editText.setTextColor(textColor.toArgb())
            editText.setOnFocusChangeListener { _, hasFocus -> currentOnFocusChanged(hasFocus) }
            editText.setOnEditorActionListener { _, _, _ ->
                currentOnImeAction?.invoke()
                currentOnImeAction != null
            }
            if (editText.text.toString() != value.text) editText.setText(value.text)
            if (!editText.hasFocus()) editText.setSelection(value.selection.end.coerceIn(0, value.text.length))
        },
    )
}

private fun KeyboardType.toInputType(singleLine: Boolean): Int {
    val textFlags = if (singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE
    return when (this) {
        KeyboardType.Number -> InputType.TYPE_CLASS_NUMBER
        KeyboardType.Decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        KeyboardType.Phone -> InputType.TYPE_CLASS_PHONE
        KeyboardType.Uri -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        KeyboardType.Email -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        KeyboardType.Password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        KeyboardType.NumberPassword -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> InputType.TYPE_CLASS_TEXT or textFlags
    }
}

private fun ImeAction.toEditorInfoAction(): Int =
    when (this) {
        ImeAction.Done -> EditorInfo.IME_ACTION_DONE
        ImeAction.Go -> EditorInfo.IME_ACTION_GO
        ImeAction.Next -> EditorInfo.IME_ACTION_NEXT
        ImeAction.Previous -> EditorInfo.IME_ACTION_PREVIOUS
        ImeAction.Search -> EditorInfo.IME_ACTION_SEARCH
        ImeAction.Send -> EditorInfo.IME_ACTION_SEND
        ImeAction.None -> EditorInfo.IME_ACTION_NONE
        else -> EditorInfo.IME_ACTION_UNSPECIFIED
    }
