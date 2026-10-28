@file:Suppress("SpellCheckingInspection")

/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeLira 2025 - Present
 *
 */

package com.github.yumelira.yumebox.data.controller

import android.util.Base64
import com.github.yumelira.yumebox.core.model.TunDnsMode
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.data.model.AccessControlMode
import com.github.yumelira.yumebox.data.model.AppColorTheme
import com.github.yumelira.yumebox.data.model.AppLanguage
import com.github.yumelira.yumebox.data.model.MonetContrast
import com.github.yumelira.yumebox.data.model.MonetStyle
import com.github.yumelira.yumebox.data.model.ProxyDisplayMode
import com.github.yumelira.yumebox.data.model.RunMode
import com.github.yumelira.yumebox.data.model.ProxySortMode
import com.github.yumelira.yumebox.data.model.ThemeMode
import com.github.yumelira.yumebox.data.model.TunStack
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.data.store.FeatureStore
import com.github.yumelira.yumebox.data.store.LinkOpenMode
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.data.store.ProfileLink
import com.github.yumelira.yumebox.data.store.ProfileLinksStore
import com.github.yumelira.yumebox.data.store.ProxyDisplaySettingsStore
import com.github.yumelira.yumebox.data.store.OverrideConfigBackupEntry
import com.github.yumelira.yumebox.data.store.OverrideConfigStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

@Serializable
private data class UserSettingsBackup(
    val format: String = UserSettingsBackupController.BACKUP_FORMAT,
    val version: Int = UserSettingsBackupController.BACKUP_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val stores: JsonObject,
    val assets: JsonObject = buildJsonObject { },
)

class UserSettingsBackupController(
    private val appSettingsStore: AppSettingsStore,
    private val featureStore: FeatureStore,
    private val networkSettingsStore: NetworkSettingsStore,
    private val profileLinksStore: ProfileLinksStore,
    private val proxyDisplaySettingsStore: ProxyDisplaySettingsStore,
    private val acgWallpaperStorage: AcgWallpaperStorage,
    private val overrideConfigStore: OverrideConfigStore,
) {
    companion object {
        const val BACKUP_FORMAT = "YumeBoxUserSettingsBackup"
        const val BACKUP_VERSION = 4
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportToJson(): String {
        val backup = UserSettingsBackup(
            stores = buildJsonObject {
                put("app", exportAppSettings())
                put("feature", exportFeatureSettings())
                put("network", exportNetworkSettings())
                put("profileLinks", exportProfileLinks())
                put("proxyDisplay", exportProxyDisplaySettings())
                put("overrideConfigs", exportOverrideConfigs())
            },
            assets = exportAssets(),
        )
        return json.encodeToString(UserSettingsBackup.serializer(), backup)
    }

    suspend fun importFromJson(rawJson: String) {
        val backup = json.decodeFromString(UserSettingsBackup.serializer(), rawJson)
        require(backup.format == BACKUP_FORMAT) { "Unsupported backup format" }
        require(backup.version <= BACKUP_VERSION) { "Unsupported backup version" }

        backup.stores["app"]?.jsonObject?.let(::importAppSettings)
        backup.stores["feature"]?.jsonObject?.let(::importFeatureSettings)
        backup.stores["network"]?.jsonObject?.let(::importNetworkSettings)
        backup.stores["profileLinks"]?.jsonObject?.let(::importProfileLinks)
        backup.stores["proxyDisplay"]?.jsonObject?.let(::importProxyDisplaySettings)
        backup.stores["overrideConfigs"]?.jsonObject?.let { obj -> importOverrideConfigs(obj) }
        importAssets(backup.assets)
    }

    fun validateBackup(rawJson: String) {
        val backup = json.decodeFromString(UserSettingsBackup.serializer(), rawJson)
        require(backup.format == BACKUP_FORMAT) { "Unsupported backup format" }
        require(backup.version in 1..BACKUP_VERSION) { "Unsupported backup version" }
    }

    private fun exportAssets(): JsonObject = buildJsonObject {
        val wallpaperBytes = acgWallpaperStorage.readBytes(appSettingsStore.acgWallpaperUri.value)
        if (wallpaperBytes != null) {
            put(
                "acgWallpaper",
                buildJsonObject {
                    put("encoding", "base64")
                    put("data", Base64.encodeToString(wallpaperBytes, Base64.NO_WRAP))
                },
            )
        }
    }

    private fun importAssets(assets: JsonObject) {
        val wallpaperObject = assets["acgWallpaper"]?.jsonObject ?: return
        val encodedData = wallpaperObject.string("data") ?: return
        val wallpaperBytes = runCatching {
            Base64.decode(encodedData, Base64.DEFAULT)
        }.getOrNull() ?: return
        val restoredUri = acgWallpaperStorage.saveBackupBytes(wallpaperBytes)
        appSettingsStore.acgWallpaperUri.set(restoredUri)
    }

    private fun exportAppSettings(): JsonObject = buildJsonObject {
        put("initialSetupCompleted", appSettingsStore.initialSetupCompleted.value)
        put("privacyPolicyAccepted", appSettingsStore.privacyPolicyAccepted.value)
        put("homePreviewGuideShown", appSettingsStore.homePreviewGuideShown.value)
        put("topBarBlurEnabled", appSettingsStore.topBarBlurEnabled.value)
        put("classicHomeEnabled", appSettingsStore.classicHomeEnabled.value)
        put("useSystemWallpaper", appSettingsStore.useSystemWallpaper.value)
        put("systemWallpaperPermissionRequested", appSettingsStore.systemWallpaperPermissionRequested.value)
        put("moeWallpaperUri", appSettingsStore.moeWallpaperUri.value)
        put("moeWallpaperSourceUri", appSettingsStore.moeWallpaperSourceUri.value)
        put("moeWallpaperZoom", appSettingsStore.moeWallpaperZoom.value)
        put("moeWallpaperBiasX", appSettingsStore.moeWallpaperBiasX.value)
        put("moeWallpaperBiasY", appSettingsStore.moeWallpaperBiasY.value)
        put("moeHomeQuote", appSettingsStore.moeHomeQuote.value)
        put("moeSidebarExpanded", appSettingsStore.moeSidebarExpanded.value)
        put("splitLeftRatio", appSettingsStore.splitLeftRatio.value)
        put("predictiveBackEnabled", appSettingsStore.predictiveBackEnabled.value)
        put("predictiveBackMaxProgress", appSettingsStore.predictiveBackMaxProgress.value)
        put("themeMode", appSettingsStore.themeMode.value.name)
        put("appLanguage", appSettingsStore.appLanguage.value.name)
        put("colorTheme", appSettingsStore.colorTheme.value.name)
        put("monetStyle", appSettingsStore.monetStyle.value.name)
        put("monetContrast", appSettingsStore.monetContrast.value.name)
        put("monetColorIntensity", appSettingsStore.monetColorIntensity.value)
        put("themeAccentColorArgb", appSettingsStore.themeAccentColorArgb.value)
        put("acgWallpaperSeedColorArgb", appSettingsStore.acgWallpaperSeedColorArgb.value)
        put("invertOnPrimaryColors", appSettingsStore.invertOnPrimaryColors.value)
        put("automaticRestart", appSettingsStore.automaticRestart.value)
        put("autoUpdateCurrentProfileOnStart", appSettingsStore.autoUpdateCurrentProfileOnStart.value)
        put("hideAppIcon", appSettingsStore.hideAppIcon.value)
        put("excludeFromRecents", appSettingsStore.excludeFromRecents.value)
        put("showTrafficNotification", appSettingsStore.showTrafficNotification.value)
        put("superIslandEnabled", appSettingsStore.superIslandEnabled.value)
        put("bottomBarAutoHide", appSettingsStore.bottomBarAutoHide.value)
        put("bottomBarUseLegacyStyle", appSettingsStore.bottomBarUseLegacyStyle.value)
        put("acgMainUiEnabled", appSettingsStore.acgMainUiEnabled.value)
        put("acgWallpaperUri", appSettingsStore.acgWallpaperUri.value)
        put("acgWallpaperZoom", appSettingsStore.acgWallpaperZoom.value)
        put("acgWallpaperBiasX", appSettingsStore.acgWallpaperBiasX.value)
        put("acgWallpaperBiasY", appSettingsStore.acgWallpaperBiasY.value)
        put("acgHomeQuote", appSettingsStore.acgHomeQuote.value)
        put("acgHomeQuoteAuthor", appSettingsStore.acgHomeQuoteAuthor.value)
        put("acgDailyQuoteEnabled", appSettingsStore.acgDailyQuoteEnabled.value)
        put("acgDailyQuote", appSettingsStore.acgDailyQuote.value)
        put("acgDailyQuoteAuthor", appSettingsStore.acgDailyQuoteAuthor.value)
        put("acgDailyQuoteDate", appSettingsStore.acgDailyQuoteDate.value)
        put("acgDailyQuoteApiUrl", appSettingsStore.acgDailyQuoteApiUrl.value)
        put("acgDailyQuoteApiHistoryJson", appSettingsStore.acgDailyQuoteApiHistoryJson.value)
        put("acgCustomQuoteEnabled", appSettingsStore.acgCustomQuoteEnabled.value)
        put("acgCustomQuoteListJson", appSettingsStore.acgCustomQuoteListJson.value)
        put("acgMergeCustomQuoteList", appSettingsStore.acgMergeCustomQuoteList.value)
        put("acgSidebarExpanded", appSettingsStore.acgSidebarExpanded.value)
        put("pageScale", appSettingsStore.pageScale.value)
        put("singleNodeTest", appSettingsStore.singleNodeTest.value)
        put("healthCheckConcurrency", appSettingsStore.healthCheckConcurrency.value)
        put("screenshotProtectionEnabled", appSettingsStore.screenshotProtectionEnabled.value)
        put("biometricUnlockEnabled", appSettingsStore.biometricUnlockEnabled.value)
        put("customUserAgent", appSettingsStore.customUserAgent.value)
    }

    private fun importAppSettings(obj: JsonObject) {
        obj.bool("initialSetupCompleted")?.let(appSettingsStore.initialSetupCompleted::set)
        obj.bool("privacyPolicyAccepted")?.let(appSettingsStore.privacyPolicyAccepted::set)
        obj.bool("homePreviewGuideShown")?.let(appSettingsStore.homePreviewGuideShown::set)
        obj.bool("topBarBlurEnabled")?.let(appSettingsStore.topBarBlurEnabled::set)
        obj.bool("classicHomeEnabled")?.let(appSettingsStore.classicHomeEnabled::set)
        obj.bool("useSystemWallpaper")?.let(appSettingsStore.useSystemWallpaper::set)
        obj.bool("systemWallpaperPermissionRequested")?.let(appSettingsStore.systemWallpaperPermissionRequested::set)
        obj.string("moeWallpaperUri")?.let(appSettingsStore.moeWallpaperUri::set)
        obj.string("moeWallpaperSourceUri")?.let(appSettingsStore.moeWallpaperSourceUri::set)
        obj.float("moeWallpaperZoom")?.let(appSettingsStore.moeWallpaperZoom::set)
        obj.float("moeWallpaperBiasX")?.let(appSettingsStore.moeWallpaperBiasX::set)
        obj.float("moeWallpaperBiasY")?.let(appSettingsStore.moeWallpaperBiasY::set)
        obj.string("moeHomeQuote")?.let(appSettingsStore.moeHomeQuote::set)
        obj.bool("moeSidebarExpanded")?.let(appSettingsStore.moeSidebarExpanded::set)
        obj.float("splitLeftRatio")?.let(appSettingsStore.splitLeftRatio::set)
        obj.bool("predictiveBackEnabled")?.let(appSettingsStore.predictiveBackEnabled::set)
        obj.float("predictiveBackMaxProgress")?.let(appSettingsStore.predictiveBackMaxProgress::set)
        obj.enumValue<ThemeMode>("themeMode")?.let(appSettingsStore.themeMode::set)
        obj.enumValue<AppLanguage>("appLanguage")?.let(appSettingsStore.appLanguage::set)
        obj.enumValue<AppColorTheme>("colorTheme")?.let(appSettingsStore.colorTheme::set)
        obj.enumValue<MonetStyle>("monetStyle")?.let(appSettingsStore.monetStyle::set)
        obj.enumValue<MonetContrast>("monetContrast")?.let(appSettingsStore.monetContrast::set)
        obj.float("monetColorIntensity")?.let(appSettingsStore.monetColorIntensity::set)
        obj.long("themeAccentColorArgb")?.let(appSettingsStore.themeAccentColorArgb::set)
        obj.long("acgWallpaperSeedColorArgb")?.let(appSettingsStore.acgWallpaperSeedColorArgb::set)
        obj.bool("invertOnPrimaryColors")?.let(appSettingsStore.invertOnPrimaryColors::set)
        obj.bool("automaticRestart")?.let(appSettingsStore.automaticRestart::set)
        obj.bool("autoUpdateCurrentProfileOnStart")?.let(appSettingsStore.autoUpdateCurrentProfileOnStart::set)
        obj.bool("hideAppIcon")?.let(appSettingsStore.hideAppIcon::set)
        obj.bool("excludeFromRecents")?.let(appSettingsStore.excludeFromRecents::set)
        obj.bool("showTrafficNotification")?.let(appSettingsStore.showTrafficNotification::set)
        obj.bool("superIslandEnabled")?.let(appSettingsStore.superIslandEnabled::set)
        obj.bool("bottomBarAutoHide")?.let(appSettingsStore.bottomBarAutoHide::set)
        obj.bool("bottomBarUseLegacyStyle")?.let(appSettingsStore.bottomBarUseLegacyStyle::set)
        obj.bool("acgMainUiEnabled")?.let(appSettingsStore.acgMainUiEnabled::set)
        obj.string("acgWallpaperUri")?.let(appSettingsStore.acgWallpaperUri::set)
        obj.float("acgWallpaperZoom")?.let(appSettingsStore.acgWallpaperZoom::set)
        obj.float("acgWallpaperBiasX")?.let(appSettingsStore.acgWallpaperBiasX::set)
        obj.float("acgWallpaperBiasY")?.let(appSettingsStore.acgWallpaperBiasY::set)
        obj.string("acgHomeQuote")?.let(appSettingsStore.acgHomeQuote::set)
        obj.string("acgHomeQuoteAuthor")?.let(appSettingsStore.acgHomeQuoteAuthor::set)
        obj.bool("acgDailyQuoteEnabled")?.let(appSettingsStore.acgDailyQuoteEnabled::set)
        obj.string("acgDailyQuote")?.let(appSettingsStore.acgDailyQuote::set)
        obj.string("acgDailyQuoteAuthor")?.let(appSettingsStore.acgDailyQuoteAuthor::set)
        obj.string("acgDailyQuoteDate")?.let(appSettingsStore.acgDailyQuoteDate::set)
        obj.string("acgDailyQuoteApiUrl")?.let(appSettingsStore.acgDailyQuoteApiUrl::set)
        obj.string("acgDailyQuoteApiHistoryJson")?.let(appSettingsStore.acgDailyQuoteApiHistoryJson::set)
        obj.bool("acgCustomQuoteEnabled")?.let(appSettingsStore.acgCustomQuoteEnabled::set)
        obj.string("acgCustomQuoteListJson")?.let(appSettingsStore.acgCustomQuoteListJson::set)
        obj.bool("acgMergeCustomQuoteList")?.let(appSettingsStore.acgMergeCustomQuoteList::set)
        obj.bool("acgSidebarExpanded")?.let(appSettingsStore.acgSidebarExpanded::set)
        obj.float("pageScale")?.let(appSettingsStore.pageScale::set)
        obj.bool("singleNodeTest")?.let(appSettingsStore.singleNodeTest::set)
        obj.int("healthCheckConcurrency")?.let { concurrency ->
            appSettingsStore.healthCheckConcurrency.set(
                when (concurrency) {
                    16, 24, 32 -> concurrency
                    else -> 8
                },
            )
        }
        obj.bool("screenshotProtectionEnabled")?.let(appSettingsStore.screenshotProtectionEnabled::set)
        obj.bool("biometricUnlockEnabled")?.let(appSettingsStore.biometricUnlockEnabled::set)
        obj.string("customUserAgent")?.let(appSettingsStore.customUserAgent::set)
    }

    private fun exportFeatureSettings(): JsonObject = buildJsonObject {
        put("panelProtocol", featureStore.panelProtocol.value.name)
        put("subStoreAutoCloseMode", featureStore.subStoreAutoCloseMode.value)
        put("allowLanAccess", featureStore.allowLanAccess.value)
        put("backendPort", featureStore.backendPort.value)
        put("frontendPort", featureStore.frontendPort.value)
        put("selectedPanelType", featureStore.selectedPanelType.value)
        put("panelOpenMode", featureStore.panelOpenMode.value.name)
        put("showWebControlInProxy", featureStore.showWebControlInProxy.value)
        put("exitUiWhenBackground", featureStore.exitUiWhenBackground.value)
    }

    private fun importFeatureSettings(obj: JsonObject) {
        obj.enumValue<com.github.yumelira.yumebox.data.model.RemoteProtocol>("panelProtocol")?.let(featureStore.panelProtocol::set)
        obj.int("subStoreAutoCloseMode")?.let(featureStore.subStoreAutoCloseMode::set)
        obj.bool("allowLanAccess")?.let(featureStore.allowLanAccess::set)
        obj.int("backendPort")?.let(featureStore.backendPort::set)
        obj.int("frontendPort")?.let(featureStore.frontendPort::set)
        obj.int("selectedPanelType")?.let(featureStore.selectedPanelType::set)
        obj.enumValue<LinkOpenMode>("panelOpenMode")?.let(featureStore.panelOpenMode::set)
        obj.bool("showWebControlInProxy")?.let(featureStore.showWebControlInProxy::set)
        obj.bool("exitUiWhenBackground")?.let(featureStore.exitUiWhenBackground::set)
    }

    private fun exportNetworkSettings(): JsonObject = buildJsonObject {
        put("disableAllOverride", networkSettingsStore.disableAllOverride.value)
        put("ebpfBypassCn", networkSettingsStore.ebpfBypassCn.value)
        put("accessControlSelectedFirst", networkSettingsStore.accessControlSelectedFirst.value)
        put("accessControlShowSystemApps", networkSettingsStore.accessControlShowSystemApps.value)
        put("accessControlSortMode", networkSettingsStore.accessControlSortMode.value.name)
        put("wifiAutomationEnabled", networkSettingsStore.wifiAutomationEnabled.value)
        put("wifiAutomationLocationRequested", networkSettingsStore.wifiAutomationLocationRequested.value)
        put("wifiAutomationRules", json.encodeToJsonElement(ListSerializer(com.github.yumelira.yumebox.data.model.WifiAutomationRule.serializer()), networkSettingsStore.wifiAutomationRules.value))
        put("wifiAutomationOtherWifiAction", networkSettingsStore.wifiAutomationOtherWifiAction.value.name)
        put("wifiAutomationNoWifiAction", networkSettingsStore.wifiAutomationNoWifiAction.value.name)
        put("wifiAutomationOtherWifiProfileUuid", networkSettingsStore.wifiAutomationOtherWifiProfileUuid.value)
        put("wifiAutomationNoWifiProfileUuid", networkSettingsStore.wifiAutomationNoWifiProfileUuid.value)
        put("runMode", networkSettingsStore.runMode.value.name)
        put("bypassPrivateNetwork", networkSettingsStore.bypassPrivateNetwork.value)
        put("dnsHijack", networkSettingsStore.dnsHijack.value)
        put("allowBypass", networkSettingsStore.allowBypass.value)
        put("enableIPv6", networkSettingsStore.enableIPv6.value)
        put("systemProxy", networkSettingsStore.systemProxy.value)
        put("tunStack", networkSettingsStore.tunStack.value.name)
        put("tunRouteExcludeAddress", networkSettingsStore.tunRouteExcludeAddress.value.toStringJsonArray())
        put("rootTunIfName", networkSettingsStore.tunIfName.value)
        put("rootTunMtu", networkSettingsStore.tunMtu.value)
        put("rootTunAutoRoute", networkSettingsStore.tunAutoRoute.value)
        put("rootTunStrictRoute", networkSettingsStore.tunStrictRoute.value)
        put("rootTunAutoRedirect", networkSettingsStore.tunAutoRedirect.value)
        put("rootTunIncludeAndroidUser", networkSettingsStore.tunIncludeAndroidUser.value.toIntJsonArray())
        put("rootTunRouteExcludeAddress", networkSettingsStore.tunRouteExcludeAddress.value.toStringJsonArray())
        put("rootTunDnsMode", networkSettingsStore.tunDnsMode.value.name)
        put("rootTunFakeIpRange", networkSettingsStore.tunFakeIpRange.value)
        put("rootTunFakeIpRange6", networkSettingsStore.tunFakeIpRange6.value)
        put("accessControlMode", networkSettingsStore.accessControlMode.value.name)
        put("accessControlPackages", networkSettingsStore.accessControlPackages.value.sorted().toStringJsonArray())
    }

    private fun importNetworkSettings(obj: JsonObject) {
        obj.bool("disableAllOverride")?.let(networkSettingsStore.disableAllOverride::set)
        obj.bool("ebpfBypassCn")?.let(networkSettingsStore.ebpfBypassCn::set)
        obj.bool("accessControlSelectedFirst")?.let(networkSettingsStore.accessControlSelectedFirst::set)
        obj.bool("accessControlShowSystemApps")?.let(networkSettingsStore.accessControlShowSystemApps::set)
        obj.enumValue<com.github.yumelira.yumebox.data.model.AccessControlSortMode>("accessControlSortMode")?.let(networkSettingsStore.accessControlSortMode::set)
        obj.bool("wifiAutomationEnabled")?.let(networkSettingsStore.wifiAutomationEnabled::set)
        obj.bool("wifiAutomationLocationRequested")?.let(networkSettingsStore.wifiAutomationLocationRequested::set)
        obj["wifiAutomationRules"]?.let { networkSettingsStore.wifiAutomationRules.set(json.decodeFromJsonElement(ListSerializer(com.github.yumelira.yumebox.data.model.WifiAutomationRule.serializer()), it)) }
        obj.enumValue<com.github.yumelira.yumebox.data.model.WifiAutomationFallbackAction>("wifiAutomationOtherWifiAction")?.let(networkSettingsStore.wifiAutomationOtherWifiAction::set)
        obj.enumValue<com.github.yumelira.yumebox.data.model.WifiAutomationFallbackAction>("wifiAutomationNoWifiAction")?.let(networkSettingsStore.wifiAutomationNoWifiAction::set)
        obj.string("wifiAutomationOtherWifiProfileUuid")?.let(networkSettingsStore.wifiAutomationOtherWifiProfileUuid::set)
        obj.string("wifiAutomationNoWifiProfileUuid")?.let(networkSettingsStore.wifiAutomationNoWifiProfileUuid::set)
        val mode = obj.enumValue<RunMode>("runMode") ?: when (obj.string("proxyMode")) {
            "Tun", "VpnService", "Http" -> RunMode.VpnService
            "RootTun" -> RunMode.Tun
            "Ebpf" -> RunMode.Ebpf
            else -> null
        }
        mode?.let(networkSettingsStore.runMode::set)
        obj.bool("bypassPrivateNetwork")?.let(networkSettingsStore.bypassPrivateNetwork::set)
        obj.bool("dnsHijack")?.let(networkSettingsStore.dnsHijack::set)
        obj.bool("allowBypass")?.let(networkSettingsStore.allowBypass::set)
        obj.bool("enableIPv6")?.let(networkSettingsStore.enableIPv6::set)
        obj.bool("systemProxy")?.let(networkSettingsStore.systemProxy::set)
        obj.enumValue<TunStack>("tunStack")?.let(networkSettingsStore.tunStack::set)
        obj.stringList("tunRouteExcludeAddress")?.let(networkSettingsStore.tunRouteExcludeAddress::set)
        obj.string("rootTunIfName")?.let(networkSettingsStore.tunIfName::set)
        obj.int("rootTunMtu")?.let(networkSettingsStore.tunMtu::set)
        obj.bool("rootTunAutoRoute")?.let(networkSettingsStore.tunAutoRoute::set)
        obj.bool("rootTunStrictRoute")?.let(networkSettingsStore.tunStrictRoute::set)
        obj.bool("rootTunAutoRedirect")?.let(networkSettingsStore.tunAutoRedirect::set)
        obj.intList("rootTunIncludeAndroidUser")?.let(networkSettingsStore.tunIncludeAndroidUser::set)
        obj.stringList("rootTunRouteExcludeAddress")?.let(networkSettingsStore.tunRouteExcludeAddress::set)
        obj.enumValue<TunDnsMode>("rootTunDnsMode")?.let(networkSettingsStore.tunDnsMode::set)
        obj.string("rootTunFakeIpRange")?.let(networkSettingsStore.tunFakeIpRange::set)
        obj.string("rootTunFakeIpRange6")?.let(networkSettingsStore.tunFakeIpRange6::set)
        obj.enumValue<AccessControlMode>("accessControlMode")?.let(networkSettingsStore.accessControlMode::set)
        obj.stringList("accessControlPackages")?.toSet()?.let(networkSettingsStore.accessControlPackages::set)
    }

    private fun exportProfileLinks(): JsonObject = buildJsonObject {
        put("linkOpenMode", profileLinksStore.linkOpenMode.value.name)
        put("links", json.encodeToJsonElement(ListSerializer(ProfileLink.serializer()), profileLinksStore.links.value))
        put("defaultLinkId", profileLinksStore.defaultLinkId.value)
    }

    private fun importProfileLinks(obj: JsonObject) {
        obj.enumValue<LinkOpenMode>("linkOpenMode")?.let(profileLinksStore.linkOpenMode::set)
        obj["links"]?.let { element ->
            runCatching {
                json.decodeFromJsonElement(ListSerializer(ProfileLink.serializer()), element)
            }.getOrNull()?.let(profileLinksStore.links::set)
        }
        obj.string("defaultLinkId")?.let(profileLinksStore.defaultLinkId::set)
    }

    private suspend fun exportOverrideConfigs(): JsonObject = buildJsonObject {
        put(
            "configs",
            json.encodeToJsonElement(
                ListSerializer(OverrideConfigBackupEntry.serializer()),
                overrideConfigStore.exportUserConfigBackup(),
            ),
        )
    }

    private suspend fun importOverrideConfigs(obj: JsonObject) {
        val entries = obj["configs"]?.let { element ->
            runCatching {
                json.decodeFromJsonElement(ListSerializer(OverrideConfigBackupEntry.serializer()), element)
            }.getOrNull()
        } ?: return
        overrideConfigStore.importUserConfigBackup(entries)
    }

    private fun exportProxyDisplaySettings(): JsonObject = buildJsonObject {
        put("sortMode", proxyDisplaySettingsStore.sortMode.value.name)
        put("displayMode", proxyDisplaySettingsStore.displayMode.value.name)
        put("proxyMode", proxyDisplaySettingsStore.proxyMode.value.name)
        put("ruleProfileUuid", proxyDisplaySettingsStore.ruleProfileUuid)
        put("globalProfileUuid", proxyDisplaySettingsStore.globalProfileUuid)
        put("directProfileUuid", proxyDisplaySettingsStore.directProfileUuid)
        put("sheetHeightFraction", proxyDisplaySettingsStore.sheetHeightFraction.value)
    }

    private fun importProxyDisplaySettings(obj: JsonObject) {
        obj.enumValue<ProxySortMode>("sortMode")?.let(proxyDisplaySettingsStore.sortMode::set)
        obj.enumValue<ProxyDisplayMode>("displayMode")?.let(proxyDisplaySettingsStore.displayMode::set)
        obj.enumValue<TunnelState.Mode>("proxyMode")?.let(proxyDisplaySettingsStore.proxyMode::set)
        obj.string("ruleProfileUuid")?.let { proxyDisplaySettingsStore.ruleProfileUuid = it }
        obj.string("globalProfileUuid")?.let { proxyDisplaySettingsStore.globalProfileUuid = it }
        obj.string("directProfileUuid")?.let { proxyDisplaySettingsStore.directProfileUuid = it }
        obj.float("sheetHeightFraction")?.let(proxyDisplaySettingsStore.sheetHeightFraction::set)
    }

    private fun List<String>.toStringJsonArray(): JsonArray = JsonArray(map(::JsonPrimitive))
    private fun List<Int>.toIntJsonArray(): JsonArray = JsonArray(map(::JsonPrimitive))

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content
    private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
    private fun JsonObject.float(key: String): Float? = this[key]?.jsonPrimitive?.floatOrNull

    private inline fun <reified T : Enum<T>> JsonObject.enumValue(key: String): T? {
        return string(key)?.let { name ->
            runCatching { enumValueOf<T>(name) }.getOrNull()
        }
    }

    private fun JsonObject.stringList(key: String): List<String>? {
        return this[key]?.jsonArray?.mapNotNull { element -> element.jsonPrimitive.content }
    }

    private fun JsonObject.intList(key: String): List<Int>? {
        return this[key]?.jsonArray?.mapNotNull { element -> element.jsonPrimitive.intOrNull }
    }
}
