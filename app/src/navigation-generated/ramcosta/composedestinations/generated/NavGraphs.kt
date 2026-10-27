package com.ramcosta.composedestinations.generated

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
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.generated.navgraphs.*
import com.ramcosta.composedestinations.spec.*

/**
 * Class generated if any Composable is annotated with `@Destination`.
 * It aggregates all [DestinationSpec]s in their [NavGraphSpec]s.
 *
 * -------------------------------------------------------
 * **Legend:**
 * * 🗺️: Navigation graph
 * * 📍: Destination
 * * 🏁: Marks 🗺️/📍as the start of the parent graph
 * * 🧩: Means 🗺️/📍is generated on external module
 * -------------------------------------------------------
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
internal object NavGraphs {

    val root = RootNavGraph
}
