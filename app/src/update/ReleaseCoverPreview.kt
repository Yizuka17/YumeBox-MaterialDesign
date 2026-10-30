package com.github.yumelira.yumebox.update

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.LoadState
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.presentation.component.AppDialog
import com.github.yumelira.yumebox.presentation.component.PreferenceValueItem
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ReleaseCoverPreview(source: String, onDismiss: () -> Unit, localImage: Boolean = false,
    description: String = "更新封面预览", saveTitle: String = "保存封面", onDelete: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember(source) { mutableFloatStateOf(1f) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var showSave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var pendingDocument by remember { mutableStateOf<DownloadedCover?>(null) }
    val imageState = rememberAsyncImageState()
    val imageInfo = (imageState.loadState as? LoadState.Success)?.result?.imageInfo
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cover = pendingDocument
        pendingDocument = null
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null && cover != null) {
            scope.launch {
                try {
                    ReleaseCoverFiles.write(context, cover, result.data!!.data!!)
                    context.toast("图片已保存")
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { context.toast(e.message ?: "保存图片失败") }
            }
        }
    }

    fun saveImage() {
        if (saving) return
        scope.launch {
            saving = true
            try {
                val cover = if (localImage) ReleaseCoverFiles.localImage(context, source) else ReleaseCoverFiles.download(context, source)
                if (Build.VERSION.SDK_INT >= 29) {
                    ReleaseCoverFiles.saveToGallery(context, cover)
                    context.toast("已保存到 图片/YumeBox")
                } else {
                    pendingDocument = cover
                    createDocument.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        type = cover.mimeType
                        addCategory(Intent.CATEGORY_OPENABLE)
                        putExtra(Intent.EXTRA_TITLE, cover.name)
                    })
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { context.toast(e.message ?: "保存图片失败，请重试") }
            finally { saving = false }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
            AsyncImage(
                uri = source,
                state = imageState,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .onSizeChanged { bounds = it }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    }
                    .semantics { onLongClick("保存图片") { showSave = true; true } }
                    .pointerInput(source) {
                        detectTapGestures(
                            onLongPress = { showSave = true },
                            onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero },
                        )
                    }
                    .pointerInput(source, bounds, imageInfo) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val fit = imageInfo?.takeIf { it.width > 0 && it.height > 0 }?.let {
                                minOf(bounds.width.toFloat() / it.width, bounds.height.toFloat() / it.height)
                            } ?: 1f
                            val width = imageInfo?.width?.times(fit) ?: bounds.width.toFloat()
                            val height = imageInfo?.height?.times(fit) ?: bounds.height.toFloat()
                            val limitX = ((width * scale - bounds.width) / 2f).coerceAtLeast(0f)
                            val limitY = ((height * scale - bounds.height) / 2f).coerceAtLeast(0f)
                            offset = Offset((offset.x + pan.x).coerceIn(-limitX, limitX), (offset.y + pan.y).coerceIn(-limitY, limitY))
                        }
                    },
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.extraLarge)) {
                Icon(Icons.Default.Close, contentDescription = "关闭预览", tint = Color.White)
            }
            if (localImage) TextButton(onClick = { saveImage() }, enabled = !saving,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text(saveTitle) }
            if (saving) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        AppDialog(
                show = showSave,
                onDismissRequest = { showSave = false },
                title = if (onDelete != null) MLang.AppSettings.Wallpaper.ImageActions else saveTitle,
        ) {
                Column {
                    PreferenceValueItem(title = MLang.AppSettings.Wallpaper.SaveImage, enabled = !saving, onClick = {
                        showSave = false
                        saveImage()
                    })
                    if (onDelete != null) PreferenceValueItem(title = MLang.AppSettings.Wallpaper.Remove, onClick = {
                        showSave = false
                        onDelete()
                    })
                }
        }
    }
}
