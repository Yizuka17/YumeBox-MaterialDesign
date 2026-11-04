package com.github.yumelira.yumebox.screen.acg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.resize.Precision
import kotlin.math.roundToInt

internal data class AcgWallpaperFrame(
    val sidebarWidth: Dp,
    val sidebarInset: Dp,
    val height: Dp,
)

internal fun acgWallpaperProfile(width: Dp, height: Dp, pageScale: Float, tabletEnabled: Boolean): WallpaperProfile =
    if (tabletEnabled && width >= 480.dp && ((width.value * pageScale).roundToInt() >= 600 || width / height >= 0.85f)) {
        WallpaperProfile.Tablet
    } else WallpaperProfile.Portrait

internal fun acgWallpaperFrame(width: Dp, height: Dp, profile: WallpaperProfile): AcgWallpaperFrame {
    val sidebar = if (profile == WallpaperProfile.Tablet) (width * 0.28f).coerceIn(208.dp, 260.dp)
        else (width * AcgUi.Sidebar.Fraction).coerceAtLeast(88.dp)
    return AcgWallpaperFrame(sidebar, (sidebar - AcgUi.Sidebar.contentOverlap).coerceAtLeast(0.dp), height * 0.66f)
}

internal data class AcgFrameKey(val window: IntSize, val density: Float, val profile: WallpaperProfile, val expanded: Boolean)

/** Project the current window into the requested orientation if that viewport has not been seen yet. */
internal fun predictedAcgFrameRatio(key: AcgFrameKey, density: Density, currentProfile: WallpaperProfile): Float = with(density) {
    val size = key.window
    val swap = currentProfile != key.profile &&
        ((key.profile == WallpaperProfile.Tablet && size.width < size.height) ||
            (key.profile == WallpaperProfile.Portrait && size.width > size.height))
    val width = (if (swap) size.height else size.width).coerceAtLeast(1)
    val height = (if (swap) size.width else size.height).coerceAtLeast(1)
    val frame = acgWallpaperFrame(width.toDp(), height.toDp(), key.profile)
    val inset = if (key.expanded) frame.sidebarInset.roundToPx() else 0
    val imageWidth = (width - inset - 2 * AcgUi.Hero.containerHorizontalInset.roundToPx()).coerceAtLeast(1)
    imageWidth.toFloat() / frame.height.roundToPx().coerceAtLeast(1)
}

/** A shared decode keeps the oriented source bounds identical in the home and crop tool. */
@Composable
internal fun rememberAcgWallpaperPainter(source: String, sampleWidth: Int = 2048, sampleHeight: Int = 2048) =
    LocalContext.current.let { context ->
        rememberAsyncImagePainter(remember(context, source, sampleWidth, sampleHeight) {
            ImageRequest(context, source) {
                size(sampleWidth, sampleHeight)
                precision(Precision.LESS_PIXELS)
            }
        })
    }
