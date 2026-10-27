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

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Switch as Md3Switch
import androidx.compose.material3.SwitchDefaults as Md3SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.yumeDestructiveActionColors
import com.github.yumelira.yumebox.presentation.util.*
import com.github.yumelira.yumebox.runtime.api.Profile
import dev.oom_wg.purejoy.mlang.MLang
import java.io.File

@Composable
fun ProfileCard(
    profile: Profile,
    workDir: File,
    isDownloading: Boolean = false,
    isUpdating: Boolean = false,
    onExport: (Profile) -> Unit,
    onUpdate: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onEdit: (Profile) -> Unit,
    onToggleEnabled: (Profile) -> Unit,
    onOverrideSettings: ((Profile) -> Unit)? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val spacing = AppTheme.spacing
    val opacity = AppTheme.opacity
    val componentSizes = AppTheme.sizes
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    val colorScheme = MaterialTheme.colorScheme

    val isDark = isSystemInDarkTheme()
    val secondaryContainer = colorScheme.secondaryContainer.copy(alpha = opacity.strong)
    val actionIconTint =
        remember(isDark, opacity) {
            colorScheme.onSurface.copy(alpha = if (isDark) opacity.subtleText else opacity.prominentText)
        }

    val isConfigSaved = remember(profile.uuid, profile.updatedAt) {
        profile.isConfigSaved(workDir)
    }

    val destructiveActionColors = yumeDestructiveActionColors()
    val updateBg = remember(colorScheme, opacity) { colorScheme.primary.copy(alpha = opacity.subtle) }
    val updateTint = remember(colorScheme) { colorScheme.primary }
    val deleteContainer = destructiveActionColors.containerColor
    val deleteContentColor = destructiveActionColors.contentColor

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = spacing.space12),
        insideMargin = PaddingValues(spacing.space16)
    ) {

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = spacing.space4)
            ) {

                Text(
                    text = profile.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = profile.getDisplayProvider(),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = spacing.space2),
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Md3Switch(
                checked = profile.enabled,
                enabled = true,
                onCheckedChange = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onToggleEnabled(profile)
                },
                colors = Md3SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    disabledCheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledCheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                ),
            )
        }

        val infoText = remember(profile) {
            profile.getInfoText()
        }

        Column(modifier = Modifier.padding(top = spacing.space8)) {

            val lines = infoText.split('\n')

            lines.forEachIndexed { _, line ->
                when {

                    line.contains('|') -> {
                        val parts = line.split('|')
                        val expireText = parts.getOrNull(0) ?: ""
                        val timeText = parts.getOrNull(1) ?: ""

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = expireText,
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )

                            if (timeText.isNotEmpty()) {
                                Text(
                                    text = timeText,
                                    fontSize = 12.sp,
                                    color = colorScheme.onTertiaryContainer.copy(alpha = opacity.strong),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier.padding(end = componentSizes.profileMetaTrailingInset)
                                )
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = line,
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = spacing.space12),
            thickness = componentSizes.thinDividerThickness,
            color = colorScheme.outline.copy(alpha = opacity.medium)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {

            Md3TonalIconButton(
                containerColor = secondaryContainer,
                minHeight = componentSizes.compactActionButtonSize,
                minWidth = componentSizes.compactActionButtonSize,
                enabled = isConfigSaved && !isDownloading,
                onClick = { if (isConfigSaved && !isDownloading) onExport(profile) }) {
                Icon(
                    modifier = Modifier
                        .size(spacing.space20)
                        .alpha(if (isConfigSaved) 1f else opacity.disabledSecondary),
                    imageVector = AppMd3Icons.Action.Share,
                    tint = actionIconTint.copy(alpha = if (isConfigSaved) 1f else opacity.disabledSecondary),
                    contentDescription = "Export"
                )
            }

            Spacer(Modifier.width(spacing.space8))

            Md3TonalIconButton(
                containerColor = secondaryContainer,
                minHeight = componentSizes.compactActionButtonSize,
                minWidth = componentSizes.compactActionButtonSize,
                enabled = !isDownloading,
                onClick = { if (!isDownloading) onEdit(profile) }) {
                Icon(
                    modifier = Modifier.size(spacing.space20),
                    imageVector = AppMd3Icons.Action.Edit,
                    tint = actionIconTint,
                    contentDescription = "Edit"
                )
            }

            Spacer(Modifier.weight(1f))

            if (profile.shouldShowUpdateButton()) {
                val updateButtonSize = componentSizes.compactActionButtonSize
                var expandedUpdateButtonWidthPx by remember { mutableIntStateOf(0) }
                val expandedUpdateButtonWidth = remember(expandedUpdateButtonWidthPx, density) {
                    if (expandedUpdateButtonWidthPx > 0) with(density) { expandedUpdateButtonWidthPx.toDp() } else null
                }

                Box(
                    modifier = Modifier
                        .padding(end = spacing.space8)
                        .height(updateButtonSize)
                        .then(expandedUpdateButtonWidth?.let(Modifier::width) ?: Modifier.wrapContentWidth()),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .height(updateButtonSize)
                            .clip(CircleShape)
                            .background(updateBg)
                            .clickable(
                                enabled = !isDownloading && !isUpdating,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                if (!isDownloading && !isUpdating) onUpdate(profile)
                            }
                            .animateContentSize(
                                animationSpec = tween(
                                    durationMillis = 280,
                                    easing = FastOutSlowInEasing,
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = isUpdating,
                            contentAlignment = Alignment.Center,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(180)) togetherWith
                                        fadeOut(animationSpec = tween(120)) using
                                        SizeTransform(
                                            clip = false,
                                            sizeAnimationSpec = { _, _ ->
                                                tween(durationMillis = 280, easing = FastOutSlowInEasing)
                                            },
                                        )
                            },
                            label = "ProfileUpdateButtonContent",
                        ) { updating ->
                            if (updating) {
                                Md3ELoading(
                                    modifier = Modifier.size(updateButtonSize),
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .height(updateButtonSize)
                                        .onSizeChanged { size ->
                                            if (size.width > 0) expandedUpdateButtonWidthPx = size.width
                                        }
                                        .padding(horizontal = spacing.space10),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                                ) {
                                    Icon(
                                        modifier = Modifier.size(spacing.space20),
                                        imageVector = AppMd3Icons.Action.Sync,
                                        tint = updateTint,
                                        contentDescription = "Update",
                                    )
                                    Text(
                                        modifier = Modifier.padding(end = componentSizes.textLineCompactSpacing),
                                        text = MLang.Component.ProfileCard.Update,
                                        color = updateTint,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Md3TonalIconButton(
                minHeight = componentSizes.compactActionButtonSize,
                minWidth = componentSizes.compactActionButtonSize,
                enabled = !isDownloading,
                onClick = { if (!isDownloading) onDelete(profile) },
                containerColor = deleteContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.space10),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(spacing.space20),
                        imageVector = AppMd3Icons.Action.Delete,
                        tint = deleteContentColor,
                        contentDescription = "Delete"
                    )
                    Text(
                        modifier = Modifier.padding(start = spacing.space4, end = componentSizes.textLineCompactSpacing),
                        text = MLang.Component.ProfileCard.Delete,
                        color = deleteContentColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Md3TonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    minHeight: androidx.compose.ui.unit.Dp = 40.dp,
    minWidth: androidx.compose.ui.unit.Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(
            modifier = Modifier
                .height(minHeight)
                .widthIn(min = minWidth),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
