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

package com.github.yumelira.yumebox.presentation.component.md3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.presentation.component.HapticSwitch

private val PreferenceMinHeight = 64.dp
private val PreferenceHorizontalPadding = 16.dp
private val PreferenceVerticalPadding = 10.dp
private val PreferenceLeadingSpacing = 16.dp
private val PreferenceTrailingSpacing = 12.dp
private val PreferenceBottomActionTopPadding = 2.dp
private val PreferenceBottomActionBottomPadding = 10.dp
private val PreferenceDividerStartPadding = 16.dp
private val PreferenceDividerEndPadding = 16.dp

@Composable
fun YumeMd3PreferenceItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    bottomContent: @Composable (() -> Unit)? = null,
    containerColor: Color = Color.Transparent,
    showDivider: Boolean = false,
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val summaryColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .clickable(
                enabled = enabled && onClick != null,
                role = role,
                onClick = { onClick?.invoke() },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PreferenceMinHeight)
                .padding(
                    horizontal = PreferenceHorizontalPadding,
                    vertical = PreferenceVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(PreferenceLeadingSpacing))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    maxLines = if (summary.isNullOrBlank()) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!summary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = summaryColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(PreferenceTrailingSpacing))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingContent()
                }
            }
        }

        if (bottomContent != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PreferenceHorizontalPadding,
                        end = PreferenceHorizontalPadding,
                        top = PreferenceBottomActionTopPadding,
                        bottom = PreferenceBottomActionBottomPadding,
                    ),
            ) {
                bottomContent()
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = PreferenceDividerStartPadding,
                    end = PreferenceDividerEndPadding,
                ),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
fun YumeMd3SwitchPreferenceItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = false,
) {
    val hapticFeedback = LocalHapticFeedback.current

    YumeMd3PreferenceItem(
        title = title,
        modifier = modifier,
        summary = summary,
        enabled = enabled,
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onCheckedChange(!checked)
        },
        role = Role.Switch,
        trailingContent = {
            HapticSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        showDivider = showDivider,
    )
}
