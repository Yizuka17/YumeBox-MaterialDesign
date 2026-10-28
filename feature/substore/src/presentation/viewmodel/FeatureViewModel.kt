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



package com.github.yumelira.yumebox.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.common.util.DeviceUtil
import com.github.yumelira.yumebox.common.util.showToastDialog
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.data.store.FeatureStore
import com.github.yumelira.yumebox.data.store.LinkOpenMode
import com.github.yumelira.yumebox.data.store.Preference
import com.github.yumelira.yumebox.data.store.SUPPORTED_HEALTH_CHECK_CONCURRENCY
import com.github.yumelira.yumebox.substore.SubStorePaths
import com.github.yumelira.yumebox.substore.SubStoreServiceController
import com.github.yumelira.yumebox.substore.SubStoreServiceRequest
import com.github.yumelira.yumebox.substore.engine.NativeLibraryManager
import com.github.yumelira.yumebox.substore.model.AutoCloseMode
import com.github.yumelira.yumebox.substore.util.DownloadProgress
import com.github.yumelira.yumebox.substore.util.SubStoreDownloadClient
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FeatureViewModel(
    store: FeatureStore,
    appSettings: AppSettingsStore,
    private val application: Application,
    private val downloadClient: SubStoreDownloadClient,
) : ViewModel() {
    val allowLanAccess: Preference<Boolean> = store.allowLanAccess
    val backendPort: Preference<Int> = store.backendPort
    val frontendPort: Preference<Int> = store.frontendPort
    val selectedPanelType: Preference<Int> = store.selectedPanelType
    val panelOpenMode: Preference<LinkOpenMode> = store.panelOpenMode
    val exitUiWhenBackground: Preference<Boolean> = store.exitUiWhenBackground
    val healthCheckConcurrency: Preference<Int> = appSettings.healthCheckConcurrency

    private val autoClosePreference = store.subStoreAutoCloseMode
    private val _autoCloseMode = MutableStateFlow(if (SubStoreServiceController.snapshot.value.isActive)
        AutoCloseMode.entries.getOrNull(autoClosePreference.value)?.takeUnless { it == AutoCloseMode.DISABLED } ?: AutoCloseMode.ALWAYS_ON
        else AutoCloseMode.DISABLED)
    val autoCloseMode: StateFlow<AutoCloseMode> = _autoCloseMode.asStateFlow()

    val serviceRunningState: StateFlow<Boolean> = SubStoreServiceController.snapshot
        .map { it.isActive }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SubStoreServiceController.snapshot.value.isActive,
        )

    init {
        viewModelScope.launch {
            SubStoreServiceController.snapshot.collect { state ->
                _autoCloseMode.value = if (state.isActive)
                    AutoCloseMode.entries.getOrNull(autoClosePreference.value)?.takeUnless { it == AutoCloseMode.DISABLED } ?: AutoCloseMode.ALWAYS_ON
                    else AutoCloseMode.DISABLED
            }
        }
    }

    private var subStoreResourceDownloadJob: Job? = null

    private val _isDownloadingSubStoreFrontend = MutableStateFlow(false)
    val isDownloadingSubStoreFrontend: StateFlow<Boolean> = _isDownloadingSubStoreFrontend.asStateFlow()

    private val _isDownloadingSubStoreBackend = MutableStateFlow(false)
    val isDownloadingSubStoreBackend: StateFlow<Boolean> = _isDownloadingSubStoreBackend.asStateFlow()

    private val _subStoreResourceProgressItems = MutableStateFlow<List<SubStoreResourceProgressState>>(emptyList())
    val subStoreResourceProgressItems: StateFlow<List<SubStoreResourceProgressState>> =
        _subStoreResourceProgressItems.asStateFlow()

    private val _isDownloadingSubStoreResources = MutableStateFlow(false)
    val isDownloadingSubStoreResources: StateFlow<Boolean> = _isDownloadingSubStoreResources.asStateFlow()

    private val _isSubStoreInitialized = MutableStateFlow(false)
    val isSubStoreInitialized: StateFlow<Boolean> = _isSubStoreInitialized.asStateFlow()

    private val _isJavetLoaded = MutableStateFlow(false)
    val isJavetLoaded: StateFlow<Boolean> = _isJavetLoaded.asStateFlow()

    companion object {
        private const val JAVET_LIB_NAME = "libjavet-node-android"
    }

    fun startService() {
        if (DeviceUtil.is32BitDevice()) {
            showToast(MLang.Feature.SubStore.Not32Bit)
            return
        }
        if (!checkSubStoreReadiness()) {
            _autoCloseMode.value = AutoCloseMode.DISABLED
            autoClosePreference.set(AutoCloseMode.DISABLED.ordinal)
            return
        }
        viewModelScope.launch {
            runCatching {
                SubStoreServiceController.startService(
                    context = application,
                    request = SubStoreServiceRequest(
                        backendPort = backendPort.value,
                        frontendPort = frontendPort.value,
                        allowLan = allowLanAccess.value,
                        autoCloseMinutes = _autoCloseMode.value.minutes,
                    ),
                )
            }.onFailure { error ->
                showToast(error.message ?: MLang.Util.Error.UnknownError)
            }
        }
    }

    private fun checkSubStoreReadiness(): Boolean {
        return when {
            !_isSubStoreInitialized.value -> {
                showToast(MLang.Feature.SubStore.DownloadSubStoreFirst); false
            }

            !_isJavetLoaded.value -> {
                showToast(MLang.Feature.SubStore.JavetNotReady); false
            }

            else -> true
        }
    }

    fun stopService() {
        viewModelScope.launch {
            SubStoreServiceController.stopService(application)
            _autoCloseMode.value = AutoCloseMode.DISABLED
            autoClosePreference.set(AutoCloseMode.DISABLED.ordinal)
        }
    }

    fun setAllowLanAccess(allow: Boolean) = allowLanAccess.set(allow)
    fun setAutoCloseMode(mode: AutoCloseMode) {
        _autoCloseMode.value = mode
        autoClosePreference.set(mode.ordinal)
        if (serviceRunningState.value) {
            SubStoreServiceController.updateAutoClose(application, mode.minutes)
        }
    }

    fun initializeSubStoreStatus() {
        viewModelScope.launch {
            _isSubStoreInitialized.value = SubStorePaths.isResourcesReady()
            initializeJavetStatus()
        }
    }

    private fun initializeJavetStatus() {
        NativeLibraryManager.initialize(application)
        _isJavetLoaded.value = if (!NativeLibraryManager.isLibraryAvailable(JAVET_LIB_NAME)) {
            NativeLibraryManager.extractAllLibraries()[JAVET_LIB_NAME] == true
        } else true
    }

    fun refreshEngineStatus() {
        viewModelScope.launch {
            initializeJavetStatus()
        }
    }

    fun setSelectedPanelType(panelType: Int) {
        selectedPanelType.set(panelType)
    }

    fun setPanelOpenMode(mode: LinkOpenMode) = panelOpenMode.set(mode)
    fun setExitUiWhenBackground(enabled: Boolean) = exitUiWhenBackground.set(enabled)
    fun setHealthCheckConcurrency(concurrency: Int) = healthCheckConcurrency.set(
        if (concurrency in SUPPORTED_HEALTH_CHECK_CONCURRENCY) concurrency else 8,
    )

    fun downloadSubStoreFrontend() {
        launchResourceDownload(
            loadingState = _isDownloadingSubStoreFrontend,
            successMessage = MLang.Feature.SubStore.FrontendDownloadSuccess,
            failureMessage = MLang.Feature.SubStore.FrontendDownloadFailed,
        ) {
            SubStorePaths.ensureStructure()
            SubStorePaths.frontendDir.apply { if (!exists()) mkdirs() }
            downloadClient.downloadAndExtract(
                url = "https://github.com/sub-store-org/Sub-Store-Front-End/releases/latest/download/dist.zip",
                targetDir = SubStorePaths.frontendDir,
            )
        }
    }

    fun downloadSubStoreBackend() {
        launchResourceDownload(
            loadingState = _isDownloadingSubStoreBackend,
            successMessage = MLang.Feature.SubStore.BackendDownloadSuccess,
            failureMessage = MLang.Feature.SubStore.BackendDownloadFailed,
        ) {
            SubStorePaths.ensureStructure()
            SubStorePaths.backendDir.apply { if (!exists()) mkdirs() }
            downloadClient.download(
                url = "https://github.com/sub-store-org/Sub-Store/releases/latest/download/sub-store.bundle.js",
                targetFile = SubStorePaths.backendBundle,
            )
        }
    }

    fun downloadSubStoreAll() {
        if (_isDownloadingSubStoreResources.value) return
        subStoreResourceDownloadJob = viewModelScope.launch {
            _isDownloadingSubStoreResources.value = true
            _isDownloadingSubStoreFrontend.value = true
            _isDownloadingSubStoreBackend.value = false
            _subStoreResourceProgressItems.value = listOf(
                SubStoreResourceProgressState(
                    itemTitle = MLang.Feature.SubStore.FrontendResourceTitle,
                    status = SubStoreResourceDownloadStatus.Pending,
                ),
                SubStoreResourceProgressState(
                    itemTitle = MLang.Feature.SubStore.BackendResourceTitle,
                    status = SubStoreResourceDownloadStatus.Pending,
                ),
            )
            var successCount = 0
            try {
                val frontendSuccess = downloadSubStoreFrontendWithProgress()
                if (frontendSuccess) successCount++
                _isDownloadingSubStoreFrontend.value = false
                _isDownloadingSubStoreBackend.value = true
                val backendSuccess = downloadSubStoreBackendWithProgress()
                if (backendSuccess) successCount++
                _isDownloadingSubStoreBackend.value = false
                _isSubStoreInitialized.value = SubStorePaths.isResourcesReady()
                delay(250L)
                showToast(MLang.Feature.SubStore.ResourcesDownloadComplete.format(successCount, 2))
            } catch (_: CancellationException) {
                // Download was canceled by leaving the sheet/page. Temporary files are cleaned by the download client.
            } catch (e: Exception) {
                showToast(MLang.Feature.SubStore.DownloadError.format(e.message ?: MLang.Util.Error.UnknownError))
            } finally {
                _isDownloadingSubStoreFrontend.value = false
                _isDownloadingSubStoreBackend.value = false
                _isDownloadingSubStoreResources.value = false
                subStoreResourceDownloadJob = null
            }
        }
    }

    fun cancelSubStoreResourceDownload() {
        subStoreResourceDownloadJob?.cancel()
        subStoreResourceDownloadJob = null
        _isDownloadingSubStoreFrontend.value = false
        _isDownloadingSubStoreBackend.value = false
        _isDownloadingSubStoreResources.value = false
    }

    private suspend fun downloadSubStoreFrontendWithProgress(): Boolean {
        val title = MLang.Feature.SubStore.FrontendResourceTitle
        updateResourceProgress(title) {
            SubStoreResourceProgressState(itemTitle = title, status = SubStoreResourceDownloadStatus.Downloading)
        }
        SubStorePaths.ensureStructure()
        SubStorePaths.frontendDir.apply { if (!exists()) mkdirs() }
        val downloaded = downloadClient.downloadAndExtract(
            url = "https://github.com/sub-store-org/Sub-Store-Front-End/releases/latest/download/dist.zip",
            targetDir = SubStorePaths.frontendDir,
            onProgress = { progress ->
                updateResourceProgress(title) {
                    progress.toSubStoreProgressState(title, SubStoreResourceDownloadStatus.Downloading)
                }
            },
        )
        updateResourceProgress(title) { current ->
            current.copy(
                progress = if (downloaded) 100 else current.progress,
                status = if (downloaded) SubStoreResourceDownloadStatus.Success else SubStoreResourceDownloadStatus.Failed,
            )
        }
        return downloaded
    }

    private suspend fun downloadSubStoreBackendWithProgress(): Boolean {
        val title = MLang.Feature.SubStore.BackendResourceTitle
        updateResourceProgress(title) {
            SubStoreResourceProgressState(itemTitle = title, status = SubStoreResourceDownloadStatus.Downloading)
        }
        SubStorePaths.ensureStructure()
        SubStorePaths.backendDir.apply { if (!exists()) mkdirs() }
        val downloaded = downloadClient.download(
            url = "https://github.com/sub-store-org/Sub-Store/releases/latest/download/sub-store.bundle.js",
            targetFile = SubStorePaths.backendBundle,
            onProgress = { progress ->
                updateResourceProgress(title) {
                    progress.toSubStoreProgressState(title, SubStoreResourceDownloadStatus.Downloading)
                }
            },
        )
        updateResourceProgress(title) { current ->
            current.copy(
                progress = if (downloaded) 100 else current.progress,
                status = if (downloaded) SubStoreResourceDownloadStatus.Success else SubStoreResourceDownloadStatus.Failed,
            )
        }
        return downloaded
    }

    private fun updateResourceProgress(
        itemTitle: String,
        transform: (SubStoreResourceProgressState) -> SubStoreResourceProgressState,
    ) {
        val currentItems = _subStoreResourceProgressItems.value
        _subStoreResourceProgressItems.value = currentItems.map { item ->
            if (item.itemTitle == itemTitle) transform(item) else item
        }
    }

    private fun DownloadProgress.toSubStoreProgressState(
        itemTitle: String,
        status: SubStoreResourceDownloadStatus,
    ): SubStoreResourceProgressState {
        return SubStoreResourceProgressState(
            itemTitle = itemTitle,
            progress = progress.coerceIn(0, 100),
            currentSize = currentSize,
            totalSize = totalSize,
            speed = speed,
            status = status,
        )
    }

    private fun showToast(msg: String) = showToastDialog(msg)

    private fun launchResourceDownload(
        loadingState: MutableStateFlow<Boolean>,
        successMessage: String,
        failureMessage: String,
        action: suspend () -> Boolean,
    ) {
        if (loadingState.value) return
        viewModelScope.launch {
            loadingState.value = true
            runCatching {
                val success = action()
                showToast(if (success) successMessage else failureMessage)
                if (success) {
                    _isSubStoreInitialized.value = SubStorePaths.isResourcesReady()
                }
            }.onFailure { e ->
                showToast(MLang.Feature.SubStore.DownloadError.format(e.message ?: MLang.Util.Error.UnknownError))
            }
            loadingState.value = false
        }
    }

    override fun onCleared() {
        cancelSubStoreResourceDownload()
        super.onCleared()
    }
}

data class SubStoreResourceProgressState(
    val itemTitle: String,
    val progress: Int = 0,
    val currentSize: Long = 0L,
    val totalSize: Long = -1L,
    val speed: String = "",
    val status: SubStoreResourceDownloadStatus = SubStoreResourceDownloadStatus.Pending,
)

enum class SubStoreResourceDownloadStatus {
    Pending,
    Downloading,
    Success,
    Failed,
}
