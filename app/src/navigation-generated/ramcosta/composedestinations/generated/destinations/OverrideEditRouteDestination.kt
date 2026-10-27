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
import com.github.yumelira.yumebox.screen.navigation.OverrideEditRoute
import com.ramcosta.composedestinations.generated.destinations.OverrideEditRouteDestinationNavArgs
import com.ramcosta.composedestinations.navargs.primitives.stringNavType
import com.ramcosta.composedestinations.navigation.DependenciesContainerBuilder
import com.ramcosta.composedestinations.navigation.DestinationDependenciesContainer
import com.ramcosta.composedestinations.navigation.require
import com.ramcosta.composedestinations.scope.DestinationScope
import com.ramcosta.composedestinations.spec.*

public data class OverrideEditRouteDestinationNavArgs(
	val configId: String,
)

/**
 * Generated from [OverrideEditRoute]
 */
public data object OverrideEditRouteDestination : BaseRoute(), TypedDestinationSpec<OverrideEditRouteDestinationNavArgs> {

    override fun invoke(navArgs: OverrideEditRouteDestinationNavArgs): Direction = with(navArgs) {
        invoke(configId)
    }

    public operator fun invoke(
		configId: String,
    ): Direction {
        return Direction(
            route = "$baseRoute" +
					"/${stringNavType.serializeValue("configId", configId)}"
        )
    }

    override val baseRoute: String = "override_edit_route"

    override val route: String = "$baseRoute/{configId}"

	override val arguments: List<NamedNavArgument> get() = listOf(
		navArgument("configId") {
			type = stringNavType
		}
	)

    @Composable
    override fun DestinationScope<OverrideEditRouteDestinationNavArgs>.Content() {
		val (configId) = navArgs
		OverrideEditRoute(
			navigator = destinationsNavigator,
			configId = configId
		)
    }

	override fun argsFrom(bundle: Bundle?): OverrideEditRouteDestinationNavArgs {
	    return OverrideEditRouteDestinationNavArgs(
			configId = stringNavType.safeGet(bundle, "configId") ?: throw RuntimeException("'configId' argument is mandatory, but was not present!"),
	    )
	}

	override fun argsFrom(savedStateHandle: SavedStateHandle): OverrideEditRouteDestinationNavArgs {
	    return OverrideEditRouteDestinationNavArgs(
			configId = stringNavType.get(savedStateHandle, "configId") ?: throw RuntimeException("'configId' argument is mandatory, but was not present!"),
	    )
	}
}
