package com.github.yumelira.yumebox.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.github.yumelira.yumebox.common.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GlobalBackupActions(val export: () -> Unit, val restore: () -> Unit)

@Composable
fun rememberGlobalBackupActions(): GlobalBackupActions {
    val context = LocalContext.current
    val activity = androidx.activity.compose.LocalActivity.current
    val controller = koinInject<GlobalBackupController>()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var prepared by remember { mutableStateOf<PreparedGlobalBackup?>(null) }
    val latestPrepared by rememberUpdatedState(prepared)
    DisposableEffect(controller) { onDispose { latestPrepared?.let(controller::discard) } }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = "正在导出备份…"
            try {
                controller.exportTo(uri)
                context.toast("备份已导出")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { context.toast("备份失败：${e.message}") }
            finally { busy = null }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = "正在检查备份完整性…"
            try {
                prepared?.let(controller::discard)
                prepared = controller.prepareImport(uri)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { context.toast("无法导入备份：${e.message}") }
            finally { busy = null }
        }
    }
    prepared?.takeIf { busy == null }?.let { backup ->
        AlertDialog(
            onDismissRequest = { controller.discard(backup); prepared = null },
            title = { Text(if (backup.legacySettingsOnly) "恢复旧版备份" else "恢复备份") },
            text = { Text(if (backup.legacySettingsOnly) "此文件只恢复旧版设置，不包含完整配置。" else
                "${backup.profileCount} 个配置 · ${backup.overrideCount} 个覆写\n恢复将替换现有数据，并重启正在运行的服务。") },
            dismissButton = { TextButton(onClick = { controller.discard(backup); prepared = null }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        busy = "正在恢复备份…"
                        try {
                            controller.restore(backup)
                            context.toast("备份恢复完成")
                            activity?.recreate()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { context.toast("恢复失败，已尝试回滚：${e.message}") }
                        finally { controller.discard(backup); prepared = null; busy = null }
                    }
                }) { Text("恢复") }
            },
        )
    }
    busy?.let { message ->
        AlertDialog(onDismissRequest = {}, title = { Text(message) }, text = { LinearProgressIndicator() }, confirmButton = {})
    }
    return GlobalBackupActions(
        export = {
            if (busy == null) export.launch("YumeBox-global-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())}.zip")
        },
        restore = { if (busy == null) restore.launch("*/*") },
    )
}
