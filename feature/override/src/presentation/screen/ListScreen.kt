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
import com.github.yumelira.yumebox.presentation.theme.UiDp
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.data.model.OverrideConfig
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.md3.YumeMd3FilledButton
import com.github.yumelira.yumebox.presentation.component.md3.YumeMd3OutlinedTextField
import com.github.yumelira.yumebox.presentation.component.md3.YumeMd3TextButton
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.presentation.theme.Spacing
import com.github.yumelira.yumebox.presentation.theme.yumeDestructiveActionColors
import com.github.yumelira.yumebox.presentation.viewmodel.OverrideConfigViewModel
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch

private val OverrideConfigItemGap = Spacing().space12

@Composable
fun OverrideListScreen(
    @Suppress("UNUSED_PARAMETER") navigator: DestinationsNavigator,
    onEditConfig: (String) -> Unit,
    onOpenCodeEditor: (configId: String, configName: String) -> Unit = { _, _ -> },
) {
    val viewModel: OverrideConfigViewModel = koinViewModel()
    val userConfigs by viewModel.userConfigs.collectAsState()
    val usageCountMap by viewModel.usageCountMap.collectAsState()
    val pendingRevealConfigId by viewModel.pendingRevealConfigId.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val showCreateDialog = remember { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    val showEditOptionsDialog = remember { mutableStateOf<OverrideConfig?>(null) }
    val isEditOptionsDialogVisible = remember { mutableStateOf(false) }
    val deleteTargetConfig = remember { mutableStateOf<OverrideConfig?>(null) }
    val exportTargetConfig = remember { mutableStateOf<OverrideConfig?>(null) }

    val listState = rememberLazyListState()
    val createFabController = rememberOverrideFabController()
    val configItems = remember(userConfigs, usageCountMap) {
        userConfigs.map { config ->
            OverrideConfigListItem(
                config = config,
                isInUse = (usageCountMap[config.id] ?: 0) > 0,
            )
        }
    }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.reorderUserConfigs(
            fromIndex = from.index,
            toIndex = to.index,
        )
    }

    val importConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult

        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && columnIndex >= 0) {
                cursor.getString(columnIndex)
            } else {
                ""
            }
        }.orEmpty().ifBlank {
            uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                .orEmpty()
        }

        scope.launch {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { reader -> reader.readText() }
                        ?: error(MLang.Override.Import.ReadError)
                }
            }.onSuccess { jsonText ->
                val importResult = viewModel.importConfigsFromJson(
                    jsonString = jsonText,
                    sourceName = displayName,
                )
                if (importResult.isSuccess) {
                    val importedCount = importResult.getOrNull() ?: 0
                    val importMessage = if (displayName.isNotBlank()) {
                        MLang.Override.Import.Success.format(displayName, importedCount)
                    } else {
                        MLang.Override.Import.SuccessDefault.format(importedCount)
                    }
                    context.toast(importMessage)
                    showCreateDialog.value = false
                } else {
                    context.toast(MLang.Override.Import.Failed.format(importResult.exceptionOrNull()?.message))
                }
            }.onFailure { throwable ->
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                context.toast(MLang.Override.Import.FileError.format(throwable.message))
            }
        }
    }

    val exportConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val targetConfig = exportTargetConfig.value
        if (uri == null || targetConfig == null) {
            exportTargetConfig.value = null
            return@rememberLauncherForActivityResult
        }

        val exportedConfig = viewModel.exportConfig(targetConfig.id)
        if (exportedConfig == null) {
            context.toast(MLang.Override.Export.Failed.format(targetConfig.name))
            exportTargetConfig.value = null
            return@rememberLauncherForActivityResult
        }

        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(exportedConfig.toByteArray())
                outputStream.flush()
            } ?: error(MLang.Override.Export.Failed.format(targetConfig.name))
        }.onSuccess {
            context.toast(MLang.Override.Export.Success.format(targetConfig.name))
        }.onFailure { throwable ->
            context.toast(MLang.Override.Export.Failed.format(throwable.message))
        }

        exportTargetConfig.value = null
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(configItems, pendingRevealConfigId) {
        val targetId = pendingRevealConfigId ?: return@LaunchedEffect
        val targetIndex = configItems.indexOfFirst { it.config.id == targetId }
        if (targetIndex < 0) return@LaunchedEffect
        val anchorIndex = (targetIndex - 1).coerceAtLeast(0)
        listState.animateScrollToItem(anchorIndex)
        viewModel.consumePendingRevealConfig(targetId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            OverrideAnimatedFab(
                controller = createFabController,
                visible = !showCreateDialog.value,
                imageVector = AppMd3Icons.Action.Add,
                contentDescription = MLang.Override.Action.Create,
                onClick = { showCreateDialog.value = true },
            )
        },
        topBar = {
            TopBar(
                title = MLang.Override.Title,
            )
        },
    ) { paddingValues ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            innerPadding = combinePaddingValues(paddingValues, mainLikePadding),
            lazyListState = listState,
            onScrollDirectionChanged = createFabController::onScrollDirectionChanged,
        ) {
            when {
                userConfigs.isEmpty() -> {
                    item(
                        key = "override-empty",
                        contentType = "override-empty",
                    ) {
                        Column(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(horizontal = UiDp.dp24, vertical = UiDp.dp80)
                                .wrapContentSize(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
                        ) {
                            CenteredText(
                                firstLine = MLang.Override.Empty.Title,
                                secondLine = MLang.Override.Empty.Hint,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
                            ) {
                                YumeMd3TextButton(
                                    text = MLang.Override.Action.New,
                                    onClick = { showCreateDialog.value = true },
                                )
                                YumeMd3FilledButton(
                                    text = MLang.Override.Action.Import,
                                    onClick = { importConfigLauncher.launch("*/*") },
                                )
                            }
                        }
                    }
                }

                else -> {
                    items(
                        items = configItems,
                        key = { it.config.id },
                        contentType = { "override-config-card" },
                    ) { item ->
                        val config = item.config
                        ReorderableItem(
                            state = reorderState,
                            key = config.id,
                        ) { isDragging ->
                            OverrideConfigCard(
                                config = config,
                                isDragging = isDragging,
                                isInUse = item.isInUse,
                                onCopy = {
                                    viewModel.duplicateConfig(config.id)
                                    context.toast(MLang.Override.Card.Copy + "：" + config.name)
                                },
                                onExport = {
                                    exportTargetConfig.value = config
                                    exportConfigLauncher.launch("${config.name}.json")
                                },
                                onEdit = {
                                    showEditOptionsDialog.value = config
                                    isEditOptionsDialogVisible.value = true
                                },
                                onDelete = {
                                    deleteTargetConfig.value = config
                                    showDeleteDialog.value = true
                                },
                            )
                        }
                    }
                }
            }
        }
        CreateConfigDialog(
            show = showCreateDialog,
            onImportClick = { importConfigLauncher.launch("*/*") },
            onConfirm = { name, description ->
                viewModel.createConfig(
                    name = name,
                    description = description.takeIf(String::isNotBlank),
                )
                showCreateDialog.value = false
            },
            onDismiss = {
                showCreateDialog.value = false
            },
        )

        DeleteConfirmDialog(
            show = showDeleteDialog,
            config = deleteTargetConfig.value,
            viewModel = viewModel,
            onConfirm = {
                deleteTargetConfig.value?.id?.let(viewModel::deleteConfig)
                deleteTargetConfig.value = null
                showDeleteDialog.value = false
            },
            onDismiss = {
                deleteTargetConfig.value = null
                showDeleteDialog.value = false
            },
        )

        // 编辑选项对话框
        showEditOptionsDialog.value?.let { config ->
            EditOptionsDialog(
                show = isEditOptionsDialogVisible.value,
                onVisualEdit = {
                    isEditOptionsDialogVisible.value = false
                    onEditConfig(config.id)
                },
                onCodeEditor = {
                    isEditOptionsDialogVisible.value = false
                    onOpenCodeEditor(config.id, config.name)
                },
                onDismiss = { isEditOptionsDialogVisible.value = false },
                onDismissFinished = { showEditOptionsDialog.value = null },
            )
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.OverrideConfigCard(
    config: OverrideConfig,
    isDragging: Boolean,
    isInUse: Boolean,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val destructiveActionColors = yumeDestructiveActionColors()
    val accentTintColor = colorScheme.primary
    val descriptionText = config.description?.takeIf(String::isNotBlank) ?: MLang.Override.Card.NoDescription

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OverrideConfigItemGap / 2)
            .longPressDraggableHandle()
            .alpha(if (isDragging) 0.92f else 1f),
        insideMargin = PaddingValues(UiDp.dp16),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(UiDp.dp8),
                ) {
                    AppText(
                        text = config.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight(550),
                        ),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = descriptionText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isInUse) {
                    OverrideConfigStateIndicator()
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = UiDp.dp12),
                thickness = UiDp.dp0_5,
                color = colorScheme.outline.copy(alpha = 0.5f),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp8)) {
                    OverrideCardActionIconButton(
                        imageVector = AppMd3Icons.Action.Copy,
                        contentDescription = MLang.Override.Card.Copy,
                        onClick = onCopy,
                    )

                    OverrideCardActionIconButton(
                        imageVector = AppMd3Icons.Action.Share,
                        contentDescription = MLang.Override.Card.Export,
                        onClick = onExport,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                AppIconLabelButton(
                    modifier = Modifier.padding(end = UiDp.dp8),
                    text = MLang.Override.Card.EditButton,
                    imageVector = AppMd3Icons.Action.Edit,
                    contentDescription = MLang.Override.Card.Edit,
                    onClick = onEdit,
                    containerColor = colorScheme.primary.copy(alpha = 0.1f),
                    contentColor = accentTintColor,
                )

                AppIconLabelButton(
                    text = MLang.Override.Card.DeleteButton,
                    imageVector = AppMd3Icons.Action.Delete,
                    contentDescription = MLang.Override.Card.Delete,
                    onClick = onDelete,
                    containerColor = destructiveActionColors.containerColor,
                    contentColor = destructiveActionColors.contentColor,
                )
            }
        }
    }
}

@Composable
private fun OverrideConfigStateIndicator() {
    val colorScheme = MaterialTheme.colorScheme
    val tint = colorScheme.primary

    OverrideStatusBadge(
        imageVector = AppMd3Icons.Security.Enabled,
        contentDescription = MLang.Override.Status.InUse,
        tint = tint,
        backgroundColor = colorScheme.primary.copy(alpha = 0.1f),
    )
}

@Composable
private fun CreateConfigDialog(
    show: MutableState<Boolean>,
    onImportClick: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var name by remember(show.value) { mutableStateOf("") }
    var description by remember(show.value) { mutableStateOf("") }
    val canConfirm = name.isNotBlank()

    AppActionBottomSheet(
        show = show.value,
        title = MLang.Override.Dialog.Create.Title,
        startAction = {
            AppBottomSheetCloseAction(onClick = onDismiss)
        },
        endAction = {
            AppBottomSheetConfirmAction(
                enabled = canConfirm,
                contentDescription = MLang.Override.Action.Create,
                onClick = {
                    if (canConfirm) {
                        keyboardController?.hide()
                        onConfirm(name, description)
                    }
                },
            )
        },
        onDismissRequest = onDismiss,
        insideMargin = DpSize(UiDp.dp32, UiDp.dp12),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
        ) {
            YumeMd3OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = MLang.Override.Dialog.Create.Name,
                modifier = Modifier.fillMaxWidth(),
            )

            YumeMd3OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = MLang.Override.Dialog.Create.Description,
                modifier = Modifier.fillMaxWidth(),
            )

            Card(applyHorizontalPadding = false) {
                PreferenceListItem(
                    title = MLang.Override.Action.ImportFile,
                    summary = MLang.Override.Dialog.Create.ImportHint,
                    startAction = {
                        AppIcon(
                            modifier = Modifier.padding(end = UiDp.dp16),
                            imageVector = AppMd3Icons.Action.Share,
                            contentDescription = MLang.Override.Action.ImportFile,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    },
                    onClick = {
                        keyboardController?.hide()
                        onImportClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    show: MutableState<Boolean>,
    config: OverrideConfig?,
    viewModel: OverrideConfigViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var isInUse by remember { mutableStateOf(false) }

    LaunchedEffect(show.value, config?.id) {
        isInUse = if (show.value && config != null) {
            viewModel.isConfigInUse(config.id)
        } else {
            false
        }
    }

    val summary = when {
        config == null -> ""
        isInUse -> MLang.Override.Dialog.Delete.InUseMessage.format(config.name)
        else -> MLang.Override.Dialog.Delete.Message.format(config.name)
    }

    AppDialog(
        show = show.value,
        title = MLang.Override.Dialog.Delete.Title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(UiDp.dp12),
        ) {
            DialogButtonRow(
                onCancel = onDismiss,
                onConfirm = onConfirm,
                cancelText = MLang.Override.Dialog.Button.Cancel,
                confirmText = MLang.Override.Dialog.Button.Delete,
                confirmDestructive = true,
            )
        }
    }
}

@Composable
private fun EditOptionsDialog(
    show: Boolean,
    onVisualEdit: () -> Unit,
    onCodeEditor: () -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    AppDialog(
        show = show,
        title = MLang.Override.Dialog.EditOptions.Title,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UiDp.dp12),
        ) {
            YumeMd3FilledButton(
                text = MLang.Override.Dialog.EditOptions.VisualEditor,
                modifier = Modifier.fillMaxWidth(),
                onClick = onVisualEdit,
            )
            YumeMd3TextButton(
                text = MLang.Override.Dialog.EditOptions.CodeEditor,
                modifier = Modifier.fillMaxWidth(),
                onClick = onCodeEditor,
            )
        }
    }
}

private data class OverrideConfigListItem(
    val config: OverrideConfig,
    val isInUse: Boolean,
)
