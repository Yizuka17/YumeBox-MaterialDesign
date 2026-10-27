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



package com.github.yumelira.yumebox.screen.profiles

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.model.FetchStatus
import com.github.yumelira.yumebox.core.presentation.AndroidContractStateViewModel
import com.github.yumelira.yumebox.core.presentation.LoadableState
import com.github.yumelira.yumebox.data.store.LinkOpenMode
import com.github.yumelira.yumebox.data.store.Preference
import com.github.yumelira.yumebox.data.store.ProfileLink
import com.github.yumelira.yumebox.data.store.ProfileLinksStore
import com.github.yumelira.yumebox.runtime.client.ProfilesRepository
import com.github.yumelira.yumebox.runtime.api.FetchObserver
import com.github.yumelira.yumebox.runtime.api.Profile
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.*

class ProfilesViewModel(
    application: Application,
    private val profilesRepository: ProfilesRepository,
    profileLinksStorage: ProfileLinksStore,
) : AndroidContractStateViewModel<ProfilesUiState, ProfilesViewModel.ProfilesUiEffect>(
    application,
    ProfilesUiState(),
) {

    val linkOpenMode: Preference<LinkOpenMode> = profileLinksStorage.linkOpenMode
    val links: Preference<List<ProfileLink>> = profileLinksStorage.links
    val defaultLinkId: Preference<String> = profileLinksStorage.defaultLinkId

    fun setOpenMode(mode: LinkOpenMode) = linkOpenMode.set(mode)

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<Profile?>(null)
    val activeProfile: StateFlow<Profile?> = _activeProfile.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val _updatingProfileIds = MutableStateFlow<Set<UUID>>(emptySet())
    val updatingProfileIds: StateFlow<Set<UUID>> = _updatingProfileIds.asStateFlow()

    private val updateJobs = mutableMapOf<UUID, Job>()
    private val profileConfigBackups = mutableMapOf<UUID, ProfileConfigBackup>()
    private val canceledProfileUpdateIds = mutableSetOf<UUID>()

    init {
        refreshProfiles()
    }

    fun refreshProfiles() {
        viewModelScope.launch {
            try {
                applyLoading(true)
                val allProfiles = profilesRepository.queryAllProfiles()
                val active = profilesRepository.queryActiveProfile()

                _profiles.value = allProfiles
                _activeProfile.value = active
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to refresh profiles")
                showError(MLang.ProfilesVM.Message.UpdateFailed.format(e.message ?: "Unknown"))
            } finally {
                applyLoading(false)
            }
        }
    }

    fun createProfile(
        type: Profile.Type,
        name: String,
        source: String = "",
        interval: Long = 0L,
        fileUri: Uri? = null
    ) {
        viewModelScope.launch {
            var createdUuid: UUID? = null
            try {
                applyLoading(true)
                val uuid = profilesRepository.createProfile(type, name, source)
                createdUuid = uuid

                _downloadProgress.value = DownloadProgress(
                    percent = 0,
                    message = MLang.ProfilesVM.Progress.Preparing,
                )

                val observer = FetchObserver { status ->
                    _downloadProgress.value = status.toDownloadProgress()
                }

                if (type == Profile.Type.File && fileUri != null) {
                    copyFileToImportedDir(fileUri, uuid)
                }

                profilesRepository.updateProfile(uuid, observer)
                _downloadProgress.value = DownloadProgress(
                    percent = 100,
                    message = MLang.ProfilesVM.Progress.ImportComplete,
                    isCompleted = true,
                )

                showMessage(MLang.ProfilesVM.Message.ProfileAdded.format(name))
                refreshProfiles()
                Timber.i("Profile created: $uuid")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to create profile")
                createdUuid?.let { uuid ->
                    runCatching { profilesRepository.deleteProfile(uuid) }
                        .onFailure { deleteError ->
                            Timber.w(deleteError, "Failed to rollback profile creation: $uuid")
                        }
                }
                refreshProfiles()
                showError(MLang.ProfilesVM.Message.AddFailed.format(e.message ?: "Unknown"))
                _downloadProgress.value = null
            } finally {
                applyLoading(false)
            }
        }
    }

    private suspend fun copyFileToImportedDir(uri: Uri, uuid: UUID) {
        withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val importedDir = File(context.filesDir, "imported/${uuid}")
            importedDir.mkdirs()

            val outputFile = File(importedDir, "config.yaml")
            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
                ?: throw IllegalArgumentException("Failed to open file: $uri")
            Timber.d("File copied: ${outputFile.absolutePath}")
        }
    }

    fun cloneProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                applyLoading(true)
                val newUuid = profilesRepository.cloneProfile(uuid)
                showMessage(MLang.ProfilesVM.Message.ProfileAdded.format("Clone"))
                refreshProfiles()
                Timber.i("Profile cloned: from=$uuid to=$newUuid")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to clone profile")
                showError(MLang.ProfilesVM.Message.AddFailed.format(e.message ?: "Unknown"))
            } finally {
                applyLoading(false)
            }
        }
    }

    fun deleteProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                applyLoading(true)
                profilesRepository.deleteProfile(uuid)
                showMessage(MLang.ProfilesVM.Message.ProfileDeleted)
                refreshProfiles()
                Timber.i("Profile deleted: $uuid")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to delete profile")
                showError(MLang.ProfilesVM.Message.DeleteFailed.format(e.message ?: "Unknown"))
            } finally {
                applyLoading(false)
            }
        }
    }

    fun activateProfile(uuid: UUID) {
        viewModelScope.launch {
            try {
                applyLoading(true)
                profilesRepository.setActiveProfile(uuid)
                showMessage(MLang.ProfilesVM.Message.ProfileUpdated.format("Active"))
                refreshProfiles()
                Timber.i("Profile activated: $uuid")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to activate profile")
                showError(MLang.ProfilesVM.Message.ToggleFailed.format(e.message ?: "Unknown"))
            } finally {
                applyLoading(false)
            }
        }
    }

    fun updateProfile(uuid: UUID) {
        if (uuid in _updatingProfileIds.value) return
        val updateJob = viewModelScope.launch {
            val backup = captureProfileConfigBackup(uuid)
            var restoreBackupOnExit = false
            profileConfigBackups[uuid] = backup
            canceledProfileUpdateIds.remove(uuid)
            _updatingProfileIds.update { it + uuid }
            try {
                applyLoading(true)
                _downloadProgress.value = DownloadProgress(
                    percent = 0,
                    message = MLang.ProfilesVM.Progress.Preparing,
                )

                val observer = FetchObserver { status ->
                    _downloadProgress.value = status.toDownloadProgress()
                }

                profilesRepository.updateProfile(uuid, observer)
                if (!profileConfigExists(uuid)) {
                    error("Updated configuration file is missing")
                }

                if (uuid !in canceledProfileUpdateIds) {
                    _downloadProgress.value = DownloadProgress(
                        percent = 100,
                        message = MLang.ProfilesVM.Progress.ImportComplete,
                        isCompleted = true,
                    )
                    showMessage(MLang.ProfilesVM.Message.ProfileUpdated.format(uuid.toString()))
                    refreshProfiles()
                    Timber.i("Profile updated: $uuid")
                }
            } catch (e: Exception) {
                restoreBackupOnExit = true
                if (e is CancellationException) {
                    Timber.d("Profile update cancelled: $uuid")
                } else {
                    Timber.e(e, "Failed to update profile")
                    showError(MLang.ProfilesVM.Message.UpdateFailed.format(e.message ?: "Unknown"))
                    _downloadProgress.value = null
                }
            } finally {
                if (uuid in canceledProfileUpdateIds || restoreBackupOnExit) {
                    restoreProfileConfigBackup(uuid)
                    canceledProfileUpdateIds.remove(uuid)
                    refreshProfiles()
                }
                updateJobs.remove(uuid)
                profileConfigBackups.remove(uuid)
                _updatingProfileIds.update { it - uuid }
                applyLoading(false)
            }
        }
        updateJobs[uuid] = updateJob
    }

    fun cancelProfileUpdateAndRestore(uuid: UUID) {
        if (uuid !in _updatingProfileIds.value && uuid !in profileConfigBackups) return
        canceledProfileUpdateIds.add(uuid)
        updateJobs[uuid]?.cancel()
        _updatingProfileIds.update { it - uuid }
        viewModelScope.launch {
            restoreProfileConfigBackup(uuid)
            refreshProfiles()
        }
    }

    fun patchProfile(uuid: UUID, name: String, source: String, interval: Long) {
        viewModelScope.launch {
            try {
                applyLoading(true)
                profilesRepository.patchProfile(uuid, com.github.yumelira.yumebox.runtime.client.ProfilePatch(name, source, interval))
                showMessage(MLang.ProfilesVM.Message.ProfileUpdated.format(name))
                refreshProfiles()
                Timber.i("Profile patched: $uuid")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to patch profile")
                showError(MLang.ProfilesVM.Message.UpdateFailed.format(e.message ?: "Unknown"))
            } finally {
                applyLoading(false)
            }
        }
    }

    fun importProfileFromFile(uri: Uri, name: String) {
        createProfile(
            type = Profile.Type.File,
            name = name,
            fileUri = uri
        )
    }

    fun reorderProfiles(from: Int, to: Int) {
        viewModelScope.launch {
            try {
                val current = _profiles.value
                if (from !in current.indices || to !in current.indices || from == to) return@launch

                val reordered = current.toMutableList()
                val moved = reordered.removeAt(from)
                reordered.add(to, moved)

                _profiles.value = reordered
                profilesRepository.reorderProfiles(reordered.map { it.uuid })
                Timber.d("Profiles reordered: $from->$to")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to reorder profiles")
                refreshProfiles()
            }
        }
    }

    fun toggleProfileEnabled(uuid: UUID) {
        viewModelScope.launch {
            try {
                val profile = profilesRepository.queryProfileByUUID(uuid)
                    ?: error("Profile not found: $uuid")

                if (profile.active) {
                    cancelProfileUpdateAndRestore(uuid)
                    profilesRepository.clearActiveProfile(profile)
                    showMessage(MLang.ProfilesVM.Message.ProfileUpdated.format(profile.name))
                } else {
                    profilesRepository.setActiveProfile(uuid)
                    showMessage(MLang.ProfilesVM.Message.ProfileUpdated.format(profile.name))
                }
                refreshProfiles()
                Timber.d("Profile toggled: $uuid, active=${!profile.active}")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to toggle profile")
                showError(MLang.ProfilesVM.Message.ToggleFailed.format(e.message ?: "Unknown"))
            }
        }
    }

    fun clearDownloadProgress() {
        _downloadProgress.value = null
    }

    fun clearError() {
        clearErrorState()
    }

    fun clearMessage() {
        clearMessageState()
    }

    private fun applyLoading(loading: Boolean) {
        super.setLoading(loading)
    }

    private suspend fun captureProfileConfigBackup(uuid: UUID): ProfileConfigBackup = withContext(Dispatchers.IO) {
        val configFile = profileConfigFile(uuid)
        ProfileConfigBackup(
            existed = configFile.exists(),
            bytes = if (configFile.exists()) configFile.readBytes() else null,
        )
    }

    private suspend fun restoreProfileConfigBackup(uuid: UUID) = withContext(Dispatchers.IO) {
        val backup = profileConfigBackups[uuid] ?: return@withContext
        val configFile = profileConfigFile(uuid)
        if (backup.existed) {
            configFile.parentFile?.mkdirs()
            configFile.writeBytes(backup.bytes ?: ByteArray(0))
        } else {
            configFile.delete()
        }
    }

    private suspend fun profileConfigExists(uuid: UUID): Boolean = withContext(Dispatchers.IO) {
        profileConfigFile(uuid).exists()
    }

    private fun profileConfigFile(uuid: UUID): File {
        return File(getApplication<Application>().filesDir, "imported/${uuid}/config.yaml")
    }

    private fun showError(message: String) {
        postError(message, ProfilesUiEffect.ShowError(message))
    }

    private fun showMessage(message: String) {
        postMessage(message, ProfilesUiEffect.ShowMessage(message))
    }

    sealed interface ProfilesUiEffect {
        data class ShowMessage(val message: String) : ProfilesUiEffect
        data class ShowError(val message: String) : ProfilesUiEffect
    }
}

private data class ProfileConfigBackup(
    val existed: Boolean,
    val bytes: ByteArray?,
)

data class ProfilesUiState(
    override val isLoading: Boolean = false,
    override val error: String? = null,
    override val message: String? = null
) : LoadableState<ProfilesUiState> {
    override fun withLoading(loading: Boolean): ProfilesUiState = copy(isLoading = loading)
    override fun withError(error: String?): ProfilesUiState = copy(error = error)
    override fun withMessage(message: String?): ProfilesUiState = copy(message = message)
}

data class DownloadProgress(
    val percent: Int?,
    val message: String,
    val isCompleted: Boolean = false,
)

private fun FetchStatus.toDownloadProgress(): DownloadProgress {
    val percent = if (max > 0) ((progress * 100) / max).coerceIn(0, 100) else null
    val detail = args.firstOrNull().orEmpty().trim()

    val message = when (action) {
        FetchStatus.Action.FetchConfiguration -> {
            if (percent == null || percent <= 5) {
                MLang.ProfilesVM.Progress.Preparing
            } else {
                detail.ifBlank { MLang.ProfilesPage.Progress.Downloading }
            }
        }

        FetchStatus.Action.FetchProviders -> {
            if (detail.isNotBlank()) detail else ""
        }

        FetchStatus.Action.SubscriptionInfo -> detail
        FetchStatus.Action.Verifying -> {
            detail.ifBlank { MLang.ProfilesVM.Progress.Verifying }
        }
    }

    return DownloadProgress(
        percent = percent,
        message = message,
    )
}
