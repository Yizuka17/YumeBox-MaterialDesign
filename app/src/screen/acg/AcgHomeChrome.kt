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

package com.github.yumelira.yumebox.screen.acg

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text as MdText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.common.util.LocaleUtil
import com.github.yumelira.yumebox.common.util.formatBytesForDisplay
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.data.network.IpMonitoringState
import com.github.yumelira.yumebox.data.model.RunMode
import com.github.yumelira.yumebox.presentation.component.CountryFlagCircle
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.presentation.icon.ShellIcons
import com.github.yumelira.yumebox.presentation.theme.AppMotion
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.util.extractFlaggedName
import com.github.yumelira.yumebox.screen.home.HomeProxyControlState
import com.github.yumelira.yumebox.screen.home.displayableExternalIp
import com.github.yumelira.yumebox.miuix.YumeMiuixBlendColorEntry as BlendColorEntry
import com.github.yumelira.yumebox.miuix.YumeMiuixBlurBlendMode as BlurBlendMode
import com.github.yumelira.yumebox.miuix.YumeMiuixBlurDefaults as BlurDefaults
import com.github.yumelira.yumebox.miuix.YumeMiuixIcon as Icon
import com.github.yumelira.yumebox.miuix.YumeMiuixLayerBackdrop as LayerBackdrop
import com.github.yumelira.yumebox.miuix.YumeMiuixText as Text
import com.github.yumelira.yumebox.miuix.YumeMiuixTheme as MiuixTheme
import com.github.yumelira.yumebox.miuix.yumeMiuixTextureBlur as textureBlur
import dev.oom_wg.purejoy.mlang.MLang

@Composable
internal fun AcgSidebarDecoration(
    backdrop: LayerBackdrop,
    blurEnabled: Boolean,
    blurProgress: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val spacing = AppTheme.spacing
    val surface = MiuixTheme.colorScheme.surface
    val isDarkSurface = surface.luminance() < 0.5f
    val glassBase = if (isDarkSurface) {
        Color.Black.copy(alpha = 0.24f)
    } else {
        surface.copy(alpha = 0.13f)
    }
    val glassTint = Color.Black.copy(alpha = 0.10f)
    val glassGradientStart = if (isDarkSurface) {
        Color.Black.copy(alpha = 0.36f)
    } else {
        surface.copy(alpha = 0.23f)
    }
    val glassGradientEnd = if (isDarkSurface) {
        surface.copy(alpha = 0.18f)
    } else {
        surface.copy(alpha = 0.16f)
    }
    val clampedBlurProgress = blurProgress.coerceIn(0f, 1f)
    val blurRadiusPx = lerpFloat(30f, 52f, clampedBlurProgress)
    val blurColors = BlurDefaults.blurColors(
        blendColors = listOf(
            BlendColorEntry(color = glassBase, mode = BlurBlendMode.SrcOver),
            BlendColorEntry(color = glassTint, mode = BlurBlendMode.SrcOver),
        ),
        saturation = if (isDarkSurface) 1.06f else 1.02f,
        contrast = if (isDarkSurface) 1.08f else 1.10f,
        brightness = if (isDarkSurface) 0.00f else -0.05f,
    )
    val blurModifier = if (blurEnabled) {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = blurRadiusPx,
            noiseCoefficient = 0f,
            blurColors = blurColors,
            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(blurModifier)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(glassGradientStart, glassGradientEnd),
                ),
                shape = RectangleShape,
            )
            .padding(horizontal = AcgUi.Sidebar.innerHorizontalPadding, vertical = spacing.space24),
        content = content,
    )
}

@Composable
internal fun AcgSidebarContent(
    topValue: String,
    bottomValue: String,
    proxyMode: RunMode,
    icons: List<AcgSidebarIconItem>,
    visibleWidth: Dp,
    tabletMode: Boolean = false,
    duration: String = "",
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AcgSidebarRail(
            topValue = topValue,
            bottomValue = bottomValue,
            proxyMode = proxyMode,
            icons = icons,
            tabletMode = tabletMode,
            duration = duration,
            modifier = if (tabletMode) Modifier.fillMaxHeight().width((visibleWidth - 12.dp).coerceAtLeast(0.dp))
                else Modifier.fillMaxHeight().width(AcgUi.Sidebar.statsWidth).offset(x = calculateAcgSidebarLaneStart(visibleWidth)),
        )
    }
}

@Composable
internal fun AcgQuoteText(
    quote: AcgQuote,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AcgUi.Quote.contentGap),
    ) {
        Text(
            text = quote.text,
            color = color,
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Medium,
            fontSize = AcgUi.Quote.textSize,
            lineHeight = AcgUi.Quote.lineHeight,
            softWrap = true,
            overflow = TextOverflow.Clip,
        )
        if (quote.author.isNotBlank()) {
            Text(
                text = "— ${quote.author}",
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = AcgUi.Quote.authorTopGap),
                color = color.copy(alpha = AcgUi.Quote.authorAlpha),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
                fontSize = AcgUi.Quote.authorSize,
                softWrap = false,
            )
        }
    }
}

@Composable
internal fun AcgProfileModeBadge(
    profileName: String?,
    tunnelMode: TunnelState.Mode?,
    modifier: Modifier = Modifier,
) {
    if (profileName == null && tunnelMode == null) return

    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    val opacity = AppTheme.opacity

    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = opacity.subtle),
        shape = RoundedCornerShape(50),
        modifier = modifier
            .wrapContentWidth(Alignment.Start)
            .heightIn(min = componentSizes.statusCapsuleHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = spacing.space12, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            MdText(
                text = profileName ?: MLang.Home.Traffic.NoProfile,
                modifier = Modifier.weight(weight = 1f, fill = false),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Box(
                modifier = Modifier
                    .size(spacing.space4)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )

            MdText(
                text = tunnelMode.toAcgTunnelDisplayName(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
internal fun AcgInlineIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val spacing = AppTheme.spacing
    val colorScheme = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val background = colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.06f)
    val tint = colorScheme.primary.copy(alpha = if (enabled) 0.92f else 0.38f)
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = AppMotion.press(),
        label = "acg_inline_icon_button_press_scale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .size(42.dp)
            .clip(AcgUi.Shape.sidebarIconButton)
            .background(background)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(spacing.space20),
        )
    }
}

@Composable
internal fun AcgLaunchButton(
    controlState: HomeProxyControlState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val spacing = AppTheme.spacing
    val colorScheme = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDestructiveAction = controlState == HomeProxyControlState.Running
    val isDarkSurface = colorScheme.surface.luminance() < 0.5f
    val background = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
        isDestructiveAction && isDarkSurface -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        isDestructiveAction -> MaterialTheme.colorScheme.errorContainer
        else -> colorScheme.primary
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f)
        isDestructiveAction && isDarkSurface -> MaterialTheme.colorScheme.error.copy(alpha = 0.92f)
        isDestructiveAction -> MaterialTheme.colorScheme.onErrorContainer
        else -> colorScheme.onPrimary
    }
    val containerAlpha = if (enabled) 1f else 0.88f
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) AcgUi.Button.PressedScale else 1f,
        animationSpec = AppMotion.press(),
        label = "acg_launch_button_press_scale",
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = containerAlpha
            }
            .widthIn(min = AcgUi.Button.fixedWidth)
            .clip(AcgUi.Shape.launchButton)
            .background(background, AcgUi.Shape.launchButton)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(
                horizontal = AcgUi.Button.horizontalPadding,
                vertical = AcgUi.Button.verticalPadding,
            ),
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space10),
        ) {
            Icon(
                imageVector = when (controlState) {
                    HomeProxyControlState.Idle -> ShellIcons.StartProxy
                    HomeProxyControlState.Running -> ShellIcons.StopProxy
                    HomeProxyControlState.Connecting,
                    HomeProxyControlState.Disconnecting,
                        -> AppMd3Icons.Home.StatusWaiting
                },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(spacing.space18),
            )
            Box(
                modifier = Modifier
                    .heightIn(min = 22.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                AnimatedContent(
                    targetState = controlState,
                    transitionSpec = {
                        (
                            slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec = tween(
                                    durationMillis = AppMotion.DURATION_FAST,
                                    easing = AppMotion.EmphasizedDecelerate,
                                ),
                            ) + fadeIn(
                                animationSpec = tween(
                                    durationMillis = AppMotion.DURATION_FAST,
                                    easing = AppMotion.EnterEasing,
                                ),
                            )
                        ).togetherWith(
                            slideOutVertically(
                                targetOffsetY = { -it / 2 },
                                animationSpec = tween(
                                    durationMillis = AppMotion.DURATION_INSTANT,
                                    easing = AppMotion.EmphasizedAccelerate,
                                ),
                            ) + fadeOut(
                                animationSpec = tween(
                                    durationMillis = AppMotion.DURATION_INSTANT,
                                    easing = AppMotion.ExitEasing,
                                ),
                            )
                        ).using(
                            SizeTransform(clip = false)
                        )
                    },
                    label = "acg_launch_button_text",
                ) { state ->
                    Text(
                        text = when (state) {
                            HomeProxyControlState.Idle -> MLang.Home.Control.Start
                            HomeProxyControlState.Connecting -> MLang.Home.Status.Connecting
                            HomeProxyControlState.Running -> MLang.Home.Control.Stop
                            HomeProxyControlState.Disconnecting -> MLang.Home.Status.Disconnecting
                        },
                        color = contentColor,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


@Composable
internal fun AcgTrafficStrip(
    downloadSpeed: Long,
    uploadSpeed: Long,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stackLabel = maxWidth < 240.dp * LocalDensity.current.fontScale
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AcgTrafficItem(label = MLang.Home.Traffic.UpShort, speed = uploadSpeed, stackLabel = stackLabel)
            AcgTrafficItem(label = MLang.Home.Traffic.DownShort, speed = downloadSpeed, stackLabel = stackLabel)
        }
    }
}

@Composable
private fun AcgTrafficItem(
    label: String,
    speed: Long,
    stackLabel: Boolean,
) {
    val (value, unit) = formatBytesForDisplay(speed)
    val onSurface = MiuixTheme.colorScheme.onSurface
    Column {
        if (stackLabel) AcgTrafficLabel(label)
        Row(
            horizontalArrangement = Arrangement.spacedBy(AcgUi.Traffic.itemGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!stackLabel) AcgTrafficLabel(label)
            Text(
                text = value,
                color = onSurface,
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = unit,
                color = onSurface.copy(alpha = 0.55f),
                style = MiuixTheme.textStyles.footnote1,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(bottom = AcgUi.Traffic.labelBottomPadding),
            )
        }
    }
}

@Composable
private fun AcgTrafficLabel(label: String) {
    Text(
        text = label,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        style = MiuixTheme.textStyles.footnote1,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(bottom = AcgUi.Traffic.labelBottomPadding),
    )
}

@Composable
internal fun AcgHomeInfoPanel(
    serverName: String?,
    serverPing: Int?,
    ipMonitoringState: IpMonitoringState,
    modifier: Modifier = Modifier,
) {
    val flaggedNode = remember(serverName) { serverName?.let(::extractFlaggedName) }
    val resolvedNodeName = flaggedNode?.displayName ?: serverName.orEmpty().ifBlank { "" }
    val externalIp = ipMonitoringState.displayableExternalIp()
    var isIpVisible by rememberSaveable(externalIp?.ip) { mutableStateOf(false) }
    val resolvedExitIp = externalIp?.ip?.let { ipAddress -> buildAcgDisplayIpValue(ipAddress, isIpVisible) }
    val resolvedExitCountryCode = externalIp?.countryCode?.let(LocaleUtil::normalizeRegionCode)
    val resolvedPing = serverPing
        ?.takeIf { it in 1..1000 }
        ?.let { ping -> MLang.Home.NodeInfo.DelayValue.format(ping) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AcgUi.Hero.infoRowMinHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        if (resolvedNodeName.isNotBlank()) {
            AcgInfoBlock(
                value = resolvedNodeName,
                supportingValue = resolvedExitIp,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = AcgUi.Info.trailingPadding),
                supportingValueFontFamily = FontFamily.Monospace,
                supportingValueColor = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.74f),
                supportingValueClickable = resolvedExitIp != null,
                onSupportingValueClick = {
                    if (resolvedExitIp != null) {
                        isIpVisible = !isIpVisible
                    }
                },
                leading = {
                    flaggedNode?.countryCode?.let { countryCode ->
                        CountryFlagCircle(countryCode = countryCode, size = AppTheme.spacing.space16)
                    }
                },
                supportingLeading = {
                    resolvedExitCountryCode?.let { countryCode ->
                        AcgSupportingCountryBadge(countryCode = countryCode)
                    }
                },
            )
        } else if (resolvedExitIp != null) {
            AcgInfoBlock(
                value = MLang.Home.IpInfo.ExitIp,
                supportingValue = resolvedExitIp,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = AcgUi.Info.trailingPadding),
                supportingValueFontFamily = FontFamily.Monospace,
                supportingValueColor = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.74f),
                supportingValueClickable = true,
                onSupportingValueClick = { isIpVisible = !isIpVisible },
                supportingLeading = {
                    resolvedExitCountryCode?.let { countryCode ->
                        AcgSupportingCountryBadge(countryCode = countryCode)
                    }
                },
            )
        } else {
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = AcgUi.Info.trailingPadding),
            )
        }

        if (resolvedPing != null) {
            AcgInfoBlock(
                value = resolvedPing,
                modifier = Modifier
                    .widthIn(max = AcgUi.Hero.delayWidth)
                    .padding(top = 1.dp),
                valueColor = when {
                    serverPing < 500 -> AppTheme.colors.acg.pingExcellent
                    else -> AppTheme.colors.acg.pingWarning
                },
                alignEnd = true,
            )
        }
    }
}

@Composable
private fun AcgInfoBlock(
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MiuixTheme.colorScheme.onBackground,
    valueFontFamily: FontFamily? = null,
    supportingLabel: String? = null,
    supportingValue: String? = null,
    supportingLabelColor: Color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.52f),
    supportingValueColor: Color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.68f),
    supportingValueFontFamily: FontFamily? = null,
    supportingValueClickable: Boolean = false,
    onSupportingValueClick: (() -> Unit)? = null,
    alignEnd: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    supportingLeading: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(AcgUi.Info.contentGap),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(
                space = AcgUi.Info.blockGap,
                alignment = if (alignEnd) Alignment.End else Alignment.Start,
            ),
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (alignEnd) Modifier else Modifier.fillMaxWidth(),
        ) {
            leading?.invoke()
            Text(
                text = value,
                modifier = Modifier.weight(1f, fill = !alignEnd),
                color = valueColor,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                fontFamily = valueFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!supportingValue.isNullOrBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (supportingValueClickable && onSupportingValueClick != null) {
                    Modifier.clickable(onClick = onSupportingValueClick)
                } else {
                    Modifier
                },
            ) {
                supportingLeading?.invoke()
                supportingLabel?.takeIf { it.isNotBlank() }?.let { label ->
                    Text(
                        text = label,
                        color = supportingLabelColor,
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
                Text(
                    text = supportingValue,
                    modifier = Modifier.weight(1f, fill = false),
                    color = supportingValueColor,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    fontFamily = supportingValueFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AcgSupportingCountryBadge(countryCode: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountryFlagCircle(
            countryCode = countryCode,
            size = 12.dp,
        )
        Text(
            text = countryCode,
            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.92f),
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun TunnelState.Mode?.toAcgTunnelDisplayName(): String = when (this) {
    TunnelState.Mode.Direct -> "Direct"
    TunnelState.Mode.Global -> "Global"
    TunnelState.Mode.Rule -> "Rule"
    else -> "Rule"
}

private fun buildAcgDisplayIpValue(
    ipAddress: String,
    isIpVisible: Boolean,
): String {
    return if (ipAddress.contains(":")) {
        formatAcgIpv6Address(ipAddress = ipAddress, isIpVisible = isIpVisible)
    } else {
        if (isIpVisible) ipAddress else maskAcgIpv4Address(ipAddress)
    }
}

private fun maskAcgIpv4Address(ipAddress: String): String {
    val segments = ipAddress.split(".")
    if (segments.size != 4) {
        return "****"
    }
    return buildString {
        append(segments[0])
        append(".")
        append(segments[1])
        append(".")
        append("*".repeat(segments[2].length.coerceAtLeast(1)))
        append(".")
        append(segments[3])
    }
}

private fun formatAcgIpv6Address(
    ipAddress: String,
    isIpVisible: Boolean,
): String {
    val visibleSegments = ipAddress.split(":").filter { it.isNotBlank() }
    if (visibleSegments.isEmpty()) {
        return "****"
    }
    if (!isIpVisible) {
        return when {
            visibleSegments.size == 1 -> "${visibleSegments.first()}:****"
            visibleSegments.size == 2 -> "${visibleSegments[0]}:${"*".repeat(visibleSegments[1].length.coerceAtLeast(4))}"
            else -> "${visibleSegments[0]}:${visibleSegments[1]}:****"
        }
    }

    val visiblePrefix = visibleSegments.take(3)
    return if (visibleSegments.size > 4) {
        "${visiblePrefix.joinToString(":")}..."
    } else {
        visiblePrefix.joinToString(":")
    }
}
