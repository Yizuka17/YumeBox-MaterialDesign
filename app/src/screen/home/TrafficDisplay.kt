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



package com.github.yumelira.yumebox.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.common.AppConstants
import com.github.yumelira.yumebox.common.util.formatBytesForDisplay
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.data.model.RunMode
import com.github.yumelira.yumebox.domain.model.TrafficData
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.presentation.theme.AppMotion
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import dev.oom_wg.purejoy.mlang.MLang

@Composable
fun TrafficDisplay(
    trafficNow: TrafficData,
    profileName: String?,
    tunnelMode: TunnelState.Mode?,
    controlState: HomeProxyControlState,
    proxyMode: RunMode,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes

    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = isEnabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(top = componentSizes.homeTrafficTopPadding, bottom = spacing.space16),
        verticalArrangement = Arrangement.spacedBy(spacing.space24)
    ) {
        DownloadSection(
            downloadSpeed = trafficNow.download,
            profileName = profileName,
            tunnelMode = tunnelMode
        )

        UploadSection(
            uploadSpeed = trafficNow.upload,
            controlState = controlState,
            proxyMode = proxyMode,
        )
    }
}

@Composable
private fun DownloadSection(
    downloadSpeed: Long,
    profileName: String?,
    tunnelMode: TunnelState.Mode?
) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes

    Column(horizontalAlignment = Alignment.Start) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = componentSizes.statusCapsuleHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DOWNLOAD",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ProfileModeBadge(profileName = profileName, tunnelMode = tunnelMode)
        }

        SpeedValue(speed = downloadSpeed)
    }
}

@Composable
private fun ProfileModeBadge(
    profileName: String?,
    tunnelMode: TunnelState.Mode?
) {
    if (profileName == null && tunnelMode == null) return

    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    val opacity = AppTheme.opacity

    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = opacity.subtle),
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(componentSizes.statusCapsuleHeight)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = spacing.space12),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8)
        ) {
            Text(
                text = profileName ?: MLang.Home.Traffic.NoProfile,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Box(
                modifier = Modifier
                    .size(spacing.space4)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )

            Text(
                text = tunnelMode.toDisplayName(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SpeedValue(speed: Long) {
    val spacing = AppTheme.spacing
    val opacity = AppTheme.opacity

    val (value, unit) = formatBytesForDisplay(speed)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = AppConstants.UI.TRAFFIC_FONT_SIZE,
                lineHeight = AppConstants.UI.TRAFFIC_FONT_SIZE,
                letterSpacing = AppConstants.UI.TRAFFIC_LETTER_SPACING
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = AppConstants.UI.TRAFFIC_UNIT_FONT_SIZE
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = opacity.medium),
            modifier = Modifier.padding(bottom = spacing.space14, start = spacing.space8)
        )
    }
}

@Composable
private fun UploadSection(
    uploadSpeed: Long,
    controlState: HomeProxyControlState,
    proxyMode: RunMode,
) {
    val spacing = AppTheme.spacing

    val (value, unit) = formatBytesForDisplay(uploadSpeed)
    val isRunning = controlState == HomeProxyControlState.Running
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(spacing.space12)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space12)
        ) {
            Text(
                text = "UPLOAD",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$value $unit",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.animateContentSize(tween(AppMotion.DURATION_FAST, easing = AppMotion.EmphasizedDecelerate))
        ) {
            ProxyStatusCapsule(controlState = controlState)
            AnimatedVisibility(
                visible = isRunning,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(AppMotion.DURATION_FAST, easing = AppMotion.EmphasizedDecelerate)
                ) + fadeIn(tween(AppMotion.DURATION_FAST, easing = AppMotion.EnterEasing)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(AppMotion.DURATION_INSTANT, easing = AppMotion.EmphasizedAccelerate)
                ) + fadeOut(tween(AppMotion.DURATION_INSTANT, easing = AppMotion.ExitEasing))
            ) {
                ProxyTypeCapsule(proxyMode = proxyMode)
            }
        }
    }
}

@Composable
private fun ProxyTypeCapsule(proxyMode: RunMode) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    val opacity = AppTheme.opacity

    val primary = MaterialTheme.colorScheme.primary
    Surface(
        color = primary.copy(alpha = opacity.subtle),
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(componentSizes.statusCapsuleHeight)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = spacing.space12),
            horizontalArrangement = Arrangement.spacedBy(spacing.space6)
        ) {
            Icon(
                imageVector = when (proxyMode) {
                    RunMode.VpnService -> AppMd3Icons.Home.ProxyModeVpn
                    RunMode.Tun -> AppMd3Icons.Home.ProxyModeTun
                    RunMode.Ebpf -> AppMd3Icons.Home.ProxyModeHttp
                },
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(spacing.space12)
            )
            Text(
                text = when (proxyMode) {
                    RunMode.VpnService -> MLang.Home.ProxyMode.Vpn
                    RunMode.Tun -> MLang.Home.ProxyMode.Tun
                    RunMode.Ebpf -> "eBPF"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = primary
            )
        }
    }
}

@Composable
private fun ProxyStatusCapsule(controlState: HomeProxyControlState) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    val opacity = AppTheme.opacity

    val primary = MaterialTheme.colorScheme.primary
    Surface(
        color = primary.copy(alpha = opacity.subtle),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .height(componentSizes.statusCapsuleHeight)
            .animateContentSize(tween(AppMotion.DURATION_FAST, easing = AppMotion.EmphasizedDecelerate))
    ) {
        AnimatedContent(
            targetState = controlState,
            transitionSpec = {
                (slideInHorizontally(
                    initialOffsetX = { it / 2 },
                    animationSpec = tween(AppMotion.DURATION_FAST, easing = AppMotion.EmphasizedDecelerate)
                ) + fadeIn(tween(AppMotion.DURATION_FAST, easing = AppMotion.EnterEasing))
                ).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { -it / 2 },
                        animationSpec = tween(AppMotion.DURATION_INSTANT, easing = AppMotion.EmphasizedAccelerate)
                    ) + fadeOut(tween(AppMotion.DURATION_INSTANT, easing = AppMotion.ExitEasing))
                )
            },
            label = "CapsuleStateTransition"
        ) { state ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = spacing.space12),
                horizontalArrangement = Arrangement.spacedBy(spacing.space6)
            ) {
                Icon(
                    imageVector = when (state) {
                        HomeProxyControlState.Idle -> AppMd3Icons.Home.StatusIdle
                        HomeProxyControlState.Connecting,
                        HomeProxyControlState.Disconnecting,
                            -> AppMd3Icons.Home.StatusWaiting
                        HomeProxyControlState.Running -> AppMd3Icons.Home.StatusRunning
                    },
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(spacing.space12)
                )
                Text(
                    text = when (state) {
                        HomeProxyControlState.Idle -> MLang.Home.Status.TapToStart
                        HomeProxyControlState.Connecting -> MLang.Home.Status.Connecting
                        HomeProxyControlState.Running -> MLang.Home.Status.Running
                        HomeProxyControlState.Disconnecting -> MLang.Home.Status.Disconnecting
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = primary
                )
            }
        }
    }
}

private fun TunnelState.Mode?.toDisplayName(): String = when (this) {
    TunnelState.Mode.Direct -> "Direct"
    TunnelState.Mode.Global -> "Global"
    TunnelState.Mode.Rule -> "Rule"
    else -> "Rule"
}
