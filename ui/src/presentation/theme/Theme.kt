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



package com.github.yumelira.yumebox.presentation.theme

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.data.model.AppColorTheme
import com.github.yumelira.yumebox.data.model.MonetContrast
import com.github.yumelira.yumebox.data.model.MonetStyle
import com.github.yumelira.yumebox.data.model.ThemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles

internal val LocalPlatformSystemUiEffect = compositionLocalOf<@Composable (Boolean) -> Unit> { { _ -> } }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YumeTheme(
    themeMode: ThemeMode? = null,
    colorTheme: AppColorTheme = AppColorTheme.MonetDynamic,
    themeSeedColorArgb: Long = DEFAULT_THEME_SEED_ARGB,
    monetStyle: MonetStyle = MonetStyle.TonalSpot,
    monetContrast: MonetContrast = MonetContrast.Standard,
    monetIntensity: Float = 0.45f,
    invertOnPrimaryColors: Boolean = false,
    fontFamily: FontFamily? = null,
    fontWeight: Int = 0,
    spacing: Spacing = Spacing(),
    radii: Radii = Radii(),
    sizes: Sizes = Sizes(),
    opacity: Opacity = Opacity(),
    appColors: AppColors? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val effectiveThemeMode = themeMode ?: ThemeMode.Auto
    val isDark = when (effectiveThemeMode) {
        ThemeMode.Auto -> (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    LocalPlatformSystemUiEffect.current(isDark)
    val materialColorScheme = remember(
        context,
        colorTheme,
        themeSeedColorArgb,
        isDark,
        monetStyle,
        monetContrast,
        monetIntensity,
        invertOnPrimaryColors,
    ) {
        when {
            colorTheme == AppColorTheme.MonetDynamic &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            colorTheme == AppColorTheme.AcgWallpaper ->
                wallpaperMonetColorScheme(themeSeedColorArgb, isDark, invertOnPrimaryColors)

            else -> {
                materialColorSchemeFromSeed(
                    seed = colorFromArgb(themeSeedColorArgb),
                    isDark = isDark,
                    invertOnPrimaryColors = invertOnPrimaryColors,
                    monetStyle = monetStyle,
                    monetContrast = monetContrast,
                    monetIntensity = monetIntensity,
                )
            }
        }
    }

    val miuixColorScheme = remember(materialColorScheme, isDark, invertOnPrimaryColors) {
        miuixColorSchemeFromMaterial(
            colorScheme = materialColorScheme,
            isDark = isDark,
            invertOnPrimaryColors = invertOnPrimaryColors,
        )
    }
    val resolvedAppColors = remember(materialColorScheme, isDark, appColors) {
        appColors ?: appColorsFromMaterial(
            colorScheme = materialColorScheme,
            isDark = isDark,
        )
    }

    val typography = remember(fontFamily, fontWeight) { yumeTypography(fontFamily, fontWeight) }
    val miuixTypography = remember(fontFamily, fontWeight) {
        defaultTextStyles().run {
            val selectedFamily = fontFamily
            val selectedWeight = fontWeight
            fun TextStyle.adjust() = withUserFont(selectedFamily, selectedWeight)
            copy(
                main = main.adjust(), paragraph = paragraph.adjust(),
                body1 = body1.adjust(), body2 = body2.adjust(), button = button.adjust(),
                footnote1 = footnote1.adjust(), footnote2 = footnote2.adjust(),
                headline1 = headline1.adjust(), headline2 = headline2.adjust(), subtitle = subtitle.adjust(),
                title1 = title1.adjust(), title2 = title2.adjust(), title3 = title3.adjust(), title4 = title4.adjust(),
            )
        }
    }
    CompositionLocalProvider(
        LocalSpacing provides spacing,
        LocalRadii provides radii,
        LocalSizes provides sizes,
        LocalOpacity provides opacity,
        LocalAppColors provides resolvedAppColors,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = typography,
            shapes = YumeMaterialShapes,
            motionScheme = MotionScheme.expressive(),
        ) {
            MiuixTheme(
                colors = miuixColorScheme,
                textStyles = miuixTypography,
            ) {
                content()
            }
        }
    }
}

private fun TextStyle.withUserFont(family: FontFamily?, weight: Int) = copy(
        fontFamily = family ?: fontFamily,
        fontWeight = if (weight in 100..900) {
            // Preserve the difference between regular body and emphasized labels/titles.
            FontWeight((weight + (fontWeight?.weight ?: 400) - 400).coerceIn(100, 900))
        } else fontWeight,
    )

private fun yumeTypography(family: FontFamily?, weight: Int) = Typography().run {
    fun TextStyle.customize() = withUserFont(family ?: FontFamily.Default, weight)
    copy(
        displayLarge = displayLarge.customize(),
        displayMedium = displayMedium.customize(),
        displaySmall = displaySmall.customize(),
        headlineLarge = headlineLarge.customize(),
        headlineMedium = headlineMedium.customize(),
        headlineSmall = headlineSmall.customize(),
        titleLarge = titleLarge.customize(),
        titleMedium = titleMedium.customize(),
        titleSmall = titleSmall.customize(),
        bodyLarge = bodyLarge.customize(),
        bodyMedium = bodyMedium.customize(),
        bodySmall = bodySmall.customize(),
        labelLarge = labelLarge.customize(),
        labelMedium = labelMedium.customize(),
        labelSmall = labelSmall.customize(),
    )
}
private val YumeMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
