package com.github.yumelira.yumebox.screen.acg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import com.github.yumelira.yumebox.presentation.theme.monetWallpaperSeed
import kotlin.math.roundToInt

internal fun extractWallpaperSeed(context: Context, source: String): Long? = runCatching {
    val uri = source.toUri()
    val resolver = context.contentResolver
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    val longestSide = maxOf(options.outWidth, options.outHeight)
    if (longestSide <= 0) return@runCatching null
    val sample = Integer.highestOneBit((longestSide / 128).coerceAtLeast(1))
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    } ?: return@runCatching null
    try {
        val factor = (128f / maxOf(decoded.width, decoded.height)).coerceAtMost(1f)
        val bitmap = Bitmap.createScaledBitmap(decoded, (decoded.width * factor).roundToInt().coerceAtLeast(1),
            (decoded.height * factor).roundToInt().coerceAtLeast(1), true)
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            monetWallpaperSeed(pixels.filter { (it ushr 24) == 255 }.toIntArray())
        } finally {
            if (bitmap !== decoded) bitmap.recycle()
        }
    } finally {
        decoded.recycle()
    }
}.getOrNull()
