package com.github.yumelira.yumebox.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class ScreenSectionScope {
    internal class Section(val key: Any, val type: Any?, val content: @Composable () -> Unit)
    internal val sections = mutableListOf<Section>()
    fun item(key: Any? = null, contentType: Any? = null, content: @Composable () -> Unit) {
        sections += Section(key ?: sections.size, contentType, content)
    }
}

/** Each semantic settings section stays together; only the available column count changes. */
@Composable
fun ScreenSectionGrid(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: Any? = null,
    lazyListState: LazyListState = rememberLazyListState(),
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    content: ScreenSectionScope.() -> Unit,
) {
    val sections = ScreenSectionScope().apply(content).sections
    val scrollKey = rememberSaveable { "settings_sections_${java.util.UUID.randomUUID()}" }
    if (!rememberTabletLayout()) {
        ScreenLazyColumn(innerPadding = innerPadding, modifier = modifier, lazyListState = lazyListState,
            topPadding = topPadding, bottomPadding = bottomPadding) {
            sections.forEach { section -> item(key = section.key, contentType = section.type) { section.content() } }
        }
    } else BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = minOf(sections.size.coerceAtLeast(1),
            (maxWidth / (360.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))).toInt().coerceAtLeast(1))
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val state = rememberRetainedStaggeredGridState(scrollKey,
            "${configuration.screenWidthDp}:${configuration.screenHeightDp}:${configuration.fontScale}:$columns")
        val bottomBar = LocalBottomBarScrollBehavior.current
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns), state = state,
            modifier = Modifier.fillMaxSize().let { if (bottomBar != null) it.nestedScroll(bottomBar.nestedScrollConnection) else it },
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + topPadding,
                bottom = innerPadding.calculateBottomPadding() + bottomPadding,
            ),
            verticalItemSpacing = 0.dp,
        ) {
            items(sections, key = { it.key }, contentType = { it.type }) { section ->
                Column(Modifier.fillMaxWidth()) { section.content() }
            }
        }
    }
}
