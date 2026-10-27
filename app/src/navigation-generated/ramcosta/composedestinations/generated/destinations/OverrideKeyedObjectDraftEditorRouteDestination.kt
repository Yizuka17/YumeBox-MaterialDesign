package com.ramcosta.composedestinations.generated.destinations

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import com.github.yumelira.yumebox.screen.navigation.OverrideKeyedObjectDraftEditorRoute
import com.ramcosta.composedestinations.navigation.DependenciesContainerBuilder
import com.ramcosta.composedestinations.navigation.DestinationDependenciesContainer
import com.ramcosta.composedestinations.navigation.require
import com.ramcosta.composedestinations.scope.DestinationScope
import com.ramcosta.composedestinations.spec.*

/**
 * Generated from [OverrideKeyedObjectDraftEditorRoute]
 */
public data object OverrideKeyedObjectDraftEditorRouteDestination : BaseRoute(), DirectionDestinationSpec {

    public operator fun invoke(): Direction = this

    override val baseRoute: String = "override_keyed_object_draft_editor_route"

    override val route: String = baseRoute

    @Composable
    override fun DestinationScope<Unit>.Content() {
		OverrideKeyedObjectDraftEditorRoute(
			navigator = destinationsNavigator
		)
    }

}
