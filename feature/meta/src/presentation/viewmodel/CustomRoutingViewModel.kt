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

package com.github.yumelira.yumebox.feature.meta.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.model.ConfigurationOverride
import com.github.yumelira.yumebox.core.model.OverrideInternalConstants
import com.github.yumelira.yumebox.data.controller.ActiveProfileOverrideReloader
import com.github.yumelira.yumebox.data.store.OverrideConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import timber.log.Timber

class CustomRoutingViewModel(
    private val overrideConfigRepository: OverrideConfigStore,
    private val activeProfileOverrideReloader: ActiveProfileOverrideReloader,
) : ViewModel() {

    private val _config = MutableStateFlow(ConfigurationOverride())
    val config: StateFlow<ConfigurationOverride> = _config.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _config.value = overrideConfigRepository.loadCustomRouting() ?: ConfigurationOverride()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load custom routing")
                _error.value = "读取自定义分流失败：${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateConfig(updatedConfig: ConfigurationOverride) {
        _config.value = updatedConfig
    }

    suspend fun saveConfig(updatedConfig: ConfigurationOverride): Boolean {
        return try {
            overrideConfigRepository.saveCustomRouting(updatedConfig)
            _config.value = updatedConfig
            // Persistence succeeded. A runtime reload failure must not trap the user on this page.
            try {
                if (!activeProfileOverrideReloader.reapplyActiveProfileIfUsingOverride(
                        OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID,
                    )) {
                    com.github.yumelira.yumebox.common.util.showToastDialog("分流已保存，重新启动代理后生效")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Custom routing saved but runtime reload failed")
                com.github.yumelira.yumebox.common.util.showToastDialog("分流已保存，重新启动代理后生效")
            }
            _error.value = null
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to save custom routing")
            _error.value = "保存自定义分流失败：${e.message}"
            false
        }
    }
}
