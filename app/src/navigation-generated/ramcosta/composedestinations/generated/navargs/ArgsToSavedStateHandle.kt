package com.ramcosta.composedestinations.generated.navargs

import androidx.lifecycle.SavedStateHandle
import com.ramcosta.composedestinations.generated.destinations.AcgWallpaperCropScreenDestinationNavArgs
import com.ramcosta.composedestinations.generated.destinations.ActivationWizardScreenDestinationNavArgs
import com.ramcosta.composedestinations.generated.destinations.MainScreenDestinationNavArgs
import com.ramcosta.composedestinations.generated.destinations.OverrideEditRouteDestinationNavArgs
import com.ramcosta.composedestinations.navargs.primitives.*
import com.ramcosta.composedestinations.navargs.primitives.array.*
import com.ramcosta.composedestinations.navargs.primitives.arraylist.*

public fun MainScreenDestinationNavArgs.toSavedStateHandle(
    handle: SavedStateHandle = SavedStateHandle()
): SavedStateHandle {
	intNavType.put(handle, "initialPage", initialPage)
    return handle
}

public fun OverrideEditRouteDestinationNavArgs.toSavedStateHandle(
    handle: SavedStateHandle = SavedStateHandle()
): SavedStateHandle {
	stringNavType.put(handle, "configId", configId)
    return handle
}

public fun ActivationWizardScreenDestinationNavArgs.toSavedStateHandle(
    handle: SavedStateHandle = SavedStateHandle()
): SavedStateHandle {
	booleanNavType.put(handle, "previewMode", previewMode)
    return handle
}

public fun AcgWallpaperCropScreenDestinationNavArgs.toSavedStateHandle(
    handle: SavedStateHandle = SavedStateHandle()
): SavedStateHandle {
	stringNavType.put(handle, "wallpaperUri", wallpaperUri)
	floatNavType.put(handle, "initialZoom", initialZoom)
	floatNavType.put(handle, "initialBiasX", initialBiasX)
	floatNavType.put(handle, "initialBiasY", initialBiasY)
    return handle
}
