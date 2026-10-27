/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

@file:Suppress("UnusedSymbol", "FunctionName")

package com.github.yumelira.yumebox.presentation.component


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.github.yumelira.yumebox.presentation.theme.UiDp

/**
 * Shared pane width tokens.
 *
 * Module master/detail layouts were retired in favor of the app-level dual-pane shell
 * ([DualPaneLayout] + [LocalDetailNavigator]). Keep width tokens here for grid/list sizing.
 */
object PaneWidths {
    val HomeMasterMin: Dp = UiDp.dp300
    val HomeMasterMax: Dp = UiDp.dp420
    val ProxyMasterMin: Dp = UiDp.dp260
    val ProxyMasterMax: Dp = UiDp.dp340
    val ProfilesMasterMin: Dp = UiDp.dp300
    val ProfilesMasterMax: Dp = UiDp.dp380
    val SettingsMasterMin: Dp = UiDp.dp280
    val SettingsMasterMax: Dp = UiDp.dp360
    val NodeGridAdaptiveMin: Dp = UiDp.dp280
}

@Composable
fun PaneWidth(
    min: Dp,
    max: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier
        .widthIn(min = min, max = max)
        .fillMaxHeight()) {
        content()
    }
}
