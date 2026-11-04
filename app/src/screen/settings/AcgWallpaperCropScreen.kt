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

package com.github.yumelira.yumebox.screen.settings


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.presentation.component.calculateWallpaperViewportLayout
import com.github.yumelira.yumebox.presentation.component.drawWallpaper
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.screen.acg.AcgWallpaperManager
import com.github.yumelira.yumebox.screen.acg.WallpaperCrop
import com.github.yumelira.yumebox.screen.acg.WallpaperProfile
import com.github.yumelira.yumebox.screen.acg.AcgFrameKey
import com.github.yumelira.yumebox.screen.acg.acgWallpaperProfile
import com.github.yumelira.yumebox.screen.acg.predictedAcgFrameRatio
import com.github.yumelira.yumebox.screen.acg.rememberAcgWallpaperPainter
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
@Destination<RootGraph>
fun AcgWallpaperCropScreen(
    navigator: DestinationsNavigator,
    wallpaperUri: String,
    initialZoom: Float = 1f,
    initialBiasX: Float = 0f,
    initialBiasY: Float = 0f,
    profile: String = "portrait",
    poolPath: String = "",
) {
    val viewModel = koinViewModel<AppSettingsViewModel>()
    val manager = koinInject<AcgWallpaperManager>()
    val targetProfile = WallpaperProfile.entries.firstOrNull { it.key == profile } ?: WallpaperProfile.Portrait
    val frames by manager.heroFrames.collectAsState()
    val expanded by viewModel.acgSidebarExpanded.state.collectAsState()
    val tabletEnabled by viewModel.acgTabletModeEnabled.state.collectAsState()
    val pageScale by viewModel.pageScale.state.collectAsState()
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val currentProfile = with(density) {
        acgWallpaperProfile(window.width.toDp(), window.height.toDp(), pageScale, tabletEnabled)
    }
    val key = AcgFrameKey(window, density.density, targetProfile, expanded)
    val measured = frames[key]
    val ratio = if (measured != null) measured.width.toFloat() / measured.height
        else predictedAcgFrameRatio(key, density, currentProfile)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var zoom by rememberSaveable(wallpaperUri) { mutableFloatStateOf(initialZoom.coerceIn(1f, 5f)) }
    var biasX by rememberSaveable(wallpaperUri) { mutableFloatStateOf(initialBiasX.coerceIn(-1f, 1f)) }
    var biasY by rememberSaveable(wallpaperUri) { mutableFloatStateOf(initialBiasY.coerceIn(-1f, 1f)) }
    val painter = rememberAcgWallpaperPainter(wallpaperUri)
    val intrinsic = painter.intrinsicSize
    val imageWidth = intrinsic.width.takeIf { it.isFinite() && it > 0f }
    val imageHeight = intrinsic.height.takeIf { it.isFinite() && it > 0f }
    val title = if (targetProfile == WallpaperProfile.Tablet) MLang.AppSettings.Wallpaper.Tablet else MLang.AppSettings.Wallpaper.Portrait

    Scaffold(
        topBar = {
            TopBar(
                title = MLang.AppSettings.Wallpaper.AdjustPosition + " (" + title + ")",
                navigationIcon = {
                    IconButton(enabled = !saving, onClick = { navigator.popBackStack() }) {
                        Icon(AppMd3Icons.Navigation.Back, MLang.Component.Navigation.Back)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
            val frameLabel = MLang.AppSettings.Wallpaper.AdjustPosition
            val frameColor = MaterialTheme.colorScheme.primary
            val matte = MaterialTheme.colorScheme.scrim.copy(alpha = 0.64f)
            Canvas(
                Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .semantics { contentDescription = frameLabel }
                    .pointerInput(ratio, imageWidth, imageHeight, saving) {
                        if (!saving && imageWidth != null && imageHeight != null) {
                            detectTransformGestures { centroid, pan, factor, _ ->
                                val (origin, frameSize) = cropFrame(size.width.toFloat(), size.height.toFloat(), ratio)
                                val current = calculateWallpaperViewportLayout(
                                    frameSize.width, frameSize.height, imageWidth, imageHeight, zoom, biasX, biasY,
                                )
                                val nextZoom = (zoom * factor).coerceIn(1f, 5f)
                                val next = calculateWallpaperViewportLayout(
                                    frameSize.width, frameSize.height, imageWidth, imageHeight, nextZoom,
                                )
                                val anchor = centroid - origin
                                val offset = anchor + (current.imageOffset - anchor) * (nextZoom / zoom) + pan
                                biasX = if (next.maxShiftX > 0.5f) (-offset.x / next.maxShiftX - 1f).coerceIn(-1f, 1f) else 0f
                                biasY = if (next.maxShiftY > 0.5f) (-offset.y / next.maxShiftY - 1f).coerceIn(-1f, 1f) else 0f
                                zoom = nextZoom
                            }
                        }
                    },
            ) {
                val (origin, frameSize) = cropFrame(size.width, size.height, ratio)
                val layout = calculateWallpaperViewportLayout(
                    frameSize.width, frameSize.height, imageWidth, imageHeight, zoom, biasX, biasY,
                )
                if (imageWidth != null && imageHeight != null) drawWallpaper(painter, layout, origin)
                val end = origin + Offset(frameSize.width, frameSize.height)
                drawRect(matte, size = Size(size.width, origin.y))
                drawRect(matte, topLeft = Offset(0f, end.y), size = Size(size.width, size.height - end.y))
                drawRect(matte, topLeft = Offset(0f, origin.y), size = Size(origin.x, frameSize.height))
                drawRect(matte, topLeft = Offset(end.x, origin.y), size = Size(size.width - end.x, frameSize.height))
                drawRect(Color.Black.copy(alpha = 0.65f), origin, frameSize, style = Stroke(4.dp.toPx()))
                drawRect(frameColor, origin, frameSize, style = Stroke(2.dp.toPx()))
            }
            val zoomLabel = "${MLang.AppSettings.Wallpaper.CropZoom} (${(zoom * 100f).roundToInt()}%)"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Slider(
                    value = zoom,
                    onValueChange = { zoom = it },
                    enabled = !saving && imageWidth != null,
                    valueRange = 1f..5f,
                    modifier = Modifier.weight(1f).semantics { stateDescription = zoomLabel },
                )
                TextButton(enabled = !saving, onClick = { zoom = 1f; biasX = 0f; biasY = 0f }) {
                    Text(MLang.AppSettings.Wallpaper.ResetCrop)
                }
                Button(
                    enabled = !saving && imageWidth != null && imageHeight != null,
                    onClick = {
                        saving = true
                        scope.launch {
                            try {
                                if (poolPath.isNotEmpty()) {
                                    manager.setPoolCrop(targetProfile, poolPath, WallpaperCrop(zoom, biasX, biasY))
                                } else {
                                    val uri = manager.setStatic(targetProfile, wallpaperUri, zoom, biasX, biasY)
                                    if (targetProfile == WallpaperProfile.Portrait) {
                                        viewModel.onAcgWallpaperUriChange(uri)
                                        viewModel.onAcgWallpaperCropChange(zoom, biasX, biasY)
                                    }
                                }
                                navigator.popBackStack()
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { context.toast(MLang.AppSettings.Wallpaper.SaveFailed) }
                            finally { saving = false }
                        }
                    },
                ) { Text(MLang.AppSettings.Button.Apply) }
            }
        }
    }
}

private fun cropFrame(width: Float, height: Float, ratio: Float): Pair<Offset, Size> {
    val frameWidth = minOf(width * 0.84f, height * 0.84f * ratio)
    val frameHeight = frameWidth / ratio
    return Offset((width - frameWidth) / 2f, (height - frameHeight) / 2f) to Size(frameWidth, frameHeight)
}
