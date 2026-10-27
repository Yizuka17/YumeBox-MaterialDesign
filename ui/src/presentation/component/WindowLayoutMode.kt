/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

@file:Suppress("UnusedSymbol")

package com.github.yumelira.yumebox.presentation.component


import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Window size class for adaptive layout.
 *
 * Product rule (main tabs):
 * - [Compact]: phone single stack; secondary screens push on the root navigator.
 * - [RailSingle]/[TwoPane]: tablet dual-pane shell (left main pager + right detail host).
 *
 * Module-level master/detail inside a tab is intentionally not used anymore; tablet detail always
 * goes through [LocalDetailNavigator] in the shell right pane.
 */
enum class WindowLayoutMode {
    Compact,
    RailSingle,
    TwoPane;

    /** Tablet dual-pane shell should be used (left pager + right detail). */
    val usesSplitShell: Boolean
        get() = this != Compact

    /** @deprecated Prefer [usesSplitShell]; kept for existing call sites. */
    val usesNavigationRail: Boolean
        get() = usesSplitShell

    val usesTwoPanes: Boolean
        get() = this == TwoPane
}

@Composable
fun rememberWindowLayoutMode(): WindowLayoutMode {
    val configuration = LocalConfiguration.current
    // Device class comes from the SHORTEST side (smallestScreenWidthDp semantics): a phone's
    // tallest side also clears 700dp, so judging by the longest side would wrongly give phones
    // the split shell in portrait. Tablets keep the shell in every rotation (issue #151 item 1),
    // while phones only enter the shell when the current width itself is wide (landscape).
    val shortestSideDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val widthDp = configuration.screenWidthDp
    return remember(shortestSideDp, widthDp) {
        when {
            shortestSideDp >= 700 -> WindowLayoutMode.TwoPane
            shortestSideDp >= 600 -> WindowLayoutMode.RailSingle
            widthDp >= 700 -> WindowLayoutMode.TwoPane
            else -> WindowLayoutMode.Compact
        }
    }
}
