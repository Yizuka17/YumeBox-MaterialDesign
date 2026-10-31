package com.ramcosta.composedestinations.generated.destinations

import androidx.compose.runtime.Composable
import com.github.yumelira.yumebox.screen.about.ReadmeScreen
import com.ramcosta.composedestinations.scope.DestinationScope
import com.ramcosta.composedestinations.spec.BaseRoute
import com.ramcosta.composedestinations.spec.Direction
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec

public data object ReadmeScreenDestination : BaseRoute(), DirectionDestinationSpec {
    public operator fun invoke(): Direction = this
    override val baseRoute = "readme_screen"
    override val route: String = baseRoute
    @Composable
    override fun DestinationScope<Unit>.Content() { ReadmeScreen(navigator = destinationsNavigator) }
}
