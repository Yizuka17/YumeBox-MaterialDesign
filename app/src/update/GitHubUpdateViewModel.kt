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

package com.github.yumelira.yumebox.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class GitHubUpdateUiState(
    val isChecking: Boolean = false,
    val candidate: UpdateCandidate? = null,
    val message: String? = null,
)

class GitHubUpdateViewModel(
    private val updateManager: GitHubUpdateManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GitHubUpdateUiState())
    val uiState: StateFlow<GitHubUpdateUiState> = _uiState.asStateFlow()
    val downloadProgress: StateFlow<UpdateDownloadProgress> = updateManager.progress
    private var downloadJob: Job? = null

    fun checkForUpdate() {
        if (_uiState.value.isChecking) return
        _uiState.value = _uiState.value.copy(isChecking = true, message = null)
        viewModelScope.launch {
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { candidate ->
                    _uiState.value = GitHubUpdateUiState(
                        isChecking = false,
                        candidate = candidate,
                        message = if (candidate == null) MLang.Component.Update.Message.NoUpdate else null,
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = GitHubUpdateUiState(
                        isChecking = false,
                        message = MLang.Component.Update.Message.CheckFailed.format(
                            throwable.message ?: MLang.Util.Error.UnknownError,
                        ),
                    )
                }
        }
    }

    fun downloadAndInstall(candidate: UpdateCandidate) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            runCatching { updateManager.downloadAndInstall(candidate) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        candidate = null,
                        message = MLang.Component.Update.Message.InstallPromptOpened,
                    )
                }
                .onFailure { throwable ->
                    val message = if (throwable.message?.contains("取消") == true) {
                        MLang.Component.Update.Message.DownloadCancelled
                    } else {
                        MLang.Component.Update.Message.InstallFailed.format(
                            throwable.message ?: MLang.Util.Error.UnknownError,
                        )
                    }
                    _uiState.value = _uiState.value.copy(message = message)
                }
        }
    }

    fun cancelDownload() {
        updateManager.cancelDownload()
    }

    fun dismissCandidate() {
        _uiState.value = _uiState.value.copy(candidate = null)
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
