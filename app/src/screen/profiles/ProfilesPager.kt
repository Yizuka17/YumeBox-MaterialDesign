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


package com.github.yumelira.yumebox.screen.profiles
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.presentation.theme.appPressSink
import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.github.yumelira.yumebox.App
import com.github.yumelira.yumebox.MainActivity
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.data.controller.OverrideService
import com.github.yumelira.yumebox.data.store.ProfileBindingProvider
import com.github.yumelira.yumebox.data.model.ProfileBinding
import com.github.yumelira.yumebox.feature.editor.presentation.language.LanguageScope
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.icon.ShellIcons
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.util.OverrideStructuredEditorStore
import com.github.yumelira.yumebox.presentation.viewmodel.OverrideConfigViewModel
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import com.github.yumelira.yumebox.runtime.api.Profile
import com.ramcosta.composedestinations.generated.destinations.OverrideConfigPreviewRouteDestination
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

@SuppressLint("UseKtx")
@Composable
fun ProfilesPager(
    mainInnerPadding: PaddingValues,
    lazyListState: LazyListState,
) {
    val navigator = LocalNavigator.current
    val profilesViewModel = koinViewModel<ProfilesViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val profiles by profilesViewModel.profiles.collectAsState()
    val updatingProfileIds by profilesViewModel.updatingProfileIds.collectAsState()
    val isRunning by homeViewModel.isRunning.collectAsState()

    val overrideConfigViewModel = koinViewModel<OverrideConfigViewModel>()
    val bindingProvider: ProfileBindingProvider = koinInject()
    val overrideService: OverrideService = koinInject()
    val systemPresets by overrideConfigViewModel.systemPresets.collectAsState()
    val userConfigs by overrideConfigViewModel.userConfigs.collectAsState()
    val context = LocalContext.current
    val tabletLayout = rememberTabletLayout()
    val configuration = LocalConfiguration.current
    val profileGridState = rememberRetainedStaggeredGridState("main_profiles_grid",
        "${configuration.screenWidthDp}:${configuration.screenHeightDp}:${configuration.fontScale}")

    val showAddBottomSheet = remember { mutableStateOf(false) }
    var isDeleteDialogVisible by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Profile?>(null) }
    val showSettingsDialog = remember { mutableStateOf(false) }
    val showShareDialog = remember { mutableStateOf(false) }
    var isEditOptionsDialogVisible by remember { mutableStateOf(false) }
    var showEditOptionsDialog by remember { mutableStateOf<Profile?>(null) }
    var openConfigPreviewAfterEditDialogDismiss by remember { mutableStateOf(false) }
    var profileToShare by remember { mutableStateOf<Profile?>(null) }
    var profileToEdit by remember { mutableStateOf<Profile?>(null) }
    var profileBinding by remember { mutableStateOf<ProfileBinding?>(null) }

    var importUrlFromScheme by remember { mutableStateOf<String?>(null) }
    val pendingImportUrl by MainActivity.pendingImportUrl.collectAsState()
    val urlProfiles = remember(profiles) {
        profiles.filter { it.type == Profile.Type.Url }
    }
    val hasUpdatableProfiles = urlProfiles.any { it.uuid !in updatingProfileIds }

    var scannedUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingImportUrl) {
        if (pendingImportUrl != null) {
            importUrlFromScheme = pendingImportUrl
            profileToEdit = null
            showAddBottomSheet.value = true
            MainActivity.clearPendingImportUrl()
        }
    }

    LaunchedEffect(showSettingsDialog.value) {
        if (showSettingsDialog.value) {
            overrideConfigViewModel.refresh()
        }
    }

    val scope = rememberCoroutineScope()

    val updateAllProfiles: () -> Unit = {
        urlProfiles.filter { it.uuid !in updatingProfileIds }.forEach { profilesViewModel.updateProfile(it.uuid) }
    }
    val addProfile: () -> Unit = { profileToEdit = null; showAddBottomSheet.value = true }
    val renderProfile: @Composable (Profile, Modifier) -> Unit = { profile, cardModifier ->
        ProfileCard(
            profile = profile,
            workDir = App.instance.filesDir.resolve("imported"),
            isDownloading = false,
            isUpdating = profile.uuid in updatingProfileIds,
            modifier = cardModifier,
            onExport = { profile ->
                if (profile.uuid !in updatingProfileIds) {
                    profileToShare = profile
                    showShareDialog.value = true
                }
            },
            onUpdate = { profile ->
                if (profile.uuid !in updatingProfileIds) {
                    profilesViewModel.updateProfile(profile.uuid)
                }
            },
            onDelete = { profile ->
                if (profile.uuid !in updatingProfileIds) {
                    showDeleteDialog = profile
                    isDeleteDialogVisible = true
                }
            },
            onEdit = { profile ->
                if (profile.uuid !in updatingProfileIds) {
                    showEditOptionsDialog = profile
                    isEditOptionsDialogVisible = true
                }
            },
            onToggleEnabled = { profile ->
                if (profile.active) {
                    profilesViewModel.cancelProfileUpdateAndRestore(profile.uuid)
                }
                scope.launch {
                    if (profile.active && isRunning) {
                        homeViewModel.stopProxy()
                    }
                    profilesViewModel.toggleProfileEnabled(profile.uuid)
                }
            },
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (tabletLayout) TabletPageSidebar(MLang.ProfilesPage.Title,
            bottomPadding = mainInnerPadding.calculateBottomPadding()) {
            TabletSidebarAction(MLang.ProfilesPage.Action.UpdateAll, ShellIcons.UpdateProfiles,
                enabled = hasUpdatableProfiles, onClick = updateAllProfiles)
            TabletSidebarAction(MLang.ProfilesPage.Action.AddProfile, ShellIcons.AddProfile, onClick = addProfile)
        }
        Scaffold(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        topBar = {
            if (!tabletLayout) TopBar(
                title = MLang.ProfilesPage.Title,
                actions = {
                    IconButton(
                        modifier = Modifier.padding(end = UiDp.dp12),
                        enabled = hasUpdatableProfiles,
                        onClick = updateAllProfiles
                    ) {
                        Icon(
                            ShellIcons.UpdateProfiles,
                            contentDescription = MLang.ProfilesPage.Action.UpdateAll
                        )
                    }

                    IconButton(
                        onClick = addProfile
                    ) {
                        Icon(
                            ShellIcons.AddProfile,
                            contentDescription = MLang.ProfilesPage.Action.AddProfile
                        )
                    }
                })
        },
    ) { innerPadding ->
        if (profiles.isEmpty()) {

            CenteredText(
                firstLine = MLang.ProfilesPage.Empty.NoProfiles,
                secondLine = MLang.ProfilesPage.Empty.Hint
            )
        } else if (tabletLayout) {
            val reorderableGridState = rememberReorderableLazyStaggeredGridState(profileGridState) { from, to ->
                profilesViewModel.reorderProfiles(from.index, to.index)
            }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(300.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
                state = profileGridState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 20.dp,
                    bottom = mainInnerPadding.calculateBottomPadding() + 12.dp,
                ), verticalItemSpacing = 12.dp,
            ) {
                items(profiles, key = { it.uuid.toString() }) { profile ->
                    ReorderableItem(reorderableGridState, key = profile.uuid.toString()) { isDragging ->
                        val dragInteractionSource = remember { MutableInteractionSource() }
                        renderProfile(profile, Modifier.fillMaxWidth().appPressSink(
                            interactionSource = dragInteractionSource, enabled = true,
                            pressedScale = 0.98f, pressedTranslationY = UiDp.dp1,
                            minimumVisibleMillis = 0L, forcePressed = isDragging,
                        ).longPressDraggableHandle(interactionSource = dragInteractionSource)
                            .alpha(if (isDragging) 0.9f else 1f))
                    }
                }
            }
        } else {
            val reorderableLazyListState =
                rememberReorderableLazyListState(lazyListState) { from, to ->
                    profilesViewModel.reorderProfiles(from.index, to.index)
                }

            ScreenLazyColumn(
                lazyListState = lazyListState,
                innerPadding = combinePaddingValues(innerPadding, mainInnerPadding),
                topPadding = UiDp.dp20,
            ) {
                items(
                    items = profiles,
                    key = { it.uuid.toString() }
                ) { profile ->
                    ReorderableItem(
                        reorderableLazyListState,
                        key = profile.uuid.toString()
                    ) { isDragging ->
                        val dragInteractionSource = remember { MutableInteractionSource() }
                        renderProfile(profile, Modifier
                                .appPressSink(
                                    interactionSource = dragInteractionSource,
                                    enabled = true,
                                    pressedScale = 0.98f,
                                    pressedTranslationY = UiDp.dp1,
                                    minimumVisibleMillis = 0L,
                                    forcePressed = isDragging,
                                )
                                .longPressDraggableHandle(interactionSource = dragInteractionSource)
                                .alpha(if (isDragging) 0.9f else 1f))
                    }
                }
            }
        }
    }
    }

    AddProfileSheet(
        show = showAddBottomSheet,
        profileToEdit = profileToEdit,
        importUrl = importUrlFromScheme ?: scannedUrl,
        onAddProfile = { name, source, type, interval, fileUri ->
            profilesViewModel.createProfile(type, name, source, interval, fileUri)
        },
        onUpdateProfile = { uuid, name, source, interval ->
            profilesViewModel.patchProfile(uuid, name, source, interval)
        },
        onDownloadComplete = {
            showAddBottomSheet.value = false
            profilesViewModel.clearDownloadProgress()
        },
        profilesViewModel = profilesViewModel
    )

    showDeleteDialog?.let { profile ->
        DeleteConfirmDialog(
            show = isDeleteDialogVisible,
            profileName = profile.name,
            onConfirm = {
            isDeleteDialogVisible = false
            profilesViewModel.deleteProfile(profile.uuid)
        },
            onDismiss = { isDeleteDialogVisible = false },
            onDismissFinished = { showDeleteDialog = null },
        )
    }

    val currentProfileToEdit = profileToEdit
    if (currentProfileToEdit != null) {
        ProfileSettingsDialog(
            show = showSettingsDialog.value,
            profile = currentProfileToEdit,
            systemPreset = systemPresets.firstOrNull(),
            userConfigs = userConfigs,
            binding = profileBinding,
            onDismiss = {
                showSettingsDialog.value = false
            },
            onDismissFinished = {
                profileToEdit = null
                profileBinding = null
            },
            onSaveProfileMeta = { newName, newSource ->
                if (newName.isNotBlank() && newSource.isNotBlank()) {
                    profilesViewModel.patchProfile(
                        currentProfileToEdit.uuid,
                        newName,
                        newSource,
                        currentProfileToEdit.interval
                    )
                }
            },
            onSaveOverrideSettings = { systemPresetEnabled, selectedOverrideIds ->
                scope.launch {
                    try {
                    val profileId = currentProfileToEdit.uuid.toString()
                    val normalizedOverrideIds = selectedOverrideIds.distinct()
                    val currentBinding = profileBinding ?: bindingProvider.getBinding(profileId)
                    val updatedBinding = currentBinding?.copy(
                        overrideIds = normalizedOverrideIds,
                        enabled = systemPresetEnabled,
                    ) ?: ProfileBinding(
                        profileId = profileId,
                        overrideIds = normalizedOverrideIds,
                        enabled = systemPresetEnabled,
                    )

                    bindingProvider.setBinding(updatedBinding)
                    profileBinding = bindingProvider.getBinding(profileId)

                    if (isRunning && homeViewModel.isCurrentProfile(currentProfileToEdit.uuid)) {
                        overrideService.applyOverride(profileId)
                    }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        timber.log.Timber.e(e, "Failed to save profile override settings")
                        context.toast("保存覆写设置失败：${e.message}")
                    }
                }
            },
        )
    }

    profileToShare?.let { profile ->
        ShareOptionsDialog(show = showShareDialog.value, profile = profile, onDismiss = {
            showShareDialog.value = false
        }, onDismissFinished = {
            profileToShare = null
        }, onShareFile = { profile ->
            val file = importedConfigFile(profile)

            if (!file.exists()) {
                context.toast(MLang.ProfilesPage.ShareDialog.ImportedConfigMissing.format(file.absolutePath))
            } else {
                runCatching {
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/x-yaml"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(
                        Intent.createChooser(
                            intent, MLang.ProfilesPage.ShareDialog.ShareFile
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                }.onFailure {
                    context.toast(it.message ?: "Share failed")
                }
            }
            showShareDialog.value = false
        }, onShareLink = { profile ->
            val context = App.instance
            val url = if (profile.type == Profile.Type.Url) profile.source else null
            url?.let {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, it)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    Intent.createChooser(
                        intent, MLang.ProfilesPage.ShareDialog.ShareLink
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
            } ?: run {
                context.toast(MLang.ProfilesPage.ShareDialog.NoLink)
            }
            showShareDialog.value = false
        })
    }

    showEditOptionsDialog?.let { profile ->
        ProfileEditOptionsDialog(
            show = isEditOptionsDialogVisible,
            onOpenConfig = {
                openConfigPreviewAfterEditDialogDismiss = false
                isEditOptionsDialogVisible = false
                openProfileConfigPreview(
                    targetFile = importedConfigFile(profile),
                    missingMessage = MLang.ProfilesPage.SettingsDialog.ConfigMissing.format(importedConfigFile(profile).absolutePath),
                    editable = true,
                    onReadFailed = context::toast,
                ) { configContent, callback ->
                    OverrideStructuredEditorStore.setupConfigPreview(
                        title = profile.name,
                        content = configContent,
                        language = LanguageScope.Yaml,
                        callback = callback,
                    )
                    openConfigPreviewAfterEditDialogDismiss = true
                }
            },
            onEditSettings = {
                openConfigPreviewAfterEditDialogDismiss = false
                isEditOptionsDialogVisible = false
                profileToEdit = profile
                scope.launch {
                    profileBinding = bindingProvider.getBinding(profile.uuid.toString())
                }
                showSettingsDialog.value = true
            },
            onDismiss = {
                openConfigPreviewAfterEditDialogDismiss = false
                isEditOptionsDialogVisible = false
            },
            onDismissFinished = {
                showEditOptionsDialog = null
                if (openConfigPreviewAfterEditDialogDismiss) {
                    openConfigPreviewAfterEditDialogDismiss = false
                    navigator.navigate(OverrideConfigPreviewRouteDestination)
                }
            },
        )
    }
}
