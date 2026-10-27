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
import com.github.yumelira.yumebox.MainScreen
import com.ramcosta.composedestinations.generated.destinations.MainScreenDestinationNavArgs
import com.ramcosta.composedestinations.navargs.primitives.intNavType
import com.ramcosta.composedestinations.navigation.DependenciesContainerBuilder
import com.ramcosta.composedestinations.navigation.DestinationDependenciesContainer
import com.ramcosta.composedestinations.navigation.require
import com.ramcosta.composedestinations.scope.DestinationScope
import com.ramcosta.composedestinations.spec.*

public data class MainScreenDestinationNavArgs(
	val initialPage: Int = 0,
)

/**
 * Generated from [MainScreen]
 */
public data object MainScreenDestination : BaseRoute(), TypedDestinationSpec<MainScreenDestinationNavArgs> {

    override fun invoke(navArgs: MainScreenDestinationNavArgs): Direction = with(navArgs) {
        invoke(initialPage)
    }

    public operator fun invoke(
		initialPage: Int = 0,
    ): Direction {
        return Direction(
            route = "$baseRoute" +
					"?initialPage=${intNavType.serializeValue(initialPage)}"
        )
    }

    override val baseRoute: String = "main_screen"

    override val route: String = "$baseRoute?initialPage={initialPage}"

	override val arguments: List<NamedNavArgument> get() = listOf(
		navArgument("initialPage") {
			type = intNavType
			val defValue: Int = 0
			defaultValue = defValue
		}
	)

    @Composable
    override fun DestinationScope<MainScreenDestinationNavArgs>.Content() {
		val (initialPage) = navArgs
		MainScreen(
			navigator = destinationsNavigator,
			initialPage = initialPage
		)
    }

	override fun argsFrom(bundle: Bundle?): MainScreenDestinationNavArgs {
	    return MainScreenDestinationNavArgs(
			initialPage = intNavType.safeGet(bundle, "initialPage") ?: throw RuntimeException("'initialPage' argument is not mandatory and not nullable but was not present!"),
	    )
	}

	override fun argsFrom(savedStateHandle: SavedStateHandle): MainScreenDestinationNavArgs {
	    return MainScreenDestinationNavArgs(
			initialPage = intNavType.get(savedStateHandle, "initialPage") ?: throw RuntimeException("'initialPage' argument is not mandatory and not nullable but was not present!"),
	    )
	}
}
