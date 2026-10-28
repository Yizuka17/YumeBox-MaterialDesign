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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.github.yumelira.yumebox.common.util.DeviceUtil
import com.github.yumelira.yumebox.data.store.LinkOpenMode
import com.github.yumelira.yumebox.data.store.SUPPORTED_HEALTH_CHECK_CONCURRENCY
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.component.md3.YumeMd3DropdownPreference
import com.github.yumelira.yumebox.presentation.viewmodel.FeatureViewModel
import com.github.yumelira.yumebox.presentation.viewmodel.SubStoreResourceDownloadStatus
import com.github.yumelira.yumebox.presentation.viewmodel.SubStoreResourceProgressState
import com.github.yumelira.yumebox.substore.model.AutoCloseMode
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel

@Composable
fun FeatureContent(
    onOpenExternalUrl: (String) -> Unit,
    onOpenInAppUrl: (String) -> Unit,
) {
    val viewModel = koinViewModel<FeatureViewModel>()
    val isServiceRunning by viewModel.serviceRunningState.collectAsState()
    val allowLanAccess by viewModel.allowLanAccess.state.collectAsState()
    val frontendPort by viewModel.frontendPort.state.collectAsState()
    val backendPort by viewModel.backendPort.state.collectAsState()
    val autoCloseMode by viewModel.autoCloseMode.collectAsState()

    val host = "127.0.0.1"
    val frontendUrl = "http://${host}:${frontendPort}"
    val backendUrl = "http://${host}:${backendPort}"
    val subStoreUrl = "${frontendUrl}/subs?api=${backendUrl}"

    val isDownloadingSubStoreFrontend by viewModel.isDownloadingSubStoreFrontend.collectAsState()
    val isDownloadingSubStoreBackend by viewModel.isDownloadingSubStoreBackend.collectAsState()
    val isDownloadingSubStoreResources by viewModel.isDownloadingSubStoreResources.collectAsState()
    val subStoreResourceProgressItems by viewModel.subStoreResourceProgressItems.collectAsState()
    val isJavetLoaded by viewModel.isJavetLoaded.collectAsState()
    val selectedPanelType by viewModel.selectedPanelType.state.collectAsState()
    val panelOpenMode by viewModel.panelOpenMode.state.collectAsState()
    val healthCheckConcurrency by viewModel.healthCheckConcurrency.state.collectAsState()
    val showSubStoreResourceDownloadSheet = remember { mutableStateOf(false) }

    val panelDisplayNames = listOf("Zashboard", "MetaCubeXD", "Yacd")

    LaunchedEffect(Unit) {
        viewModel.initializeSubStoreStatus()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopBar(title = MLang.Feature.Title)
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Title(MLang.Feature.ServiceStatus.Section)
                Card {
                    val autoCloseItems = AutoCloseMode.entries.map { it.getDisplayName() }
                    val autoCloseValues = AutoCloseMode.entries

                    EnumSelector(
                        title = MLang.Feature.ServiceStatus.SwitchStartSubStore,
                        summary = MLang.Feature.ServiceStatus.AutoCloseModeSummary,
                        currentValue = autoCloseMode,
                        items = autoCloseItems,
                        values = autoCloseValues,
                        onValueChange = { mode ->
                            viewModel.setAutoCloseMode(mode)
                            if (mode != AutoCloseMode.DISABLED && !isServiceRunning) {
                                viewModel.startService()
                            } else if (mode == AutoCloseMode.DISABLED && isServiceRunning) {
                                viewModel.stopService()
                            }
                        },
                    )
                    PreferenceSwitchItem(
                        title = MLang.Feature.ServiceStatus.AllowLan,
                        summary = MLang.Feature.ServiceStatus.AllowLanSummary,
                        checked = allowLanAccess,
                        onCheckedChange = { viewModel.setAllowLanAccess(it) },
                    )
                    PreferenceArrowItem(
                        title = "Sub-Store",
                        summary = subStoreUrl,
                        enabled = !DeviceUtil.is32BitDevice() && isServiceRunning,
                        onClick = {
                            if (!isServiceRunning) return@PreferenceArrowItem
                            when (panelOpenMode) {
                                LinkOpenMode.IN_APP -> onOpenInAppUrl(subStoreUrl)
                                LinkOpenMode.EXTERNAL_BROWSER -> onOpenExternalUrl(subStoreUrl)
                            }
                        }
                    )
                }
            }

            item {
                val currentPanelName = panelDisplayNames.getOrElse(selectedPanelType) {
                    MLang.Feature.Panel.Unknown
                }
                val panelUrl = panelUrlFor(selectedPanelType)
                val panelOpenModeItems = listOf(
                    MLang.ProfilesPage.LinkSettings.OpenModeInApp,
                    MLang.ProfilesPage.LinkSettings.OpenModeExternal,
                )
                val panelOpenModeIndex = when (panelOpenMode) {
                    LinkOpenMode.IN_APP -> 0
                    LinkOpenMode.EXTERNAL_BROWSER -> 1
                }

                Title(MLang.Feature.Panel.Section)
                Card {
                    val safeSelectedPanelType = selectedPanelType.coerceIn(0, panelDisplayNames.lastIndex)
                    YumeMd3DropdownPreference(
                        title = MLang.Feature.Panel.SelectPanel,
                        summary = null,
                        items = panelDisplayNames,
                        selectedIndex = safeSelectedPanelType,
                        onSelectedIndexChange = { viewModel.setSelectedPanelType(it) },
                    )

                    PreferenceListItem(
                        title = "URL",
                        summary = panelUrl.ifEmpty { currentPanelName },
                        onClick = {
                            if (panelUrl.isBlank()) return@PreferenceListItem
                            when (panelOpenMode) {
                                LinkOpenMode.IN_APP -> onOpenInAppUrl(panelUrl)
                                LinkOpenMode.EXTERNAL_BROWSER -> onOpenExternalUrl(panelUrl)
                            }
                        },
                    )

                    YumeMd3DropdownPreference(
                        title = MLang.ProfilesPage.LinkSettings.OpenMode,
                        summary = null,
                        items = panelOpenModeItems,
                        selectedIndex = panelOpenModeIndex,
                        onSelectedIndexChange = { index ->
                            viewModel.setPanelOpenMode(
                                when (index) {
                                    0 -> LinkOpenMode.IN_APP
                                    1 -> LinkOpenMode.EXTERNAL_BROWSER
                                    else -> LinkOpenMode.IN_APP
                                },
                            )
                        },
                    )
                }
            }

            item {
                Title(MLang.Feature.Node.Section)
                Card {
                    YumeMd3DropdownPreference(
                        title = MLang.Feature.Node.HealthCheckConcurrencyTitle,
                        summary = MLang.Feature.Node.HealthCheckConcurrencySummary.format(healthCheckConcurrency),
                        items = SUPPORTED_HEALTH_CHECK_CONCURRENCY.map(Int::toString),
                        selectedIndex = SUPPORTED_HEALTH_CHECK_CONCURRENCY.indexOf(healthCheckConcurrency)
                            .takeIf { it >= 0 } ?: 0,
                        onSelectedIndexChange = { index ->
                            SUPPORTED_HEALTH_CHECK_CONCURRENCY.getOrNull(index)
                                ?.let(viewModel::setHealthCheckConcurrency)
                        },
                    )
                }
            }

            item {
                Title(MLang.Feature.SubStore.SectionHint)
                Card {

                    PreferenceArrowItem(
                        title = MLang.Feature.SubStore.BuiltinEngine,
                        summary = if (isJavetLoaded) MLang.Feature.SubStore.JavetAvailable else MLang.Feature.SubStore.JavetNotReady,
                        onClick = viewModel::refreshEngineStatus,
                    )
                    PreferenceArrowItem(
                        title = MLang.Feature.SubStore.DownloadResources,
                        summary = MLang.Feature.SubStore.DownloadResourcesSummary,
                        onClick = { showSubStoreResourceDownloadSheet.value = true },
                        enabled = !isDownloadingSubStoreResources,
                    )
                }
            }
        }

        SubStoreResourceDownloadSheet(
            show = showSubStoreResourceDownloadSheet,
            viewModel = viewModel,
            items = subStoreResourceProgressItems,
            isDownloading = isDownloadingSubStoreResources,
            isDownloadingFrontend = isDownloadingSubStoreFrontend,
            isDownloadingBackend = isDownloadingSubStoreBackend,
        )
    }
}

@Composable
private fun SubStoreResourceDownloadSheet(
    show: androidx.compose.runtime.MutableState<Boolean>,
    viewModel: FeatureViewModel,
    items: List<SubStoreResourceProgressState>,
    isDownloading: Boolean,
    isDownloadingFrontend: Boolean,
    isDownloadingBackend: Boolean,
) {
    AppActionBottomSheet(
        show = show.value,
        title = MLang.Feature.SubStore.DownloadResources,
        onDismissRequest = {
            viewModel.cancelSubStoreResourceDownload()
            show.value = false
        },
        startAction = {
            AppBottomSheetCloseAction(
                onClick = {
                    viewModel.cancelSubStoreResourceDownload()
                    show.value = false
                },
            )
        },
        endAction = {
            AppBottomSheetConfirmAction(
                enabled = !isDownloading,
                onClick = { viewModel.downloadSubStoreAll() },
            )
        },
        content = {
            Column {
                PreferenceListItem(
                    title = MLang.Feature.SubStore.FrontendResourceTitle,
                    summary = MLang.Feature.SubStore.FrontendResourceSummary,
                )
                PreferenceListItem(
                    title = MLang.Feature.SubStore.BackendResourceTitle,
                    summary = MLang.Feature.SubStore.BackendResourceSummary,
                )
                if (isDownloading || items.isNotEmpty()) {
                    SubStoreResourceDownloadProgressContent(
                        items = items,
                        isUpdating = isDownloading || isDownloadingFrontend || isDownloadingBackend,
                    )
                }
            }
        },
    )

    DisposableEffect(show.value) {
        onDispose {
            if (show.value) {
                viewModel.cancelSubStoreResourceDownload()
            }
        }
    }
}

@Composable
private fun SubStoreResourceDownloadProgressContent(
    items: List<SubStoreResourceProgressState>,
    isUpdating: Boolean,
) {
    val showUpdatingHeader = isUpdating && items.any(SubStoreResourceProgressState::isInProgress)
    val spacing = com.github.yumelira.yumebox.presentation.theme.AppTheme.spacing
    Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.space16, vertical = spacing.space12),
        verticalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        if (showUpdatingHeader) {
            Row(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                Md3ELoading(modifier = androidx.compose.ui.Modifier.size(spacing.space32))
                Column {
                    Text(
                        text = MLang.Feature.SubStore.ProgressTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = MLang.Feature.SubStore.ProgressSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items.forEach { item ->
            SubStoreResourceDownloadProgressRow(item)
        }
    }
}

@Composable
private fun SubStoreResourceDownloadProgressRow(item: SubStoreResourceProgressState) {
    val spacing = com.github.yumelira.yumebox.presentation.theme.AppTheme.spacing
    Column(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.itemTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.statusLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val progress = when {
            item.status == SubStoreResourceDownloadStatus.Success -> 1f
            item.totalSize > 0L || item.progress > 0 -> item.progress / 100f
            else -> null
        }
        SubStoreResourceWavyProgressIndicator(
            progress = progress,
            completed = item.status == SubStoreResourceDownloadStatus.Success,
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        )
        Text(
            text = item.detailText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubStoreResourceWavyProgressIndicator(
    progress: Float?,
    completed: Boolean,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val normalizedProgress = progress?.coerceIn(0f, 1f)
    if (normalizedProgress == null) {
        Md3EIndeterminateLinearWavyProgressIndicator(modifier = modifier)
    } else {
        Md3ELinearWavyProgressIndicator(
            progress = if (completed) 1f else normalizedProgress,
            modifier = modifier,
        )
    }
}

private fun SubStoreResourceProgressState.isInProgress(): Boolean {
    return status == SubStoreResourceDownloadStatus.Pending ||
        status == SubStoreResourceDownloadStatus.Downloading
}

private fun SubStoreResourceProgressState.statusLabel(): String {
    return when (status) {
        SubStoreResourceDownloadStatus.Pending -> MLang.Feature.SubStore.StatusPending
        SubStoreResourceDownloadStatus.Downloading -> MLang.Feature.SubStore.StatusDownloading.format(progress)
        SubStoreResourceDownloadStatus.Success -> MLang.Feature.SubStore.StatusSuccess
        SubStoreResourceDownloadStatus.Failed -> MLang.Feature.SubStore.StatusFailed
    }
}

private fun SubStoreResourceProgressState.detailText(): String {
    return when {
        status == SubStoreResourceDownloadStatus.Success -> MLang.Feature.SubStore.ProgressSuccess
        status == SubStoreResourceDownloadStatus.Failed -> MLang.Feature.SubStore.ProgressFailed
        totalSize > 0L -> MLang.Feature.SubStore.ProgressDetail.format(
            com.github.yumelira.yumebox.common.util.formatBytes(currentSize),
            com.github.yumelira.yumebox.common.util.formatBytes(totalSize),
            speed,
        )
        currentSize > 0L -> MLang.Feature.SubStore.ProgressDetailUnknownTotal.format(
            com.github.yumelira.yumebox.common.util.formatBytes(currentSize),
            speed,
        )
        else -> MLang.Feature.SubStore.ProgressWaiting
    }
}

private fun panelUrlFor(panelType: Int): String {
    return when (panelType) {
        0 -> "https://board.zash.run.place"
        1 -> "https://metacubex.github.io/metacubexd"
        2 -> "https://yacd.haishan.me"
        else -> "https://board.zash.run.place"
    }
}
