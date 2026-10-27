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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.model.ConfigurationOverride
import com.github.yumelira.yumebox.data.controller.ActiveProfileOverrideReloader
import com.github.yumelira.yumebox.data.store.OverrideConfigStore
import com.github.yumelira.yumebox.data.controller.OverrideResolver
import com.github.yumelira.yumebox.data.store.ProfileBindingProvider
import com.github.yumelira.yumebox.data.model.OverrideConfig
import com.github.yumelira.yumebox.data.model.OverrideMetadata
import com.github.yumelira.yumebox.presentation.util.OverrideSaveEvent
import com.github.yumelira.yumebox.presentation.util.OverrideSaveState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import timber.log.Timber
import dev.oom_wg.purejoy.mlang.MLang

data class OverrideEditSession(
    val routeConfigId: String,
    val targetConfigId: String,
    val persistedId: String?,
    val createdAt: Long,
    val name: String,
    val description: String,
    val config: ConfigurationOverride,
    val draftSnapshot: String,
    val persistedName: String,
    val persistedDescription: String,
    val persistedSnapshot: String,
) {
    val canSave: Boolean
        get() = name.isNotBlank()

    val hasPersistedChanges: Boolean
        get() = name != persistedName ||
            description != persistedDescription ||
            draftSnapshot != persistedSnapshot

    val hasUnsavedInvalidChanges: Boolean
        get() = hasPersistedChanges && !canSave
}

class OverrideConfigViewModel(
    private val configRepo: OverrideConfigStore,
    private val resolver: OverrideResolver,
    private val bindingProvider: ProfileBindingProvider,
    private val activeProfileOverrideReloader: ActiveProfileOverrideReloader,
) : ViewModel() {

    companion object {
        private const val TAG = "OverrideConfigViewModel"
        private const val TEXT_AUTOSAVE_DELAY_MILLIS = 220L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val _configs = MutableStateFlow<List<OverrideConfig>>(emptyList())
    val configs: StateFlow<List<OverrideConfig>> = _configs.asStateFlow()

    private val _systemPresets = MutableStateFlow<List<OverrideConfig>>(emptyList())
    val systemPresets: StateFlow<List<OverrideConfig>> = _systemPresets.asStateFlow()

    private val _userConfigs = MutableStateFlow<List<OverrideConfig>>(emptyList())
    val userConfigs: StateFlow<List<OverrideConfig>> = _userConfigs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _usageCountMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val usageCountMap: StateFlow<Map<String, Int>> = _usageCountMap.asStateFlow()
    private val _pendingRevealConfigId = MutableStateFlow<String?>(null)
    val pendingRevealConfigId: StateFlow<String?> = _pendingRevealConfigId.asStateFlow()
    private var bindingObserverJob: kotlinx.coroutines.Job? = null
    private val editCoordinator = OverrideEditSessionCoordinator(
        scope = viewModelScope,
        configRepo = configRepo,
        activeProfileOverrideReloader = activeProfileOverrideReloader,
        reloadConfigs = { loadConfigs() },
        updateLocalCacheAfterSave = ::updateLocalCacheAfterSave,
        loggerTag = TAG,
        textAutosaveDelayMillis = TEXT_AUTOSAVE_DELAY_MILLIS,
    )

    val selectedConfig: StateFlow<OverrideConfig?> = editCoordinator.selectedConfig
    val editSession: StateFlow<OverrideEditSession?> = editCoordinator.editSession
    val saveState: StateFlow<OverrideSaveState> = editCoordinator.saveState
    val events: SharedFlow<OverrideSaveEvent> = editCoordinator.events

    init {
        loadConfigs()
        observeBindingChanges()
    }

    private fun loadConfigs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {

                val presets = configRepo.getSystemPresets()
                _systemPresets.value = presets

                val userConfigs = configRepo.getUserConfigs()
                _userConfigs.value = userConfigs

                _configs.value = presets + userConfigs

                loadUsageCounts()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load configs")
            }
            _isLoading.value = false
        }
    }

    private suspend fun loadUsageCounts() {
        val countMap = mutableMapOf<String, Int>()
        for (config in _configs.value) {
            countMap[config.id] = resolver.getOverrideUsageCount(config.id)
        }
        _usageCountMap.value = countMap
    }

    fun refresh() {
        loadConfigs()
    }

    fun getConfigJsonContent(configId: String): String? {
        return editCoordinator.getConfigJsonContent(configId)
    }

    fun saveConfigJsonContent(configId: String, content: String): Boolean {
        return editCoordinator.saveConfigJsonContent(configId, content)
    }

    private fun observeBindingChanges() {
        bindingObserverJob?.cancel()
        bindingObserverJob = viewModelScope.launch {
            bindingProvider.getAllBindingsFlow()
                .drop(1)
                .collectLatest {
                    loadUsageCounts()
                }
        }
    }

    fun createConfig(
        name: String,
        description: String? = null,
    ) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val config = OverrideConfig(
                    id = OverrideMetadata.generateId(),
                    name = name,
                    description = description,
                    config = ConfigurationOverride(),
                    isSystem = false,
                    createdAt = now,
                    updatedAt = now,
                )
                configRepo.save(config)
                _pendingRevealConfigId.value = config.id
                loadConfigs()
                Timber.tag(TAG).i("Created config: ${config.id}")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to create config")
            }
        }
    }

    fun updateConfig(config: OverrideConfig) {
        editCoordinator.updateConfig(config)
    }

    fun saveConfig(config: OverrideConfig) {
        editCoordinator.saveConfig(config)
    }

    private fun updateLocalCacheAfterSave(config: OverrideConfig) {
        val updatedUserConfigs = _userConfigs.value.toMutableList()
        val existingIndex = updatedUserConfigs.indexOfFirst { it.id == config.id }
        if (existingIndex >= 0) {
            updatedUserConfigs[existingIndex] = config
        } else {
            updatedUserConfigs += config
        }
        _userConfigs.value = updatedUserConfigs
        _configs.value = _systemPresets.value + updatedUserConfigs
        editCoordinator.syncSelectedConfig(config)
    }

    fun deleteConfig(id: String) {
        viewModelScope.launch {
            try {
                val shouldResyncRuntime = activeProfileOverrideReloader.isActiveProfileUsingOverride(id)
                val deleted = configRepo.delete(id)
                if (deleted) {
                    if (shouldResyncRuntime && !activeProfileOverrideReloader.reapplyActiveProfileOverride()) {
                        Timber.tag(TAG).w("Override deleted but failed to reapply active profile: $id")
                    }
                    loadConfigs()
                    Timber.tag(TAG).i("Deleted config: $id")
                } else {
                    Timber.tag(TAG).w("Cannot delete config: $id (builtin preset or not found)")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to delete config")
            }
        }
    }

    fun duplicateConfig(id: String) {
        viewModelScope.launch {
            try {
                val duplicated = configRepo.duplicate(id)
                if (duplicated != null) {
                    _pendingRevealConfigId.value = duplicated.id
                    loadConfigs()
                    Timber.tag(TAG).i("Duplicated config: $id -> ${duplicated.id}")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to duplicate config")
            }
        }
    }

    fun reorderUserConfigs(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentConfigs = _userConfigs.value
            if (fromIndex !in currentConfigs.indices || fromIndex == toIndex) {
                return@launch
            }

            val reorderedConfigs = currentConfigs.toMutableList().also { configs ->
                val movingConfig = configs.removeAt(fromIndex)
                val targetIndex = toIndex.coerceIn(0, configs.size)
                configs.add(targetIndex, movingConfig)
            }

            _userConfigs.value = reorderedConfigs
            _configs.value = _systemPresets.value + reorderedConfigs

            try {
                configRepo.reorderUserConfigs(reorderedConfigs.map(OverrideConfig::id))
                loadConfigs()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to reorder configs")
                loadConfigs()
            }
        }
    }

    fun selectConfig(id: String) {
        viewModelScope.launch {
            editCoordinator.selectConfig(id)
        }
    }

    fun startEditSession(configId: String) {
        editCoordinator.startEditSession(configId)
    }

    fun updateDraftName(value: String) {
        editCoordinator.updateDraftName(value)
    }

    fun updateDraftDescription(value: String) {
        editCoordinator.updateDraftDescription(value)
    }

    fun updateDraftConfig(
        updatedConfig: ConfigurationOverride,
        saveImmediately: Boolean = true,
    ) {
        editCoordinator.updateDraftConfig(updatedConfig, saveImmediately)
    }

    fun mutateDraftConfig(
        saveImmediately: Boolean = true,
        transform: (ConfigurationOverride) -> ConfigurationOverride,
    ) {
        editCoordinator.mutateDraftConfig(saveImmediately, transform)
    }

    fun flushDraftSave(onSaved: (() -> Unit)? = null) {
        editCoordinator.flushDraftSave(onSaved)
    }

    fun clearEditSession() {
        editCoordinator.clearEditSession()
    }

    suspend fun importConfigsFromJson(
        jsonString: String,
        sourceName: String? = null,
    ): Result<Int> {
        return try {
            val importedConfigs = parseImportedOverrideConfigs(
                json = json,
                jsonString = jsonString,
                sourceName = sourceName,
            )
            for (importedConfig in importedConfigs) {
                configRepo.save(importedConfig)
            }
            _pendingRevealConfigId.value = importedConfigs.lastOrNull()?.id
            loadConfigs()
            Result.success(importedConfigs.size)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).e(e, "Failed to import config")
            Result.failure(e)
        }
    }

    fun exportConfig(id: String): String? {
        return try {
            val config = _configs.value.find { it.id == id } ?: return null
            json.encodeToString(config)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to export config")
            null
        }
    }

    suspend fun isConfigInUse(id: String): Boolean {
        return resolver.isOverrideInUse(id)
    }

    fun consumePendingRevealConfig(configId: String) {
        if (_pendingRevealConfigId.value == configId) {
            _pendingRevealConfigId.value = null
        }
    }
}

@Serializable
private data class OverrideConfigImportEnvelope(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val config: ConfigurationOverride? = null,
    val isSystem: Boolean = false,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
)

internal fun parseImportedOverrideConfigs(
    json: Json,
    jsonString: String,
    sourceName: String? = null,
    nowProvider: () -> Long = System::currentTimeMillis,
): List<OverrideConfig> {
    val normalizedJsonString = jsonString.trim()
    require(normalizedJsonString.isNotEmpty()) { MLang.Override.Save.ImportEmpty }

    val importedElements = when (val rootElement = json.parseToJsonElement(normalizedJsonString)) {
        is JsonArray -> rootElement
        else -> JsonArray(listOf(rootElement))
    }
    val hasMultipleEntries = importedElements.size > 1

    return importedElements.mapIndexed { index, element ->
        parseImportedOverrideConfigEntry(
            json = json,
            element = element,
            sourceName = sourceName,
            index = index,
            hasMultipleEntries = hasMultipleEntries,
            now = nowProvider(),
        )
    }
}

private fun parseImportedOverrideConfigEntry(
    json: Json,
    element: JsonElement,
    sourceName: String?,
    index: Int,
    hasMultipleEntries: Boolean,
    now: Long,
): OverrideConfig {
    runCatching {
        return json.decodeFromJsonElement(OverrideConfig.serializer(), element)
    }

    val importEnvelope = runCatching {
        json.decodeFromJsonElement(OverrideConfigImportEnvelope.serializer(), element)
    }.getOrNull()

    if (importEnvelope?.config != null) {
        return OverrideConfig(
            id = importEnvelope.id?.takeIf(String::isNotBlank) ?: OverrideMetadata.generateId(),
            name = importEnvelope.name?.takeIf(String::isNotBlank)
                ?: buildImportedConfigName(sourceName, index, hasMultipleEntries),
            description = importEnvelope.description?.takeIf(String::isNotBlank),
            config = importEnvelope.config,
            isSystem = false,
            createdAt = importEnvelope.createdAt ?: now,
            updatedAt = importEnvelope.updatedAt ?: now,
        )
    }

    val configurationOverride = json.decodeFromJsonElement(ConfigurationOverride.serializer(), element)
    return OverrideConfig(
        id = OverrideMetadata.generateId(),
        name = buildImportedConfigName(sourceName, index, hasMultipleEntries),
        description = null,
        config = configurationOverride,
        isSystem = false,
        createdAt = now,
        updatedAt = now,
    )
}

internal fun buildImportedConfigName(
    sourceName: String?,
    index: Int,
    hasMultipleEntries: Boolean,
): String {
    val baseName = normalizeImportedConfigSourceName(sourceName) ?: MLang.Override.Save.ImportDefaultName
    return if (hasMultipleEntries) {
        "$baseName ${index + 1}"
    } else {
        baseName
    }
}

internal fun normalizeImportedConfigSourceName(sourceName: String?): String? {
    var normalizedName = sourceName
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null

    val removableSuffixes = listOf(".json", ".yaml", ".yml")
    while (true) {
        val matchedSuffix = removableSuffixes.firstOrNull { suffix ->
            normalizedName.length > suffix.length && normalizedName.endsWith(suffix, ignoreCase = true)
        } ?: break
        normalizedName = normalizedName.dropLast(matchedSuffix.length).trimEnd()
    }

    return normalizedName.takeIf(String::isNotBlank)
}
