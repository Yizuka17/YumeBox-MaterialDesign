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

package com.github.yumelira.yumebox


import com.github.yumelira.yumebox.presentation.theme.UiDp
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.common.util.openUrl
import com.github.yumelira.yumebox.data.store.LinkOpenMode
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.screen.ProxyPager
import com.github.yumelira.yumebox.presentation.viewmodel.FeatureViewModel
import com.github.yumelira.yumebox.presentation.webview.WebViewUtils.getPanelUrl
import com.github.yumelira.yumebox.screen.acg.AcgHomePage
import com.github.yumelira.yumebox.screen.acg.calculateHomeVisibility
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import com.github.yumelira.yumebox.screen.home.HomePager
import com.github.yumelira.yumebox.screen.profiles.ProfilesPager
import com.github.yumelira.yumebox.screen.settings.AppSettingsViewModel
import com.github.yumelira.yumebox.screen.settings.SettingPager
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ProvidersScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.flow.collect
import org.koin.androidx.compose.koinViewModel

@Composable
@Destination<RootGraph>
fun MainScreen(
    navigator: DestinationsNavigator,
    initialPage: Int = 0,
) {
    val activity = LocalActivity.current
    val initialMainPage = initialPage.coerceIn(0, 3)
    val pagerState = rememberPagerState(initialPage = initialMainPage, pageCount = { 4 })
    val mainPagerState = rememberMainPagerState(pagerState)
    val profilesListState = rememberRetainedLazyListState("main_profiles")
    val settingsListState = rememberRetainedLazyListState("main_settings")

    val appSettingsViewModel = koinViewModel<AppSettingsViewModel>()
    val featureViewModel = koinViewModel<FeatureViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val isProxyRunning by homeViewModel.isRunning.collectAsState()
    val bottomBarAutoHideEnabled by appSettingsViewModel.bottomBarAutoHide.state.collectAsState()
    val bottomBarUseLegacyStyle by appSettingsViewModel.bottomBarUseLegacyStyle.state.collectAsState()
    val acgMainUiEnabled by appSettingsViewModel.acgMainUiEnabled.state.collectAsState()
    val acgWallpaperUri by appSettingsViewModel.acgWallpaperUri.state.collectAsState()
    val acgWallpaperZoom by appSettingsViewModel.acgWallpaperZoom.state.collectAsState()
    val acgWallpaperBiasX by appSettingsViewModel.acgWallpaperBiasX.state.collectAsState()
    val acgWallpaperBiasY by appSettingsViewModel.acgWallpaperBiasY.state.collectAsState()
    val selectedPanelType by featureViewModel.selectedPanelType.state.collectAsState()
    val panelOpenMode by featureViewModel.panelOpenMode.state.collectAsState()
    val bottomBarScrollBehavior = rememberBottomBarScrollBehavior(autoHideEnabled = bottomBarAutoHideEnabled)
    val pagerFlingBehavior = rememberMainPagerFlingBehavior(mainPagerState.pagerState)
    var settledMainPage by remember { mutableIntStateOf(initialMainPage) }
    val homeVisibility by remember(mainPagerState) {
        derivedStateOf {
            calculateHomeVisibility(
                currentPage = mainPagerState.pagerState.currentPage,
                currentPageOffsetFraction = mainPagerState.pagerState.currentPageOffsetFraction,
            )
        }
    }
    val acgBottomBarVisible by remember(acgMainUiEnabled, settledMainPage, mainPagerState.selectedPage) {
        derivedStateOf {
            if (!acgMainUiEnabled) {
                true
            } else if (mainPagerState.selectedPage == 0) {
                false
            } else {
                settledMainPage != 0
            }
        }
    }
    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    LaunchedEffect(mainPagerState.pagerState.currentPage, mainPagerState.pagerState.isScrollInProgress) {
        if (!mainPagerState.pagerState.isScrollInProgress) {
            settledMainPage = mainPagerState.pagerState.currentPage
        }
    }

    LaunchedEffect(settledMainPage, acgMainUiEnabled, bottomBarScrollBehavior) {
        if (!acgMainUiEnabled || settledMainPage != 0) {
            bottomBarScrollBehavior.forceShowBottomBar()
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        homeViewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(homeViewModel) {
        homeViewModel.vpnPrepareIntent.collect { intent ->
            vpnPermissionLauncher.launch(intent)
        }
    }

    val handlePageChange: (Int) -> Unit = remember(mainPagerState) {
        { targetPage -> mainPagerState.animateToPage(targetPage) }
    }

    BackHandler(enabled = mainPagerState.selectedPage != 0) {
        handlePageChange(0)
    }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalTopBarBackEnabled provides false,
        LocalPagerState provides mainPagerState.pagerState,
        LocalMainPagerState provides mainPagerState,
        LocalHandlePageChange provides handlePageChange,
        LocalBottomBarScrollBehavior provides bottomBarScrollBehavior,
        LocalBottomBarUseLegacyStyle provides bottomBarUseLegacyStyle,
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
        ) { innerPadding ->
            Box(Modifier.fillMaxSize()) {
                val layoutDirection = LocalLayoutDirection.current
                val visibleBottomBarReservedHeight = rememberBottomBarReservedHeight(
                    useLegacyStyle = bottomBarUseLegacyStyle,
                )
                val mainStartPadding = WindowInsets.systemBars.asPaddingValues().calculateStartPadding(layoutDirection)
                val mainEndPadding = WindowInsets.systemBars.asPaddingValues().calculateEndPadding(layoutDirection)
                fun mainInnerPaddingForPage(page: Int): PaddingValues {
                    val pageBottomBarReservedHeight = if (acgMainUiEnabled && page == 0) {
                        UiDp.dp0
                    } else {
                        visibleBottomBarReservedHeight
                    }
                    return PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding() + pageBottomBarReservedHeight,
                        start = mainStartPadding,
                        end = mainEndPadding,
                    )
                }
                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = mainPagerState.pagerState,
                    beyondViewportPageCount = 3,
                    flingBehavior = pagerFlingBehavior,
                    userScrollEnabled = true,
                    overscrollEffect = null,
                    pageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(
                        state = mainPagerState.pagerState,
                        orientation = Orientation.Horizontal,
                    ),
                ) { page ->
                    MainRootPageContent(
                        page = page,
                        mainInnerPadding = mainInnerPaddingForPage(page),
                        acgMainUiEnabled = acgMainUiEnabled,
                        acgWallpaperUri = acgWallpaperUri,
                        acgWallpaperZoom = acgWallpaperZoom,
                        acgWallpaperBiasX = acgWallpaperBiasX,
                        acgWallpaperBiasY = acgWallpaperBiasY,
                        navigator = navigator,
                        activity = activity,
                        selectedPanelType = selectedPanelType,
                        panelOpenMode = panelOpenMode,
                        homePageProgress = homeVisibility,
                        selectedPage = settledMainPage,
                        isProxyRunning = isProxyRunning,
                        onProxyStartRequested = homeViewModel::startCurrentOrRecommendedProxy,
                        profilesListState = profilesListState,
                        settingsListState = settingsListState,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    BottomBarContent(
                        isVisible = acgBottomBarVisible,
                        useLegacyStyle = bottomBarUseLegacyStyle,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainRootPageContent(
    page: Int,
    mainInnerPadding: PaddingValues,
    acgMainUiEnabled: Boolean,
    acgWallpaperUri: String,
    acgWallpaperZoom: Float,
    acgWallpaperBiasX: Float,
    acgWallpaperBiasY: Float,
    navigator: DestinationsNavigator,
    activity: Activity?,
    selectedPanelType: Int,
    panelOpenMode: LinkOpenMode,
    homePageProgress: Float,
    selectedPage: Int,
    isProxyRunning: Boolean,
    onProxyStartRequested: () -> Unit,
    profilesListState: LazyListState,
    settingsListState: LazyListState,
) {
    when (page) {
        0 -> {
            if (acgMainUiEnabled) {
                AcgHomePage(
                    mainInnerPadding = mainInnerPadding,
                    wallpaperUri = acgWallpaperUri,
                    wallpaperZoom = acgWallpaperZoom,
                    wallpaperBiasX = acgWallpaperBiasX,
                    wallpaperBiasY = acgWallpaperBiasY,
                    isActive = selectedPage == 0,
                    pageProgress = if (selectedPage == 0 || homePageProgress < 0.999f) homePageProgress else 1f,
                )
            } else {
                HomePager(
                    mainInnerPadding = mainInnerPadding,
                    isActive = selectedPage == 0,
                )
            }
        }

        1 -> ProxyPager(
            mainInnerPadding = mainInnerPadding,
            onNavigateToProviders = {
                navigator.navigate(ProvidersScreenDestination) {
                    launchSingleTop = true
                }
            },
            onOpenPanel = onOpenPanel@{
                val context = activity ?: return@onOpenPanel
                val panelUrl = getPanelUrl(selectedPanelType)
                if (panelUrl.isEmpty()) return@onOpenPanel
                when (panelOpenMode) {
                    LinkOpenMode.IN_APP -> WebViewActivity.start(context, panelUrl)
                    LinkOpenMode.EXTERNAL_BROWSER -> openUrl(context, panelUrl)
                }
            },
            isPageActive = selectedPage == 1,
            isProxyRunning = isProxyRunning,
            onProxyStartRequested = onProxyStartRequested,
        )

        2 -> ProfilesPager(
            mainInnerPadding = mainInnerPadding,
            lazyListState = profilesListState,
        )
        3 -> SettingPager(
            mainInnerPadding = mainInnerPadding,
            lazyListState = settingsListState,
        )
    }
}
