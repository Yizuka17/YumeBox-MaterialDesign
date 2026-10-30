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


package com.github.yumelira.yumebox.screen.settings
import com.github.yumelira.yumebox.presentation.theme.UiDp
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.TextFieldValue
import com.github.yumelira.yumebox.common.util.AppIconHelper
import com.github.yumelira.yumebox.common.util.BiometricHelper
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.data.model.AppColorTheme
import com.github.yumelira.yumebox.data.model.AppLanguage
import com.github.yumelira.yumebox.data.model.ThemeMode
import com.github.yumelira.yumebox.feature.editor.presentation.language.LanguageScope
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.AppTextFieldDialog
import com.github.yumelira.yumebox.presentation.component.HapticSwitch
import com.github.yumelira.yumebox.presentation.component.PreferenceArrowItem
import com.github.yumelira.yumebox.presentation.component.PreferenceEnumItem
import com.github.yumelira.yumebox.presentation.component.PreferenceSwitchItem
import com.github.yumelira.yumebox.presentation.component.PreferenceValueItem
import com.github.yumelira.yumebox.presentation.component.ScreenSectionGrid
import com.github.yumelira.yumebox.presentation.component.TextEditBottomSheet
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.WarningBottomSheet
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.util.OverrideStructuredEditorStore
import com.github.yumelira.yumebox.screen.settings.component.ThemeColorPickerItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AcgWallpaperConfigScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AcgQuoteConfigScreenDestination
import com.ramcosta.composedestinations.generated.destinations.OverrideConfigPreviewRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel
import androidx.core.net.toUri
import kotlin.math.abs

@Composable
@Destination<RootGraph>
fun AppSettingsScreen(
    navigator: DestinationsNavigator,
) {
    val viewModel = koinViewModel<AppSettingsViewModel>()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopBar(title = MLang.AppSettings.Title)
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenSectionGrid(
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                AppHomeSettingsSection(
                    viewModel = viewModel,
                    navigator = navigator,
                )
            }
            item { AppThemeSettingsSection(viewModel) }
            item { AppInterfaceSettingsSection(viewModel) }
            item { AppServiceSettingsSection(viewModel) }
            item { AppBehaviorSettingsSection(viewModel) }
            item { AppPrivacySettingsSection(viewModel) }
        }
    }
}

@Composable
private fun AppBehaviorSettingsSection(viewModel: AppSettingsViewModel) {
    val context = LocalContext.current
    val automaticRestart by viewModel.automaticRestart.state.collectAsState()
    val autoUpdateCurrentProfileOnStart by viewModel.autoUpdateCurrentProfileOnStart.state.collectAsState()

    Title(MLang.AppSettings.Section.Behavior)
    Card {
        PreferenceSwitchItem(
            title = MLang.AppSettings.Behavior.AutoStartTitle,
            summary = MLang.AppSettings.Behavior.AutoStartSummary,
            checked = automaticRestart,
            onCheckedChange = viewModel::onAutomaticRestartChange,
        )
        if (com.github.yumelira.yumebox.common.util.XiaomiAutoStart.isSupported()) {
            PreferenceArrowItem(
                title = "系统自启动权限",
                summary = "打开 HyperOS 自启动设置，允许 YumeBox MD3 在后台启动",
                onClick = {
                    if (!com.github.yumelira.yumebox.common.util.XiaomiAutoStart.openSettings(context)) {
                        context.toast("无法打开系统自启动设置")
                    }
                },
            )
        }
        PreferenceSwitchItem(
            title = MLang.AppSettings.Behavior.AutoUpdateOnStartTitle,
            summary = MLang.AppSettings.Behavior.AutoUpdateOnStartSummary,
            checked = autoUpdateCurrentProfileOnStart,
            onCheckedChange = viewModel::onAutoUpdateCurrentProfileOnStartChange,
        )
    }
}

@Composable
private fun AppThemeSettingsSection(viewModel: AppSettingsViewModel) {
    val themeMode by viewModel.themeMode.state.collectAsState()
    val colorTheme by viewModel.colorTheme.state.collectAsState()
    val themeSeedColorArgb by viewModel.themeSeedColorArgb.state.collectAsState()
    val invertOnPrimaryColors by viewModel.invertOnPrimaryColors.state.collectAsState()

    Title(MLang.AppSettings.Interface.ColorThemeTitle)
    Card {
        PreferenceEnumItem(
            title = MLang.AppSettings.Interface.ThemeModeTitle,
            summary = MLang.AppSettings.Interface.ThemeModeSummary,
            currentValue = themeMode,
            items = listOf(
                MLang.AppSettings.Interface.ThemeModeSystem,
                MLang.AppSettings.Interface.ThemeModeLight,
                MLang.AppSettings.Interface.ThemeModeDark,
            ),
            values = ThemeMode.entries,
            onValueChange = viewModel::onThemeModeChange,
        )
        PreferenceEnumItem(
            title = MLang.AppSettings.Interface.ColorThemeModeTitle,
            summary = MLang.AppSettings.Interface.ColorThemeModeSummary,
            currentValue = colorTheme,
            items = listOf(
                MLang.AppSettings.Interface.ColorThemeModeMonet,
                MLang.AppSettings.Interface.ColorThemeModeCustom,
                MLang.AppSettings.Interface.ColorThemeModeAcgWallpaper,
            ),
            values = listOf(
                AppColorTheme.MonetDynamic,
                AppColorTheme.Custom,
                AppColorTheme.AcgWallpaper,
            ),
            onValueChange = viewModel::onColorThemeChange,
        )
        if (colorTheme == AppColorTheme.Custom) {
            ThemeColorPickerItem(
                themeSeedColorArgb = themeSeedColorArgb,
                onThemeSeedColorChange = viewModel::onThemeSeedColorChange,
            )
        } else if (colorTheme == AppColorTheme.AcgWallpaper) {
            PreferenceValueItem(
                title = MLang.AppSettings.Interface.ColorThemePickerTitle,
                summary = MLang.AppSettings.Interface.ColorThemeAcgWallpaperSummary,
                onClick = { },
            )
        } else {
            PreferenceValueItem(
                title = MLang.AppSettings.Interface.ColorThemePickerTitle,
                summary = MLang.AppSettings.Interface.ColorThemeDynamicSummary,
                onClick = { },
            )
        }
        PreferenceSwitchItem(
            title = MLang.AppSettings.Interface.ThemeColorPolarityInvertTitle,
            summary = MLang.AppSettings.Interface.ThemeColorPolarityInvertSummary,
            checked = invertOnPrimaryColors,
            onCheckedChange = viewModel::onInvertOnPrimaryColorsChange,
        )
    }
}

@Composable
private fun AppInterfaceSettingsSection(viewModel: AppSettingsViewModel) {
    val appLanguage by viewModel.appLanguage.state.collectAsState()
    val bottomBarAutoHide by viewModel.bottomBarAutoHide.state.collectAsState()
    val bottomBarUseLegacyStyle by viewModel.bottomBarUseLegacyStyle.state.collectAsState()
    val pageScale by viewModel.pageScale.state.collectAsState()
    val tabletMode by viewModel.acgTabletModeEnabled.state.collectAsState()
    Title(MLang.AppSettings.Section.Interface)
    Card {
        PreferenceSwitchItem(
            title = MLang.AppSettings.Wallpaper.TabletMode,
            summary = MLang.AppSettings.Wallpaper.TabletSummary,
            checked = tabletMode,
            onCheckedChange = viewModel.acgTabletModeEnabled::set,
        )
        PreferenceEnumItem(
            title = MLang.AppSettings.Interface.LanguageTitle,
            summary = MLang.AppSettings.Interface.LanguageSummary,
            currentValue = appLanguage,
            items = listOf(
                MLang.AppSettings.Interface.LanguageSystem,
                MLang.AppSettings.Interface.LanguageChinese,
                MLang.AppSettings.Interface.LanguageTraditionalChinese,
                MLang.AppSettings.Interface.LanguageEnglish,
            ),
            values = listOf(AppLanguage.System, AppLanguage.Zh, AppLanguage.ZhTw, AppLanguage.En),
            onValueChange = viewModel::onAppLanguageChange,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.Interface.AutoHideNavbarTitle,
            summary = MLang.AppSettings.Interface.AutoHideNavbarSummary,
            checked = bottomBarAutoHide,
            onCheckedChange = viewModel::onBottomBarAutoHideChange,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.Interface.LegacyNavbarStyleTitle,
            summary = MLang.AppSettings.Interface.LegacyNavbarStyleSummary,
            checked = bottomBarUseLegacyStyle,
            onCheckedChange = viewModel::onBottomBarUseLegacyStyleChange,
        )
        PageScalePreferenceItem(
            pageScale = pageScale,
            onApply = viewModel::onPageScaleChange,
        )
    }
}

@Composable
private fun AppPrivacySettingsSection(viewModel: AppSettingsViewModel) {
    val context = LocalContext.current
    val excludeFromRecents by viewModel.excludeFromRecents.state.collectAsState()

    Title(MLang.AppSettings.Section.Privacy)
    Card {
        BiometricProtectedPreferenceSwitch(
            checkedFlow = viewModel.biometricUnlockEnabled.state,
            title = MLang.AppSettings.Privacy.BiometricUnlockTitle,
            summary = MLang.AppSettings.Privacy.BiometricUnlockSummary,
            enableTitle = MLang.AppSettings.Privacy.BiometricDialogTitleEnable,
            disableTitle = MLang.AppSettings.Privacy.BiometricDialogTitleDisable,
            onConfirmedChange = viewModel::onBiometricUnlockEnabledChange,
        )
        BiometricProtectedPreferenceSwitch(
            checkedFlow = viewModel.screenshotProtectionEnabled.state,
            title = MLang.AppSettings.Privacy.ScreenshotProtectionTitle,
            summary = MLang.AppSettings.Privacy.ScreenshotProtectionSummary,
            enableTitle = MLang.AppSettings.Privacy.ScreenshotDialogTitleEnable,
            disableTitle = MLang.AppSettings.Privacy.ScreenshotDialogTitleDisable,
            onConfirmedChange = viewModel::onScreenshotProtectionEnabledChange,
        )
        HideAppIconPreferenceItem(
            hideAppIconFlow = viewModel.hideAppIcon.state,
            onHideAppIconChange = viewModel::onHideAppIconChange,
            context = context,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.Privacy.HideFromRecentsTitle,
            summary = MLang.AppSettings.Privacy.HideFromRecentsSummary,
            checked = excludeFromRecents,
            onCheckedChange = viewModel::onExcludeFromRecentsChange,
        )
    }
}

@Composable
private fun AppServiceSettingsSection(viewModel: AppSettingsViewModel) {
    val context = LocalContext.current
    val showTrafficNotification by viewModel.showTrafficNotification.state.collectAsState()
    val singleNodeTest by viewModel.singleNodeTest.state.collectAsState()
    val exitUiWhenBackground by viewModel.exitUiWhenBackground.state.collectAsState()

    Title(MLang.AppSettings.Section.Service)
    Card {
        if (com.github.yumelira.yumebox.common.util.XiaomiAutoStart.isSupported()) {
            val settings = org.koin.compose.koinInject<com.github.yumelira.yumebox.data.store.AppSettingsStore>()
            val islandEnabled by settings.superIslandEnabled.state.collectAsState()
            var shizukuAvailable by remember {
                mutableStateOf(com.github.yumelira.yumebox.runtime.service.shizuku.ShizukuManager.isAvailable())
            }
            PreferenceSwitchItem(
                title = "超级岛",
                summary = "在支持的 HyperOS 系统上显示代理状态和流量",
                checked = islandEnabled,
                onCheckedChange = settings.superIslandEnabled::set,
            )
            PreferenceArrowItem(
                title = "Shizuku 授权",
                summary = if (shizukuAvailable) "已授权" else "用于 HyperOS 超级岛兼容",
                onClick = {
                    val manager = com.github.yumelira.yumebox.runtime.service.shizuku.ShizukuManager
                    if (manager.isRunning()) {
                        manager.requestPermission { granted ->
                            shizukuAvailable = granted
                            context.toast(if (granted) "Shizuku 已授权" else "未获得 Shizuku 授权")
                        }
                    } else if (!manager.openShizuku(context)) context.toast("请先安装并启动 Shizuku")
                },
            )
        }
        PreferenceSwitchItem(
            title = MLang.AppSettings.ServiceSection.TrafficNotificationTitle,
            summary = MLang.AppSettings.ServiceSection.TrafficNotificationSummary,
            checked = showTrafficNotification,
            onCheckedChange = viewModel::onShowTrafficNotificationChange,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.ServiceSection.SingleNodeTestTitle,
            summary = MLang.AppSettings.ServiceSection.SingleNodeTestSummary,
            checked = singleNodeTest,
            onCheckedChange = viewModel::onSingleNodeTestChange,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.ServiceSection.ExitUiWhenBackgroundTitle,
            summary = MLang.AppSettings.ServiceSection.ExitUiWhenBackgroundSummary,
            checked = exitUiWhenBackground,
            onCheckedChange = viewModel::onExitUiWhenBackgroundChange,
        )
        PreferenceArrowItem(
            title = MLang.AppSettings.ServiceSection.BatteryOptimizationTitle,
            onClick = {
                if (!openBatteryOptimizationSettings(context)) {
                    context.toast(MLang.Util.Error.UnknownError)
                }
            },
        )
    }
}

@Composable
internal fun NetworkUserAgentSettingsSection(viewModel: AppSettingsViewModel) {
    val customUserAgent by viewModel.customUserAgent.state.collectAsState()

    Title(MLang.AppSettings.Section.Network)
    Card {
        CustomUserAgentPreferenceItem(
            customUserAgent = customUserAgent,
            onConfirm = viewModel::applyCustomUserAgent,
        )
    }
}

@Composable
private fun AppHomeSettingsSection(
    viewModel: AppSettingsViewModel,
    navigator: DestinationsNavigator,
) {
    val acgMainUiEnabled by viewModel.acgMainUiEnabled.state.collectAsState()
    val acgDailyQuoteApiEnabled by viewModel.acgDailyQuoteEnabled.state.collectAsState()
    val acgCustomQuoteEnabled by viewModel.acgCustomQuoteEnabled.state.collectAsState()
    val acgSidebarExpanded by viewModel.acgSidebarExpanded.state.collectAsState()
    Title(MLang.AppSettings.Section.Home)
    Card {
        PreferenceSwitchItem(
            title = MLang.AppSettings.Experimental.AcgHomeTitle,
            summary = MLang.AppSettings.Experimental.AcgHomeSummary,
            checked = acgMainUiEnabled,
            onCheckedChange = viewModel::onAcgMainUiEnabledChange,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.Experimental.AcgSidebarExpandedTitle,
            summary = MLang.AppSettings.Experimental.AcgSidebarExpandedSummary,
            checked = acgSidebarExpanded,
            onCheckedChange = viewModel::onAcgSidebarExpandedChange,
        )
        PreferenceArrowItem(
            title = MLang.AppSettings.Experimental.DailyQuoteTitle,
            summary = when {
                acgDailyQuoteApiEnabled && acgCustomQuoteEnabled -> MLang.AppSettings.Experimental.DailyQuoteSummaryApiAndCustomEnabled
                acgDailyQuoteApiEnabled -> MLang.AppSettings.Experimental.DailyQuoteSummaryApiEnabled
                acgCustomQuoteEnabled -> MLang.AppSettings.Experimental.DailyQuoteSummaryCustomEnabled
                else -> MLang.AppSettings.Experimental.DailyQuoteSummaryConfig
            },
            onClick = {
                navigator.navigate(AcgQuoteConfigScreenDestination) {
                    launchSingleTop = true
                }
            },
        )
        PreferenceArrowItem(
            title = MLang.AppSettings.Experimental.WallpaperTitle,
            summary = MLang.AppSettings.Experimental.WallpaperSummary,
            onClick = { navigator.navigate(AcgWallpaperConfigScreenDestination) { launchSingleTop = true } },
        )
    }
}

@Composable
@Destination<RootGraph>
fun AcgQuoteConfigScreen(
    navigator: DestinationsNavigator,
) {
    val viewModel = koinViewModel<AppSettingsViewModel>()
    val acgDailyQuoteApiEnabled by viewModel.acgDailyQuoteEnabled.state.collectAsState()
    val acgCustomQuoteEnabled by viewModel.acgCustomQuoteEnabled.state.collectAsState()
    val acgDailyQuoteApiUrl by viewModel.acgDailyQuoteApiUrl.state.collectAsState()
    val acgCustomQuoteListJson by viewModel.acgCustomQuoteListJson.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopBar(title = MLang.AppSettings.Experimental.DailyQuoteConfigTitle)
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenSectionGrid(
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Title(MLang.AppSettings.Experimental.DailyQuoteTitle)
                Card {
                    AcgQuotePreferenceItem(
                        title = MLang.AppSettings.Experimental.DailyQuoteApiTitle,
                        summary = acgDailyQuoteApiUrl,
                        dialogTitle = MLang.AppSettings.Experimental.DailyQuoteApiEditTitle,
                        currentValue = acgDailyQuoteApiUrl,
                        endActions = {
                            HapticSwitch(
                                checked = acgDailyQuoteApiEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.onAcgDailyQuoteEnabledChange(enabled)
                                    if (enabled || acgCustomQuoteEnabled) {
                                        viewModel.refreshDailyAcgQuoteIfNeeded(force = true)
                                    }
                                },
                            )
                        },
                        onConfirm = {
                            viewModel.onAcgDailyQuoteApiUrlChange(it)
                            if (acgDailyQuoteApiEnabled || acgCustomQuoteEnabled) {
                                viewModel.refreshDailyAcgQuoteIfNeeded(force = true)
                            }
                        },
                    )
                    AcgTextEditorPreferenceItem(
                        title = MLang.AppSettings.Experimental.CustomQuoteTitle,
                        summary = MLang.AppSettings.Experimental.CustomQuoteSummary,
                        editorTitle = MLang.AppSettings.Experimental.CustomQuoteEditorTitle,
                        content = acgCustomQuoteListJson.ifBlank { customQuoteListTemplate() },
                        navigator = navigator,
                        endActions = {
                            HapticSwitch(
                                checked = acgCustomQuoteEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.onAcgCustomQuoteEnabledChange(enabled)
                                    if (enabled || acgDailyQuoteApiEnabled) {
                                        viewModel.refreshDailyAcgQuoteIfNeeded(force = true)
                                    }
                                },
                            )
                        },
                        onSave = {
                            viewModel.onAcgCustomQuoteListJsonChange(it)
                            if (acgDailyQuoteApiEnabled || acgCustomQuoteEnabled) {
                                viewModel.refreshDailyAcgQuoteIfNeeded(force = true)
                            }
                        },
                    )
                    PreferenceArrowItem(
                        title = MLang.AppSettings.Experimental.DailyQuoteDocsTitle,
                        summary = MLang.AppSettings.Experimental.DailyQuoteDocsSummary,
                        onClick = {
                            if (!openDailyQuoteDocs(context)) {
                                context.toast(MLang.Util.Error.UnknownError)
                            }
                        },
                    )
                }
            }
        }
    }
}

private const val DAILY_QUOTE_DOCS_URL =
    "https://github.com/Yizuka17/YumeBox-MaterialDesign/blob/Yume/docs/DailyQuote.md"

private fun openDailyQuoteDocs(context: android.content.Context): Boolean = runCatching {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, DAILY_QUOTE_DOCS_URL.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}.isSuccess

private fun customQuoteListTemplate() = """// ${MLang.AppSettings.Experimental.CustomQuoteTemplateComment}
// ${MLang.AppSettings.Experimental.CustomQuoteTemplateStringArray}
// ["${MLang.AppSettings.Experimental.CustomQuoteTemplateSentenceOne}", "${MLang.AppSettings.Experimental.CustomQuoteTemplateSentenceTwo}"]
// ${MLang.AppSettings.Experimental.CustomQuoteTemplateObjectArray}
[
  {
    "text": "${MLang.AppSettings.Experimental.CustomQuoteTemplateSampleTextOne}",
    "author": "${MLang.AppSettings.Experimental.CustomQuoteTemplateSampleAuthorOne}"
  },
  {
    "text": "${MLang.AppSettings.Experimental.CustomQuoteTemplateSampleTextTwo}",
    "author": "${MLang.AppSettings.Experimental.CustomQuoteTemplateSampleAuthorTwo}"
  },
  {
    "hitokoto": "${MLang.AppSettings.Experimental.CustomQuoteTemplateSampleTextThree}",
    "from": "${MLang.AppSettings.Experimental.CustomQuoteTemplateSampleSource}"
  }
]
"""

@Composable
private fun BiometricProtectedPreferenceSwitch(
    checkedFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    title: String,
    summary: String,
    enableTitle: String,
    disableTitle: String,
    onConfirmedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val checked by checkedFlow.collectAsState()
    val showUnavailableDialogState = remember { mutableStateOf(false) }
    var unavailableMessage by remember { mutableStateOf("") }

    PreferenceSwitchItem(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = { targetState ->
            requestBiometricConfirmation(
                context = context,
                title = if (targetState) enableTitle else disableTitle,
                allowBypassWhenUnavailable = !targetState,
                onUnavailable = { message ->
                    unavailableMessage = message
                    showUnavailableDialogState.value = true
                },
                onSuccess = { onConfirmedChange(targetState) },
            )
        },
    )

    WarningBottomSheet(
        show = showUnavailableDialogState,
        title = MLang.AppSettings.Privacy.BiometricUnavailableTitle,
        messages = listOf(
            unavailableMessage.ifBlank {
                MLang.AppSettings.Privacy.BiometricUnavailableMessage
            },
        ),
        onConfirm = { showUnavailableDialogState.value = false },
    )
}

@Composable
private fun HideAppIconPreferenceItem(
    hideAppIconFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onHideAppIconChange: (Boolean) -> Unit,
    context: android.content.Context,
) {
    val hideAppIcon by hideAppIconFlow.collectAsState()
    val showHideIconDialogState = remember { mutableStateOf(false) }

    PreferenceSwitchItem(
        title = MLang.AppSettings.Privacy.HideIconTitle,
        summary = MLang.AppSettings.Privacy.HideIconSummary,
        checked = hideAppIcon,
        onCheckedChange = { checked ->
            if (checked) {
                showHideIconDialogState.value = true
            } else {
                onHideAppIconChange(false)
                AppIconHelper.toggleIcon(context, false)
            }
        },
    )

    WarningBottomSheet(
        show = showHideIconDialogState,
        title = MLang.AppSettings.WarningDialog.Title,
        messages = listOf(
            MLang.AppSettings.WarningDialog.HideIconMsg1,
            MLang.AppSettings.WarningDialog.HideIconMsg2,
        ),
        onConfirm = {
            onHideAppIconChange(true)
            AppIconHelper.toggleIcon(context, true)
        },
    )
}

@Composable
private fun AcgQuotePreferenceItem(
    title: String,
    summary: String,
    dialogTitle: String,
    currentValue: String,
    enabled: Boolean = true,
    endActions: @Composable (androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    onConfirm: (String) -> Unit,
) {
    val showEditDialogState = remember { mutableStateOf(false) }
    val textFieldState = remember { mutableStateOf(TextFieldValue()) }

    PreferenceValueItem(
        title = title,
        summary = summary,
        enabled = enabled,
        endActions = endActions,
        onClick = {
            textFieldState.value = TextFieldValue(currentValue)
            showEditDialogState.value = true
        },
    )

    TextEditBottomSheet(
        show = showEditDialogState,
        title = dialogTitle,
        textFieldValue = textFieldState,
        onConfirm = onConfirm,
    )
}

@Composable
private fun AcgTextEditorPreferenceItem(
    title: String,
    summary: String,
    editorTitle: String,
    content: String,
    navigator: DestinationsNavigator,
    endActions: @Composable (androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    onSave: ((String) -> Unit)? = null,
) {
    PreferenceValueItem(
        title = title,
        summary = summary,
        endActions = endActions,
        onClick = {
            OverrideStructuredEditorStore.setupConfigPreview(
                title = editorTitle,
                content = content,
                language = LanguageScope.Text,
                callback = onSave,
            )
            navigator.navigate(OverrideConfigPreviewRouteDestination)
        },
    )
}

private fun requestBiometricConfirmation(
    context: android.content.Context,
    title: String,
    allowBypassWhenUnavailable: Boolean = false,
    onUnavailable: (String) -> Unit,
    onSuccess: () -> Unit,
) {
    val activity = BiometricHelper.findFragmentActivity(context)
    if (activity == null) {
        if (allowBypassWhenUnavailable) {
            onSuccess()
        } else {
            onUnavailable(MLang.AppSettings.Privacy.BiometricUnavailableMessage)
        }
        return
    }

    if (!BiometricHelper.canAuthenticate(activity)) {
        if (allowBypassWhenUnavailable) {
            onSuccess()
        } else {
            onUnavailable(BiometricHelper.getAuthenticationStatusMessage(activity))
        }
        return
    }

    BiometricHelper.authenticate(
        activity = activity,
        title = title,
        onSuccess = onSuccess,
    )
}

private fun openBatteryOptimizationSettings(
    context: android.content.Context,
): Boolean {
    val power = context.getSystemService(android.os.PowerManager::class.java)
    if (power?.isIgnoringBatteryOptimizations(context.packageName) == true) {
        context.toast(MLang.AppSettings.ServiceSection.BatteryOptimizationAlreadyDisabled)
        return true
    }
    return runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        })
    }.isSuccess
}

@Composable
private fun PageScalePreferenceItem(
    pageScale: Float,
    onApply: (Float) -> Unit,
) {
    var pageScaleLocal by remember(pageScale) { mutableFloatStateOf(pageScale) }
    var lastHapticScale by remember(pageScale) { mutableFloatStateOf(pageScale) }
    var lastHapticTimeMs by remember { mutableStateOf(0L) }
    var lastChangeScale by remember(pageScale) { mutableFloatStateOf(pageScale) }
    var lastChangeTimeMs by remember { mutableStateOf(0L) }
    val hapticFeedback = LocalHapticFeedback.current
    val pageScalePercentText = remember(pageScaleLocal) { "${(pageScaleLocal * 100).toInt()}%" }
    val showPageScaleDialogState = remember { mutableStateOf(false) }

    fun performPageScaleSliderHaptic(targetScale: Float) {
        val now = SystemClock.uptimeMillis()
        val elapsedMs = (now - lastChangeTimeMs).coerceAtLeast(1L)
        val percentPerSecond = abs(targetScale - lastChangeScale) * 100_000f / elapsedMs
        val minIntervalMs = when {
            percentPerSecond >= 120f -> 28L
            percentPerSecond >= 60f -> 42L
            percentPerSecond >= 25f -> 60L
            else -> 90L
        }
        val crossedPercentStep = abs(targetScale - lastHapticScale) >= 0.01f
        if (crossedPercentStep && now - lastHapticTimeMs >= minIntervalMs) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
            lastHapticScale = targetScale
            lastHapticTimeMs = now
        }
        lastChangeScale = targetScale
        lastChangeTimeMs = now
    }

    PreferenceArrowItem(
        title = MLang.AppSettings.Interface.PageScaleTitle,
        summary = MLang.AppSettings.Interface.PageScaleSummary,
        endActions = {
            Text(
                text = pageScalePercentText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = { showPageScaleDialogState.value = true },
        holdDownState = showPageScaleDialogState.value,
        bottomAction = {
            Slider(
                value = pageScaleLocal,
                onValueChange = { value ->
                    pageScaleLocal = value
                    performPageScaleSliderHaptic(value)
                },
                onValueChangeFinished = { onApply(pageScaleLocal) },
                valueRange = 0.8f..1.2f,
            )
        },
    )

    PageScaleDialog(
        show = showPageScaleDialogState.value,
        pageScale = pageScaleLocal,
        onPageScaleChange = { pageScaleLocal = it },
        onApply = onApply,
        onDismissRequest = { showPageScaleDialogState.value = false },
    )
}

@Composable
private fun CustomUserAgentPreferenceItem(
    customUserAgent: String,
    onConfirm: (String) -> Unit,
) {
    val customUserAgentSummary = remember(customUserAgent) {
        customUserAgent.ifEmpty {
            MLang.AppSettings.Network.CustomUserAgentSummaryDefault
        }
    }
    val showEditCustomUserAgentDialogState = remember { mutableStateOf(false) }
    val customUserAgentTextFieldState = remember { mutableStateOf(TextFieldValue()) }

    PreferenceValueItem(
        title = MLang.AppSettings.Network.CustomUserAgentTitle,
        summary = customUserAgentSummary,
        onClick = {
            customUserAgentTextFieldState.value = TextFieldValue(customUserAgent)
            showEditCustomUserAgentDialogState.value = true
        },
    )

    TextEditBottomSheet(
        show = showEditCustomUserAgentDialogState,
        title = MLang.AppSettings.EditDialog.UserAgentTitle,
        textFieldValue = customUserAgentTextFieldState,
        onConfirm = onConfirm,
    )
}

@Composable
private fun PageScaleDialog(
    show: Boolean,
    pageScale: Float,
    onPageScaleChange: (Float) -> Unit,
    onApply: (Float) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var scaleText by remember(show, pageScale) {
        mutableStateOf((pageScale * 100).toInt().toString())
    }

    AppTextFieldDialog(
        show = show,
        title = MLang.AppSettings.Interface.PageScaleTitle,
        value = scaleText,
        onValueChange = { value ->
            if (value.isEmpty() || value.all(Char::isDigit)) {
                scaleText = value
            }
        },
        onDismissRequest = onDismissRequest,
        onConfirm = {
            val parsedPercent = scaleText.toFloatOrNull() ?: (pageScale * 100)
            val clampedScale = parsedPercent.coerceIn(80f, 120f) / 100f
            onPageScaleChange(clampedScale)
            onApply(clampedScale)
            onDismissRequest()
        },
        summary = MLang.AppSettings.Interface.PageScaleDialogSummary,
        renderInRootScaffold = true,
        singleLine = true,
        trailingIcon = {
            Text(
                text = "%",
                modifier = Modifier.padding(horizontal = UiDp.dp16),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
