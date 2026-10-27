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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.github.yumelira.yumebox.presentation.component.AppDialog
import com.github.yumelira.yumebox.presentation.component.DialogButtonRow
import com.github.yumelira.yumebox.presentation.component.JsonTextEditorDialog
import com.github.yumelira.yumebox.presentation.component.OpenObjectMapEditor
import com.github.yumelira.yumebox.presentation.component.OpenRuleListEditor
import com.github.yumelira.yumebox.presentation.component.OpenStringListModifiersEditor
import com.github.yumelira.yumebox.presentation.component.OpenStringMapEditor
import com.github.yumelira.yumebox.presentation.component.OpenStructuredObjectListEditor
import com.github.yumelira.yumebox.presentation.component.OpenSubRulesEditor
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.StringMapEditorDialog
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.OverrideEditContent
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.util.OverrideSaveEvent
import com.github.yumelira.yumebox.presentation.util.OverrideSaveState
import com.github.yumelira.yumebox.presentation.util.rememberOverrideReferenceCatalog
import com.github.yumelira.yumebox.presentation.viewmodel.OverrideConfigViewModel
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import androidx.compose.material3.Scaffold

@Composable
fun OverrideEditScreen(
    navigator: DestinationsNavigator,
    configId: String,
    onOpenStringListEditor: OpenStringListModifiersEditor,
    onOpenRuleListEditor: OpenRuleListEditor,
    onOpenObjectListEditor: OpenStructuredObjectListEditor,
    onOpenObjectMapEditor: OpenObjectMapEditor,
    onOpenSubRulesEditor: OpenSubRulesEditor,
) {
    val viewModel: OverrideConfigViewModel = koinViewModel()
    val editSession by viewModel.editSession.collectAsState()
    val saveState by viewModel.saveState.collectAsState()

    val isNewConfig = configId == "new"
    val showDiscardDialog = remember { mutableStateOf(false) }
    var stringMapEditorState by remember { mutableStateOf<StringMapEditorDialogState?>(null) }
    var jsonEditorState by remember { mutableStateOf<JsonEditorDialogState?>(null) }

    val editorListState = rememberLazyListState()
    var expandedSectionNames by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val referenceCatalog = editSession?.let { session ->
        rememberOverrideReferenceCatalog(session.config)
    }

    LaunchedEffect(configId) {
        viewModel.startEditSession(configId)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OverrideSaveEvent.Saved -> Unit
                is OverrideSaveEvent.Failed -> {
                    com.github.yumelira.yumebox.common.util.showToastDialog(event.message)
                }
            }
        }
    }

    val isSaving = saveState == OverrideSaveState.Saving
    val hasUnsavedInvalidChanges = editSession?.hasUnsavedInvalidChanges == true

    fun requestExit() {
        when {
            stringMapEditorState != null -> stringMapEditorState = null
            jsonEditorState != null -> jsonEditorState = null
            isSaving -> Unit
            hasUnsavedInvalidChanges -> showDiscardDialog.value = true
            else -> {
                viewModel.flushDraftSave {
                    viewModel.clearEditSession()
                    navigator.navigateUp()
                }
            }
        }
    }

    BackHandler(onBack = ::requestExit)

    Scaffold(
        topBar = {
            TopBar(
                title = if (isNewConfig) MLang.Override.Edit.TitleNew else MLang.Override.Edit.TitleEdit,
            )
        },
    ) { paddingValues ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(

            innerPadding = combinePaddingValues(paddingValues, mainLikePadding),
            lazyListState = editorListState,
        ) {
            editSession?.let { session ->
                OverrideEditContent(
                    name = session.name,
                    description = session.description,
                    config = session.config,
                    referenceCatalog = referenceCatalog ?: return@let,
                    currentConfigProvider = {
                        viewModel.editSession.value?.config ?: session.config
                    },
                    expandedSectionNames = expandedSectionNames,
                    onNameChange = viewModel::updateDraftName,
                    onDescriptionChange = viewModel::updateDraftDescription,
                    onConfigChange = { updatedConfig ->
                        viewModel.updateDraftConfig(
                            updatedConfig = updatedConfig,
                            saveImmediately = true,
                        )
                    },
                    onSectionToggle = { section ->
                        expandedSectionNames = if (section.name in expandedSectionNames) {
                            expandedSectionNames - section.name
                        } else {
                            expandedSectionNames + section.name
                        }
                    },
                    onEditStringList = onOpenStringListEditor,
                    onEditRuleList = { title, values, availableModes, selectedMode, referenceCatalog, callback ->
                        onOpenRuleListEditor(
                            title,
                            values,
                            availableModes,
                            selectedMode,
                            referenceCatalog,
                            callback,
                        )
                    },
                    onEditStringMap = { title, keyPlaceholder, valuePlaceholder, value, callback ->
                        stringMapEditorState = StringMapEditorDialogState(
                            title = title,
                            keyPlaceholder = keyPlaceholder,
                            valuePlaceholder = valuePlaceholder,
                            value = value,
                            onValueChange = callback,
                        )
                    },
                    onEditJson = { title, placeholder, value, callback ->
                        jsonEditorState = JsonEditorDialogState(
                            title = title,
                            placeholder = placeholder,
                            value = value,
                            onValueChange = callback,
                        )
                    },
                    onEditObjectList = { type, title, values, availableModes, selectedMode, referenceCatalog, callback ->
                        onOpenObjectListEditor(
                            type,
                            title,
                            values,
                            availableModes,
                            selectedMode,
                            referenceCatalog,
                            callback,
                        )
                    },
                    onEditObjectMap = { type, title, values, availableModes, selectedMode, callback ->
                        onOpenObjectMapEditor(
                            type,
                            title,
                            values,
                            availableModes,
                            selectedMode,
                            callback,
                        )
                    },
                    onEditSubRules = { title, values, availableModes, selectedMode, referenceCatalog, callback ->
                        onOpenSubRulesEditor(
                            title,
                            values,
                            availableModes,
                            selectedMode,
                            referenceCatalog,
                            callback,
                        )
                    },
                )
            }
        }

        AppDialog(
            show = showDiscardDialog.value,
            title = MLang.Override.Edit.EmptyName.Title,
            summary = MLang.Override.Edit.EmptyName.Summary,
            onDismissRequest = { showDiscardDialog.value = false },
        ) {
            DialogButtonRow(
                onCancel = { showDiscardDialog.value = false },
                onConfirm = {
                    showDiscardDialog.value = false
                    viewModel.clearEditSession()
                    navigator.navigateUp()
                },
                cancelText = MLang.Override.Edit.Button.Cancel,
                confirmText = MLang.Override.Edit.Button.Discard,
            )
        }

        StringMapEditorDialog(
            show = stringMapEditorState != null,
            title = stringMapEditorState?.title.orEmpty(),
            keyPlaceholder = stringMapEditorState?.keyPlaceholder.orEmpty(),
            valuePlaceholder = stringMapEditorState?.valuePlaceholder.orEmpty(),
            value = stringMapEditorState?.value,
            onValueChange = stringMapEditorState?.onValueChange ?: {},
            onDismiss = { stringMapEditorState = null },
        )

        JsonTextEditorDialog(
            show = jsonEditorState != null,
            title = jsonEditorState?.title.orEmpty(),
            placeholder = jsonEditorState?.placeholder.orEmpty(),
            value = jsonEditorState?.value,
            onValueChange = jsonEditorState?.onValueChange ?: {},
            onDismiss = { jsonEditorState = null },
        )
    }
}

private data class StringMapEditorDialogState(
    val title: String,
    val keyPlaceholder: String,
    val valuePlaceholder: String,
    val value: Map<String, String>?,
    val onValueChange: (Map<String, String>?) -> Unit,
)

private data class JsonEditorDialogState(
    val title: String,
    val placeholder: String,
    val value: String?,
    val onValueChange: (String?) -> Unit,
)
