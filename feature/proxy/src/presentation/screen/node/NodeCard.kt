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


package com.github.yumelira.yumebox.presentation.screen.node

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.presentation.component.CountryFlagCircle
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.presentation.theme.AppMotion
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.appPressSink
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun nodeLatencyLabel(delay: Int?): Pair<String, Color>? = when {
    delay == null -> null
    delay < 0 -> MLang.Proxy.Node.Timeout to AppTheme.colors.latency.timeout
    delay == 0 -> null
    delay in 1..300 -> MLang.Home.NodeInfo.DelayValue.format(delay) to AppTheme.colors.latency.fast
    delay in 301..1000 -> MLang.Home.NodeInfo.DelayValue.format(delay) to AppTheme.colors.latency.moderate
    delay in 1001..3000 -> MLang.Home.NodeInfo.DelayValue.format(delay) to AppTheme.colors.latency.slow
    else -> null
}

internal fun String.displayName(): String = when (this) {
    Proxy.Type.Direct -> "Direct"
    Proxy.Type.Reject -> "Reject"
    Proxy.Type.RejectDrop -> "RejectDrop"
    Proxy.Type.Compatible -> "Compatible"
    Proxy.Type.Pass -> "Pass"
    Proxy.Type.Relay -> "Relay"
    Proxy.Type.Selector -> "Selector"
    Proxy.Type.Fallback -> "Fallback"
    Proxy.Type.URLTest -> "URLTest"
    Proxy.Type.LoadBalance -> "LoadBalance"
    Proxy.Type.Smart -> "Smart"
    Proxy.Type.Unknown -> "Unknown"
    Proxy.Type.Shadowsocks -> "SS"
    Proxy.Type.ShadowsocksR -> "SSR"
    Proxy.Type.Snell -> "Snell"
    Proxy.Type.Socks5 -> "SOCKS5"
    Proxy.Type.Http -> "HTTP"
    Proxy.Type.Vmess -> "VMess"
    Proxy.Type.Vless -> "VLESS"
    Proxy.Type.Trojan -> "Trojan"
    Proxy.Type.Hysteria -> "Hysteria"
    Proxy.Type.Hysteria2 -> "Hysteria2"
    Proxy.Type.Tuic -> "TUIC"
    Proxy.Type.WireGuard -> "WireGuard"
    Proxy.Type.Dns -> "DNS"
    Proxy.Type.Ssh -> "SSH"
    Proxy.Type.Mieru -> "Mieru"
    Proxy.Type.AnyTLS -> "AnyTLS"
    Proxy.Type.Sudoku -> "Sudoku"
    Proxy.Type.Masque -> "Masque"
    Proxy.Type.TrustTunnel -> "TrustTunnel"
    else -> this
}

internal fun String.iconLabel(): String = when (this) {
    Proxy.Type.Direct -> "DI"
    Proxy.Type.Reject -> "RJ"
    Proxy.Type.RejectDrop -> "RD"
    Proxy.Type.Compatible -> "CP"
    Proxy.Type.Pass -> "PS"
    Proxy.Type.Relay -> "RL"
    Proxy.Type.Selector -> "SE"
    Proxy.Type.Fallback -> "FB"
    Proxy.Type.URLTest -> "UT"
    Proxy.Type.LoadBalance -> "LB"
    Proxy.Type.Smart -> "SM"
    Proxy.Type.Unknown -> "UN"
    Proxy.Type.Shadowsocks -> "SS"
    Proxy.Type.ShadowsocksR -> "SR"
    Proxy.Type.Snell -> "SN"
    Proxy.Type.Socks5 -> "S5"
    Proxy.Type.Http -> "HT"
    Proxy.Type.Vmess -> "VM"
    Proxy.Type.Vless -> "VL"
    Proxy.Type.Trojan -> "TR"
    Proxy.Type.Hysteria -> "HY"
    Proxy.Type.Hysteria2 -> "H2"
    Proxy.Type.Tuic -> "TU"
    Proxy.Type.WireGuard -> "WG"
    Proxy.Type.Dns -> "DN"
    Proxy.Type.Ssh -> "SH"
    Proxy.Type.Mieru -> "MI"
    Proxy.Type.AnyTLS -> "AT"
    Proxy.Type.Sudoku -> "SU"
    Proxy.Type.Masque -> "MQ"
    Proxy.Type.TrustTunnel -> "TT"
    else -> take(2).uppercase()
}

internal data class ProxySelectionPalette(
    val containerColor: Color,
    val borderColor: Color,
    val contentColor: Color,
    val supportingColor: Color,
    val chipBackgroundColor: Color,
    val chipContentColor: Color,
    val trailingBadgeBackgroundColor: Color,
    val trailingBadgeContentColor: Color,
    val iconBackgroundColor: Color,
    val iconContentColor: Color,
)

@Composable
internal fun rememberProxySelectionPalette(
    selected: Boolean,
    defaultContainerColor: Color? = null,
): ProxySelectionPalette {
    val colorScheme = MaterialTheme.colorScheme
    val surface = colorScheme.surface
    val isDarkTheme = isSystemInDarkTheme()
    val fallbackContainer = defaultContainerColor
        ?: colorScheme.surfaceVariant.copy(alpha = 0.42f).compositeOver(surface)
    val selectedContainer = colorScheme.primaryContainer
        .copy(alpha = if (isDarkTheme) 0.62f else 0.78f)
        .compositeOver(surface)

    return if (selected) {
        ProxySelectionPalette(
            containerColor = selectedContainer,
            borderColor = Color.Transparent,
            contentColor = colorScheme.primary,
            supportingColor = colorScheme.onSurfaceVariant,
            chipBackgroundColor = colorScheme.primary.copy(alpha = 0.10f),
            chipContentColor = colorScheme.primary,
            trailingBadgeBackgroundColor = colorScheme.primary.copy(alpha = 0.10f),
            trailingBadgeContentColor = colorScheme.primary,
            iconBackgroundColor = colorScheme.primary.copy(alpha = 0.10f),
            iconContentColor = colorScheme.primary,
        )
    } else {
        ProxySelectionPalette(
            containerColor = fallbackContainer,
            borderColor = Color.Transparent,
            contentColor = colorScheme.onSurface,
            supportingColor = colorScheme.onSurface.copy(alpha = 0.72f),
            chipBackgroundColor = colorScheme.primary.copy(alpha = 0.10f),
            chipContentColor = colorScheme.primary,
            trailingBadgeBackgroundColor = Color.Transparent,
            trailingBadgeContentColor = colorScheme.primary,
            iconBackgroundColor = colorScheme.primary.copy(alpha = 0.10f),
            iconContentColor = colorScheme.primary,
        )
    }
}

@Composable
internal fun RotatingRefreshIcon(
    isRotating: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MiuixTheme.colorScheme.primary,
    contentDescription: String? = MLang.Proxy.Action.Test,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "node_delay_test_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
        ),
        label = "node_delay_test_rotation_value",
    )

    Icon(
        imageVector = AppMd3Icons.Action.Refresh,
        contentDescription = contentDescription,
        tint = tint,
        modifier = if (isRotating) modifier.rotate(rotation) else modifier,
    )
}

@Composable
internal fun NodeSelectableCard(
    isSelected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    paddingVertical: Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val radii = AppTheme.radii
    val sizes = AppTheme.sizes
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    val shape = RoundedCornerShape(radii.radius18)
    val palette = rememberProxySelectionPalette(selected = isSelected)
    val fastEffectsSpec = AppMotion.fastEffects<Color>()
    val transition = updateTransition(targetState = palette, label = "node_card_selection")
    val backgroundColor by transition.animateColor(
        transitionSpec = { fastEffectsSpec },
        label = "node_card_background_color",
    ) { it.containerColor }
    val borderColor by transition.animateColor(
        transitionSpec = { fastEffectsSpec },
        label = "node_card_border_color",
    ) { it.borderColor }
    val cardRipple = ripple(
        bounded = true,
        color = MaterialTheme.colorScheme.primary,
    )

    Box(
        modifier = modifier
            .appPressSink(
                interactionSource = interactionSource,
                enabled = onClick != null,
            )
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(sizes.nodeCardBorderWidth, borderColor, shape)
            .let { cardModifier ->
                if (onClick != null) {
                    cardModifier.clickable(
                        interactionSource = interactionSource,
                        indication = cardRipple,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onClick()
                        },
                    )
                } else {
                    cardModifier
                }
            }
            .padding(horizontal = sizes.nodeCardPaddingHorizontal, vertical = paddingVertical),
        content = content,
    )
}

@Composable
internal fun NodeCard(
    proxy: Proxy,
    isSelected: Boolean,
    onClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    isDelayTesting: Boolean = false,
    isThisProxyTesting: Boolean = false,
    onSingleNodeTestClick: ((String) -> Unit)? = null,
    showCountryFlag: Boolean = true,
    singleNodeTestEnabled: Boolean = true,
) {
    val spacing = AppTheme.spacing
    val sizes = AppTheme.sizes
    val palette = rememberProxySelectionPalette(selected = isSelected)
    val onCardClick = remember(proxy.name, onClick) {
        onClick?.let { click -> { click(proxy.name) } }
    }
    val onNodeTestClick = remember(proxy.name, onSingleNodeTestClick) {
        onSingleNodeTestClick?.let { click -> { click(proxy.name) } }
    }
    val delayInteractionSource = remember { MutableInteractionSource() }
    val iconInteractionSource = remember { MutableInteractionSource() }

    NodeSelectableCard(
        isSelected = isSelected,
        onClick = onCardClick,
        modifier = modifier,
        paddingVertical = spacing.space12,
    ) {
        val presentation = remember(proxy.name, proxy.title) {
            resolveProxyDisplayPresentation(name = proxy.name, title = proxy.title)
        }
        val delayLabel = nodeLatencyLabel(proxy.delay)
        val iconLabel = remember(proxy.type) { proxy.type.iconLabel() }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.space10),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                NodeLargeIcon(
                    countryCode = presentation.countryCode.takeIf { showCountryFlag },
                    typeName = iconLabel,
                    selected = isSelected,
                )

                when {
                    delayLabel != null -> {
                        val (delayText, delayColor) = delayLabel
                        Text(
                            text = delayText,
                            style = MiuixTheme.textStyles.footnote1,
                            color = delayColor,
                            maxLines = 1,
                            modifier = Modifier.let { base ->
                                if (onNodeTestClick != null && singleNodeTestEnabled) {
                                    base.clickable(
                                        interactionSource = delayInteractionSource,
                                        indication = null,
                                        onClick = onNodeTestClick,
                                    )
                                } else {
                                    base
                                }
                            },
                        )
                    }

                    onNodeTestClick != null && singleNodeTestEnabled -> {
                        if (isThisProxyTesting || isDelayTesting) {
                            RotatingRefreshIcon(
                                isRotating = true,
                                modifier = Modifier.size(spacing.space18),
                                tint = palette.supportingColor,
                            )
                        } else {
                            Icon(
                                imageVector = AppMd3Icons.Proxy.CloudTest,
                                contentDescription = MLang.Proxy.Action.Test,
                                tint = palette.supportingColor,
                                modifier = Modifier
                                    .size(spacing.space18)
                                    .clickable(
                                        interactionSource = iconInteractionSource,
                                        indication = null,
                                        onClick = onNodeTestClick,
                                    ),
                            )
                        }
                    }
                }
            }

            Text(
                text = presentation.displayName,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
                color = palette.contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = iconLabel,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = palette.chipContentColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTheme.radii.full))
                        .background(palette.chipBackgroundColor)
                        .padding(horizontal = spacing.space8, vertical = spacing.space4),
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTheme.radii.full))
                            .background(palette.trailingBadgeBackgroundColor)
                            .padding(horizontal = spacing.space6, vertical = spacing.space6),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = AppMd3Icons.Action.Check,
                            contentDescription = null,
                            tint = palette.trailingBadgeContentColor,
                            modifier = Modifier.size(spacing.space14),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
internal fun NodeLargeIcon(
    countryCode: String?,
    typeName: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val sizes = AppTheme.sizes
    val palette = rememberProxySelectionPalette(selected = selected)

    Box(
        modifier = modifier
            .size(sizes.nodeLargeIconSize)
            .clip(RoundedCornerShape(sizes.nodeLargeIconCornerRadius))
            .background(palette.iconBackgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if (countryCode != null) {
            CountryFlagCircle(countryCode = countryCode, size = sizes.nodeLargeIconFlagSize - 2.dp)
        } else {
            Text(
                text = typeName.take(2),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
                color = palette.iconContentColor,
            )
        }
    }
}
