package com.ramcosta.composedestinations.generated.destinations

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import com.github.yumelira.yumebox.screen.settings.AcgQuoteConfigScreen
import com.ramcosta.composedestinations.navigation.DependenciesContainerBuilder
import com.ramcosta.composedestinations.navigation.DestinationDependenciesContainer
import com.ramcosta.composedestinations.navigation.require
import com.ramcosta.composedestinations.scope.DestinationScope
import com.ramcosta.composedestinations.spec.*

/**
 * Generated from [AcgQuoteConfigScreen]
 */
public data object AcgQuoteConfigScreenDestination : BaseRoute(), DirectionDestinationSpec {

    public operator fun invoke(): Direction = this

    override val baseRoute: String = "acg_quote_config_screen"

    override val route: String = baseRoute

    @Composable
    override fun DestinationScope<Unit>.Content() {
		AcgQuoteConfigScreen(
			navigator = destinationsNavigator
		)
    }

}
