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

import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.core.presentation.ContractStateViewModel
import com.github.yumelira.yumebox.core.presentation.LoadableState
import com.github.yumelira.yumebox.core.model.TunnelState
import com.github.yumelira.yumebox.data.controller.RuntimeOverrideController
import com.github.yumelira.yumebox.data.model.ProxySortMode
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.data.store.ProxyDisplaySettingsStore
import com.github.yumelira.yumebox.domain.model.ProxyGroupInfo
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.runtime.client.ProxyGroupSyncPriority
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.launch

class ProxyViewModel(
    private val runtimeOverrideController: RuntimeOverrideController,
    private val proxyFacade: ProxyFacade,
    private val proxyDisplaySettingsStore: ProxyDisplaySettingsStore,
    appSettings: AppSettingsStore,
) : ContractStateViewModel<ProxyViewModel.ProxyUiState, ProxyViewModel.ProxyUiEffect>(ProxyUiState()) {
    private val _testingGroupNames = MutableStateFlow<Set<String>>(emptySet())
    val testingGroupNames: StateFlow<Set<String>> = _testingGroupNames.asStateFlow()

    private val _testingProxyNames = MutableStateFlow<Set<String>>(emptySet())
    val testingProxyNames: StateFlow<Set<String>> = _testingProxyNames.asStateFlow()

    private val groupSorter = ProxyGroupSorter()

    val tunnelMode: StateFlow<TunnelState.Mode> = proxyDisplaySettingsStore.proxyMode.state

    val sortMode: StateFlow<ProxySortMode> = proxyDisplaySettingsStore.sortMode.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProxySortMode.DEFAULT)

    val singleNodeTest: StateFlow<Boolean> = appSettings.singleNodeTest.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val healthCheckConcurrency: StateFlow<Int> = appSettings.healthCheckConcurrency.state
        .map(::normalizeHealthCheckConcurrency)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_HEALTH_CHECK_CONCURRENCY)

    private val lockedGroupTestDelays = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())

    val proxyGroups: StateFlow<List<ProxyGroupInfo>> = combine(
        proxyFacade.proxyGroups,
        lockedGroupTestDelays,
    ) { groups, lockedDelays ->
        groups.filterNot(ProxyGroupInfo::hidden).map { group ->
            group.withLockedDelays(lockedDelays[group.name].orEmpty())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeSyncSources = mutableSetOf<String>()
    private var groupTestJob: Job? = null

    init {
        proxyFacade.warmUpProxyGroups()
        viewModelScope.launch {
            proxyGroups
                .distinctUntilChangedBy { groups -> groups.map(ProxyGroupInfo::name) }
                .collect { groups ->
                    groupSorter.track(groups)
                }
        }
    }

    val sortedProxyGroups: StateFlow<List<ProxyGroupInfo>> = groupSorter.bind(
        scope = viewModelScope,
        proxyGroups = proxyGroups,
        sortMode = sortMode,
    )

    fun ensureCoreLoaded(
        isActive: Boolean,
        source: String = "proxy_page",
    ) {
        val changed = if (isActive) {
            activeSyncSources.add(source)
        } else {
            activeSyncSources.remove(source)
        }
        if (!changed) return
        proxyFacade.setProxyGroupSyncPriority(
            priority = if (isActive) ProxyGroupSyncPriority.FAST else ProxyGroupSyncPriority.OFF,
            source = source,
        )
        if (isActive) {
            viewModelScope.launch {
                runCatching {
                    if (proxyGroups.value.isEmpty()) {
                        proxyFacade.refreshProxyGroups()
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
            }
        }
    }

    fun refreshGroup(groupName: String) {
        viewModelScope.launch {
            runCatching {
                proxyFacade.refreshProxyGroup(groupName)
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        }
    }

    fun testDelay(groupName: String? = null) {
        if (groupTestJob?.isActive == true) return
        groupTestJob = viewModelScope.launch {
            setLoading(true)
            clearError()
            val testMode = tunnelMode.value
            val currentGroups = proxyGroups.value
            val targetGroup = groupName
                ?.let { name -> currentGroups.firstOrNull { it.name == name } }
                ?: currentGroups.firstOrNull()
            val targetGroupName = targetGroup?.name
            val testingTargets: Set<String> = targetGroupName?.let(::setOf).orEmpty()
            if (testingTargets.isNotEmpty()) {
                _testingGroupNames.update { it + testingTargets }
            }

            val result = runCatching {
                if (targetGroup != null && targetGroupName != null) {
                    showMessage(MLang.Proxy.Testing.Group.format(targetGroupName))
                    runGroupHealthCheck(
                        group = targetGroup,
                        testMode = testMode,
                    )
                    showMessage(MLang.Proxy.Testing.RequestSent)
                }
            }

            setLoading(false)

            if (testingTargets.isNotEmpty()) {
                _testingGroupNames.update { it - testingTargets }
            }

            result.exceptionOrNull()?.let { error ->
                if (error is CancellationException) throw error
                showError(MLang.Proxy.Testing.Failed.format(error.message))
            }
        }
    }

    private suspend fun runGroupHealthCheck(
        group: ProxyGroupInfo,
        testMode: TunnelState.Mode,
    ) {
        val groupName = group.name
        val targets = group.proxies
            .filterNot { proxy -> proxy.isGroup }
            .distinctBy(Proxy::name)
        if (targets.isEmpty()) {
            proxyFacade.healthCheck(groupName)
            if (tunnelMode.value == testMode && proxyGroups.value.any { it.name == groupName }) {
                proxyFacade.refreshProxyGroup(groupName)
            }
            return
        }

        lockedGroupTestDelays.update { it + (groupName to emptyMap()) }
        val semaphore = Semaphore(healthCheckConcurrency.value)
        coroutineScope {
            targets.map { proxy ->
                async {
                    semaphore.withPermit {
                        val proxyName = proxy.name
                        _testingProxyNames.update { it + proxyName }
                        try {
                            val delay = runCatching {
                                proxyFacade.healthCheckProxy(proxyName)
                            }.getOrDefault(-1)
                            if (tunnelMode.value == testMode && proxyGroups.value.any { it.name == groupName }) {
                                lockedGroupTestDelays.update { all ->
                                    val groupDelays = all[groupName].orEmpty() + (proxyName to delay)
                                    all + (groupName to groupDelays)
                                }
                            }
                        } finally {
                            _testingProxyNames.update { it - proxyName }
                        }
                    }
                }
        }.awaitAll()
        }
    }

    fun setSortMode(mode: ProxySortMode) {
        proxyDisplaySettingsStore.sortMode.set(mode)
    }

    fun setTunnelMode(mode: TunnelState.Mode) {
        viewModelScope.launch {
            cancelActiveTests()
            runCatching {
                runtimeOverrideController.updateProfile { source ->
                    val values = com.github.yumelira.yumebox.core.util.YamlCodec.loadMap(source).toMutableMap()
                    values["mode"] = mode.name.lowercase()
                    com.github.yumelira.yumebox.core.util.YamlCodec.dumpMap(values)
                }.getOrThrow()
                if (proxyFacade.isRunning.value) proxyFacade.reloadProxy()
                proxyDisplaySettingsStore.proxyMode.set(mode)
                showMessage(MLang.Proxy.Mode.Switched.format(mode.displayName()))
            }.onFailure { error ->
                showError(MLang.Proxy.Mode.SwitchFailed.format(error.message ?: mode.displayName()))
            }
        }
    }

    private fun cancelActiveTests() {
        groupTestJob?.cancel()
        groupTestJob = null
        _testingGroupNames.value = emptySet()
        _testingProxyNames.value = emptySet()
        setLoading(false)
    }

    private fun TunnelState.Mode.displayName(): String = when (this) {
        TunnelState.Mode.Direct -> MLang.Proxy.Mode.Direct
        TunnelState.Mode.Global -> MLang.Proxy.Mode.Global
        TunnelState.Mode.Rule -> MLang.Proxy.Mode.Rule
        TunnelState.Mode.Script -> "Script"
    }

    fun selectProxy(
        groupName: String,
        proxyName: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                val success = proxyFacade.selectProxy(groupName, proxyName)
                if (success) {
                    showMessage(MLang.Proxy.Selection.Switched.format(proxyName))
                    onSuccess?.invoke()
                } else {
                    showError(MLang.Proxy.Selection.Failed)
                }
            }.onFailure { error ->
                showError(MLang.Proxy.Selection.Error.format(error.message))
            }
        }
    }

    fun testProxyDelay(groupName: String, proxyName: String) {
        if (proxyName in _testingProxyNames.value) return
        viewModelScope.launch {
            val testMode = tunnelMode.value
            _testingProxyNames.update { it + proxyName }
            runCatching {
                val delay = proxyFacade.healthCheckProxy(proxyName)
                lockedGroupTestDelays.update { all ->
                    val groupDelays = all[groupName]
                    if (groupDelays == null) {
                        all
                    } else {
                        all + (groupName to (groupDelays + (proxyName to delay)))
                    }
                }
                if (tunnelMode.value == testMode && proxyGroups.value.any { it.name == groupName }) {
                    proxyFacade.refreshProxyGroup(groupName)
                }
            }
            _testingProxyNames.update { it - proxyName }
        }
    }

    private fun showMessage(message: String) {
        postMessage(message, ProxyUiEffect.ShowMessage(message))
    }

    private fun showError(error: String) {
        postError(error, ProxyUiEffect.ShowError(error))
    }

    fun clearError() {
        clearErrorState()
    }

    private fun ProxyGroupInfo.withLockedDelays(lockedDelays: Map<String, Int>): ProxyGroupInfo {
        if (lockedDelays.isEmpty()) return this
        val updatedProxies = proxies.map { proxy ->
            lockedDelays[proxy.name]?.let { delay -> proxy.copy(delay = delay) } ?: proxy
        }
        return copy(proxies = updatedProxies)
    }

    private fun normalizeHealthCheckConcurrency(value: Int): Int {
        return if (value in SUPPORTED_HEALTH_CHECK_CONCURRENCY) value else DEFAULT_HEALTH_CHECK_CONCURRENCY
    }

    private companion object {
        private const val DEFAULT_HEALTH_CHECK_CONCURRENCY = 8
        private val SUPPORTED_HEALTH_CHECK_CONCURRENCY = setOf(8, 16, 24, 32)
    }

    data class ProxyUiState(
        override val isLoading: Boolean = false,
        override val message: String? = null,
        override val error: String? = null
    ) : LoadableState<ProxyUiState> {
        override fun withLoading(loading: Boolean): ProxyUiState = copy(isLoading = loading)
        override fun withError(error: String?): ProxyUiState = copy(error = error)
        override fun withMessage(message: String?): ProxyUiState = copy(message = message)
    }

    sealed interface ProxyUiEffect {
        data class ShowMessage(val message: String) : ProxyUiEffect
        data class ShowError(val message: String) : ProxyUiEffect
    }
}
