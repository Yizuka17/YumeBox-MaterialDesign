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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.domain.model.ProxyGroupInfo
import com.github.yumelira.yumebox.presentation.component.CountryFlagCircle
import com.github.yumelira.yumebox.presentation.component.Md3ELoading
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.UiDp
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class GroupBadge(
    val label: String,
)

private const val ZAKO_GROUP_NAME = "zako"

private fun groupBadge(type: String): GroupBadge = GroupBadge(type)

private fun ProxyGroupInfo.isZakoGroup(): Boolean = name.equals(ZAKO_GROUP_NAME, ignoreCase = true)

internal fun LazyListScope.nodeGroupItems(
    groups: List<ProxyGroupInfo>,
    onGroupClick: (ProxyGroupInfo) -> Unit,
    testingGroupNames: Set<String> = emptySet(),
    itemVerticalPadding: Dp = UiDp.dp6,
) {
    items(
        items = groups,
        key = { group -> "${group.type}:${group.name}" },
        contentType = { "NodeGroupCard" },
    ) { group ->
        NodeGroupCard(
            group = group,
            isDelayTesting = testingGroupNames.contains(group.name),
            onClick = onGroupClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = itemVerticalPadding),
        )
    }
}

@Composable
internal fun NodeGroupCard(
    group: ProxyGroupInfo,
    isDelayTesting: Boolean,
    onClick: ((ProxyGroupInfo) -> Unit)?,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    showTrailingIndicator: Boolean = true,
) {
    val spacing = AppTheme.spacing
    val palette = rememberProxySelectionPalette(selected = isSelected)

    val proxiesByName = remember(group.proxies) {
        group.proxies.associateBy(Proxy::name)
    }
    val currentProxy = remember(group.now, proxiesByName) {
        proxiesByName[group.now]
    }
    val currentNode = remember(currentProxy?.name, currentProxy?.title, group.now) {
        resolveProxyDisplayPresentation(
            name = currentProxy?.name ?: group.now,
            title = currentProxy?.title,
        )
    }
    val isZakoGroup = remember(group.name) { group.isZakoGroup() }
    val currentNodeName = if (isZakoGroup) {
        MLang.Proxy.Zako.Others
    } else {
        remember(currentNode.displayName, group.now) {
            currentNode.displayName.ifBlank { group.now.trim() }.ifBlank { MLang.Proxy.Mode.Direct }
        }
    }
    val currentDelay = remember(currentProxy, isZakoGroup) { currentProxy?.delay?.takeUnless { isZakoGroup } }
    val badge = remember(group.type) { groupBadge(group.type) }
    val badgeLabel = if (isZakoGroup) MLang.Proxy.Zako.Badge else badge.label
    val delayLabel = nodeLatencyLabel(currentDelay)
    val onCardClick = remember(group, onClick) { onClick?.let { click -> { click(group) } } }

    NodeSelectableCard(
        isSelected = isSelected,
        onClick = onCardClick,
        modifier = modifier,
        paddingVertical = spacing.space12,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Top,
            ) {
                when {
                    delayLabel != null -> {
                        val (delayText, delayColor) = delayLabel
                        Text(
                            text = delayText,
                            style = MiuixTheme.textStyles.footnote1,
                            color = delayColor,
                            maxLines = 1,
                        )
                    }

                    isDelayTesting -> {
                        Md3ELoading(modifier = Modifier.size(spacing.space18))
                    }
                }
            }

            Text(
                text = group.name,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
                color = palette.contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiDp.dp8),
            ) {
                currentNode.countryCode?.let { countryCode ->
                    CountryFlagCircle(countryCode = countryCode, size = UiDp.dp18)
                }
                Text(
                    text = currentNodeName,
                    style = MiuixTheme.textStyles.footnote1,
                    color = palette.supportingColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = badgeLabel,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = palette.chipContentColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTheme.radii.full))
                        .background(palette.chipBackgroundColor)
                        .padding(horizontal = spacing.space8, vertical = spacing.space4),
                )

                when {
                    isSelected -> {
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
                    }

                    showTrailingIndicator -> Spacer(modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}
