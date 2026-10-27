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
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

@file:Suppress("FunctionName")

package com.github.yumelira.yumebox.screen.navigation

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.github.yumelira.yumebox.WebViewActivity
import com.github.yumelira.yumebox.common.util.DashboardShortcutHelper
import com.github.yumelira.yumebox.common.util.buildRemotePanelUrl
import com.github.yumelira.yumebox.common.util.requiresLocalNetworkPermission
import com.github.yumelira.yumebox.data.model.RemoteBackend
import com.github.yumelira.yumebox.data.store.RemoteControllerStore
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.screen.FeatureContent
import com.github.yumelira.yumebox.screen.feature.PanelShortcutDialog
import com.github.yumelira.yumebox.screen.feature.RemoteControllerSection
import com.github.yumelira.yumebox.screen.settings.backup.BackupRestoreSection
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun FeatureScreen(navigator: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val remoteControllerStore = koinInject<RemoteControllerStore>()

    var pendingLocalNetworkAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val localNetworkPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingLocalNetworkAction?.takeIf { granted }?.invoke()
            pendingLocalNetworkAction = null
        }
    val requestLocalNetworkPermission: (RemoteBackend, () -> Unit) -> Unit = { backend, action ->
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        if (
            Build.VERSION.SDK_INT < 37 ||
            !backend.requiresLocalNetworkPermission() ||
            ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingLocalNetworkAction = action
            localNetworkPermissionLauncher.launch(permission)
        }
    }

    var shortcutTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var shortcutDialogVisible by remember { mutableStateOf(false) }

    FeatureContent(
        onOpenInAppUrl = { url -> WebViewActivity.start(context, url) },
        onOpenPanel = { panelUrl ->
            val backend = remoteControllerStore.activeBackend()
            val targetUrl = backend?.let { buildRemotePanelUrl(panelUrl, it) } ?: panelUrl
            val openPanel = { WebViewActivity.start(context, targetUrl) }
            if (backend != null) {
                requestLocalNetworkPermission(backend, openPanel)
            } else {
                openPanel()
            }
        },
        onCreatePanelShortcut = { url, label ->
            val targetUrl =
                remoteControllerStore.activeBackend()?.let { buildRemotePanelUrl(url, it) } ?: url
            shortcutTarget = targetUrl to label
            shortcutDialogVisible = true
        },
        topSection = {
            RemoteControllerSection(onRequestLocalNetworkPermission = requestLocalNetworkPermission)
            shortcutTarget?.let { (url, label) ->
                PanelShortcutDialog(
                    show = shortcutDialogVisible,
                    url = url,
                    defaultLabel = label,
                    onDismiss = { shortcutDialogVisible = false },
                    onConfirm = { name, iconUri ->
                        scope.launch {
                            DashboardShortcutHelper.createPanelShortcut(context, url, name, iconUri)
                        }
                        shortcutDialogVisible = false
                    },
                    onDismissFinished = { shortcutTarget = null },
                )
            }
        },
        bottomSection = {
            BackupRestoreSection()
        },
    )
}
