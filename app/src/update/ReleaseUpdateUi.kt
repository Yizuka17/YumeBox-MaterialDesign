package com.github.yumelira.yumebox.update

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.github.yumelira.yumebox.common.util.openUrl
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.screen.about.UpdateCandidateDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Uses the fork's existing dialog for foreground automatic and manual update checks. */
@Composable
fun ReleaseUpdateHost() {
    val manager = koinInject<GitHubUpdateManager>()
    val candidate by manager.candidate.collectAsState()
    val progress by manager.progress.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    UpdateCandidateDialog(
        candidate = candidate,
        downloadProgress = progress,
        onDismiss = { manager.dismiss() },
        onCancelDownload = manager::cancelDownload,
        onDownload = { update ->
            if (update.asset == null) openUrl(context, update.releaseUrl)
            else scope.launch {
                try { manager.downloadAndInstall(update); manager.dismiss() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { context.toast(e.message ?: "下载失败，请重试") }
            }
        },
    )
}
