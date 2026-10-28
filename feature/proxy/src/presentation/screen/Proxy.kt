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

package com.github.yumelira.yumebox.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon as MdIcon
import androidx.compose.material3.IconButton as MdIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MdText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.data.model.ProxySortMode
import com.github.yumelira.yumebox.domain.model.ProxyGroupInfo
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.CenteredText
import com.github.yumelira.yumebox.presentation.component.Md3ELoading
import com.github.yumelira.yumebox.presentation.component.LocalBottomBarScrollBehavior
import com.github.yumelira.yumebox.presentation.component.SmallTopBar
import com.github.yumelira.yumebox.presentation.component.rememberRetainedStaggeredGridState
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.presentation.screen.node.NodeCard
import com.github.yumelira.yumebox.presentation.screen.node.NodeGroupCard
import com.github.yumelira.yumebox.presentation.screen.node.NodeSortPopup
import com.github.yumelira.yumebox.presentation.theme.AppMotion
import com.github.yumelira.yumebox.presentation.theme.LocalSpacing
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.presentation.viewmodel.ProxyViewModel
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior

private const val GLOBAL_GROUP_NAME = "GLOBAL"
private const val ZAKO_GROUP_NAME = "zako"

@Composable
fun ProxyPager(
    mainInnerPadding: PaddingValues,
    onNavigateToProviders: (() -> Unit)?,
    onOpenPanel: (() -> Unit)?,
    isPageActive: Boolean,
    isProxyRunning: Boolean,
    onProxyStartRequested: (() -> Unit)? = null,
) {
    val proxyViewModel = koinViewModel<ProxyViewModel>()
    val proxyGroups by proxyViewModel.sortedProxyGroups.collectAsState()
    val testingGroupNames by proxyViewModel.testingGroupNames.collectAsState()
    val testingProxyNames by proxyViewModel.testingProxyNames.collectAsState()
    val sortMode by proxyViewModel.sortMode.collectAsState()
    val tunnelMode by proxyViewModel.tunnelMode.collectAsState()
    var displayTunnelMode by remember { mutableStateOf(tunnelMode) }
    val singleNodeTest by proxyViewModel.singleNodeTest.collectAsState()
    val groupScrollBehavior = MiuixScrollBehavior(snapAnimationSpec = null)

    var showSortPopup by remember { mutableStateOf(false) }
    var selectedGroupName by remember { mutableStateOf<String?>(null) }
    var pendingTestGroupName by remember { mutableStateOf<String?>(null) }
    var pendingTestProxyName by remember { mutableStateOf<String?>(null) }

    val globalGroup = remember(proxyGroups) {
        proxyGroups.firstOrNull(ProxyGroupInfo::isGlobalGroup)
    }
    val selectedGroup = remember(proxyGroups, selectedGroupName) {
        proxyGroups.firstOrNull { it.name == selectedGroupName } ?: proxyGroups.firstOrNull()
    }
    val isViewingZako = displayTunnelMode == TunnelState.Mode.Global && selectedGroupName.isZakoGroupName()
    val delayTargetGroup = if (isViewingZako) globalGroup else selectedGroup
    val effectiveSelectedGroupName = delayTargetGroup?.name
    val onTestDelay = remember(effectiveSelectedGroupName, proxyViewModel) {
        { proxyViewModel.testDelay(effectiveSelectedGroupName) }
    }
    val onTestDelayAction: () -> Unit = remember(
        isProxyRunning,
        onProxyStartRequested,
        onTestDelay,
        effectiveSelectedGroupName,
    ) {
        {
            if (isProxyRunning) {
                onTestDelay()
            } else {
                pendingTestGroupName = effectiveSelectedGroupName
                pendingTestProxyName = null
                onProxyStartRequested?.invoke()
                Unit
            }
        }
    }
    val effectiveTestingGroupNames = remember(testingGroupNames, pendingTestGroupName) {
        pendingTestGroupName?.let { testingGroupNames + it } ?: testingGroupNames
    }
    val effectiveTestingProxyNames = remember(testingProxyNames, pendingTestProxyName) {
        pendingTestProxyName?.let { testingProxyNames + it } ?: testingProxyNames
    }
    val gridState = rememberRetainedStaggeredGridState(
        "main_proxy_${displayTunnelMode.name}_${effectiveSelectedGroupName.orEmpty()}"
    )
    val modes = remember { listOf(TunnelState.Mode.Rule, TunnelState.Mode.Global, TunnelState.Mode.Direct) }

    LaunchedEffect(tunnelMode) {
        displayTunnelMode = tunnelMode
    }

    LaunchedEffect(proxyGroups, selectedGroupName, displayTunnelMode) {
        val selectableGroups = if (displayTunnelMode == TunnelState.Mode.Global) {
            proxyGroups.globalModeTabGroups()
        } else {
            proxyGroups
        }
        when {
            selectableGroups.isEmpty() -> selectedGroupName = null
            selectedGroupName == null || selectableGroups.none { it.name == selectedGroupName } -> {
                selectedGroupName = selectableGroups.first().name
            }
        }
    }

    LaunchedEffect(sortMode, effectiveSelectedGroupName) {
        if (sortMode == ProxySortMode.BY_LATENCY) {
            gridState.scrollToItem(0)
        }
    }

    LaunchedEffect(isProxyRunning, pendingTestGroupName, pendingTestProxyName) {
        if (!isProxyRunning) return@LaunchedEffect
        val groupName = pendingTestGroupName ?: return@LaunchedEffect
        val proxyName = pendingTestProxyName
        if (proxyName != null) {
            proxyViewModel.testProxyDelay(groupName, proxyName)
        } else {
            proxyViewModel.testDelay(groupName)
        }
        delay(500)
        if (pendingTestGroupName == groupName && pendingTestProxyName == proxyName) {
            pendingTestGroupName = null
            pendingTestProxyName = null
        }
    }

    LaunchedEffect(isPageActive) {
        proxyViewModel.ensureCoreLoaded(isPageActive, source = "proxy_page")
    }

    DisposableEffect(proxyViewModel) {
        onDispose {
            proxyViewModel.ensureCoreLoaded(false, source = "proxy_page")
        }
    }

    Scaffold(
        topBar = {
            ProxyTopBar(
                title = MLang.Proxy.Title,
                scrollBehavior = groupScrollBehavior,
                onNavigateToProviders = onNavigateToProviders,
                onOpenPanel = onOpenPanel,
                onTestDelay = if (effectiveSelectedGroupName != null && !isViewingZako) onTestDelayAction else null,
                showSortPopup = showSortPopup,
                onShowSortPopupChange = { showSortPopup = it },
                sortMode = sortMode,
                onSortSelected = proxyViewModel::setSortMode,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            ProxySurfboardContent(
                proxyGroups = proxyGroups,
                selectedGroup = selectedGroup,
                selectedGroupName = selectedGroupName,
                testingGroupNames = effectiveTestingGroupNames,
                testingProxyNames = effectiveTestingProxyNames,
                gridState = gridState,
                modes = modes,
                tunnelMode = displayTunnelMode,
                onTunnelModeSelected = { mode ->
                    displayTunnelMode = mode
                    proxyViewModel.setTunnelMode(mode)
                },
                innerPadding = innerPadding,
                mainInnerPadding = mainInnerPadding,
                onGroupSelected = { selectedGroupName = it },
                onSelectProxy = { groupName, proxyName, onSuccess ->
                    proxyViewModel.selectProxy(groupName, proxyName, onSuccess = onSuccess)
                },
                onProxyStartRequested = onProxyStartRequested,
                isProxyRunning = isProxyRunning,
                onTestDelay = onTestDelayAction,
                onTestProxyDelay = { proxyName ->
                    if (!isProxyRunning) {
                        pendingTestGroupName = effectiveSelectedGroupName
                        pendingTestProxyName = proxyName
                        onProxyStartRequested?.invoke()
                    } else {
                        effectiveSelectedGroupName?.let { groupName ->
                            proxyViewModel.testProxyDelay(groupName, proxyName)
                        }
            }
                    Unit
                },
                singleNodeTestEnabled = singleNodeTest,
            )
        }
    }
}

@Composable
private fun ProxyTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    onNavigateToProviders: (() -> Unit)?,
    onOpenPanel: (() -> Unit)?,
    onTestDelay: (() -> Unit)?,
    showSortPopup: Boolean,
    onShowSortPopupChange: (Boolean) -> Unit,
    sortMode: ProxySortMode,
    onSortSelected: (ProxySortMode) -> Unit,
) {
    SmallTopBar(
        title = title,
        scrollBehavior = scrollBehavior,
        navigationIconPadding = UiDp.dp12,
        actionIconPadding = UiDp.dp12,
        titlePadding = UiDp.dp12,
        navigationIcon = {
            Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp12)) {
                if (onNavigateToProviders != null) {
                    IconButton(onClick = onNavigateToProviders) {
                        Icon(AppMd3Icons.Proxy.Profiles, contentDescription = MLang.Providers.Title)
                    }
                }
                if (onOpenPanel != null) {
                    IconButton(onClick = onOpenPanel) {
                        Icon(AppMd3Icons.Proxy.Panel, contentDescription = MLang.Proxy.Action.Panel)
                    }
                }
            }
        },
        actions = {
            if (onTestDelay != null) {
                IconButton(
                    modifier = Modifier.padding(end = UiDp.dp12),
                    onClick = onTestDelay,
                ) {
                    Icon(AppMd3Icons.Action.SpeedTest, contentDescription = MLang.Proxy.Action.Test)
                }
    }
            Box {
                MdIconButton(onClick = { onShowSortPopupChange(true) }) {
                    MdIcon(
                        imageVector = AppMd3Icons.Action.Sort,
                        contentDescription = MLang.Proxy.Action.Sort,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                NodeSortPopup(
                    show = showSortPopup,
                    onDismiss = { onShowSortPopupChange(false) },
                    sortMode = sortMode,
                    onSortSelected = onSortSelected,
                )
            }
        },
    )
}

@Composable
private fun ProxySurfboardContent(
    proxyGroups: List<ProxyGroupInfo>,
    selectedGroup: ProxyGroupInfo?,
    selectedGroupName: String?,
    testingGroupNames: Set<String>,
    testingProxyNames: Set<String>,
    gridState: LazyStaggeredGridState,
    modes: List<TunnelState.Mode>,
    tunnelMode: TunnelState.Mode,
    onTunnelModeSelected: (TunnelState.Mode) -> Unit,
    innerPadding: PaddingValues,
    mainInnerPadding: PaddingValues,
    onGroupSelected: (String) -> Unit,
    onSelectProxy: (String, String, (() -> Unit)?) -> Unit,
    onProxyStartRequested: (() -> Unit)?,
    isProxyRunning: Boolean,
    onTestDelay: () -> Unit,
    onTestProxyDelay: (String) -> Unit,
    singleNodeTestEnabled: Boolean,
) {
    val spacing = LocalSpacing.current
    val selectedName = selectedGroupName ?: selectedGroup?.name
    val isZakoPage = tunnelMode == TunnelState.Mode.Global && selectedName.isZakoGroupName()
    val tabGroups = remember(proxyGroups, tunnelMode) {
        if (tunnelMode == TunnelState.Mode.Global) proxyGroups.globalModeTabGroups() else proxyGroups
    }
    val tabSelectedName = selectedName
    val zakoStatusGroups = remember(proxyGroups) { proxyGroups.zakoStatusGroups() }
    val displayProxies = remember(proxyGroups, selectedGroup, tunnelMode, isZakoPage) {
        if (
            tunnelMode == TunnelState.Mode.Global &&
            !isZakoPage &&
            selectedGroup?.isGlobalGroup() == true
        ) {
            selectedGroup.proxies.filterNot { proxy ->
                proxy.isGroup || proxyGroups.any { group ->
                    !group.isGlobalGroup() && !group.isZakoGroup() && group.name == proxy.name
                }
            }
        } else {
            selectedGroup?.proxies.orEmpty()
        }
    }
    val isTesting = !isZakoPage && selectedName?.let(testingGroupNames::contains) == true
    val currentPage = modes.indexOf(tunnelMode).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = currentPage, pageCount = { modes.size })
    val bottomBarScrollBehavior = LocalBottomBarScrollBehavior.current
    val isGridAtTop by remember(gridState) {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }
    }

    LaunchedEffect(isGridAtTop, bottomBarScrollBehavior) {
        if (isGridAtTop) {
            bottomBarScrollBehavior?.showBottomBar()
        }
    }

    var optimisticSelectedProxyName by remember(selectedGroup?.name) { mutableStateOf<String?>(null) }
    val effectiveNow = optimisticSelectedProxyName ?: selectedGroup?.now

    LaunchedEffect(selectedGroup?.now) {
        if (selectedGroup?.now == optimisticSelectedProxyName) {
            optimisticSelectedProxyName = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding()),
    ) {
        val scrollModeSelector = maxHeight - mainInnerPadding.calculateBottomPadding() < 160.dp
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .let { modifier ->
                        if (bottomBarScrollBehavior != null) {
                            modifier.nestedScroll(bottomBarScrollBehavior.nestedScrollConnection)
                        } else {
                            modifier
                        }
                    },
                contentPadding = PaddingValues(
                    start = UiDp.dp12,
                    end = UiDp.dp12,
                    top = if (scrollModeSelector) UiDp.dp8 else UiDp.dp64,
                    bottom = mainInnerPadding.calculateBottomPadding() + spacing.space12,
                ),
                horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
                verticalItemSpacing = UiDp.dp12,
            ) {
                if (scrollModeSelector) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        ProxyModeSelector(
                            currentMode = tunnelMode,
                            onModeSelected = onTunnelModeSelected,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item(span = StaggeredGridItemSpan.FullLine) {
                    ProxyGroupTabSection(
                        groups = tabGroups,
                        selectedGroupName = tabSelectedName,
                        onGroupSelected = onGroupSelected,
                    )
                }

                    item(span = StaggeredGridItemSpan.FullLine) {
                    AnimatedVisibility(
                        visible = isTesting,
                        enter = expandVertically(
                            animationSpec = AppMotion.fastSpatial<IntSize>(),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = tween(durationMillis = AppMotion.Proxy.RefreshIndicatorFadeDuration)),
                        exit = shrinkVertically(
                            animationSpec = AppMotion.fastSpatial<IntSize>(),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = tween(durationMillis = AppMotion.Proxy.RefreshIndicatorFadeDuration)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = UiDp.dp2, bottom = UiDp.dp4),
                            contentAlignment = Alignment.Center,
                        ) {
                            Md3ELoading()
                        }
                    }
                }

                if (isZakoPage) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        ZakoDescriptionCard()
                    }
                    items(items = zakoStatusGroups, key = { it.name }) { group ->
                        NodeGroupCard(
                            group = group,
                            isDelayTesting = false,
                            isSelected = false,
                            showTrailingIndicator = false,
                            onClick = null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else if (selectedGroup == null) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        CenteredText(
                            firstLine = MLang.Proxy.Empty.NoNodes,
                            secondLine = MLang.Proxy.Empty.Hint,
                        )
                    }
                } else {
                    items(items = displayProxies, key = { it.name }) { proxy ->
                        NodeCard(
                            proxy = proxy,
                            isSelected = proxy.name == effectiveNow,
                            onClick = { proxyName ->
                                if (selectedGroup.type == com.github.yumelira.yumebox.core.model.Proxy.Type.Selector) {
                                    optimisticSelectedProxyName = proxyName
                                    onSelectProxy(
                                        selectedGroup.name,
                                        proxyName,
                                        { optimisticSelectedProxyName = null },
                                    )
                                } else {
                                    onTestDelay()
                                }
                            },
                            isDelayTesting = isTesting,
                            isThisProxyTesting = proxy.name in testingProxyNames,
                            onSingleNodeTestClick = onTestProxyDelay,
                            showCountryFlag = true,
                            singleNodeTestEnabled = singleNodeTestEnabled,
                        )
                    }
                }
            }
        }

        if (!scrollModeSelector) Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = UiDp.dp8, start = UiDp.dp12, end = UiDp.dp12),
            contentAlignment = Alignment.Center,
        ) {
            ProxyModeSelector(
                currentMode = tunnelMode,
                onModeSelected = onTunnelModeSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProxyModeSelector(
    currentMode: TunnelState.Mode,
    onModeSelected: (TunnelState.Mode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(TunnelState.Mode.Rule, TunnelState.Mode.Global, TunnelState.Mode.Direct)
    val selectedIndex = modes.indexOf(currentMode).coerceAtLeast(0)
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(999.dp)
    var containerWidthPx by remember { mutableIntStateOf(0) }
    val indicatorWidthPx = remember(containerWidthPx, modes.size) {
        (containerWidthPx.toFloat() / modes.size).coerceAtLeast(0f)
    }
    val indicatorWidth = with(density) { indicatorWidthPx.toDp() }
    val indicatorOffset by animateDpAsState(
        targetValue = with(density) { (indicatorWidthPx * selectedIndex).toDp() },
        animationSpec = AppMotion.indicator(),
        label = "proxy_mode_indicator_offset",
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = UiDp.dp8,
                shape = shape,
                clip = false,
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(UiDp.dp4),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(UiDp.dp40)
                .onSizeChanged { containerWidthPx = it.width },
        ) {
            if (indicatorWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationX = indicatorOffset.toPx() }
                        .width(indicatorWidth)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
            }

            Row(
                modifier = Modifier.matchParentSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            modes.forEach { mode ->
                val selected = mode == currentMode
                val textColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = AppMotion.color(),
                    label = "proxy_mode_text_color",
                )
                val textScale by animateFloatAsState(
                    targetValue = if (selected) 1.03f else 1f,
                    animationSpec = AppMotion.fastSpatial(),
                    label = "proxy_mode_text_scale",
                )
                val itemInteractionSource = remember(mode) { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(UiDp.dp40)
                        .clip(shape)
                        .clickable(
                            interactionSource = itemInteractionSource,
                            indication = null,
                            enabled = !selected,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onModeSelected(mode)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    MdText(
                        text = mode.displayName(),
                        modifier = Modifier.graphicsLayer {
                            scaleX = textScale
                            scaleY = textScale
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
}

private fun TunnelState.Mode.displayName(): String = when (this) {
    TunnelState.Mode.Direct -> MLang.Proxy.Mode.Direct
    TunnelState.Mode.Global -> MLang.Proxy.Mode.Global
    TunnelState.Mode.Rule -> MLang.Proxy.Mode.Rule
    TunnelState.Mode.Script -> "Script"
}

private fun ProxyGroupInfo.isGlobalGroup(): Boolean = name.equals(GLOBAL_GROUP_NAME, ignoreCase = true)

private fun ProxyGroupInfo.isZakoGroup(): Boolean = name.equals(ZAKO_GROUP_NAME, ignoreCase = true)

private fun String?.isZakoGroupName(): Boolean = this?.equals(ZAKO_GROUP_NAME, ignoreCase = true) == true

private fun List<ProxyGroupInfo>.globalModeTabGroups(): List<ProxyGroupInfo> {
    val globalGroup = firstOrNull(ProxyGroupInfo::isGlobalGroup)
    val zakoGroup = firstOrNull(ProxyGroupInfo::isZakoGroup)
    return listOfNotNull(globalGroup, zakoGroup).ifEmpty { this }
}

private fun List<ProxyGroupInfo>.zakoStatusGroups(): List<ProxyGroupInfo> {
    return filterNot { group -> group.isGlobalGroup() || group.isZakoGroup() }
}

@Composable
private fun ZakoDescriptionCard() {
    MdText(
        text = MLang.Proxy.Zako.Description,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(horizontal = UiDp.dp16, vertical = UiDp.dp18),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProxyGroupTabSection(
    groups: List<ProxyGroupInfo>,
    selectedGroupName: String?,
    onGroupSelected: (String) -> Unit,
) {
    ProxyGroupTabs(
        groups = groups,
        selectedGroupName = selectedGroupName,
        onGroupSelected = onGroupSelected,
    )
}

@Composable
private fun ProxyGroupTabs(
    groups: List<ProxyGroupInfo>,
    selectedGroupName: String?,
    onGroupSelected: (String) -> Unit,
) {
    var showAllGroups by remember { mutableStateOf(false) }
    val allGroupsArrowRotation by animateFloatAsState(
        targetValue = if (showAllGroups) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "proxy_group_all_groups_arrow_rotation",
    )
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiDp.dp8),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(start = UiDp.dp4, end = UiDp.dp4),
        ) {
            items(items = groups, key = { it.name }) { group ->
                ProxyGroupTab(
                    title = group.name,
                    selected = group.name == selectedGroupName,
                    modifier = Modifier.widthIn(min = 84.dp),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onGroupSelected(group.name)
                    },
                )
            }
        }
        Box(
            modifier = Modifier
                .size(UiDp.dp36)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    showAllGroups = true
                }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AppMd3Icons.Navigation.DownAngle,
                contentDescription = MLang.Proxy.Title,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(UiDp.dp20)
                    .rotate(allGroupsArrowRotation),
            )
        }
    }

    AppActionBottomSheet(
        show = showAllGroups,
        title = MLang.Proxy.Title,
        onDismissRequest = { showAllGroups = false },
        contentScrollEnabled = false,
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
            verticalItemSpacing = UiDp.dp12,
            contentPadding = PaddingValues(bottom = UiDp.dp16),
        ) {
            items(items = groups, key = { it.name }) { group ->
                NodeGroupCard(
                    group = group,
                    isDelayTesting = false,
                    isSelected = group.name == selectedGroupName,
                    showTrailingIndicator = false,
                    onClick = {
                        onGroupSelected(group.name)
                        coroutineScope.launch {
                            delay(180)
                            showAllGroups = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProxyGroupTab(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val colorScheme = MaterialTheme.colorScheme
    val activeColor = colorScheme.primary
    val defaultColor = colorScheme.onSurface
    val transition = updateTransition(targetState = selected, label = "proxy_group_tab_selection")
    val fastEffectsSpec = AppMotion.fastEffects<Color>()
    val fastSpatialSpec = AppMotion.fastSpatial<Float>()
    val indicatorSpec = AppMotion.indicator<Float>()
    val selectionScaleX = remember { Animatable(1f) }
    val animatedContainerColor by transition.animateColor(
        transitionSpec = { fastEffectsSpec },
        label = "proxy_group_tab_container",
    ) { isSelected ->
        if (isSelected) activeColor.copy(alpha = 0.16f) else Color.Transparent
    }
    val animatedTextColor by transition.animateColor(
        transitionSpec = { fastEffectsSpec },
        label = "proxy_group_tab_text",
    ) { isSelected ->
        if (isSelected) activeColor else defaultColor
    }
    LaunchedEffect(selected) {
        if (selected) {
            selectionScaleX.snapTo(1f)
            selectionScaleX.animateTo(1.06f, animationSpec = fastSpatialSpec)
            selectionScaleX.animateTo(1f, animationSpec = indicatorSpec)
        } else {
            selectionScaleX.animateTo(1f, animationSpec = fastSpatialSpec)
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = selectionScaleX.value
                scaleY = 1f
            }
            .height(UiDp.dp40)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(animatedContainerColor),
        )
        MdText(
            text = title,
            modifier = Modifier.padding(horizontal = UiDp.dp12),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = animatedTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


