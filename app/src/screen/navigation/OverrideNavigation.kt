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



package com.github.yumelira.yumebox.screen.navigation

import androidx.compose.runtime.Composable
import com.github.yumelira.yumebox.feature.editor.presentation.language.LanguageScope
import com.github.yumelira.yumebox.feature.editor.presentation.screen.ConfigPreviewScreen
import com.github.yumelira.yumebox.presentation.screen.*
import com.github.yumelira.yumebox.presentation.util.OverrideStructuredEditorStore
import com.github.yumelira.yumebox.presentation.viewmodel.OverrideConfigViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.OverrideConfigPreviewRouteDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideEditRouteDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideKeyedObjectDraftEditorRouteDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideKeyedObjectMapEditorRouteDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideObjectListEditorRouteDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideProxyDraftEditorRouteDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideProxyGroupDraftEditorRouteDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideRuleDraftEditorRouteDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideRuleListEditorRouteDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideStringListEditorRouteDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideSubRuleDraftEditorRouteDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideSubRuleMapEditorRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject
import androidx.compose.ui.platform.LocalContext
import com.github.yumelira.yumebox.common.util.toast

@Composable
@Destination<RootGraph>
fun OverrideScreen(navigator: DestinationsNavigator) {
    val overrideConfigViewModel: OverrideConfigViewModel = koinInject()
    val context = LocalContext.current

    OverrideListScreen(navigator = navigator, onEditConfig = { configId ->
        navigator.navigate(
            OverrideEditRouteDestination(
                configId = configId,
            )
        )
    }, onOpenCodeEditor = openCode@{ configId, configName ->
        val jsonContent = try {
            overrideConfigViewModel.getConfigJsonContent(configId) ?: error("配置文件不存在")
        } catch (e: Exception) {
            context.toast("打开配置失败：${e.message}")
            return@openCode
        }
        OverrideStructuredEditorStore.setupConfigPreview(
            title = configName,
            content = jsonContent,
            language = LanguageScope.Json,
            callback = { content ->
                overrideConfigViewModel.saveConfigJsonContent(configId, content)
            },
        )
        navigator.navigate(OverrideConfigPreviewRouteDestination)
    })
}

@Composable
@Destination<RootGraph>
fun OverrideEditRoute(
    navigator: DestinationsNavigator,
    configId: String,
) {
    OverrideEditScreen(
        navigator = navigator,
        configId = configId,
        onOpenStringListEditor = { title, placeholder, replaceValue, startValue, endValue, onReplaceChange, onStartChange, onEndChange ->
            val values = com.github.yumelira.yumebox.presentation.util.OverrideListModeValues(
                replaceValue = replaceValue,
                startValue = startValue,
                endValue = endValue,
            )
            val availableModes = listOf(
                com.github.yumelira.yumebox.presentation.util.OverrideListEditorMode.Replace,
                com.github.yumelira.yumebox.presentation.util.OverrideListEditorMode.Start,
                com.github.yumelira.yumebox.presentation.util.OverrideListEditorMode.End,
            )
            OverrideStructuredEditorStore.setupStringListEditor(
                title = title,
                placeholder = placeholder,
                availableModes = availableModes,
                selectedMode = com.github.yumelira.yumebox.presentation.util.resolveInitialEditorMode(
                    availableModes = availableModes,
                    values = values,
                ),
                values = values,
            ) { updatedValues ->
                onReplaceChange(updatedValues.replaceValue)
                onStartChange(updatedValues.startValue)
                onEndChange(updatedValues.endValue)
            }
            navigator.navigate(OverrideStringListEditorRouteDestination)
        },
        onOpenRuleListEditor = { title, values, availableModes, selectedMode, referenceCatalog, callback ->
            OverrideStructuredEditorStore.setupRuleEditor(
                title = title,
                availableModes = availableModes,
                selectedMode = selectedMode,
                values = values,
                referenceCatalog = referenceCatalog,
                callback = callback,
            )
            navigator.navigate(OverrideRuleListEditorRouteDestination)
        },
        onOpenObjectListEditor = { type, title, values, availableModes, selectedMode, referenceCatalog, callback ->
            OverrideStructuredEditorStore.setupObjectEditor(
                type = type,
                title = title,
                availableModes = availableModes,
                selectedMode = selectedMode,
                values = values,
                referenceCatalog = referenceCatalog,
                callback = callback,
            )
            navigator.navigate(OverrideObjectListEditorRouteDestination)
        },
        onOpenObjectMapEditor = { type, title, values, availableModes, selectedMode, callback ->
            OverrideStructuredEditorStore.setupKeyedObjectMapEditor(
                type = type,
                title = title,
                availableModes = availableModes,
                selectedMode = selectedMode,
                values = values,
                callback = callback,
            )
            navigator.navigate(OverrideKeyedObjectMapEditorRouteDestination)
        },
        onOpenSubRulesEditor = { title, values, availableModes, selectedMode, referenceCatalog, callback ->
            OverrideStructuredEditorStore.setupSubRuleGroupEditor(
                title = title,
                availableModes = availableModes,
                selectedMode = selectedMode,
                values = values,
                referenceCatalog = referenceCatalog,
                callback = callback,
            )
            navigator.navigate(OverrideSubRuleMapEditorRouteDestination)
        })
}

@Composable
@Destination<RootGraph>
fun OverrideRuleListEditorRoute(
    navigator: DestinationsNavigator,
) {
    OverrideRuleListEditorScreen(
        navigator = navigator,
        onOpenRuleDraftEditor = { title, initialValue, callback ->
            OverrideStructuredEditorStore.setupRuleDraftEditor(
                title = title,
                value = initialValue,
                callback = callback,
            )
            navigator.navigate(OverrideRuleDraftEditorRouteDestination)
        },
    )
}

@Composable
@Destination<RootGraph>
fun OverrideObjectListEditorRoute(
    navigator: DestinationsNavigator,
) {
    OverrideObjectListEditorScreen(
        navigator = navigator,
        onOpenProxyDraftEditor = { title, initialValue, callback ->
            OverrideStructuredEditorStore.setupProxyDraftEditor(
                title = title,
                value = initialValue,
                callback = callback,
            )
            navigator.navigate(OverrideProxyDraftEditorRouteDestination)
        },
        onOpenProxyGroupDraftEditor = { title, initialValue, callback ->
            OverrideStructuredEditorStore.setupProxyGroupDraftEditor(
                title = title,
                value = initialValue,
                callback = callback,
            )
            navigator.navigate(OverrideProxyGroupDraftEditorRouteDestination)
        },
    )
}

@Composable
@Destination<RootGraph>
fun OverrideKeyedObjectMapEditorRoute(
    navigator: DestinationsNavigator,
) {
    OverrideKeyedObjectMapEditorScreen(
        navigator = navigator,
        onOpenDraftEditor = { type, title, initialValue, callback ->
            OverrideStructuredEditorStore.setupKeyedObjectDraftEditor(
                type = type,
                title = title,
                value = initialValue,
                callback = callback,
            )
            navigator.navigate(OverrideKeyedObjectDraftEditorRouteDestination)
        },
    )
}

@Composable
@Destination<RootGraph>
fun OverrideSubRuleMapEditorRoute(
    navigator: DestinationsNavigator,
) {
    OverrideSubRuleMapEditorScreen(
        navigator = navigator,
        onOpenDraftEditor = { title, initialValue, callback ->
            OverrideStructuredEditorStore.setupSubRuleDraftEditor(
                title = title,
                value = initialValue,
                callback = callback,
            )
            navigator.navigate(OverrideSubRuleDraftEditorRouteDestination)
        },
    )
}

@Composable
@Destination<RootGraph>
fun OverrideStringListEditorRoute(
    navigator: DestinationsNavigator,
) {
    OverrideStringListEditorScreen(
        navigator = navigator,
    )
}

@Composable
@Destination<RootGraph>
fun OverrideRuleDraftEditorRoute(
    navigator: DestinationsNavigator,
) {
    OverrideRuleDraftEditorScreen(
        navigator = navigator,
    )
}

@Composable
@Destination<RootGraph>
fun OverrideProxyDraftEditorRoute(
    navigator: DestinationsNavigator,
) {
    OverrideProxyDraftEditorScreen(
        navigator = navigator,
    )
}

@Composable
@Destination<RootGraph>
fun OverrideProxyGroupDraftEditorRoute(
    navigator: DestinationsNavigator,
) {
    OverrideProxyGroupDraftEditorScreen(
        navigator = navigator,
    )
}

@Composable
@Destination<RootGraph>
fun OverrideKeyedObjectDraftEditorRoute(
    navigator: DestinationsNavigator,
) {
    OverrideKeyedObjectDraftEditorScreen(
        navigator = navigator,
    )
}

@Composable
@Destination<RootGraph>
fun OverrideSubRuleDraftEditorRoute(
    navigator: DestinationsNavigator,
) {
    OverrideSubRuleDraftEditorScreen(
        navigator = navigator,
        onOpenRuleListEditor = { title, values, availableModes, selectedMode, referenceCatalog, callback ->
            OverrideStructuredEditorStore.setupRuleEditor(
                title = title,
                availableModes = availableModes,
                selectedMode = selectedMode,
                values = values,
                referenceCatalog = referenceCatalog,
                callback = callback,
            )
            navigator.navigate(OverrideRuleListEditorRouteDestination)
        },
    )
}

@Composable
@Destination<RootGraph>
fun OverrideConfigPreviewRoute(
    navigator: DestinationsNavigator,
) {
    ConfigPreviewScreen(
        navigator = navigator,
        title = OverrideStructuredEditorStore.configPreviewTitle,
        initialContent = OverrideStructuredEditorStore.configPreviewContent,
        language = OverrideStructuredEditorStore.configPreviewLanguage,
        onSave = OverrideStructuredEditorStore.configPreviewCallback,
    )
}
