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



package com.github.yumelira.yumebox

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.github.yumelira.yumebox.common.runtime.StartupGate
import com.github.yumelira.yumebox.common.util.AppLanguageManager
import com.github.yumelira.yumebox.common.util.AppIconHelper
import com.github.yumelira.yumebox.common.util.IntentController
import com.github.yumelira.yumebox.common.util.ProxyAutoStartHelper
import com.github.yumelira.yumebox.core.util.AutoStartSessionGate
import com.github.yumelira.yumebox.core.util.StartupTaskCoordinator
import com.github.yumelira.yumebox.di.APPLICATION_SCOPE_NAME
import com.github.yumelira.yumebox.data.store.FeatureStore
import com.github.yumelira.yumebox.data.model.AppColorTheme
import com.github.yumelira.yumebox.presentation.component.StartupBiometricContent
import com.github.yumelira.yumebox.presentation.component.ToastDialogHost
import com.github.yumelira.yumebox.presentation.component.rememberStartupBiometricGateState
import com.github.yumelira.yumebox.screen.acg.AcgWallpaperManager
import com.github.yumelira.yumebox.screen.acg.extractWallpaperSeed
import com.github.yumelira.yumebox.screen.settings.AppFontStorage
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.yumelira.yumebox.presentation.theme.NavigationTransitions
import com.github.yumelira.yumebox.presentation.theme.ProvideAndroidPlatformTheme
import com.github.yumelira.yumebox.presentation.theme.YumeTheme
import com.github.yumelira.yumebox.screen.onboarding.OnboardingLauncher
import com.github.yumelira.yumebox.screen.settings.AppSettingsViewModel
import com.github.yumelira.yumebox.update.GitHubUpdateManager
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.qualifier.named

class MainActivity : FragmentActivity() {

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
        private const val EXTRA_EXIT_UI_WHEN_BACKGROUND = "exit_ui_when_background"
        private val _pendingImportUrl = MutableStateFlow<String?>(null)
        val pendingImportUrl: StateFlow<String?> = _pendingImportUrl.asStateFlow()
        fun clearPendingImportUrl() {
            _pendingImportUrl.value = null
        }
    }

    private val appSettingsStorage: com.github.yumelira.yumebox.data.store.AppSettingsStore by inject()
    private val featureStore: FeatureStore by inject()
    private val networkSettingsStorage: com.github.yumelira.yumebox.data.store.NetworkSettingsStore by inject()
    private val profilesRepository: com.github.yumelira.yumebox.runtime.client.ProfilesRepository by inject()
    private val proxyFacade: com.github.yumelira.yumebox.runtime.client.ProxyFacade by inject()
    private val serviceCache: MMKV by inject(qualifier = named("service_cache"))
    private val applicationScope: CoroutineScope by inject(qualifier = named(APPLICATION_SCOPE_NAME))
    private val updateManager: GitHubUpdateManager by inject()
    private val wallpapers: AcgWallpaperManager by inject()

    private lateinit var intentController: IntentController
    private var pageRequest by mutableIntStateOf(0)
    private var requestedPage by mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        loadStartupGate()
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)
        applyExcludeFromRecents(appSettingsStorage.excludeFromRecents.value)
        applyScreenshotProtection(appSettingsStorage.screenshotProtectionEnabled.value)

        intentController = IntentController(this, lifecycleScope)
        handleIntent(intent)

        if (!appSettingsStorage.initialSetupCompleted.value) {
            OnboardingLauncher.start(this, previewMode = false)
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION,
                )
            }
        }

        setContent {
            val appSettingsViewModel = koinViewModel<AppSettingsViewModel>()
            val themeMode = appSettingsViewModel.themeMode.state.collectAsState().value
            val colorTheme = appSettingsViewModel.colorTheme.state.collectAsState().value
            val themeSeedColorArgb = appSettingsViewModel.themeSeedColorArgb.state.collectAsState().value
            val wallpaperProfile by wallpapers.activeProfile.collectAsState()
            val wallpaperOptions by wallpapers.options.collectAsState()
            val displayedWallpapers by wallpapers.displayedSources.collectAsState()
            val dailyWallpaper by appSettingsViewModel.acgDailyWallpaperEnabled.state.collectAsState()
            val legacyWallpaper by appSettingsViewModel.acgWallpaperUri.state.collectAsState()
            val wallpaperModel = remember(wallpaperProfile, wallpaperOptions, dailyWallpaper, legacyWallpaper, displayedWallpapers) {
                displayedWallpapers[wallpaperProfile] ?: wallpapers.displayUri(wallpaperProfile, dailyWallpaper)
            }
            val wallpaperSeed by produceState<Long?>(null, colorTheme, wallpaperModel) {
                value = if (colorTheme == AppColorTheme.AcgWallpaper) withContext(Dispatchers.Default) {
                    extractWallpaperSeed(this@MainActivity, wallpaperModel)
                } else null
            }
            val effectiveColorTheme = if (colorTheme == AppColorTheme.AcgWallpaper && wallpaperSeed == null) {
                AppColorTheme.MonetDynamic
            } else colorTheme
            val effectiveThemeSeedColorArgb =
                if (colorTheme == AppColorTheme.AcgWallpaper) wallpaperSeed ?: themeSeedColorArgb else themeSeedColorArgb
            val customFont by appSettingsStorage.customFont.state.collectAsState()
            val fontWeight by appSettingsStorage.appFontWeight.state.collectAsState()
            val fontSizeScale by appSettingsStorage.appFontSizeScale.state.collectAsState()
            val fontFamily by produceState<FontFamily?>(null, customFont, fontWeight) {
                value = withContext(Dispatchers.IO) {
                    AppFontStorage.load(this@MainActivity, customFont, fontWeight)
                }
            }
            val invertOnPrimaryColors = appSettingsViewModel.invertOnPrimaryColors.state.collectAsState().value
            val excludeFromRecents = appSettingsViewModel.excludeFromRecents.state.collectAsState().value
            val hideAppIcon by appSettingsViewModel.hideAppIcon.state.collectAsState()
            val pageScale = appSettingsViewModel.pageScale.state.collectAsState().value
            val tabletModeEnabled by appSettingsViewModel.acgTabletModeEnabled.state.collectAsState()
            val screenshotProtectionEnabled = appSettingsViewModel.screenshotProtectionEnabled.state.collectAsState().value
            val biometricUnlockEnabled by appSettingsViewModel.biometricUnlockEnabled.state.collectAsState()

            val biometricGateState = rememberStartupBiometricGateState(
                activity = this@MainActivity,
                biometricUnlockEnabled = biometricUnlockEnabled,
            )

            LaunchedEffect(excludeFromRecents) {
                this@MainActivity.applyExcludeFromRecents(excludeFromRecents)
            }

            LaunchedEffect(hideAppIcon) {
                AppIconHelper.toggleIcon(this@MainActivity, hideAppIcon)
            }

            LaunchedEffect(screenshotProtectionEnabled) {
                this@MainActivity.applyScreenshotProtection(screenshotProtectionEnabled)
            }

            ProvideAndroidPlatformTheme {
                val systemDensity = LocalDensity.current
                val scaledDensity = remember(systemDensity, pageScale, fontSizeScale) {
                    Density(systemDensity.density * pageScale, systemDensity.fontScale * fontSizeScale.coerceIn(0.85f, 1.35f))
                }
                CompositionLocalProvider(LocalDensity provides scaledDensity,
                    com.github.yumelira.yumebox.presentation.component.LocalTabletModeEnabled provides tabletModeEnabled) {
                    YumeTheme(
                        themeMode = themeMode,
                        colorTheme = effectiveColorTheme,
                        themeSeedColorArgb = effectiveThemeSeedColorArgb,
                        invertOnPrimaryColors = invertOnPrimaryColors,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                    ) {
                        if (!biometricGateState.isAuthenticated) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                StartupBiometricContent(
                                    isAuthenticating = biometricGateState.isAuthenticating,
                                    biometricErrorMessage = biometricGateState.biometricErrorMessage,
                                    onRetry = biometricGateState.retryAuthentication,
                                    onExit = { finishAndRemoveTask() },
                                )
                            }
                        } else {
                            val navController = rememberNavController()

                            Surface(
                                modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface
                            ) {
                                DestinationsNavHost(
                                    navGraph = NavGraphs.root,
                                    navController = navController,
                                    defaultTransitions = NavigationTransitions.defaultStyle,
                                )
                                LaunchedEffect(pageRequest) {
                                    if (pageRequest > 0) {
                                        navController.navigate(
                                            com.ramcosta.composedestinations.generated.destinations.MainScreenDestination(requestedPage).route,
                                        ) {
                                            popUpTo(navController.graph.id)
                                            launchSingleTop = true
                                        }
                                    }
                                }
                                ToastDialogHost()
                                com.github.yumelira.yumebox.update.ReleaseUpdateHost()
                            }
                        }
                    }
                }
            }

        }

        applicationScope.launch {
            if (!AutoStartSessionGate.tryBeginAutoActions()) {
                return@launch
            }
            var handled = false
            try {
                StartupTaskCoordinator.awaitWarmup()
                with(com.github.yumelira.yumebox.common.util.AutoStartDependencies(
                    featureStore, proxyFacade, profilesRepository, appSettingsStorage,
                    networkSettingsStorage, serviceCache,
                )) { ProxyAutoStartHelper.checkAndAutoStart(this@MainActivity) }

                handled = true
            } finally {
                AutoStartSessionGate.finishAutoActions(markHandled = handled)
            }
        }
    }

    private fun loadStartupGate() {
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            StartupGate.loadPrimary()
            return
        }

        runCatching {
            StartupGate.loadPrimary()
        }.onFailure { throwable ->
            Timber.w(throwable, "Skip startup gate native library in debug build")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level < TRIM_MEMORY_UI_HIDDEN || isFinishing) {
            return
        }
        if (!featureStore.exitUiWhenBackground.value) {
            return
        }
        if (proxyFacade.runtimeSnapshot.value.running) {
            finishAndRemoveTask()
        }
    }

    @Suppress("SpellCheckingInspection")
    private fun handleIntent(intent: Intent?) {
        intent?.let { safeIntent ->
            val page = when (safeIntent.action) {
                com.github.yumelira.yumebox.runtime.api.Components.ACTION_OPEN_HOME -> 0
                com.github.yumelira.yumebox.runtime.api.Components.ACTION_OPEN_PROXY -> 1
                else -> null
            }
            if (page != null) {
                requestedPage = page
                pageRequest++
                safeIntent.action = Intent.ACTION_MAIN
            }
            if (safeIntent.getBooleanExtra(EXTRA_EXIT_UI_WHEN_BACKGROUND, false)) {
                finishAndRemoveTask()
                return
            }
            safeIntent.data?.let { uri ->
                val scheme = uri.scheme
                if (scheme == "clash" || scheme == "clashmeta") {
                    val host = uri.host
                    if (host == "install-config") {
                        val configUrl = uri.getQueryParameter("url")
                        if (!configUrl.isNullOrBlank()) {
                            _pendingImportUrl.value = configUrl
                        }
                    }
                }
            }

            intentController.handleIntent(safeIntent)
        }
    }

    private fun applyScreenshotProtection(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyExcludeFromRecents(exclude: Boolean) {

        runCatching {
            val am = getSystemService(ActivityManager::class.java) ?: return@runCatching
            val currentTaskId = taskId
            val task = am.appTasks.firstOrNull { appTask: ActivityManager.AppTask ->
                val taskInfo = appTask.taskInfo ?: return@firstOrNull false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    taskInfo.taskId == currentTaskId
                } else {
                    taskInfo.id == currentTaskId
                }
            }
            task?.setExcludeFromRecents(exclude)
        }
    }
}
