@file:OptIn(com.ramcosta.composedestinations.annotation.internal.InternalDestinationsApi::class)

package com.ramcosta.composedestinations.generated.navgraphs

import androidx.annotation.Keep
import com.github.yumelira.yumebox.MainScreen
import com.github.yumelira.yumebox.screen.about.AboutScreen
import com.github.yumelira.yumebox.screen.about.OpenSourceLicensesScreen
import com.github.yumelira.yumebox.screen.connection.ConnectionScreen
import com.github.yumelira.yumebox.screen.log.LogScreen
import com.github.yumelira.yumebox.screen.navigation.CustomRoutingRoute
import com.github.yumelira.yumebox.screen.navigation.FeatureScreen
import com.github.yumelira.yumebox.screen.navigation.KeyValueEditorScreen
import com.github.yumelira.yumebox.screen.navigation.OverrideConfigPreviewRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideEditRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideKeyedObjectDraftEditorRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideKeyedObjectMapEditorRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideObjectListEditorRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideProxyDraftEditorRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideProxyGroupDraftEditorRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideRuleDraftEditorRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideRuleListEditorRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideScreen
import com.github.yumelira.yumebox.screen.navigation.OverrideStringListEditorRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideSubRuleDraftEditorRoute
import com.github.yumelira.yumebox.screen.navigation.OverrideSubRuleMapEditorRoute
import com.github.yumelira.yumebox.screen.navigation.ProvidersScreen
import com.github.yumelira.yumebox.screen.navigation.StringListEditorScreen
import com.github.yumelira.yumebox.screen.onboarding.ActivationWizardScreen
import com.github.yumelira.yumebox.screen.onboarding.AppStartScreen
import com.github.yumelira.yumebox.screen.settings.AccessControlScreen
import com.github.yumelira.yumebox.screen.settings.AcgQuoteConfigScreen
import com.github.yumelira.yumebox.screen.settings.AcgWallpaperCropScreen
import com.github.yumelira.yumebox.screen.settings.AppDataManagementScreen
import com.github.yumelira.yumebox.screen.settings.AppSettingsScreen
import com.github.yumelira.yumebox.screen.settings.MetaFeatureScreen
import com.github.yumelira.yumebox.screen.settings.NetworkSettingsScreen
import com.github.yumelira.yumebox.screen.traffic.TrafficStatisticsScreen
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.animations.defaults.NoTransitions
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.generated.destinations.AppStartScreenDestination
import com.ramcosta.composedestinations.generated.navgraphs.*
import com.ramcosta.composedestinations.spec.*
import com.ramcosta.composedestinations.spec.DirectionNavHostGraphSpec

/**
 * Generated from [RootGraph]
 *
 * * 🗺️[RootGraph]
 * * ∙∙∙∙∙∙∙∙↳📍🏁[AppStartScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[MainScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[AboutScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[OpenSourceLicensesScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[ConnectionScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[LogScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[FeatureScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[StringListEditorScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[KeyValueEditorScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[CustomRoutingRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideEditRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideRuleListEditorRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideObjectListEditorRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideKeyedObjectMapEditorRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideSubRuleMapEditorRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideStringListEditorRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideRuleDraftEditorRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideProxyDraftEditorRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideProxyGroupDraftEditorRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideKeyedObjectDraftEditorRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideSubRuleDraftEditorRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[OverrideConfigPreviewRoute]
 * * ∙∙∙∙∙∙∙∙↳📍[ProvidersScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[ActivationWizardScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[AccessControlScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[AcgWallpaperCropScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[AppDataManagementScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[AppSettingsScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[AcgQuoteConfigScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[MetaFeatureScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[NetworkSettingsScreen]
 * * ∙∙∙∙∙∙∙∙↳📍[TrafficStatisticsScreen]
 */
@Keep
public data object RootNavGraph : BaseRoute(), DirectionNavHostGraphSpec {

    override val startRoute: TypedRoute<Unit> = AppStartScreenDestination

    override val defaultStartDirection: Direction = defaultStartDirection()

    override val destinations: List<DestinationSpec> get() = listOf(
		AboutScreenDestination,
		ReadmeScreenDestination,
		AccessControlScreenDestination,
		AcgQuoteConfigScreenDestination,
		AcgWallpaperConfigScreenDestination,
		AcgWallpaperCropScreenDestination,
		ActivationWizardScreenDestination,
		AppDataManagementScreenDestination,
		AppSettingsScreenDestination,
		AppStartScreenDestination,
		ConnectionScreenDestination,
		CustomRoutingRouteDestination,
		FeatureScreenDestination,
		KeyValueEditorScreenDestination,
		LogScreenDestination,
		MainScreenDestination,
		MetaFeatureScreenDestination,
		NetworkSettingsScreenDestination,
		OpenSourceLicensesScreenDestination,
		OverrideConfigPreviewRouteDestination,
		OverrideEditRouteDestination,
		OverrideKeyedObjectDraftEditorRouteDestination,
		OverrideKeyedObjectMapEditorRouteDestination,
		OverrideObjectListEditorRouteDestination,
		OverrideProxyDraftEditorRouteDestination,
		OverrideProxyGroupDraftEditorRouteDestination,
		OverrideRuleDraftEditorRouteDestination,
		OverrideRuleListEditorRouteDestination,
		OverrideScreenDestination,
		OverrideStringListEditorRouteDestination,
		OverrideSubRuleDraftEditorRouteDestination,
		OverrideSubRuleMapEditorRouteDestination,
		ProvidersScreenDestination,
		StringListEditorScreenDestination,
		TrafficStatisticsScreenDestination
    )

	override val defaultTransitions: NavHostAnimatedDestinationStyle = NoTransitions

	override val route: String = "root"



}
