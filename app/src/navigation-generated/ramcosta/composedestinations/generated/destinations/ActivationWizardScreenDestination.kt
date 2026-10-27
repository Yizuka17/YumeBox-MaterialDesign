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
import com.github.yumelira.yumebox.screen.onboarding.ActivationWizardScreen
import com.ramcosta.composedestinations.generated.destinations.ActivationWizardScreenDestinationNavArgs
import com.ramcosta.composedestinations.navargs.primitives.booleanNavType
import com.ramcosta.composedestinations.navigation.DependenciesContainerBuilder
import com.ramcosta.composedestinations.navigation.DestinationDependenciesContainer
import com.ramcosta.composedestinations.navigation.require
import com.ramcosta.composedestinations.scope.DestinationScope
import com.ramcosta.composedestinations.spec.*

public data class ActivationWizardScreenDestinationNavArgs(
	val previewMode: Boolean = false,
)

/**
 * Generated from [ActivationWizardScreen]
 */
public data object ActivationWizardScreenDestination : BaseRoute(), TypedDestinationSpec<ActivationWizardScreenDestinationNavArgs> {

    override fun invoke(navArgs: ActivationWizardScreenDestinationNavArgs): Direction = with(navArgs) {
        invoke(previewMode)
    }

    public operator fun invoke(
		previewMode: Boolean = false,
    ): Direction {
        return Direction(
            route = "$baseRoute" +
					"?previewMode=${booleanNavType.serializeValue(previewMode)}"
        )
    }

    override val baseRoute: String = "activation_wizard_screen"

    override val route: String = "$baseRoute?previewMode={previewMode}"

	override val arguments: List<NamedNavArgument> get() = listOf(
		navArgument("previewMode") {
			type = booleanNavType
			val defValue: Boolean = false
			defaultValue = defValue
		}
	)

    @Composable
    override fun DestinationScope<ActivationWizardScreenDestinationNavArgs>.Content() {
		val (previewMode) = navArgs
		ActivationWizardScreen(
			navigator = destinationsNavigator,
			previewMode = previewMode
		)
    }

	override fun argsFrom(bundle: Bundle?): ActivationWizardScreenDestinationNavArgs {
	    return ActivationWizardScreenDestinationNavArgs(
			previewMode = booleanNavType.safeGet(bundle, "previewMode") ?: throw RuntimeException("'previewMode' argument is not mandatory and not nullable but was not present!"),
	    )
	}

	override fun argsFrom(savedStateHandle: SavedStateHandle): ActivationWizardScreenDestinationNavArgs {
	    return ActivationWizardScreenDestinationNavArgs(
			previewMode = booleanNavType.get(savedStateHandle, "previewMode") ?: throw RuntimeException("'previewMode' argument is not mandatory and not nullable but was not present!"),
	    )
	}
}
