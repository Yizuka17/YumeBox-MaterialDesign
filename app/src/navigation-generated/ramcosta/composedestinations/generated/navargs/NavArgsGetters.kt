@file:Suppress("UNCHECKED_CAST")

package com.ramcosta.composedestinations.generated

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import com.ramcosta.composedestinations.generated.destinations.AcgWallpaperCropScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AcgWallpaperCropScreenDestinationNavArgs
import com.ramcosta.composedestinations.generated.destinations.ActivationWizardScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ActivationWizardScreenDestinationNavArgs
import com.ramcosta.composedestinations.generated.destinations.MainScreenDestination
import com.ramcosta.composedestinations.generated.destinations.MainScreenDestinationNavArgs
import com.ramcosta.composedestinations.generated.destinations.OverrideEditRouteDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideEditRouteDestinationNavArgs
import com.ramcosta.composedestinations.spec.DestinationSpec
import com.ramcosta.composedestinations.spec.NavGraphSpec

public inline fun <reified T: Any> SavedStateHandle.navArgs(): T {
    return navArgs(T::class.java, this)
}

public inline fun <reified T: Any> NavBackStackEntry.navArgs(): T {
    return navArgs(T::class.java, this)
}

public fun <T: Any> navArgs(argsClass: Class<T>, argsContainer: NavBackStackEntry): T {
    return destinationWithArgsType(argsClass).argsFrom(argsContainer) as T
}

public fun <T: Any> navArgs(argsClass: Class<T>, argsContainer: SavedStateHandle): T {
    return destinationWithArgsType(argsClass).argsFrom(argsContainer) as T
}



private fun <T: Any> destinationWithArgsType(argsClass: Class<T>): DestinationSpec {
    return when (argsClass) {
		MainScreenDestinationNavArgs::class.java -> MainScreenDestination
		OverrideEditRouteDestinationNavArgs::class.java -> OverrideEditRouteDestination
		ActivationWizardScreenDestinationNavArgs::class.java -> ActivationWizardScreenDestination
		AcgWallpaperCropScreenDestinationNavArgs::class.java -> AcgWallpaperCropScreenDestination
        else -> error("Class ${argsClass} is not a destination arguments class known by this module!")
    }
}
