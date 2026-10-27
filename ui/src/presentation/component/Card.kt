/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

@file:Suppress("FunctionName")

package com.github.yumelira.yumebox.presentation.component


import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.presentation.theme.horizontalPadding
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 24,
    insideMargin: PaddingValues = PaddingValues(UiDp.dp0),
    applyHorizontalPadding: Boolean = true,
    colors: CardColors = CardDefaults.defaultColors(),
    onClick: (() -> Unit)? = null,
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    content: @Composable () -> Unit,
) {
    val resolvedModifier = if (applyHorizontalPadding) modifier.horizontalPadding() else modifier
    if (onClick == null) {
        MiuixCard(
            modifier = resolvedModifier,
            cornerRadius = cornerRadius.dp,
            insideMargin = insideMargin,
            colors = colors,
            content = { content() },
        )
    } else {
        MiuixCard(
            modifier = resolvedModifier,
            cornerRadius = cornerRadius.dp,
            insideMargin = insideMargin,
            colors = colors,
            onClick = onClick,
            pressFeedbackType = pressFeedbackType,
            content = { content() },
        )
    }
}
