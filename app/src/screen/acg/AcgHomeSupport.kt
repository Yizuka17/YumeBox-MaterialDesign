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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.github.yumelira.yumebox.data.model.RunMode
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.Sizes
import com.github.yumelira.yumebox.presentation.theme.Opacity
import com.github.yumelira.yumebox.presentation.theme.Radii
import com.github.yumelira.yumebox.presentation.theme.Spacing
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.miuix.YumeMiuixIcon as Icon
import com.github.yumelira.yumebox.miuix.YumeMiuixText as Text
import com.github.yumelira.yumebox.miuix.YumeMiuixTheme as MiuixTheme
import dev.oom_wg.purejoy.mlang.MLang
import kotlin.math.abs

private val acgSpacing = Spacing()
private val acgRadii = Radii()
private val acgSizes = Sizes()
private val acgOpacity = Opacity()

internal object AcgUi {
    object Shape {
        val hero = RoundedCornerShape(
            topStart = acgSpacing.space28,
            topEnd = acgSpacing.space28,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )
        val launchButton = RoundedCornerShape(acgRadii.full)
        val sidebarIconButton = RoundedCornerShape(20.dp)
    }

    object Sidebar {
        val Fraction = 0.25f
        val contentOverlap = acgSpacing.space28
        val innerHorizontalPadding = acgSpacing.space12
        val visibleOpticalOffset = -acgSpacing.space4
        val topInset = acgSizes.heroStartButtonSize - acgSpacing.space2
        val bottomInset = acgSpacing.space32 + acgSpacing.space6
        val statsWidth = acgSizes.nodeDelayColumnWidth
        val timeGap = acgSpacing.space12
        val timeValueHeight = acgSizes.searchFieldMinHeight + acgSpacing.space4 / 2
        val modeTopGap = acgSpacing.space18
        val modeFontSize = 15.sp
        val dividerWidth = acgSizes.settingsIconGlyphSize
        val dividerHeight = acgSpacing.space2
        val iconSpacing = acgSpacing.space20
        val iconSize = UiDp.dp22
        val iconContainerSize = UiDp.dp48
        val modeLetterSpacing = 1.4.sp
        val IconButtonPressedAlpha = 0.18f
        val digitLetterSpacing = 1.6.sp
        val TimeAlpha = 0.92f
        val DividerAlpha = 0.42f
        val IconAlpha = 0.88f
        val collapsedVisibleWidth = acgSpacing.space8
    }

    object Hero {
        val containerHorizontalInset = acgSpacing.space12
        val contentHorizontalInset = acgSpacing.space12
        val trafficRowGap = acgSpacing.space28
        val trafficBottomInset = acgSpacing.space12
        val runtimeInfoTopGap = acgSpacing.space16
        val delayWidth = UiDp.dp72
        val belowHeroTopGap = acgSpacing.space8
        val belowHeroContentGap = acgSpacing.space12
        val infoPlaceholderAlpha = acgOpacity.surfaceSoft
        val infoRowMinHeight = acgSpacing.space32 + acgSpacing.space8
        val infoPlaceholderNodeWidth = acgSizes.homeIdleTopPadding - acgSpacing.space8
    }

    object Button {
        val bottomInset = acgSpacing.space28
        val fixedWidth = acgSizes.homeIdleTopPadding + acgSpacing.space4
        val horizontalPadding = acgSizes.settingsIconGlyphSize
        val verticalPadding = acgSpacing.space14 + acgSpacing.space2 / 2
        val PressedScale = 0.94f
    }

    object Quote {
        val contentGap = acgSpacing.space12
        val authorTopGap = acgSpacing.space14
        val textSize = 23.sp
        val lineHeight = 31.sp
        val authorSize = 16.sp
        val authorAlpha = acgOpacity.elevatedSurface
    }

    object Traffic {
        val itemGap = acgSpacing.space6
        val labelBottomPadding = acgSpacing.space3
    }

    object Info {
        val trailingPadding = acgSpacing.space12
        val blockGap = acgSpacing.space8
        val contentGap = acgSpacing.space6
    }
}

internal fun calculateAcgSidebarLaneStart(visibleWidth: Dp): Dp {
    val visibleContentWidth =
        (visibleWidth - AcgUi.Sidebar.innerHorizontalPadding).coerceAtLeast(UiDp.dp0)
    val visibleCenterLine = visibleContentWidth / 2
    val centerLine = visibleCenterLine + AcgUi.Sidebar.visibleOpticalOffset
    return centerLine - (AcgUi.Sidebar.statsWidth / 2)
}

@Composable
internal fun AcgSidebarRail(
    topValue: String,
    bottomValue: String,
    proxyMode: RunMode,
    icons: List<AcgSidebarIconItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(AcgUi.Sidebar.topInset))

        AcgSidebarValueStack(
            topValue = topValue,
            bottomValue = bottomValue,
        )

        AcgSidebarModeText(
            mode = proxyMode.toAcgDisplayName(),
            modifier = Modifier.padding(top = AcgUi.Sidebar.modeTopGap),
        )

        Spacer(modifier = Modifier.weight(1f))

        AcgSidebarIconRail(icons = icons)

        Spacer(modifier = Modifier.height(AcgUi.Sidebar.bottomInset))
    }
}

@Composable
private fun AcgSidebarValueStack(
    topValue: String,
    bottomValue: String,
) {
    val contentColor = MiuixTheme.colorScheme.onSurface
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AcgUi.Sidebar.timeGap),
    ) {
        AcgSidebarTimeValue(value = topValue)
        Box(
            modifier = Modifier
                .width(AcgUi.Sidebar.dividerWidth)
                .height(AcgUi.Sidebar.dividerHeight)
                .background(contentColor.copy(alpha = AcgUi.Sidebar.DividerAlpha)),
        )
        AcgSidebarTimeValue(value = bottomValue)
    }
}

@Composable
private fun AcgSidebarIconRail(
    icons: List<AcgSidebarIconItem>,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AcgUi.Sidebar.iconSpacing),
    ) {
        icons.forEach { item ->
            AcgSidebarIconItemView(item = item)
        }
    }
}

@Composable
private fun AcgSidebarIconItemView(
    item: AcgSidebarIconItem,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MiuixTheme.colorScheme
    val containerColor = if (isPressed) {
        colorScheme.surface.copy(alpha = AcgUi.Sidebar.IconButtonPressedAlpha)
    } else {
        Color.Transparent
    }
    val iconTint = colorScheme.onSurface.copy(alpha = AcgUi.Sidebar.IconAlpha)

    Box(
        modifier = Modifier
            .size(AcgUi.Sidebar.iconContainerSize)
            .graphicsLayer {
                scaleX = if (isPressed) 0.94f else 1f
                scaleY = if (isPressed) 0.94f else 1f
            }
            .clip(AcgUi.Shape.sidebarIconButton)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = item.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(AcgUi.Sidebar.iconSize),
        )
    }
}

@Composable
private fun AcgSidebarModeText(
    mode: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = mode.uppercase(),
        modifier = modifier,
        style = MiuixTheme.textStyles.footnote1.copy(
            fontSize = AcgUi.Sidebar.modeFontSize,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = AcgUi.Sidebar.modeLetterSpacing,
        ),
        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.92f),
    )
}

@Composable
private fun AcgSidebarTimeValue(value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AcgUi.Sidebar.timeValueHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = AcgUi.Sidebar.TimeAlpha),
            style = MiuixTheme.textStyles.title1,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontSize = 37.sp,
            letterSpacing = AcgUi.Sidebar.digitLetterSpacing,
            softWrap = false,
        )
    }
}

internal enum class AcgWallpaperQualityMode {
    Foreground,
    BackgroundBlur,
}

internal fun lerpFloat(
    start: Float,
    stop: Float,
    progress: Float,
): Float = start + (stop - start) * progress

internal fun lerpDp(
    start: Dp,
    stop: Dp,
    progress: Float,
): Dp = start + (stop - start) * progress

internal fun calculateHomeVisibility(
    currentPage: Int,
    currentPageOffsetFraction: Float,
): Float {
    val offset = abs(currentPage.toFloat() + currentPageOffsetFraction)
    return 1f - offset.coerceIn(0f, 1f)
}

internal data class AcgSidebarIconItem(
    val icon: ImageVector,
    val onClick: () -> Unit,
)

internal data class AcgQuote(
    val text: String,
    val author: String,
)

internal fun RunMode.toAcgDisplayName(): String = when (this) {
    RunMode.VpnService -> MLang.Home.ProxyMode.Vpn
    RunMode.Tun -> MLang.Home.ProxyMode.Tun
    RunMode.Ebpf -> "eBPF"
}

internal data class AcgDurationPair(
    val top: String = "00",
    val bottom: String = "00",
)

internal fun formatAcgDuration(elapsedMillis: Long): AcgDurationPair {
    val totalSeconds = (elapsedMillis / 1000L).coerceAtLeast(0L)
    val totalMinutes = totalSeconds / 60L
    val totalHours = totalMinutes / 60L
    return if (totalMinutes < 60L) {
        AcgDurationPair(
            top = totalMinutes.toString().padStart(2, '0'),
            bottom = (totalSeconds % 60L).toString().padStart(2, '0'),
        )
    } else {
        AcgDurationPair(
            top = totalHours.toString().padStart(2, '0'),
            bottom = (totalMinutes % 60L).toString().padStart(2, '0'),
        )
    }
}
