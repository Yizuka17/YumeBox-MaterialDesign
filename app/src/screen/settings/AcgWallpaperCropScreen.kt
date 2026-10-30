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


import com.github.yumelira.yumebox.presentation.theme.UiDp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.statusBarsPadding
import com.github.yumelira.yumebox.presentation.component.AppBackButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.net.toUri
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.presentation.component.calculateWallpaperViewportLayout
import com.github.yumelira.yumebox.presentation.theme.DEFAULT_ACG_WALLPAPER_THEME_SEED_ARGB
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.sketch.resize.Scale
import com.github.panpf.sketch.util.screenSize
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel
import com.github.yumelira.yumebox.screen.acg.AcgWallpaperManager
import com.github.yumelira.yumebox.screen.acg.WallpaperProfile
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
@Destination<RootGraph>
fun AcgWallpaperCropScreen(
    navigator: DestinationsNavigator,
    wallpaperUri: String,
    initialZoom: Float = 1f,
    initialBiasX: Float = 0f,
    initialBiasY: Float = 0f,
    profile: String = "portrait",
) {
    val viewModel = koinViewModel<AppSettingsViewModel>()
    val manager = koinInject<AcgWallpaperManager>()
    val targetProfile = WallpaperProfile.entries.firstOrNull { it.key == profile } ?: WallpaperProfile.Portrait
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var biasX by remember(wallpaperUri, initialBiasX) {
        mutableFloatStateOf(initialBiasX.coerceIn(-1f, 1f))
    }
    var biasY by remember(wallpaperUri, initialBiasY) {
        mutableFloatStateOf(initialBiasY.coerceIn(-1f, 1f))
    }
    val painter = rememberAsyncImagePainter(
        request = remember(wallpaperUri, context) {
            ImageRequest(context, wallpaperUri) {
                crossfade(true)
                precision(Precision.LESS_PIXELS)
                scale(Scale.CENTER_CROP)
                size(context.screenSize())
            }
        }
    )
    val density = LocalDensity.current
    val imageBounds by produceState<Pair<Int, Int>?>(initialValue = null, wallpaperUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(wallpaperUri.toUri())?.use { input ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.outWidth to options.outHeight
                    } else {
                        null
                    }
                }
            }.getOrNull()
        }
    }
    val wallpaperSeedColorArgb by produceState(initialValue = DEFAULT_ACG_WALLPAPER_THEME_SEED_ARGB, wallpaperUri) {
        value = withContext(Dispatchers.IO) {
            extractDominantWallpaperColorArgb(context, wallpaperUri) ?: DEFAULT_ACG_WALLPAPER_THEME_SEED_ARGB
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
            val canvasWidth = if (targetProfile == WallpaperProfile.Tablet) minOf(maxWidth, (maxHeight - UiDp.dp100).coerceAtLeast(UiDp.dp1) * 1.6f) else maxWidth
            val canvasHeight = if (targetProfile == WallpaperProfile.Tablet) canvasWidth / 1.6f else maxHeight
            val containerWidthPx = with(density) { canvasWidth.toPx() }.coerceAtLeast(1f)
            val containerHeightPx = with(density) { canvasHeight.toPx() }.coerceAtLeast(1f)
            val painterIntrinsic = painter.intrinsicSize
            val imageWidthPx = painterIntrinsic.width.takeIf { it > 0f && it.isFinite() }
                ?: imageBounds?.first?.toFloat()
            val imageHeightPx = painterIntrinsic.height.takeIf { it > 0f && it.isFinite() }
                ?: imageBounds?.second?.toFloat()
            val viewportLayout = calculateWallpaperViewportLayout(
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                imageWidthPx = imageWidthPx,
                imageHeightPx = imageHeightPx,
                zoom = initialZoom,
                biasX = biasX,
                biasY = biasY,
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(canvasWidth, canvasHeight)
                    .pointerInput(viewportLayout.maxShiftX, viewportLayout.maxShiftY) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (viewportLayout.maxShiftX > 0.5f) {
                                val currentTranslationX = biasX.coerceIn(-1f, 1f) * viewportLayout.maxShiftX
                                val nextTranslationX = (
                                    currentTranslationX - dragAmount.x
                                ).coerceIn(-viewportLayout.maxShiftX, viewportLayout.maxShiftX)
                                biasX = (nextTranslationX / viewportLayout.maxShiftX).coerceIn(-1f, 1f)
                            } else {
                                biasX = 0f
                            }
                            if (viewportLayout.maxShiftY > 0.5f) {
                                val currentTranslationY = biasY.coerceIn(-1f, 1f) * viewportLayout.maxShiftY
                                val nextTranslationY = (
                                    currentTranslationY - dragAmount.y
                                ).coerceIn(-viewportLayout.maxShiftY, viewportLayout.maxShiftY)
                                biasY = (nextTranslationY / viewportLayout.maxShiftY).coerceIn(-1f, 1f)
                            } else {
                                biasY = 0f
                            }
                        }
                    },
            ) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(viewportLayout.biasX, viewportLayout.biasY),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(),
                )
            }

            AppBackButton(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()
                    .padding(UiDp.dp12).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(UiDp.dp24)),
                enabled = !saving,
            )

            val bottomInsetPx = with(density) {
                max(
                    WindowInsets.navigationBars.getBottom(this),
                    WindowInsets.systemGestures.getBottom(this),
                ).toFloat()
            }
            Button(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = UiDp.dp16,
                        end = UiDp.dp16,
                        bottom = with(density) { bottomInsetPx.toDp() } + UiDp.dp12,
                    )
                    .fillMaxWidth()
                    .height(UiDp.dp52)
                    .clip(RoundedCornerShape(UiDp.dp12)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            val uri = manager.setStatic(targetProfile, wallpaperUri, 1f, viewportLayout.biasX, viewportLayout.biasY)
                            if (targetProfile == WallpaperProfile.Portrait) {
                                viewModel.onAcgWallpaperUriChange(uri)
                                viewModel.onAcgWallpaperCropChange(1f, viewportLayout.biasX, viewportLayout.biasY)
                            }
                            viewModel.onAcgWallpaperSeedColorChange(wallpaperSeedColorArgb)
                            navigator.popBackStack()
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { context.toast(MLang.AppSettings.Wallpaper.SaveFailed) }
                        finally { saving = false }
                    }
                },
            ) {
                Text(
                    text = MLang.AppSettings.Button.Apply,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
    }
}

internal fun extractDominantWallpaperColorArgb(
    context: android.content.Context,
    wallpaperUri: String,
): Long? = runCatching {
    val uri = wallpaperUri.toUri()
    val bounds = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeStream(input, null, this)
        }
    } ?: return@runCatching null

    val maxSide = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
    val sampleSize = (maxSide / 160).coerceAtLeast(1)
    val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(
            input,
            null,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    } ?: return@runCatching null

    try {
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var weightSum = 0L
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        pixels.forEach { color ->
            val alpha = AndroidColor.alpha(color)
            if (alpha < 180) return@forEach
            val red = AndroidColor.red(color)
            val green = AndroidColor.green(color)
            val blue = AndroidColor.blue(color)
            val maxChannel = maxOf(red, green, blue)
            val minChannel = minOf(red, green, blue)
            val saturationWeight = (maxChannel - minChannel).coerceAtLeast(16)
            val brightnessWeight = (255 - kotlin.math.abs(142 - maxChannel)).coerceAtLeast(32)
            val weight = saturationWeight * brightnessWeight
            redSum += red.toLong() * weight
            greenSum += green.toLong() * weight
            blueSum += blue.toLong() * weight
            weightSum += weight.toLong()
        }
        if (weightSum <= 0L) return@runCatching null
        AndroidColor.argb(
            255,
            (redSum / weightSum).toInt().coerceIn(0, 255),
            (greenSum / weightSum).toInt().coerceIn(0, 255),
            (blueSum / weightSum).toInt().coerceIn(0, 255),
        ).toLong() and 0xFFFFFFFFL
    } finally {
        bitmap.recycle()
    }
}.getOrNull()
