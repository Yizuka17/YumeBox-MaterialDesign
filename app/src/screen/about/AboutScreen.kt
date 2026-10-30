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

package com.github.yumelira.yumebox.screen.about

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.github.yumelira.yumebox.BuildConfig
import com.github.yumelira.yumebox.R
import com.github.yumelira.yumebox.common.util.openUrl
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.BuildInfo
import com.github.yumelira.yumebox.presentation.component.AppDialog
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.DialogButtonRow
import com.github.yumelira.yumebox.presentation.component.Md3EIndeterminateLinearWavyProgressIndicator
import com.github.yumelira.yumebox.presentation.component.Md3ELinearWavyProgressIndicator
import com.github.yumelira.yumebox.presentation.component.ScreenSectionGrid
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.md3.YumeMd3FilledButton
import com.github.yumelira.yumebox.presentation.component.md3.YumeMd3PreferenceItem
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.update.GitHubUpdateViewModel
import com.github.yumelira.yumebox.update.UpdateCandidate
import com.github.yumelira.yumebox.update.UpdateDownloadProgress
import com.github.yumelira.yumebox.update.ReleaseCoverPreview
import com.github.panpf.sketch.AsyncImage as SketchAsyncImage
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.LoadState
import com.github.panpf.sketch.state.IntColorDrawableStateImage
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.OpenSourceLicensesScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CancellationException
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>
fun AboutScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val updateViewModel = koinViewModel<GitHubUpdateViewModel>()
    val updateUiState by updateViewModel.uiState.collectAsState()
    val downloadProgress by updateViewModel.downloadProgress.collectAsState()
    val updateManager = org.koin.compose.koinInject<com.github.yumelira.yumebox.update.GitHubUpdateManager>()
    var automaticUpdates by remember { mutableStateOf(updateManager.autoCheck) }
    var previewUpdates by remember { mutableStateOf(updateManager.includePrereleases) }
    val coreVersion by produceState(initialValue = MLang.About.App.VersionLoading) {
        value = try {
            BuildInfo.CORE_VERSION
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            MLang.About.App.VersionFailed
        }
    }

    LaunchedEffect(updateUiState.message) {
        updateUiState.message?.let { message ->
            context.toast(message)
            updateViewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopBar(title = MLang.About.Title)
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenSectionGrid(
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(UiDp.dp24))

                    Icon(
                        painter = painterResource(id = R.drawable.yume),
                        contentDescription = "App Icon",
                        modifier = Modifier
                            .size(UiDp.dp120)
                            .clip(RoundedCornerShape(UiDp.dp24)),
                        tint = Color.Unspecified,
                    )

                    Spacer(modifier = Modifier.height(UiDp.dp24))

                    Text(
                        text = "YumeBox MD3",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(UiDp.dp8))

                    Text(
                        text = "v${BuildConfig.VERSION_NAME} ($coreVersion)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(UiDp.dp32))
                }

                Card {
                    YumeMd3PreferenceItem(
                        title = "YumeBox MD3",
                        summary = "A Material Design 3 / Material You fork of YumeBox, an open-source Android client based on Mihomo",
                    )
                    YumeMd3PreferenceItem(
                        title = MLang.About.License.CheckUpdate,
                        summary = if (updateUiState.isChecking) {
                            MLang.Component.Update.Message.Checking
                        } else {
                            MLang.About.License.CheckUpdateSummary
                        },
                        enabled = !updateUiState.isChecking && !downloadProgress.isDownloading,
                        onClick = updateViewModel::checkForUpdate,
                        trailingContent = { ChevronText() },
                        showDivider = false,
                    )
                    com.github.yumelira.yumebox.presentation.component.md3.YumeMd3SwitchPreferenceItem(
                        title = "自动检查更新",
                        summary = "应用在前台时，每 6 小时从 GitHub Releases 检查正式版",
                        checked = automaticUpdates,
                        onCheckedChange = { automaticUpdates = it; updateManager.autoCheck = it },
                    )
                    com.github.yumelira.yumebox.presentation.component.md3.YumeMd3SwitchPreferenceItem(
                        title = "接收预发布版本",
                        summary = "同时检测 Alpha、Beta 和预览发布",
                        checked = previewUpdates,
                        onCheckedChange = { previewUpdates = it; updateManager.includePrereleases = it },
                    )
                }

            }
            item {
                Title(MLang.About.Section.ProjectLinks)
                Card {
                    AboutLinkItem(
                        title = "YumeBox-MaterialDesign",
                        url = "https://github.com/Yizuka17/YumeBox-MaterialDesign",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = false,
                    )
                    AboutLinkItem(
                        title = "YumeBox",
                        url = "https://github.com/YumeLira/YumeBox",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = false,
                    )
                    AboutLinkItem(
                        title = "Mihomo",
                        url = "https://github.com/MetaCubeX/mihomo",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = false,
                    )
                }

            }
            item {
                Title(MLang.About.Section.More)
                Card {
                    AboutLinkItem(
                        title = MLang.About.Link.TelegramGroup,
                        url = "https://t.me/OOM_Group",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = true,
                    )
                    AboutLinkItem(
                        title = MLang.About.Link.TelegramChannel,
                        url = "https://t.me/YumeLira",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = true,
                    )
                }

            }
            item {
                Title(MLang.About.Section.License)
                Card {
                    YumeMd3PreferenceItem(
                        title = MLang.About.License.Libraries,
                        summary = MLang.About.License.LibrariesSummary,
                        onClick = { navigator.navigate(OpenSourceLicensesScreenDestination) },
                        trailingContent = { ChevronText() },
                    )
                    YumeMd3PreferenceItem(
                        title = MLang.About.License.AgplName,
                        summary = MLang.About.License.AgplDescription,
                        showDivider = false,
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = UiDp.dp32),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = MLang.About.Copyright,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(UiDp.dp32))
            }
        }
    }


}

@Composable
private fun AboutLinkItem(
    title: String,
    url: String,
    onOpenUrl: (String) -> Unit,
    showArrow: Boolean,
) {
    YumeMd3PreferenceItem(
        title = title,
        summary = url,
        onClick = { onOpenUrl(url) },
        trailingContent = if (showArrow) {
            { ChevronText() }
        } else {
            null
        },
    )
}

@Composable
private fun ChevronText() {
    Icon(
        imageVector = AppMd3Icons.Navigation.Forward,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun UpdateCandidateDialog(
    candidate: UpdateCandidate?,
    downloadProgress: UpdateDownloadProgress,
    onDismiss: () -> Unit,
    onDownload: (UpdateCandidate) -> Unit,
    onCancelDownload: () -> Unit,
) {
    if (candidate == null) return

    val releaseNotes = candidate.releaseNotes.ifBlank { MLang.Component.Update.Message.Available }
    val message = buildString {
        appendLine("${MLang.Component.Update.Message.CurrentVersion}: ${BuildConfig.VERSION_NAME}")
        appendLine("${MLang.Component.Update.Message.RemoteVersion}: ${candidate.versionName}")
        if (candidate.tag.isNotBlank()) {
            appendLine("Tag: ${candidate.tag}")
        }
        appendLine()
        append(releaseNotes)
    }

    AppDialog(
        show = true,
        onDismissRequest = {
            if (downloadProgress.isDownloading) {
                onCancelDownload()
            } else {
                onDismiss()
            }
        },
    ) {
        if (downloadProgress.isDownloading) {
            UpdateDownloadContent(
                progress = downloadProgress,
                onCancelDownload = onCancelDownload,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = UiDp.dp420)
                        .verticalScroll(rememberScrollState()),
                ) {
                    UpdateDialogTitle()
                    UpdateCoverImage(
                        cachedCoverUri = candidate.cachedCoverUri,
                        coverUrl = candidate.coverUrl,
                        coverDataUri = candidate.coverDataUri,
                    )
                    UpdateDialogMessage(message)
                }
                DialogButtonRow(
                    onCancel = onDismiss,
                    onConfirm = { onDownload(candidate) },
                    confirmText = MLang.Component.Update.Action.DownloadNow,
                )
            }
        }
    }
}

@Composable
private fun UpdateCoverImage(
    cachedCoverUri: String,
    coverUrl: String,
    coverDataUri: String,
) {
    if (cachedCoverUri.isBlank() && coverUrl.isBlank() && coverDataUri.isBlank()) return

    val context = LocalContext.current
    val placeholderColorInt = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()
    var coverImage by remember(cachedCoverUri, coverUrl, coverDataUri) {
        mutableStateOf(
            cachedCoverUri.ifBlank {
                coverUrl.ifBlank {
                    coverDataUri
                }
            },
        )
    }
    if (coverImage.isBlank()) return
    val state = rememberAsyncImageState()
    var showPreview by remember(coverImage) { mutableStateOf(false) }
    var coverRatio by remember(coverImage) { mutableFloatStateOf(16f / 9f) }

    LaunchedEffect(state.loadState, coverImage, cachedCoverUri, coverUrl, coverDataUri) {
        (state.loadState as? LoadState.Success)?.result?.imageInfo
            ?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { coverRatio = it.width.toFloat() / it.height.toFloat() }
        if (state.loadState is LoadState.Error) {
            coverImage = when {
                coverImage == cachedCoverUri && coverUrl.isNotBlank() -> coverUrl
                coverImage != coverDataUri && coverDataUri.isNotBlank() -> coverDataUri
                else -> coverImage
            }
        }
    }

    val request = ImageRequest(context, coverImage) {
        placeholder(IntColorDrawableStateImage(placeholderColorInt))
        error(IntColorDrawableStateImage(placeholderColorInt))
        crossfade(true)
    }

    SketchAsyncImage(
        request = request,
        contentDescription = MLang.Component.Update.Message.CoverDesc,
        contentScale = ContentScale.Fit,
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(coverRatio)
            .clip(RoundedCornerShape(UiDp.dp12))
            .clickable(onClickLabel = "查看封面") { showPreview = true },
    )
    if (showPreview) {
        ReleaseCoverPreview(coverImage, onDismiss = { showPreview = false })
    }
    Spacer(modifier = Modifier.height(UiDp.dp12))
}

@Composable
private fun UpdateDownloadContent(
    progress: UpdateDownloadProgress,
    onCancelDownload: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiDp.dp12)) {
        Column(Modifier.weight(1f, fill = false).heightIn(max = UiDp.dp420)
            .verticalScroll(rememberScrollState())) {
            UpdateDialogTitle()
            UpdateDialogMessage(progress.message.ifBlank { MLang.Component.Update.Message.Downloading })
            Spacer(modifier = Modifier.height(UiDp.dp12))
            UpdateWavyProgressIndicator(progress = progress.progress, modifier = Modifier.fillMaxWidth())
        }
        YumeMd3FilledButton(
            text = MLang.Component.Update.Action.CancelDownload,
            onClick = onCancelDownload,
            destructive = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UpdateDialogTitle() {
    Text(MLang.Component.Update.Title.Available, style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(UiDp.dp16))
}

@Composable
private fun UpdateWavyProgressIndicator(
    progress: Int,
    modifier: Modifier = Modifier,
) {
    val normalizedProgress = progress
        .takeIf { it > 0 }
        ?.coerceIn(0, 100)
        ?.div(100f)

    if (normalizedProgress == null) {
        Md3EIndeterminateLinearWavyProgressIndicator(modifier = modifier)
    } else {
        Md3ELinearWavyProgressIndicator(
            progress = normalizedProgress,
            modifier = modifier,
        )
    }
}

@Composable
private fun UpdateDialogMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
