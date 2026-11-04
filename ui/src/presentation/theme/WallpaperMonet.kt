package com.github.yumelira.yumebox.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.score.Score

/** Material's ranked wallpaper colors use a fixed quantizer seed for a given pixel input. */
fun monetWallpaperSeed(opaquePixels: IntArray): Long? {
    if (opaquePixels.isEmpty()) return null
    val populations = QuantizerCelebi.quantize(opaquePixels, 128)
    return Score.score(populations).firstOrNull()?.toLong()?.and(0xFFFFFFFFL)
}

internal fun wallpaperMonetColorScheme(seed: Long, isDark: Boolean, invertOnPrimary: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seed.toInt()), isDark, 0.0, ColorSpec.SpecVersion.SPEC_2021)
    val colors = MaterialDynamicColors()
    fun role(value: DynamicColor) = Color(value.getArgb(scheme))
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = role(colors.primary()),
        onPrimary = if (invertOnPrimary) (if (isDark) Color.White else Color.Black) else role(colors.onPrimary()),
        primaryContainer = role(colors.primaryContainer()),
        onPrimaryContainer = role(colors.onPrimaryContainer()),
        inversePrimary = role(colors.inversePrimary()),
        secondary = role(colors.secondary()),
        onSecondary = role(colors.onSecondary()),
        secondaryContainer = role(colors.secondaryContainer()),
        onSecondaryContainer = role(colors.onSecondaryContainer()),
        tertiary = role(colors.tertiary()),
        onTertiary = role(colors.onTertiary()),
        tertiaryContainer = role(colors.tertiaryContainer()),
        onTertiaryContainer = role(colors.onTertiaryContainer()),
        background = role(colors.background()),
        onBackground = role(colors.onBackground()),
        surface = role(colors.surface()),
        onSurface = role(colors.onSurface()),
        surfaceVariant = role(colors.surfaceVariant()),
        onSurfaceVariant = role(colors.onSurfaceVariant()),
        surfaceTint = role(colors.surfaceTint()),
        inverseSurface = role(colors.inverseSurface()),
        inverseOnSurface = role(colors.inverseOnSurface()),
        error = role(colors.error()),
        onError = role(colors.onError()),
        errorContainer = role(colors.errorContainer()),
        onErrorContainer = role(colors.onErrorContainer()),
        outline = role(colors.outline()),
        outlineVariant = role(colors.outlineVariant()),
        scrim = role(colors.scrim()),
        surfaceBright = role(colors.surfaceBright()),
        surfaceDim = role(colors.surfaceDim()),
        surfaceContainerLowest = role(colors.surfaceContainerLowest()),
        surfaceContainerLow = role(colors.surfaceContainerLow()),
        surfaceContainer = role(colors.surfaceContainer()),
        surfaceContainerHigh = role(colors.surfaceContainerHigh()),
        surfaceContainerHighest = role(colors.surfaceContainerHighest()),
        primaryFixed = role(colors.primaryFixed()),
        primaryFixedDim = role(colors.primaryFixedDim()),
        onPrimaryFixed = role(colors.onPrimaryFixed()),
        onPrimaryFixedVariant = role(colors.onPrimaryFixedVariant()),
        secondaryFixed = role(colors.secondaryFixed()),
        secondaryFixedDim = role(colors.secondaryFixedDim()),
        onSecondaryFixed = role(colors.onSecondaryFixed()),
        onSecondaryFixedVariant = role(colors.onSecondaryFixedVariant()),
        tertiaryFixed = role(colors.tertiaryFixed()),
        tertiaryFixedDim = role(colors.tertiaryFixedDim()),
        onTertiaryFixed = role(colors.onTertiaryFixed()),
        onTertiaryFixedVariant = role(colors.onTertiaryFixedVariant()),
    )
}
