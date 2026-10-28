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
 * Copyright (c)  YumeLira 2025 - Present
 *
 */


package com.github.yumelira.yumebox.presentation.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar as MaterialTopAppBar
import androidx.compose.material3.CenterAlignedTopAppBar as MaterialCenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.presentation.theme.UiDp

@Composable
private fun defaultTopBarContainerColor(): Color {
    return MaterialTheme.colorScheme.surface
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    title: String,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: Any? = null,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") titlePadding: Dp = UiDp.dp16,
    @Suppress("UNUSED_PARAMETER") navigationIconPadding: Dp = UiDp.dp16,
    @Suppress("UNUSED_PARAMETER") actionIconPadding: Dp = UiDp.dp16,
    containerColor: Color = Color.Unspecified,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val resolvedContainerColor = if (containerColor == Color.Unspecified) {
        defaultTopBarContainerColor()
    } else {
        containerColor
    }

    MaterialTopAppBar(
        expandedHeight = if (LocalConfiguration.current.screenHeightDp < 400) 48.dp else TopAppBarDefaults.TopAppBarExpandedHeight,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = resolvedContainerColor,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallTopBar(
    title: String,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: Any? = null,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") titlePadding: Dp = UiDp.dp16,
    @Suppress("UNUSED_PARAMETER") navigationIconPadding: Dp = UiDp.dp16,
    @Suppress("UNUSED_PARAMETER") actionIconPadding: Dp = UiDp.dp16,
    containerColor: Color = Color.Unspecified,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val resolvedContainerColor = if (containerColor == Color.Unspecified) {
        defaultTopBarContainerColor()
    } else {
        containerColor
    }

    MaterialCenterAlignedTopAppBar(
        expandedHeight = if (LocalConfiguration.current.screenHeightDp < 400) 48.dp else TopAppBarDefaults.TopAppBarExpandedHeight,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = resolvedContainerColor,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
