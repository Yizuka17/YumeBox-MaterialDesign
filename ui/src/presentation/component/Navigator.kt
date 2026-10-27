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

package com.github.yumelira.yumebox.presentation.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew

/**
 * Thin wrapper over the Decompose navigation stack with Compose-observable route history.
 *
 * Replaces compose-destinations' DestinationsNavigator. The [push]/[pop]/[replaceAll]/[popUntil]
 * methods cover every navigation pattern previously used in YumeBox. The
 * [navigateUp]/[popBackStack] aliases keep call sites that came from the compose-destinations API
 * churn-free.
 */
@Stable
class Navigator(initial: List<Any> = emptyList()) {
    val backStack = mutableStateListOf<Any>().apply { addAll(initial) }
    val navigation = StackNavigation<Any>()
    /** Pushes [key] onto the stack unless it is already on top (mirrors launchSingleTop). */
    fun push(key: Any) {
        if (backStack.lastOrNull() != key) {
            backStack.add(key)
            navigation.pushNew(key)
        }
    }

    /** Pops the top entry. The root entry is never popped. Returns true if a pop happened. */
    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        navigation.pop()
        return true
    }

    /** Keeps compatibility callers in sync after Decompose applies a navigation transaction. */
    fun syncBackStack(configurations: List<Any>) {
        if (backStack == configurations) return
        backStack.clear()
        backStack.addAll(configurations)
    }

    /** Replaces the entire stack with [keys]. */
    fun replaceAll(keys: List<Any>) {
        val uniqueKeys = keys.distinct()
        if (backStack == uniqueKeys) return
        backStack.clear()
        backStack.addAll(uniqueKeys)
        navigation.navigate { uniqueKeys }
    }

    /** Replaces the top entry with [key]. */
    fun replace(key: Any) {
        if (backStack.lastOrNull() == key) return
        if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.lastIndex)
        }
        backStack.add(key)
        navigation.navigate { backStack.toList() }
    }

    /**
     * Pops entries until [predicate] is satisfied for the top entry (inclusive of matched stays).
     */
    fun popUntil(predicate: (Any) -> Boolean) {
        val original = backStack.toList()
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
        if (backStack == original) return
        navigation.navigate { backStack.toList() }
    }

    /** Compatibility alias for [pop] (compose-destinations API). */
    fun navigateUp(): Boolean = pop()

    /** Compatibility alias for [pop] (compose-destinations API). */
    fun popBackStack(): Boolean = pop()
}
