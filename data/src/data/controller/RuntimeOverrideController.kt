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
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

@file:Suppress("UnusedSymbol")

package com.github.yumelira.yumebox.data.controller


import com.github.yumelira.yumebox.data.model.OverrideConfig
import com.github.yumelira.yumebox.data.model.OverrideContentType
import com.github.yumelira.yumebox.data.store.OverrideConfigStore
import com.github.yumelira.yumebox.runtime.api.Profile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class RuntimeOverrideController(
    private val configStore: OverrideConfigStore,
    private val queryActiveProfile: suspend () -> Profile?,
) {
    private val updateMutex = Mutex()

    suspend fun updateProfile(transform: (String) -> String): Result<String> =
        updateInternal(transform)

    private suspend fun loadInternal(profile: Profile): Result<String> = runCatching {
        configStore.getById(runtimeOverrideId(profile.uuid))?.content.orEmpty()
    }

    private suspend fun saveInternal(profile: Profile, content: String): Result<Unit> = runCatching {
        if (content.isBlank()) {
            configStore.delete(runtimeOverrideId(profile.uuid))
            return@runCatching
        }
        val configId = runtimeOverrideId(profile.uuid)
        val existing = configStore.getById(configId)
        configStore.save(
            OverrideConfig(
                id = configId,
                name = INTERNAL_RUNTIME_NAME,
                description = "internal runtime override for ${profile.uuid}",
                contentType = OverrideContentType.Yaml,
                content = content,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun updateInternal(transform: (String) -> String): Result<String> =
        updateMutex.withLock {
            val profile =
                queryActiveProfile()
                    ?: return@withLock Result.failure(
                        IllegalStateException("No active profile selected")
                    )
            val current =
                loadInternal(profile).getOrElse {
                    return@withLock Result.failure(it)
                }
            val updated = transform(current)
            val saveResult = saveInternal(profile, updated)
            if (saveResult.isFailure) {
                return@withLock Result.failure(
                    saveResult.exceptionOrNull() ?: IllegalStateException("保存运行时覆写失败")
                )
            }
            Result.success(updated)
        }

    private fun runtimeOverrideId(profileUuid: UUID): String =
        "${OverrideConfigStore.INTERNAL_RUNTIME_PREFIX}-profile-$profileUuid"

    private companion object {
        private const val INTERNAL_RUNTIME_NAME = "运行时覆写"
    }
}
