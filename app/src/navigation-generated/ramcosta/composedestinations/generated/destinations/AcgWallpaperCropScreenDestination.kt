package com.ramcosta.composedestinations.generated.destinations

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.github.yumelira.yumebox.screen.settings.AcgWallpaperCropScreen
import com.ramcosta.composedestinations.generated.destinations.AcgWallpaperCropScreenDestinationNavArgs
import com.ramcosta.composedestinations.navargs.primitives.floatNavType
import com.ramcosta.composedestinations.navargs.primitives.stringNavType
import com.ramcosta.composedestinations.navigation.DependenciesContainerBuilder
import com.ramcosta.composedestinations.navigation.DestinationDependenciesContainer
import com.ramcosta.composedestinations.navigation.require
import com.ramcosta.composedestinations.scope.DestinationScope
import com.ramcosta.composedestinations.spec.*

public data class AcgWallpaperCropScreenDestinationNavArgs(
	val wallpaperUri: String,
	val initialZoom: Float = 1f,
	val initialBiasX: Float = 0f,
	val initialBiasY: Float = 0f,
	val profile: String = "portrait",
)

/**
 * Generated from [AcgWallpaperCropScreen]
 */
public data object AcgWallpaperCropScreenDestination : BaseRoute(), TypedDestinationSpec<AcgWallpaperCropScreenDestinationNavArgs> {

    override fun invoke(navArgs: AcgWallpaperCropScreenDestinationNavArgs): Direction = with(navArgs) {
        invoke(wallpaperUri, initialZoom, initialBiasX, initialBiasY, profile)
    }

    public operator fun invoke(
		wallpaperUri: String,
		initialZoom: Float = 1f,
		initialBiasX: Float = 0f,
		initialBiasY: Float = 0f,
        profile: String = "portrait",
    ): Direction {
        return Direction(
            route = "$baseRoute" +
					"/${stringNavType.serializeValue("wallpaperUri", wallpaperUri)}" +
					"?initialZoom=${floatNavType.serializeValue(initialZoom)}" +
					"&initialBiasX=${floatNavType.serializeValue(initialBiasX)}" +
					"&initialBiasY=${floatNavType.serializeValue(initialBiasY)}" +
                    "&profile=${stringNavType.serializeValue("profile", profile)}"
        )
    }

    override val baseRoute: String = "acg_wallpaper_crop_screen"

    override val route: String = "$baseRoute/{wallpaperUri}?initialZoom={initialZoom}&initialBiasX={initialBiasX}&initialBiasY={initialBiasY}&profile={profile}"

	override val arguments: List<NamedNavArgument> get() = listOf(
		navArgument("wallpaperUri") {
			type = stringNavType
		},
		navArgument("initialZoom") {
			type = floatNavType
			val defValue: Float = 1f
			defaultValue = defValue
		},
		navArgument("initialBiasX") {
			type = floatNavType
			val defValue: Float = 0f
			defaultValue = defValue
		},
		navArgument("profile") {
            type = stringNavType
            defaultValue = "portrait"
        },
		navArgument("initialBiasY") {
			type = floatNavType
			val defValue: Float = 0f
			defaultValue = defValue
		}
	)

    @Composable
    override fun DestinationScope<AcgWallpaperCropScreenDestinationNavArgs>.Content() {
		val (wallpaperUri, initialZoom, initialBiasX, initialBiasY, profile) = navArgs
		AcgWallpaperCropScreen(
			navigator = destinationsNavigator,
			wallpaperUri = wallpaperUri,
			initialZoom = initialZoom,
			initialBiasX = initialBiasX,
			initialBiasY = initialBiasY,
            profile = profile
		)
    }

	override fun argsFrom(bundle: Bundle?): AcgWallpaperCropScreenDestinationNavArgs {
	    return AcgWallpaperCropScreenDestinationNavArgs(
			profile = stringNavType.safeGet(bundle, "profile") ?: "portrait",
			wallpaperUri = stringNavType.safeGet(bundle, "wallpaperUri") ?: throw RuntimeException("'wallpaperUri' argument is mandatory, but was not present!"),
			initialZoom = floatNavType.safeGet(bundle, "initialZoom") ?: throw RuntimeException("'initialZoom' argument is not mandatory and not nullable but was not present!"),
			initialBiasX = floatNavType.safeGet(bundle, "initialBiasX") ?: throw RuntimeException("'initialBiasX' argument is not mandatory and not nullable but was not present!"),
			initialBiasY = floatNavType.safeGet(bundle, "initialBiasY") ?: throw RuntimeException("'initialBiasY' argument is not mandatory and not nullable but was not present!"),
	    )
	}

	override fun argsFrom(savedStateHandle: SavedStateHandle): AcgWallpaperCropScreenDestinationNavArgs {
	    return AcgWallpaperCropScreenDestinationNavArgs(
			profile = stringNavType.get(savedStateHandle, "profile") ?: "portrait",
			wallpaperUri = stringNavType.get(savedStateHandle, "wallpaperUri") ?: throw RuntimeException("'wallpaperUri' argument is mandatory, but was not present!"),
			initialZoom = floatNavType.get(savedStateHandle, "initialZoom") ?: throw RuntimeException("'initialZoom' argument is not mandatory and not nullable but was not present!"),
			initialBiasX = floatNavType.get(savedStateHandle, "initialBiasX") ?: throw RuntimeException("'initialBiasX' argument is not mandatory and not nullable but was not present!"),
			initialBiasY = floatNavType.get(savedStateHandle, "initialBiasY") ?: throw RuntimeException("'initialBiasY' argument is not mandatory and not nullable but was not present!"),
	    )
	}
}
