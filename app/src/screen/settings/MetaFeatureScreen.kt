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



package com.github.yumelira.yumebox.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.core.model.GeoFileType
import com.github.yumelira.yumebox.core.model.GeoXItem
import com.github.yumelira.yumebox.core.model.geoXItems
import com.github.yumelira.yumebox.core.util.runtimeHomeDir
import com.github.yumelira.yumebox.data.controller.GeoXDataController
import com.github.yumelira.yumebox.data.controller.GeoXUpdateRecord
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.substore.util.DownloadProgress
import com.github.yumelira.yumebox.substore.util.SubStoreDownloadClient
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ConnectionScreenDestination
import com.ramcosta.composedestinations.generated.destinations.TrafficStatisticsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class GeoXDownloadProgressState(
    val itemTitle: String,
    val progress: Int = 0,
    val currentSize: Long = 0L,
    val totalSize: Long = -1L,
    val speed: String = "",
    val status: GeoXDownloadStatus = GeoXDownloadStatus.Pending,
)

private enum class GeoXDownloadStatus {
    Pending,
    Downloading,
    Validating,
    Success,
    Failed,
}

@Composable
@Destination<RootGraph>
fun MetaFeatureScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadClient: SubStoreDownloadClient = koinInject()
    val geoXDataController: GeoXDataController = koinInject()

    val showGeoXDownloadSheet = remember { mutableStateOf(false) }
    val showGeoXImportSheet = remember { mutableStateOf(false) }
    var geoXUpdateRecords by remember { mutableStateOf(geoXDataController.getGeoFileUpdateRecords()) }

    fun refreshGeoXUpdateRecords() {
        geoXUpdateRecords = geoXDataController.getGeoFileUpdateRecords()
    }

    Scaffold(
        topBar = {
            TopBar(
                title = MLang.MetaFeature.Title,
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenSectionGrid(
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Title(MLang.MetaFeature.Section.ConnectionAndTraffic)
                Card {
                    PreferenceArrowItem(
                        title = MLang.Connection.Title,
                        summary = MLang.Connection.Summary,
                        onClick = {
                            navigator.navigate(ConnectionScreenDestination) {
                                launchSingleTop = true
                            }
                        },
                    )
                    PreferenceArrowItem(
                        title = MLang.TrafficStatistics.Title,
                        summary = MLang.TrafficStatistics.EntrySummary,
                        onClick = {
                            navigator.navigate(TrafficStatisticsScreenDestination) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
            item {
                Title(MLang.MetaFeature.Section.CustomRouting)
                Card {
                    PreferenceArrowItem(
                        title = MLang.MetaFeature.CustomRouting.Title,
                        summary = MLang.MetaFeature.CustomRouting.Summary,
                        onClick = {
                            navigator.navigate(com.ramcosta.composedestinations.generated.destinations.CustomRoutingRouteDestination) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
            item {
                Title(MLang.MetaFeature.Section.GeoXUpdate)
                Card {
                    PreferenceArrowItem(
                        title = MLang.MetaFeature.GeoX.OnlineUpdateTitle,
                        summary = MLang.MetaFeature.GeoX.OnlineUpdateSummary,
                        onClick = {
                            refreshGeoXUpdateRecords()
                            showGeoXDownloadSheet.value = true
                        },
                    )
                    PreferenceArrowItem(
                        title = MLang.MetaFeature.GeoX.LocalUpdateTitle,
                        summary = MLang.MetaFeature.GeoX.LocalUpdateSummary,
                        onClick = {
                            refreshGeoXUpdateRecords()
                            showGeoXImportSheet.value = true
                        },
                    )
                }
            }
        }

        GeoXDownloadSheet(
            show = showGeoXDownloadSheet,
            context = context,
            scope = scope,
            downloadClient = downloadClient,
            geoXDataController = geoXDataController,
            updateRecords = geoXUpdateRecords,
            onUpdateRecordsChanged = { geoXUpdateRecords = it },
            onRefreshUpdateRecords = ::refreshGeoXUpdateRecords,
        )

        GeoXImportSheet(
            show = showGeoXImportSheet,
            context = context,
            scope = scope,
            geoXDataController = geoXDataController,
            updateRecords = geoXUpdateRecords,
            onUpdateRecordsChanged = { geoXUpdateRecords = it },
            onRefreshUpdateRecords = ::refreshGeoXUpdateRecords,
        )
    }
}

@Composable
private fun GeoXDownloadSheet(
    show: MutableState<Boolean>,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    downloadClient: SubStoreDownloadClient,
    geoXDataController: GeoXDataController,
    updateRecords: Map<String, GeoXUpdateRecord>,
    onUpdateRecordsChanged: (Map<String, GeoXUpdateRecord>) -> Unit,
    onRefreshUpdateRecords: () -> Unit,
) {
    val selectedItems = remember { mutableStateMapOf<GeoFileType, Boolean>() }
    val progressItems = remember { mutableStateMapOf<GeoFileType, GeoXDownloadProgressState>() }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadSession by remember { mutableIntStateOf(0) }

    fun resetDownloadState() {
        downloadSession++
        downloadJob?.cancel()
        downloadJob = null
        isDownloading = false
        selectedItems.clear()
        progressItems.clear()
    }

    fun cancelDownloadAndClose() {
        resetDownloadState()
        show.value = false
    }

    DisposableEffect(Unit) {
        onDispose { resetDownloadState() }
    }

    LaunchedEffect(show.value) {
        if (show.value) onRefreshUpdateRecords()
    }

    AppActionBottomSheet(
        show = show.value,
        title = MLang.MetaFeature.Download.DialogTitle,
        onDismissRequest = { cancelDownloadAndClose() },
        startAction = {
            AppBottomSheetCloseAction(
                onClick = { cancelDownloadAndClose() },
            )
        },
        endAction = {
            AppBottomSheetConfirmAction(
                enabled = selectedItems.values.any { it } && !isDownloading,
                onClick = {
                    val itemsToDownload = geoXItems.filter { selectedItems[it.type] == true }
                    if (itemsToDownload.isEmpty()) {
                        context.toast(MLang.MetaFeature.Download.SelectFiles)
                        return@AppBottomSheetConfirmAction
                    }
                    isDownloading = true
                    val currentSession = ++downloadSession
                    progressItems.clear()
                    itemsToDownload.forEach { item ->
                        progressItems[item.type] = GeoXDownloadProgressState(itemTitle = item.title)
                    }
                    downloadJob = downloadGeoXFiles(
                        context = context,
                        scope = scope,
                        downloadClient = downloadClient,
                        geoXDataController = geoXDataController,
                        items = itemsToDownload,
                        onProgress = { type, progress ->
                            if (currentSession == downloadSession) {
                                progressItems[type] = progress
                            }
                        },
                        onComplete = { successCount, totalCount ->
                            if (currentSession == downloadSession) {
                                isDownloading = false
                                downloadJob = null
                                onUpdateRecordsChanged(geoXDataController.getGeoFileUpdateRecords())
                                context.toast(MLang.MetaFeature.Download.DownloadComplete.format(successCount, totalCount))
                            }
                        },
                    )
                },
            )
        },
        content = {
            Column {
                geoXItems.forEach { item ->
                    PreferenceListItem(
                        title = item.title,
                        summary = item.lastUpdateSummary(updateRecords[item.fileName]),
                        endActions = {
                            Checkbox(
                                checked = selectedItems[item.type] ?: false,
                                enabled = !isDownloading,
                                onCheckedChange = { checked -> selectedItems[item.type] = checked }
                            )
                        },
                        onClick = if (isDownloading) {
                            null
                        } else {
                            { selectedItems[item.type] = !(selectedItems[item.type] ?: false) }
                        }
                    )
                }
                if (isDownloading || progressItems.isNotEmpty()) {
                    GeoXDownloadProgressContent(
                        items = geoXItems.mapNotNull { progressItems[it.type] },
                        isUpdating = isDownloading,
                    )
                }
            }
        })
}

@Composable
private fun GeoXImportSheet(
    show: MutableState<Boolean>,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    geoXDataController: GeoXDataController,
    updateRecords: Map<String, GeoXUpdateRecord>,
    onUpdateRecordsChanged: (Map<String, GeoXUpdateRecord>) -> Unit,
    onRefreshUpdateRecords: () -> Unit,
) {
    var importTargetItem by remember { mutableStateOf<GeoXItem?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    LaunchedEffect(show.value) {
        if (show.value) onRefreshUpdateRecords()
    }

    val importGeoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val targetItem = importTargetItem
        importTargetItem = null
        if (uri == null || targetItem == null) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            val imported = withContext(Dispatchers.IO) {
                geoXDataController.importGeoFile(targetItem.fileName, uri)
            }
            onUpdateRecordsChanged(geoXDataController.getGeoFileUpdateRecords())
            isImporting = false
            context.toast(
                if (imported) {
                    MLang.MetaFeature.Download.ImportSuccess.format(targetItem.title)
                } else {
                    MLang.MetaFeature.Download.ImportFailed.format(targetItem.title)
                }
            )
        }
    }

    AppActionBottomSheet(
        show = show.value,
        title = MLang.MetaFeature.Download.LocalDialogTitle,
        onDismissRequest = { if (!isImporting) show.value = false },
        startAction = {
            AppBottomSheetCloseAction(
                enabled = !isImporting,
                onClick = { show.value = false },
            )
        },
        content = {
            Column {
                geoXItems.forEach { item ->
                    PreferenceListItem(
                        title = item.title,
                        summary = item.lastUpdateSummary(updateRecords[item.fileName]),
                        enabled = !isImporting,
                        onClick = if (isImporting) {
                            null
                        } else {
                            {
                                importTargetItem = item
                                importGeoFileLauncher.launch("*/*")
                            }
                        },
                    )
                }
            }
        },
    )
}

private fun downloadGeoXFiles(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    downloadClient: SubStoreDownloadClient,
    geoXDataController: GeoXDataController,
    items: List<GeoXItem>,
    onProgress: (GeoFileType, GeoXDownloadProgressState) -> Unit,
    onComplete: (Int, Int) -> Unit,
): Job {
    return scope.launch {
        var successCount = 0
        try {
            withContext(Dispatchers.IO) {
                val runtimeHome = context.runtimeHomeDir
                runtimeHome.mkdirs()
                items.forEach { item ->
                    val targetFile = File(runtimeHome, item.fileName)
                    var latestState = GeoXDownloadProgressState(
                        itemTitle = item.title,
                        status = GeoXDownloadStatus.Downloading,
                    )
                    withContext(Dispatchers.Main.immediate) {
                        onProgress(item.type, latestState)
                    }
                    val downloaded = downloadClient.download(
                        url = item.url,
                        targetFile = targetFile,
                        onProgress = { progress ->
                            latestState = progress.toGeoXProgressState(item.title, GeoXDownloadStatus.Downloading)
                            scope.launch(Dispatchers.Main.immediate) {
                                onProgress(item.type, latestState)
                            }
                        },
                        validator = { file -> geoXDataController.isGeoFileUsable(file, item.fileName, deep = true) },
                    )
                    latestState = latestState.copy(
                        progress = 100,
                        status = GeoXDownloadStatus.Validating,
                    )
                    withContext(Dispatchers.Main.immediate) {
                        onProgress(item.type, latestState)
                    }
                    val promoted = downloaded && geoXDataController.promoteDownloadedGeoFile(item.fileName)
                    latestState = latestState.copy(
                        progress = if (promoted) 100 else latestState.progress,
                        status = if (promoted) GeoXDownloadStatus.Success else GeoXDownloadStatus.Failed,
                    )
                    withContext(Dispatchers.Main.immediate) {
                        onProgress(item.type, latestState)
                    }
                    if (promoted) {
                        successCount++
                    }
                }
            }
            onComplete(successCount, items.size)
        } catch (_: CancellationException) {
            // Download was canceled by leaving the sheet/page. Keep the existing usable files untouched.
        }
    }
}

private fun GeoXItem.lastUpdateSummary(record: GeoXUpdateRecord?): String {
    if (record == null) return MLang.MetaFeature.Download.LastUpdateNever
    return MLang.MetaFeature.Download.LastUpdate.format(
        record.source.displayName,
        formatGeoXUpdateTime(record.timestamp),
    )
}

private val com.github.yumelira.yumebox.data.controller.GeoXUpdateSource.displayName: String
    get() = when (name) {
        "Local" -> MLang.MetaFeature.Download.LastUpdateSourceLocal
        else -> MLang.MetaFeature.Download.LastUpdateSourceOnline
    }

private fun formatGeoXUpdateTime(timestamp: Long): String {
    return SimpleDateFormat("yy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun DownloadProgress.toGeoXProgressState(
    itemTitle: String,
    status: GeoXDownloadStatus,
): GeoXDownloadProgressState {
    return GeoXDownloadProgressState(
        itemTitle = itemTitle,
        progress = progress.coerceIn(0, 100),
        currentSize = currentSize,
        totalSize = totalSize,
        speed = speed,
        status = status,
    )
}

private fun GeoXDownloadProgressState.isInProgress(): Boolean {
    return status == GeoXDownloadStatus.Pending ||
        status == GeoXDownloadStatus.Downloading ||
        status == GeoXDownloadStatus.Validating
}

@Composable
private fun GeoXDownloadProgressContent(
    items: List<GeoXDownloadProgressState>,
    isUpdating: Boolean,
) {
    val showUpdatingHeader = isUpdating && items.any(GeoXDownloadProgressState::isInProgress)
    val spacing = com.github.yumelira.yumebox.presentation.theme.AppTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.space16, vertical = spacing.space12),
        verticalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        if (showUpdatingHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                Md3ELoading(modifier = Modifier.size(spacing.space32))
                Column {
                    Text(
                        text = MLang.MetaFeature.Download.ProgressTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = MLang.MetaFeature.Download.ProgressSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items.forEach { item ->
            GeoXDownloadProgressRow(item)
        }
    }
}

@Composable
private fun GeoXDownloadProgressRow(item: GeoXDownloadProgressState) {
    val spacing = com.github.yumelira.yumebox.presentation.theme.AppTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        Md3EWavyProgressIndicator(
            progress = if (item.totalSize > 0L || item.progress > 0) item.progress / 100f else null,
            completed = item.status == GeoXDownloadStatus.Success,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = item.detailText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Md3EWavyProgressIndicator(
    progress: Float?,
    completed: Boolean,
    modifier: Modifier = Modifier,
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

private fun GeoXDownloadProgressState.statusLabel(): String {
    return when (status) {
        GeoXDownloadStatus.Pending -> MLang.MetaFeature.Download.StatusPending
        GeoXDownloadStatus.Downloading -> MLang.MetaFeature.Download.StatusDownloading.format(progress)
        GeoXDownloadStatus.Validating -> MLang.MetaFeature.Download.StatusValidating
        GeoXDownloadStatus.Success -> MLang.MetaFeature.Download.StatusSuccess
        GeoXDownloadStatus.Failed -> MLang.MetaFeature.Download.StatusFailed
    }
}

private fun GeoXDownloadProgressState.detailText(): String {
    return when {
        status == GeoXDownloadStatus.Success -> MLang.MetaFeature.Download.ProgressSuccess
        status == GeoXDownloadStatus.Failed -> MLang.MetaFeature.Download.ProgressFailed
        totalSize > 0L -> MLang.MetaFeature.Download.ProgressDetail.format(
            com.github.yumelira.yumebox.common.util.formatBytes(currentSize),
            com.github.yumelira.yumebox.common.util.formatBytes(totalSize),
            speed,
        )
        currentSize > 0L -> MLang.MetaFeature.Download.ProgressDetailUnknownTotal.format(
            com.github.yumelira.yumebox.common.util.formatBytes(currentSize),
            speed,
        )
        else -> MLang.MetaFeature.Download.ProgressWaiting
    }
}
