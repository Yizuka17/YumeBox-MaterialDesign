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


import com.github.yumelira.yumebox.presentation.theme.UiDp
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.resize.Precision
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.data.model.RunMode
import com.github.yumelira.yumebox.data.model.ThemeMode
import com.github.yumelira.yumebox.domain.model.TrafficData
import com.github.yumelira.yumebox.presentation.component.LocalHandlePageChange
import com.github.yumelira.yumebox.presentation.component.Md3ELoading
import com.github.yumelira.yumebox.presentation.component.calculateWallpaperViewportLayout
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.presentation.icon.ShellIcons
import com.github.yumelira.yumebox.presentation.theme.AppMotion
import com.github.yumelira.yumebox.screen.home.HomeProxyControlState
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import com.github.yumelira.yumebox.screen.home.displayableExternalIp
import com.github.yumelira.yumebox.screen.settings.AppSettingsViewModel
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.yumelira.yumebox.miuix.YumeMiuixTheme
import com.github.yumelira.yumebox.miuix.rememberYumeMiuixLayerBackdrop
import com.github.yumelira.yumebox.miuix.yumeMiuixLayerBackdrop
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun AcgHomePage(
    mainInnerPadding: PaddingValues,
    wallpaperUri: String,
    wallpaperZoom: Float = 1f,
    wallpaperBiasX: Float = 0f,
    wallpaperBiasY: Float = 0f,
    isActive: Boolean,
    pageProgress: Float = 1f,
    sidebarProgress: Float = pageProgress,
) {
    val homeViewModel = koinViewModel<HomeViewModel>()
    val appSettingsViewModel = koinViewModel<AppSettingsViewModel>()
    val wallpapers = koinInject<AcgWallpaperManager>()
    val wallpaperOptions by wallpapers.options.collectAsState()
    val tabletEnabled by appSettingsViewModel.acgTabletModeEnabled.state.collectAsState()
    val pageScale by appSettingsViewModel.pageScale.state.collectAsState()
    val dailyWallpaper by appSettingsViewModel.acgDailyWallpaperEnabled.state.collectAsState()
    val context: Context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val controlState by homeViewModel.controlState.collectAsState()
    val uiState by homeViewModel.uiState.collectAsState()
    val profiles by homeViewModel.profiles.collectAsState()
    val profilesLoaded by homeViewModel.profilesLoaded.collectAsState()
    val recommendedProfile by homeViewModel.recommendedProfile.collectAsState()
    val hasEnabledProfile by homeViewModel.hasEnabledProfile.collectAsState(initial = false)
    val currentProfile by homeViewModel.currentProfile.collectAsState()
    val selectedServerName by homeViewModel.selectedServerName.collectAsState()
    val selectedServerPing by homeViewModel.selectedServerPing.collectAsState()
    val ipMonitoringState by homeViewModel.ipMonitoringState.collectAsState()
    val trafficNow by homeViewModel.trafficNow.collectAsState()
    val proxyMode by homeViewModel.proxyMode.collectAsState()
    val tunnelMode by homeViewModel.tunnelMode.collectAsState()
    val runtimeSnapshot by homeViewModel.runtimeSnapshot.collectAsState()
    val themeMode by appSettingsViewModel.themeMode.state.collectAsState()
    val acgDailyQuoteApiEnabled by appSettingsViewModel.acgDailyQuoteEnabled.state.collectAsState()
    val acgCustomQuoteEnabled by appSettingsViewModel.acgCustomQuoteEnabled.state.collectAsState()
    val acgDailyQuote by appSettingsViewModel.acgDailyQuote.state.collectAsState()
    val acgDailyQuoteAuthor by appSettingsViewModel.acgDailyQuoteAuthor.state.collectAsState()
    val isRefreshingDailyAcgQuote by appSettingsViewModel.isRefreshingDailyAcgQuote.collectAsState()
    val sidebarExpanded by appSettingsViewModel.acgSidebarExpanded.state.collectAsState()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(acgDailyQuoteApiEnabled, acgCustomQuoteEnabled) {
        if (acgDailyQuoteApiEnabled || acgCustomQuoteEnabled) {
            appSettingsViewModel.refreshDailyAcgQuoteIfNeeded()
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.refreshProxyMode()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            context.toast(it, Toast.LENGTH_LONG)
            homeViewModel.consumeError()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            context.toast(it, Toast.LENGTH_SHORT)
            homeViewModel.consumeMessage()
        }
    }

    LaunchedEffect(isActive) {
        homeViewModel.setHomeScreenActive(isActive)
        if (isActive) {
            homeViewModel.reconcileRuntimeState()
            homeViewModel.refreshProxyMode()
        }
    }

    DisposableEffect(homeViewModel) {
        onDispose {
            homeViewModel.setHomeScreenActive(false)
        }
    }

    DisposableEffect(lifecycleOwner, homeViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.reconcileRuntimeState()
                homeViewModel.refreshProxyMode()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val visualControlState = controlState
    val now by produceState(
        initialValue = System.currentTimeMillis(),
        visualControlState,
        runtimeSnapshot.startedAt,
    ) {
        if (visualControlState != HomeProxyControlState.Running || runtimeSnapshot.startedAt == null) {
            value = System.currentTimeMillis()
            return@produceState
        }
        PollingTimers.ticks(PollingTimerSpecs.MoeElapsedClock).collect {
            value = System.currentTimeMillis()
        }
    }
    val startedAt = runtimeSnapshot.startedAt
    val isRunning = visualControlState == HomeProxyControlState.Running
    val elapsedMillis = if (isRunning && startedAt != null) {
        (now - startedAt).coerceAtLeast(0L)
    } else {
        0L
    }
    val durationPair = remember(elapsedMillis, isRunning) {
        if (isRunning) {
            formatAcgDuration(elapsedMillis)
        } else {
            AcgDurationPair()
        }
    }
    val trafficData = remember(trafficNow, isRunning) {
        if (isRunning) TrafficData.from(trafficNow) else TrafficData.zero
    }
    val hasDisplayableExternalIp = ipMonitoringState.displayableExternalIp() != null
    val systemDark = isSystemInDarkTheme()
    val isDarkHomeSurface = when (themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.Auto -> systemDark
    }
    val wallpaperBackdrop = rememberYumeMiuixLayerBackdrop()
    val contentSurface = if (isDarkHomeSurface) {
        YumeMiuixTheme.colorScheme.surface
    } else {
        YumeMiuixTheme.colorScheme.background
    }
    val heroBlendColor = contentSurface
    val handlePageChange = LocalHandlePageChange.current
    val sidebarIcons = remember(handlePageChange, homeViewModel) {
        listOf(
            AcgSidebarIconItem(AppMd3Icons.Action.SpeedTest, MLang.AppSettings.Wallpaper.TestNode) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                homeViewModel.testCurrentNodeDelay()
            },
            AcgSidebarIconItem(ShellIcons.OpenProxy, MLang.Component.BottomBar.Proxy) { handlePageChange(1) },
            AcgSidebarIconItem(AppMd3Icons.Shell.OpenProfileConfig, MLang.Component.BottomBar.Config) { handlePageChange(2) },
            AcgSidebarIconItem(ShellIcons.OpenSettings, MLang.Component.BottomBar.Setting) { handlePageChange(3) },
        )
    }
    val dailyQuoteEnabled = acgDailyQuoteApiEnabled || acgCustomQuoteEnabled
    val quote = if (dailyQuoteEnabled) {
        AcgQuote(
            text = acgDailyQuote.ifBlank { MLang.AppSettings.Experimental.AcgQuoteDefault },
            author = acgDailyQuoteAuthor,
        )
    } else {
        AcgQuote(
            text = MLang.AppSettings.Experimental.AcgQuoteDefault,
            author = MLang.AppSettings.Experimental.AcgQuoteAuthorDefault,
        )
    }
    val animatedSidebarToggleProgress by animateFloatAsState(
        targetValue = if (sidebarExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (sidebarExpanded) 420 else 320,
            easing = if (sidebarExpanded) AppMotion.EmphasizedDecelerate else AppMotion.EmphasizedAccelerate,
        ),
        label = "acg_sidebar_toggle",
    )

    val handleProxyAction: () -> Unit = {
        if (!hasEnabledProfile || recommendedProfile == null) {
            context.toast(MLang.ProfilesVM.Error.ProfileNotExist, Toast.LENGTH_SHORT)
        } else if (visualControlState == HomeProxyControlState.Idle) {
            recommendedProfile?.let { profile ->
                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                homeViewModel.startProxy(profileId = profile.uuid.toString(), mode = null)
            }
        } else if (visualControlState == HomeProxyControlState.Running) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
            scope.launch {
                homeViewModel.stopProxy()
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The window breakpoint must not flip when the app's "100%" slider stores
        // a fractional scale such as 1.001. Keep enough usable width for the rail.
        val windowWidthDp = kotlin.math.round(maxWidth.value * pageScale).toInt()
        val tabletMode = tabletEnabled && maxWidth >= 480.dp &&
            (windowWidthDp >= 600 || maxWidth / maxHeight >= 0.85f)
        val wallpaperProfile = if (tabletMode) WallpaperProfile.Tablet else WallpaperProfile.Portrait
        val wallpaperOption = wallpaperOptions.getValue(wallpaperProfile)
        val activeWallpaperUri = wallpapers.displayUri(wallpaperProfile, dailyWallpaper)
        val useProfileCrop = wallpapers.fileUri(wallpaperOption.staticImage) != null
        val showingDailyImage = dailyWallpaper && wallpapers.fileUri(wallpaperOption.current) != null && wallpaperOption.current != wallpaperOption.staticImage
        val legacyCrop = wallpaperProfile == WallpaperProfile.Portrait
        val activeWallpaperZoom = if (showingDailyImage) 1f else if (useProfileCrop) wallpaperOption.zoom else if (legacyCrop) wallpaperZoom else 1f
        val activeWallpaperBiasX = if (showingDailyImage) 0f else if (useProfileCrop) wallpaperOption.biasX else if (legacyCrop) wallpaperBiasX else 0f
        val activeWallpaperBiasY = if (showingDailyImage) 0f else if (useProfileCrop) wallpaperOption.biasY else if (legacyCrop) wallpaperBiasY else 0f
        LaunchedEffect(wallpaperProfile, dailyWallpaper, isActive, wallpaperOption.apiEnabled, wallpaperOption.apiUrl, wallpaperOption.poolEnabled, wallpaperOption.pool) {
            while (isActive && dailyWallpaper) {
                wallpapers.refresh(wallpaperProfile)
                kotlinx.coroutines.delay(60_000L)
            }
        }
        val sidebarWidth = if (tabletMode) (maxWidth * 0.28f).coerceIn(208.dp, 260.dp)
            else (maxWidth * AcgUi.Sidebar.Fraction).coerceAtLeast(88.dp)
        val contentStart = (sidebarWidth - AcgUi.Sidebar.contentOverlap).coerceAtLeast(UiDp.dp0)
        val collapsedVisibleWidth = AcgUi.Sidebar.collapsedVisibleWidth
        val heroHeight = maxHeight * 0.66f
        val heroMaxWidth = maxWidth - (AcgUi.Hero.containerHorizontalInset * 2)
        val heroBottomBlendSolidHeight = 56.dp
        val heroBottomBlendGradientHeight = 90.dp
        val heroBottomBlendTotalHeight = heroBottomBlendSolidHeight + heroBottomBlendGradientHeight
        val clampedPageProgress = pageProgress.coerceIn(0f, 1f)
        val clampedSidebarProgress = sidebarProgress.coerceIn(0f, 1f)
        val effectiveSidebarProgress = clampedSidebarProgress * animatedSidebarToggleProgress
        val sidebarVisibleWidth =
            lerpDp(collapsedVisibleWidth, contentStart, effectiveSidebarProgress)
        val contentPanelStart = lerpDp(UiDp.dp0, contentStart, effectiveSidebarProgress)
        val sidebarOffset = lerpDp((-56).dp, UiDp.dp0, effectiveSidebarProgress)
        val sidebarAlpha = lerpFloat(0.78f, 1f, effectiveSidebarProgress) * clampedPageProgress
        val contentCorner = lerpDp(UiDp.dp0, UiDp.dp30, effectiveSidebarProgress)
        val swipePressProgress = FastOutSlowInEasing.transform(1f - clampedPageProgress)
        val heroImageScale = if (clampedPageProgress >= 0.999f) {
            1f
        } else {
            lerpFloat(1f, 0.972f, swipePressProgress)
        }
        val sidebarBlurReady by remember(
            effectiveSidebarProgress
        ) {
            derivedStateOf {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        effectiveSidebarProgress > 0.03f
            }
        }

        AcgWallpaperBackground(
            wallpaperUri = activeWallpaperUri,
            wallpaperZoom = activeWallpaperZoom,
            wallpaperBiasX = activeWallpaperBiasX,
            wallpaperBiasY = activeWallpaperBiasY,
            qualityMode = AcgWallpaperQualityMode.BackgroundBlur,
            modifier = Modifier
                .matchParentSize()
                .yumeMiuixLayerBackdrop(wallpaperBackdrop),
        )

        AcgSidebarDecoration(
            backdrop = wallpaperBackdrop,
            blurEnabled = sidebarBlurReady,
            blurProgress = effectiveSidebarProgress,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(sidebarWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = with(density) { sidebarOffset.toPx() }
                    alpha = sidebarAlpha
                },
            content = {
                AcgSidebarContent(
                    topValue = durationPair.top,
                    bottomValue = durationPair.bottom,
                    proxyMode = proxyMode,
                    icons = sidebarIcons,
                    visibleWidth = sidebarVisibleWidth,
                    tabletMode = tabletMode,
                    duration = java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", elapsedMillis / 3_600_000, elapsedMillis / 60_000 % 60, elapsedMillis / 1_000 % 60),
                )
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = contentPanelStart)
                .graphicsLayer {
                    shape =
                        RoundedCornerShape(topStart = contentCorner, bottomStart = contentCorner)
                    clip = true
                }
                .background(contentSurface),
        ) {
            var actionsHeight by remember { mutableStateOf(0.dp) }
            var runtimeInfoHeight by remember { mutableStateOf(0.dp) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop, bottom = actionsHeight + 12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AcgUi.Hero.containerHorizontalInset)
                        .background(contentSurface)
                        .pointerInput(sidebarExpanded) {
                            detectTapGestures(
                                onDoubleTap = {
                                    appSettingsViewModel.onAcgSidebarExpandedChange(!sidebarExpanded)
                                },
                            )
                        }
                        .graphicsLayer {
                            shape = AcgUi.Shape.hero
                            clip = true
                        }
                ) {
                    AcgWallpaperBackground(
                        wallpaperUri = activeWallpaperUri,
                        wallpaperZoom = activeWallpaperZoom,
                        wallpaperBiasX = activeWallpaperBiasX,
                        wallpaperBiasY = activeWallpaperBiasY,
                        qualityMode = AcgWallpaperQualityMode.Foreground,
                        stableRequestWidth = heroMaxWidth,
                        stableRequestHeight = heroHeight,
                        foregroundMotionScale = heroImageScale,
                        modifier = Modifier.matchParentSize(),
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(maxOf(heroBottomBlendTotalHeight, runtimeInfoHeight + 48.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.03f to heroBlendColor.copy(alpha = 0.03f),
                                        0.08f to heroBlendColor.copy(alpha = 0.10f),
                                        0.14f to heroBlendColor.copy(alpha = 0.22f),
                                        0.22f to heroBlendColor.copy(alpha = 0.42f),
                                        0.32f to heroBlendColor.copy(alpha = 0.60f),
                                        0.44f to heroBlendColor.copy(alpha = 0.70f),
                                        0.58f to heroBlendColor.copy(alpha = 0.80f),
                                        0.74f to heroBlendColor.copy(alpha = 0.90f),
                                        0.88f to heroBlendColor.copy(alpha = 0.97f),
                                        0.96f to heroBlendColor.copy(alpha = 0.995f),
                                        1.0f to contentSurface,
                                    )
                                )
                            )
                    )

                    // Measure the information as one flow. The hero may grow in short windows;
                    // no row is positioned independently over another row.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = heroHeight)
                            .padding(
                                start = AcgUi.Hero.contentHorizontalInset,
                                end = AcgUi.Hero.contentHorizontalInset,
                                top = 24.dp,
                                bottom = AcgUi.Hero.trafficBottomInset,
                            ),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        AnimatedVisibility(
                            visible = isRunning || hasDisplayableExternalIp,
                            modifier = Modifier.onSizeChanged { size ->
                                runtimeInfoHeight = with(density) { size.height.toDp() }
                            },
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(AcgUi.Hero.runtimeInfoTopGap),
                            ) {
                                AcgTrafficStrip(
                                    downloadSpeed = trafficData.download,
                                    uploadSpeed = trafficData.upload,
                                )
                                AcgHomeInfoPanel(
                                    serverName = selectedServerName.takeIf { isRunning },
                                    serverPing = selectedServerPing.takeIf { isRunning },
                                    ipMonitoringState = ipMonitoringState,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AnimatedVisibility(visible = isRunning) {
                                    AcgProfileModeBadge(
                                        profileName = currentProfile?.name,
                                        tunnelMode = tunnelMode,
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AcgUi.Hero.containerHorizontalInset + AcgUi.Hero.contentHorizontalInset,
                            end = AcgUi.Hero.containerHorizontalInset + AcgUi.Hero.contentHorizontalInset,
                            top = AcgUi.Hero.belowHeroTopGap,
                        ),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(AcgUi.Hero.belowHeroContentGap),
                ) {
                    AcgQuoteText(
                        quote = quote,
                        color = YumeMiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size -> actionsHeight = with(density) { size.height.toDp() } }
                    .fillMaxWidth()
                    .padding(
                        start = UiDp.dp12,
                        end = UiDp.dp12,
                        bottom = mainInnerPadding.calculateBottomPadding() + AcgUi.Button.bottomInset,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = isRefreshingDailyAcgQuote,
                        contentAlignment = Alignment.Center,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                                    scaleIn(
                                        initialScale = 0.86f,
                                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    )) togetherWith
                                    (fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing)) +
                                            scaleOut(
                                                targetScale = 0.86f,
                                                animationSpec = tween(160, easing = FastOutSlowInEasing),
                                            )) using
                                    SizeTransform(
                                        clip = false,
                                        sizeAnimationSpec = { _, _ ->
                                            tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                        },
                                    )
                        },
                        label = "acg_quote_refresh_button_content",
                    ) { refreshing ->
                        if (refreshing) {
                            Box(
                                modifier = Modifier.size(42.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Md3ELoading()
                            }
                        } else {
                            AcgInlineIconButton(
                                icon = AppMd3Icons.Action.Refresh,
                                contentDescription = MLang.Home.Acg.RefreshQuote,
                                enabled = dailyQuoteEnabled,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    appSettingsViewModel.refreshDailyAcgQuoteIfNeeded(force = true)
                                },
                            )
                        }
                    }
                }
                AcgLaunchButton(
                    controlState = visualControlState,
                    enabled = profilesLoaded && profiles.isNotEmpty() && visualControlState.canInteract,
                    onClick = handleProxyAction,
                )
            }
        }
    }
}

@Composable
private fun AcgWallpaperBackground(
    wallpaperUri: String,
    modifier: Modifier = Modifier,
    wallpaperZoom: Float = 1f,
    wallpaperBiasX: Float = 0f,
    wallpaperBiasY: Float = 0f,
    qualityMode: AcgWallpaperQualityMode = AcgWallpaperQualityMode.Foreground,
    stableRequestWidth: androidx.compose.ui.unit.Dp? = null,
    stableRequestHeight: androidx.compose.ui.unit.Dp? = null,
    foregroundMotionScale: Float = 1f,
) {
    val clampedZoom = wallpaperZoom.coerceIn(1f, 5f)
    val model: String = wallpaperUri.ifBlank { "file:///android_asset/wallpaper.jpg" }
    val context: Context = LocalContext.current
    val density = LocalDensity.current

    val imageBounds by produceState<Pair<Int, Int>?>(initialValue = null, model) {
        if (model.startsWith("file:///android_asset/")) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(model.toUri())?.use { input ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.outWidth to options.outHeight
                    } else {
                        null
                    }
                }
            }.getOrNull()
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val containerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        val sampleMultiplier = if (qualityMode == AcgWallpaperQualityMode.BackgroundBlur) 0.75f else 1.35f
        val stableWidthPx = stableRequestWidth?.let { with(density) { it.toPx() } } ?: 0f
        val stableHeightPx = stableRequestHeight?.let { with(density) { it.toPx() } } ?: 0f
        val requestBaseWidthPx = maxOf(containerWidthPx, stableWidthPx)
        val requestBaseHeightPx = maxOf(containerHeightPx, stableHeightPx)
        val targetRequestWidth = kotlin.math.ceil(requestBaseWidthPx * sampleMultiplier).toInt().coerceAtLeast(1)
        val targetRequestHeight = kotlin.math.ceil(requestBaseHeightPx * sampleMultiplier).toInt().coerceAtLeast(1)
        val request = remember(context, model, targetRequestWidth, targetRequestHeight) {
            ImageRequest(context, model) {
                crossfade(true)
                size(targetRequestWidth, targetRequestHeight)
                // Keep the source aspect ratio. EXACTLY pre-crops the bitmap and loses
                // pixels needed when the viewport changes between portrait and landscape.
                precision(Precision.LESS_PIXELS)
            }
        }
        val painter = rememberAsyncImagePainter(request = request)

        val intrinsic = painter.intrinsicSize
        val imageWidthPx = intrinsic.width.takeIf { it > 0f && it.isFinite() }
            ?: imageBounds?.first?.toFloat()
        val imageHeightPx = intrinsic.height.takeIf { it > 0f && it.isFinite() }
            ?: imageBounds?.second?.toFloat()
        val viewportLayout = calculateWallpaperViewportLayout(
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
            imageWidthPx = imageWidthPx,
            imageHeightPx = imageHeightPx,
            zoom = clampedZoom,
            biasX = wallpaperBiasX,
            biasY = wallpaperBiasY,
        )
        val foregroundScale = foregroundMotionScale.coerceIn(0.96f, 1.04f)
        val imageOverscanScale = 1.04f
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(viewportLayout.biasX, viewportLayout.biasY),
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = imageOverscanScale * foregroundScale
                    scaleY = imageOverscanScale * foregroundScale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    translationY = 2f
                },
        )
    }
}
