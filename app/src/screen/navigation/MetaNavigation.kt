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


import androidx.compose.runtime.Composable
import com.github.yumelira.yumebox.feature.editor.language.LanguageScope
import com.github.yumelira.yumebox.feature.meta.presentation.screen.CustomRoutingScreen
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.util.OverrideEditorStore

@Composable
fun CustomRoutingRoute(navigator: Navigator) {
    CustomRoutingScreen(
        onNavigateBack = { navigator.pop() },
        onOpenYamlEditor = { title, content, onSave ->
            OverrideEditorStore.setupConfigPreview(
                title = title,
                content = content,
                language = LanguageScope.Yaml,
                callback = onSave,
            )
            navigator.push(Route.OverrideConfigPreview)
        },
    )
}
