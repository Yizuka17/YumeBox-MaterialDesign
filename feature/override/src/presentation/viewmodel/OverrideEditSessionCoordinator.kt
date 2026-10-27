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

import com.github.yumelira.yumebox.core.model.ConfigurationOverride
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.data.controller.ActiveProfileOverrideReloader
import com.github.yumelira.yumebox.data.store.OverrideConfigStore
import com.github.yumelira.yumebox.data.model.OverrideConfig
import com.github.yumelira.yumebox.data.model.OverrideMetadata
import com.github.yumelira.yumebox.presentation.util.OverrideSaveEvent
import com.github.yumelira.yumebox.presentation.util.OverrideSaveState
import com.github.yumelira.yumebox.presentation.util.encodeOverrideConfigForDiff
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal class OverrideEditSessionCoordinator(
    private val scope: CoroutineScope,
    private val configRepo: OverrideConfigStore,
    private val activeProfileOverrideReloader: ActiveProfileOverrideReloader,
    private val reloadConfigs: suspend () -> Unit,
    private val updateLocalCacheAfterSave: (OverrideConfig) -> Unit,
    private val loggerTag: String,
    private val textAutosaveDelayMillis: Long,
) {
    private val _selectedConfig = MutableStateFlow<OverrideConfig?>(null)
    val selectedConfig: StateFlow<OverrideConfig?> = _selectedConfig.asStateFlow()

    private val _editSession = MutableStateFlow<OverrideEditSession?>(null)
    val editSession: StateFlow<OverrideEditSession?> = _editSession.asStateFlow()

    private val _saveState = MutableStateFlow<OverrideSaveState>(OverrideSaveState.Idle)
    val saveState: StateFlow<OverrideSaveState> = _saveState.asStateFlow()

    private val _events = MutableSharedFlow<OverrideSaveEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<OverrideSaveEvent> = _events.asSharedFlow()

    private val silentSaveLock = Any()
    private val pendingSilentConfigs = mutableMapOf<String, OverrideConfig>()
    private val pendingSilentCallbacks = mutableMapOf<String, ((OverrideConfig) -> Unit)?>()
    private val activeSilentSaveIds = mutableSetOf<String>()
    private var pendingEditSaveJob: Job? = null

    fun getConfigJsonContent(configId: String): String? {
        return configRepo.getConfigJsonContent(configId)
    }

    fun saveConfigJsonContent(configId: String, content: String): Boolean {
        return configRepo.saveConfigJsonContent(configId, content)
    }

    fun updateConfig(config: OverrideConfig) {
        saveConfig(config)
    }

    fun saveConfig(config: OverrideConfig) {
        scope.launch {
            persistConfig(
                config = config,
                emitSuccessEvent = true,
                refreshAfterSave = true,
                updateSaveState = true,
            )
        }
    }

    fun saveConfigSilently(
        config: OverrideConfig,
        onSaved: ((OverrideConfig) -> Unit)? = null,
    ) {
        val shouldLaunchWorker = synchronized(silentSaveLock) {
            pendingSilentConfigs[config.id] = config
            if (onSaved != null) {
                pendingSilentCallbacks[config.id] = onSaved
            } else {
                pendingSilentCallbacks.remove(config.id)
            }
            activeSilentSaveIds.add(config.id)
        }

        if (!shouldLaunchWorker) {
            return
        }

        scope.launch {
            drainSilentSaveQueue(config.id)
        }
    }

    suspend fun selectConfig(id: String) {
        _selectedConfig.value = configRepo.getById(id)
    }

    fun clearSelectedConfig() {
        _selectedConfig.value = null
    }

    fun syncSelectedConfig(config: OverrideConfig) {
        if (_selectedConfig.value?.id == config.id) {
            _selectedConfig.value = config
        }
    }

    fun startEditSession(configId: String) {
        scope.launch {
            val currentSession = _editSession.value
            if (currentSession?.routeConfigId == configId) {
                return@launch
            }

            pendingEditSaveJob?.cancel()

            if (configId == "new") {
                _selectedConfig.value = null
                val emptyConfig = ConfigurationOverride()
                _editSession.value = OverrideEditSession(
                    routeConfigId = configId,
                    targetConfigId = OverrideMetadata.generateId(),
                    persistedId = null,
                    createdAt = System.currentTimeMillis(),
                    name = "",
                    description = "",
                    config = emptyConfig,
                    draftSnapshot = encodeOverrideConfigForDiff(emptyConfig),
                    persistedName = "",
                    persistedDescription = "",
                    persistedSnapshot = encodeOverrideConfigForDiff(ConfigurationOverride()),
                )
                return@launch
            }

            try {
                val config = configRepo.getById(configId)
                _selectedConfig.value = config
                _editSession.value = config?.let(::createEditSession)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to open override editor")
                _editSession.value = null
                _events.emit(OverrideSaveEvent.Failed(e.message ?: MLang.Override.Save.Failed))
            }
        }
    }

    fun updateDraftName(value: String) {
        updateEditSession(textAutosaveDelayMillis) { session ->
            session.copy(name = value)
        }
    }

    fun updateDraftDescription(value: String) {
        updateEditSession(textAutosaveDelayMillis) { session ->
            session.copy(description = value)
        }
    }

    fun updateDraftConfig(
        updatedConfig: ConfigurationOverride,
        saveImmediately: Boolean = true,
    ) {
        updateEditSession(
            saveDelayMillis = if (saveImmediately) 0L else textAutosaveDelayMillis,
        ) { session ->
            session.copy(
                config = updatedConfig,
                draftSnapshot = encodeOverrideConfigForDiff(updatedConfig),
            )
        }
    }

    fun mutateDraftConfig(
        saveImmediately: Boolean = true,
        transform: (ConfigurationOverride) -> ConfigurationOverride,
    ) {
        val currentSession = _editSession.value ?: return
        updateDraftConfig(
            updatedConfig = transform(currentSession.config),
            saveImmediately = saveImmediately,
        )
    }

    fun saveDraftNow(onSaved: (() -> Unit)? = null) {
        pendingEditSaveJob?.cancel()
        val session = _editSession.value
        if (session == null || !session.canSave || !session.hasPersistedChanges) {
            onSaved?.invoke()
            return
        }
        saveDraftSession(session, onSaved)
    }

    fun flushDraftSave(onSaved: (() -> Unit)? = null) {
        pendingEditSaveJob?.cancel()
        saveDraftNow(onSaved)
    }

    fun clearEditSession() {
        pendingEditSaveJob?.cancel()
        pendingEditSaveJob = null
        _editSession.value = null
        clearSelectedConfig()
    }

    private suspend fun persistConfig(
        config: OverrideConfig,
        emitSuccessEvent: Boolean,
        refreshAfterSave: Boolean,
        updateSaveState: Boolean,
        onSaved: ((OverrideConfig) -> Unit)? = null,
    ) {
        if (updateSaveState) {
            _saveState.value = OverrideSaveState.Saving
        }
        try {
            if (configRepo.isSystemPreset(config.id)) {
                Timber.tag(loggerTag).w("Cannot update builtin preset: ${config.id}")
                if (updateSaveState) {
                    _saveState.value = OverrideSaveState.Idle
                }
                _events.tryEmit(OverrideSaveEvent.Failed(MLang.Override.Save.PresetNotModifiable))
                return
            }
            configRepo.save(config)
            val runtimeSynced = activeProfileOverrideReloader.reapplyActiveProfileIfUsingOverride(config.id)
            if (refreshAfterSave) {
                reloadConfigs()
            } else {
                updateLocalCacheAfterSave(config)
            }
            Timber.tag(loggerTag).i("Updated config: ${config.id}")
            onSaved?.invoke(config)
            if (emitSuccessEvent) {
                if (runtimeSynced) {
                    _events.emit(OverrideSaveEvent.Saved(config.id))
                } else {
                    _events.emit(OverrideSaveEvent.Failed(MLang.Override.Save.ApplyFailed))
                }
            }
        } catch (e: Exception) {
            Timber.tag(loggerTag).e(e, "Failed to update config")
            _events.emit(
                OverrideSaveEvent.Failed(e.message ?: MLang.Override.Save.Failed),
            )
        } finally {
            if (updateSaveState) {
                _saveState.value = OverrideSaveState.Idle
            }
        }
    }

    private suspend fun drainSilentSaveQueue(configId: String) {
        while (true) {
            val nextRequest = synchronized(silentSaveLock) {
                val nextConfig = pendingSilentConfigs.remove(configId)
                if (nextConfig == null) {
                    activeSilentSaveIds.remove(configId)
                    null
                } else {
                    SilentSaveRequest(
                        config = nextConfig,
                        onSaved = pendingSilentCallbacks.remove(configId),
                    )
                }
            } ?: return

            persistConfig(
                config = nextRequest.config,
                emitSuccessEvent = false,
                refreshAfterSave = false,
                updateSaveState = false,
                onSaved = nextRequest.onSaved,
            )
        }
    }

    private fun createEditSession(config: OverrideConfig): OverrideEditSession {
        val snapshot = encodeOverrideConfigForDiff(config.config)
        return OverrideEditSession(
            routeConfigId = config.id,
            targetConfigId = config.id,
            persistedId = config.id,
            createdAt = config.createdAt,
            name = config.name,
            description = config.description.orEmpty(),
            config = config.config,
            draftSnapshot = snapshot,
            persistedName = config.name,
            persistedDescription = config.description.orEmpty(),
            persistedSnapshot = snapshot,
        )
    }

    private fun updateEditSession(
        saveDelayMillis: Long,
        transform: (OverrideEditSession) -> OverrideEditSession,
    ) {
        val currentSession = _editSession.value ?: return
        val updatedSession = transform(currentSession)
        if (updatedSession == currentSession) {
            return
        }
        _editSession.value = updatedSession
        scheduleDraftSave(updatedSession, saveDelayMillis)
    }

    private fun scheduleDraftSave(
        session: OverrideEditSession,
        delayMillis: Long,
    ) {
        pendingEditSaveJob?.cancel()
        pendingEditSaveJob = scope.launch {
            if (delayMillis > 0L) {
                PollingTimers.awaitTick(
                    PollingTimerSpecs.dynamic(
                        name = "override_editor_draft_save",
                        intervalMillis = delayMillis,
                        initialDelayMillis = delayMillis,
                    ),
                )
            }
            val latestSession = _editSession.value
            if (latestSession == null || latestSession.targetConfigId != session.targetConfigId) {
                return@launch
            }
            saveDraftSession(latestSession)
        }
    }

    private fun saveDraftSession(
        session: OverrideEditSession,
        onSaved: (() -> Unit)? = null,
    ) {
        if (!session.canSave || !session.hasPersistedChanges) {
            onSaved?.invoke()
            return
        }

        saveConfigSilently(
            config = OverrideConfig(
                id = session.targetConfigId,
                name = session.name,
                description = session.description.takeIf(String::isNotBlank),
                config = session.config,
                isSystem = false,
                createdAt = session.createdAt,
                updatedAt = System.currentTimeMillis(),
            ),
        ) { savedConfig ->
            syncPersistedEditSession(savedConfig)
            onSaved?.invoke()
        }
    }

    private fun syncPersistedEditSession(savedConfig: OverrideConfig) {
        val persistedSnapshot = encodeOverrideConfigForDiff(savedConfig.config)
        _editSession.update { currentSession ->
            if (currentSession == null || currentSession.targetConfigId != savedConfig.id) {
                return@update currentSession
            }
            currentSession.copy(
                persistedId = savedConfig.id,
                createdAt = savedConfig.createdAt,
                persistedName = savedConfig.name,
                persistedDescription = savedConfig.description.orEmpty(),
                persistedSnapshot = persistedSnapshot,
            )
        }
        _selectedConfig.value = savedConfig
    }
}

private data class SilentSaveRequest(
    val config: OverrideConfig,
    val onSaved: ((OverrideConfig) -> Unit)? = null,
)
