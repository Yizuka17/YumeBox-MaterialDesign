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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.max

data class WallpaperViewportLayout(
    val maxShiftX: Float,
    val maxShiftY: Float,
    val biasX: Float,
    val biasY: Float,
    val renderedWidth: Float,
    val renderedHeight: Float,
) {
    val imageOffset: Offset get() = Offset(-maxShiftX * (1f + biasX), -maxShiftY * (1f + biasY))
}

fun DrawScope.drawWallpaper(painter: Painter, layout: WallpaperViewportLayout, frameOrigin: Offset = Offset.Zero) {
    val origin = frameOrigin + layout.imageOffset
    translate(origin.x, origin.y) {
        with(painter) { draw(Size(layout.renderedWidth, layout.renderedHeight)) }
    }
}

fun calculateWallpaperViewportLayout(
    containerWidthPx: Float,
    containerHeightPx: Float,
    imageWidthPx: Float?,
    imageHeightPx: Float?,
    zoom: Float = 1f,
    biasX: Float = 0f,
    biasY: Float = 0f,
): WallpaperViewportLayout {
    val safeContainerWidth = containerWidthPx.coerceAtLeast(1f)
    val safeContainerHeight = containerHeightPx.coerceAtLeast(1f)
    val safeZoom = zoom.coerceIn(1f, 5f)
    val safeImageWidth = imageWidthPx?.takeIf { it > 0f && it.isFinite() }
    val safeImageHeight = imageHeightPx?.takeIf { it > 0f && it.isFinite() }

    val renderedWidthPx: Float
    val renderedHeightPx: Float

    if (safeImageWidth != null && safeImageHeight != null) {
        val coverScale = max(
            safeContainerWidth / safeImageWidth,
            safeContainerHeight / safeImageHeight,
        ) * safeZoom
        renderedWidthPx = safeImageWidth * coverScale
        renderedHeightPx = safeImageHeight * coverScale
    } else {
        renderedWidthPx = safeContainerWidth * safeZoom
        renderedHeightPx = safeContainerHeight * safeZoom
    }

    val maxShiftX = ((renderedWidthPx - safeContainerWidth) / 2f).coerceAtLeast(0f)
    val maxShiftY = ((renderedHeightPx - safeContainerHeight) / 2f).coerceAtLeast(0f)
    val clampedBiasX = if (maxShiftX > 0.5f) biasX.coerceIn(-1f, 1f) else 0f
    val clampedBiasY = if (maxShiftY > 0.5f) biasY.coerceIn(-1f, 1f) else 0f

    return WallpaperViewportLayout(
        maxShiftX = maxShiftX,
        maxShiftY = maxShiftY,
        biasX = clampedBiasX,
        biasY = clampedBiasY,
        renderedWidth = renderedWidthPx,
        renderedHeight = renderedHeightPx,
    )
}
