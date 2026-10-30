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

package com.github.yumelira.yumebox.presentation.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect

/**
 * Retains scroll states for root-level pages outside individual page composition.
 *
 * Main pager pages can be disposed and recreated when navigating to another destination
 * or when the ACG home changes layout/prefetch behavior. Existing list/grid owners retain
 * their state; staggered grids retain only a position, since lane layout and drag/fling
 * state must never be carried into a different viewport or attached to two grids.
 */
object PageScrollStateStore {
    private val lazyListStates = linkedMapOf<String, LazyListState>()
    private val lazyGridStates = linkedMapOf<String, LazyGridState>()
    private val staggeredGridPositions = linkedMapOf<String, Pair<Int, Int>>()

    fun staggeredGridState(key: String): LazyStaggeredGridState =
        staggeredGridPositions[key].let { position ->
            LazyStaggeredGridState(position?.first ?: 0, position?.second ?: 0)
        }

    fun saveStaggeredGridPosition(key: String, state: LazyStaggeredGridState) {
        staggeredGridPositions[key] = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
    }

    fun lazyListState(key: String): LazyListState {
        return lazyListStates.getOrPut(key) { LazyListState() }
    }

    fun lazyGridState(key: String): LazyGridState {
        return lazyGridStates.getOrPut(key) { LazyGridState() }
    }

    fun clear(key: String) {
        lazyListStates.remove(key)
        lazyGridStates.remove(key)
        staggeredGridPositions.remove(key)
    }

    fun clearAll() {
        lazyListStates.clear()
        lazyGridStates.clear()
        staggeredGridPositions.clear()
    }
}

@Composable
fun rememberRetainedLazyListState(key: String): LazyListState {
    return remember(key) { PageScrollStateStore.lazyListState(key) }
}

@Composable
fun rememberRetainedLazyGridState(key: String): LazyGridState {
    return remember(key) { PageScrollStateStore.lazyGridState(key) }
}

@Composable
fun rememberRetainedStaggeredGridState(key: String, layoutKey: Any? = null): LazyStaggeredGridState {
    // Retain only a position. A drag/fling mutex and layout attachment belong to one viewport.
    val state = remember(key, layoutKey) { PageScrollStateStore.staggeredGridState(key) }
    LaunchedEffect(key, state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { PageScrollStateStore.saveStaggeredGridPosition(key, state) }
    }
    DisposableEffect(key, state) {
        onDispose { PageScrollStateStore.saveStaggeredGridPosition(key, state) }
    }
    return state
}
