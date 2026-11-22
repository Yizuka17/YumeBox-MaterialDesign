package com.github.yumelira.yumebox.screen.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.common.util.openUrl
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
@Destination<RootGraph>
fun ReadmeScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val repository = remember(context) { ReadmeRepository(context.applicationContext) }
    var path by rememberSaveable { mutableStateOf("README.md") }
    var refresh by remember { mutableIntStateOf(0) }
    var markdown by remember(path) { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val blocks = remember(markdown) { parseReadme(markdown) }
    var history by rememberSaveable { mutableStateOf(listOf<String>()) }

    LaunchedEffect(path, refresh) {
        loading = true
        try {
            if (markdown.isEmpty()) markdown = repository.stored(path)
            markdown = repository.refresh(path, force = refresh > 0)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("Readme", "Unable to refresh " + path, error)
            if (refresh > 0) context.toast("无法刷新，显示离线内容")
        } finally { loading = false }
    }
    val openLink: (String) -> Unit = { target ->
        runCatching { readmeLink(path, target) }.getOrNull()?.let { uri ->
            val local = localReadmePath(uri)
            if (local != null) {
                if (local != path) {
                    history = history + path
                    path = local
                    scope.launch { state.scrollToItem(0) }
                } else if (uri.fragment != null) {
                    val index = blocks.indexOfFirst { it.anchor == uri.fragment }
                    if (index >= 0) scope.launch { state.animateScrollToItem(index) }
                }
            } else if (uri.scheme in setOf("https", "http")) openUrl(context, uri.toString())
        }
    }
    BackHandler(history.isNotEmpty()) {
        path = history.last()
        history = history.dropLast(1)
        scope.launch { state.scrollToItem(0) }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopBar(title = "Readme", actions = {
                IconButton(onClick = { refresh++ }, enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(AppMd3Icons.Action.Refresh, contentDescription = "刷新Readme")
                }
            })
        },
    ) { padding ->
        SelectionContainer {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(blocks, key = { index, _ -> path + ":" + index }) { _, block ->
                    NativeReadmeBlock(block.node, path, openLink)
                }
            }
        }
    }
}
