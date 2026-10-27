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

package com.github.yumelira.yumebox.feature.meta.presentation.screen


import com.github.yumelira.yumebox.presentation.theme.UiDp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.github.yumelira.yumebox.core.model.ConfigurationOverride
import com.github.yumelira.yumebox.core.model.officialMrsPresetIconUrl
import com.github.yumelira.yumebox.data.util.*
import com.github.yumelira.yumebox.feature.meta.presentation.viewmodel.CustomRoutingViewModel
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.RoutingSwitchCard
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CustomRoutingScreen(
    onNavigateBack: () -> Unit,
) {
    val viewModel: CustomRoutingViewModel = koinViewModel()
    val config by viewModel.config.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var showExitError by remember { mutableStateOf(false) }

    val selectedUrlTestRegions = remember { mutableStateListOf<OverridePresetRegion>() }
    val selectedFallbackRegions = remember { mutableStateListOf<OverridePresetRegion>() }
    val enabledItems = remember { mutableStateListOf<OverridePresetItem>() }
    var enableUrlTestGroup by remember { mutableStateOf(true) }
    var enableFallbackGroup by remember { mutableStateOf(false) }
    var hasInitializedSelection by remember { mutableStateOf(false) }
    var isDirty by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(config, isLoading) {
        if (isLoading || error != null) return@LaunchedEffect
        if (hasInitializedSelection && isDirty) return@LaunchedEffect
        val initialSelection = inferPresetTemplateSelection(config)
        selectedUrlTestRegions.clear()
        selectedUrlTestRegions.addAll(sortPresetRegions(initialSelection.urlTestRegions))
        selectedFallbackRegions.clear()
        selectedFallbackRegions.addAll(sortPresetRegions(initialSelection.fallbackRegions))
        enabledItems.clear()
        enabledItems.addAll(sortPresetItems(initialSelection.enabledItems))
        enableUrlTestGroup = initialSelection.enableUrlTestGroup
        enableFallbackGroup = initialSelection.enableFallbackGroup
        hasInitializedSelection = true
        isDirty = false
    }

    val scrollBehavior = MiuixScrollBehavior()

    fun saveAndExit() {
        if (isSaving) return
        if (!isDirty) {
            onNavigateBack()
            return
        }
        if (!hasInitializedSelection || isLoading) return
        val newSelection = OverridePresetTemplateSelection(
            urlTestRegions = selectedUrlTestRegions.toSet(),
            fallbackRegions = selectedFallbackRegions.toSet(),
            enabledItems = enabledItems.toSet(),
            enableUrlTestGroup = enableUrlTestGroup,
            enableFallbackGroup = enableFallbackGroup,
        )
        val newConfig = applyPresetTemplateToConfig(
            base = ConfigurationOverride(),
            selection = newSelection,
        )
        scope.launch {
            isSaving = true
            try {
                if (viewModel.saveConfig(newConfig)) {
                    isDirty = false
                    onNavigateBack()
                } else {
                    showExitError = true
                }
            } finally {
                isSaving = false
            }
        }
    }

    BackHandler {
        saveAndExit()
    }

    if (error != null && !hasInitializedSelection && !isLoading) {
        AlertDialog(
            onDismissRequest = onNavigateBack,
            title = { androidx.compose.material3.Text("读取失败") },
            text = { androidx.compose.material3.Text(error.orEmpty()) },
            confirmButton = {
                TextButton(onClick = onNavigateBack) { androidx.compose.material3.Text("返回") }
            },
        )
    } else if (showExitError) {
        AlertDialog(
            onDismissRequest = { showExitError = false },
            title = { androidx.compose.material3.Text("保存失败") },
            text = { androidx.compose.material3.Text(error ?: "请重试，或放弃本次修改后返回。") },
            confirmButton = {
                TextButton(onClick = { showExitError = false; saveAndExit() }) {
                    androidx.compose.material3.Text("重试")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitError = false; onNavigateBack() }) {
                    androidx.compose.material3.Text("放弃修改并返回")
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopBar(
                title = MLang.MetaFeature.CustomRouting.Title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = ::saveAndExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { paddingValues ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(paddingValues, mainLikePadding),
        ) {
            item(key = "feature-explanation") {
                Card(modifier = Modifier.padding(top = UiDp.dp16)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiDp.dp16, vertical = UiDp.dp14),
                        verticalArrangement = Arrangement.spacedBy(UiDp.dp4),
                    ) {
                        Text(
                            text = MLang.MetaFeature.CustomRouting.Description,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }

            item(key = "preset-group-types") {
                RoutingSwitchCard(
                    title = MLang.MetaFeature.CustomRouting.GroupTypeTitle,
                    items = listOf("urltest", "fallback"),
                    iconUrl = { type ->
                        officialMrsPresetIconUrl(
                            if (type == "urltest") "Urltest" else "Available",
                        )
                    },
                    isChecked = { type ->
                        when (type) {
                            "urltest" -> enableUrlTestGroup
                            else -> enableFallbackGroup
                        }
                    },
                    onCheckedChange = { item, checked ->
                        when (item) {
                            "urltest" -> {
                                enableUrlTestGroup = checked
                                isDirty = true
                            }
                            "fallback" -> {
                                enableFallbackGroup = checked
                                isDirty = true
                            }
                        }
                    },
                    itemTitle = {
                        if (it == "urltest") {
                            MLang.MetaFeature.CustomRouting.GroupTypeUrlTest
                        } else {
                            MLang.MetaFeature.CustomRouting.GroupTypeFallback
                        }
                    },
                )
            }

            item(key = "preset-urltest-regions") {
                RoutingSwitchCard(
                    title = MLang.MetaFeature.CustomRouting.UrlTestRegionGroupTitle,
                    items = orderedPresetRegions(),
                    iconUrl = OverridePresetRegion::icon,
                    isChecked = { region -> region in selectedUrlTestRegions },
                    onCheckedChange = { region, checked ->
                        toggleSelection(selectedUrlTestRegions, region, checked)
                        isDirty = true
                    },
                    itemTitle = OverridePresetRegion::displayName,
                )
            }

            item(key = "preset-fallback-regions") {
                RoutingSwitchCard(
                    title = MLang.MetaFeature.CustomRouting.FallbackRegionGroupTitle,
                    items = orderedPresetRegions(),
                    iconUrl = OverridePresetRegion::icon,
                    isChecked = { region -> region in selectedFallbackRegions },
                    onCheckedChange = { region, checked ->
                        toggleSelection(selectedFallbackRegions, region, checked)
                        isDirty = true
                    },
                    itemTitle = OverridePresetRegion::displayName,
                )
            }

            item(key = "preset-base-items") {
                RoutingSwitchCard(
                    title = MLang.Override.Draft.BasicRouting,
                    items = orderedBasePresetItems(),
                    iconUrl = OverridePresetItem::icon,
                    isChecked = { item -> item in enabledItems },
                    onCheckedChange = { item, checked ->
                        toggleSelection(enabledItems, item, checked)
                        isDirty = true
                    },
                    itemTitle = OverridePresetItem::title,
                )
            }

            item(key = "preset-service-items") {
                RoutingSwitchCard(
                    title = MLang.Override.Draft.ServiceRouting,
                    items = orderedServicePresetItems(),
                    iconUrl = OverridePresetItem::icon,
                    isChecked = { item -> item in enabledItems },
                    onCheckedChange = { item, checked ->
                        toggleSelection(enabledItems, item, checked)
                        isDirty = true
                    },
                    itemTitle = OverridePresetItem::title,
                )
            }
        }
    }
}

private fun <T> toggleSelection(
    items: MutableList<T>,
    item: T,
    checked: Boolean,
) {
    if (checked) {
        if (item !in items) {
            items.add(item)
        }
    } else {
        items.remove(item)
    }
}
