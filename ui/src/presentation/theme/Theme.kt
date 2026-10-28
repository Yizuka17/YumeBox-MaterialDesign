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
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.data.model.AppColorTheme
import com.github.yumelira.yumebox.data.model.MonetContrast
import com.github.yumelira.yumebox.data.model.MonetStyle
import com.github.yumelira.yumebox.data.model.ThemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

    CompositionLocalProvider(
        LocalSpacing provides spacing,
        LocalRadii provides radii,
        LocalSizes provides sizes,
        LocalOpacity provides opacity,
        LocalAppColors provides resolvedAppColors,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = YumeMaterialTypography,
            shapes = YumeMaterialShapes,
            motionScheme = MotionScheme.expressive(),
        ) {
            MiuixTheme(
                colors = miuixColorScheme,
            ) {
                content()
            }
        }
    }
}

private val YumeMaterialTypography = Typography().run {
    val systemFontFamily = FontFamily.Default
    copy(
        displayLarge = displayLarge.copy(fontFamily = systemFontFamily),
        displayMedium = displayMedium.copy(fontFamily = systemFontFamily),
        displaySmall = displaySmall.copy(fontFamily = systemFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = systemFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = systemFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = systemFontFamily),
        titleLarge = titleLarge.copy(fontFamily = systemFontFamily),
        titleMedium = titleMedium.copy(fontFamily = systemFontFamily),
        titleSmall = titleSmall.copy(fontFamily = systemFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = systemFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = systemFontFamily),
        bodySmall = bodySmall.copy(fontFamily = systemFontFamily),
        labelLarge = labelLarge.copy(fontFamily = systemFontFamily),
        labelMedium = labelMedium.copy(fontFamily = systemFontFamily),
        labelSmall = labelSmall.copy(fontFamily = systemFontFamily),
    )
}
private val YumeMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
