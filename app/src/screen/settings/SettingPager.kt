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

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.BuildConfig
import com.github.yumelira.yumebox.WebViewActivity
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.viewmodel.SettingEvent
import com.github.yumelira.yumebox.presentation.viewmodel.SettingViewModel
import com.ramcosta.composedestinations.generated.destinations.AboutScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AppDataManagementScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AppSettingsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FeatureScreenDestination
import com.ramcosta.composedestinations.generated.destinations.LogScreenDestination
import com.ramcosta.composedestinations.generated.destinations.MetaFeatureScreenDestination
import com.ramcosta.composedestinations.generated.destinations.NetworkSettingsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideScreenDestination
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel

@Composable
private fun CircularIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: Float = 1f,
) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val componentSizes = AppTheme.sizes

    Box(
        modifier = modifier
            .padding(start = spacing.space4, end = spacing.space16)
            .requiredSize(componentSizes.settingsIconSlotSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .layout { measurable, _ ->
                    val containerSize = componentSizes.settingsIconContainerSize.roundToPx()
                    val parentSize = componentSizes.settingsIconSlotSize.roundToPx()
                    val offset = (containerSize - parentSize) / 2

                    val placeable = measurable.measure(
                        androidx.compose.ui.unit.Constraints.fixed(containerSize, containerSize)
                    )
                    layout(parentSize, parentSize) {
                        placeable.place(-offset, -offset)
                    }
                }
                .size(componentSizes.settingsIconContainerSize)
                .clip(RoundedCornerShape(radii.radius16))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(componentSizes.settingsIconGlyphSize)
                    .graphicsLayer(
                        scaleX = iconSize,
                        scaleY = iconSize,
                        transformOrigin = TransformOrigin.Center,
                    )
            )
        }
    }
}

@SuppressLint("LocalContextResourcesRead")
@Composable
fun SettingPager(
    mainInnerPadding: PaddingValues,
    lazyListState: LazyListState,
) {
    val viewModel = koinViewModel<SettingViewModel>()
    val navigator = LocalNavigator.current
    val context = LocalContext.current

    val versionInfo = BuildConfig.VERSION_NAME
    val backupActions = com.github.yumelira.yumebox.backup.rememberGlobalBackupActions()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingEvent.OpenWebView -> {
                    runCatching {
                        WebViewActivity.start(context, event.url)
                    }.getOrElse { throwable ->
                        context.toast(MLang.Settings.Error.WebviewFailed.format(throwable.message))
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopBar(title = MLang.Settings.Title)
        },
    ) { innerPadding ->
        ScreenLazyColumn(
            innerPadding = combinePaddingValues(innerPadding, mainInnerPadding),
            lazyListState = lazyListState,
        ) {

            item {
                Title(MLang.Settings.Section.UiSettings)
                Card {
                    SettingsEntryItem(
                        title = MLang.Settings.UiSettings.App,
                        summary = MLang.Settings.UiSettings.AppSummary,
                        imageVector = AppMd3Icons.Settings.App,
                        onClick = { navigator.navigate(AppSettingsScreenDestination) { launchSingleTop = true } },
                    )
                }
            }
            item {
                Title(MLang.Settings.Section.NetworkSettings)
                Card {
                    SettingsEntryItem(
                        title = MLang.Settings.NetworkSettings.Network,
                        summary = MLang.Settings.NetworkSettings.NetworkSummary,
                        imageVector = AppMd3Icons.Settings.Network,
                        onClick = { navigator.navigate(NetworkSettingsScreenDestination) { launchSingleTop = true } },
                    )
                    SettingsEntryItem(
                        title = MLang.Settings.NetworkSettings.Override,
                        summary = MLang.Settings.NetworkSettings.OverrideSummary,
                        imageVector = AppMd3Icons.Settings.Override,
                        onClick = { navigator.navigate(OverrideScreenDestination) { launchSingleTop = true } },
                    )
                    SettingsEntryItem(
                        title = MLang.Settings.NetworkSettings.MetaFeatures,
                        summary = MLang.Settings.NetworkSettings.MetaFeaturesSummary,
                        imageVector = AppMd3Icons.Settings.MetaFeatures,
                        onClick = {
                            navigator.navigate(MetaFeatureScreenDestination) {
                                launchSingleTop = true
                            }
                        },
                    )
                    SettingsEntryItem(
                        title = MLang.Settings.NetworkSettings.Lab,
                        summary = MLang.Settings.NetworkSettings.LabSummary,
                        imageVector = AppMd3Icons.Settings.Lab,
                        onClick = {
                            navigator.navigate(FeatureScreenDestination) { launchSingleTop = true }
                        },
                    )
                }
            }
            item {
                Title(MLang.Settings.Section.DataSettings)
                Card {
                    SettingsEntryItem(
                        title = MLang.Settings.DataSettings.ExportBackup,
                        summary = MLang.Settings.DataSettings.ExportBackupSummary,
                        imageVector = AppMd3Icons.Settings.ExportBackup,
                        onClick = backupActions.export,
                    )
                    SettingsEntryItem(
                        title = MLang.Settings.DataSettings.ImportBackup,
                        summary = MLang.Settings.DataSettings.ImportBackupSummary,
                        imageVector = AppMd3Icons.Settings.ImportBackup,
                        onClick = backupActions.restore,
                    )
                    SettingsEntryItem(
                        title = MLang.Settings.DataSettings.AppDataManagement,
                        summary = MLang.Settings.DataSettings.AppDataManagementSummary,
                        imageVector = AppMd3Icons.Settings.AppDataManagement,
                        onClick = { navigator.navigate(AppDataManagementScreenDestination) { launchSingleTop = true } },
                    )
                }
            }
            item {
                Title(MLang.Settings.Section.More)

                Card {
                    SettingsEntryItem(
                        title = MLang.Settings.More.Logs,
                        summary = MLang.Settings.More.LogsSummary,
                        imageVector = AppMd3Icons.Settings.Logs,
                        onClick = { navigator.navigate(LogScreenDestination) { launchSingleTop = true } },
                    )
                    SettingsEntryItem(
                        title = MLang.Settings.More.About,
                        summary = MLang.Settings.More.AboutSummary,
                        imageVector = AppMd3Icons.Settings.About,
                        onClick = { navigator.navigate(AboutScreenDestination) { launchSingleTop = true } },
                        endActions = {
                            VersionBadge(versionInfo)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsEntryItem(
    title: String,
    summary: String,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    endActions: @Composable (RowScope.() -> Unit)? = null,
) {
    PreferenceArrowItem(
        title = title,
        summary = summary,
        onClick = onClick,
        startAction = {
            CircularIcon(
                imageVector = imageVector,
                contentDescription = null,
            )
        },
        endActions = endActions,
    )
}

@Composable
private fun VersionBadge(
    versionInfo: String?
) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .height(componentSizes.versionBadgeHeight)
            .padding(end = spacing.space12)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = spacing.space12),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8)
        ) {
            Text(
                text = versionInfo ?: "Unknown",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
