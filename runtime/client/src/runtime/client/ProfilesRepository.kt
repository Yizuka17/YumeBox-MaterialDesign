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

package com.github.yumelira.yumebox.runtime.client

import android.content.Context
import android.content.Intent
import com.github.yumelira.yumebox.core.data.RepositoryUtils.safeApiCall
import com.github.yumelira.yumebox.runtime.api.*
import com.github.yumelira.yumebox.runtime.client.access.RuntimeAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*

/** Profile CRUD via `RuntimeAccess` / `ProfileApi`. */
class ProfilesRepository(private val context: Context) {
    private val appContext = context.appContextOrSelf

    suspend fun createProfile(
        type: Profile.Type,
        name: String,
        source: String = "",
        ageSecretKey: String? = null,
    ): UUID =
        safeApiCall(TAG, "createProfile") {
                Timber.d("Creating profile: type=$type, name=$name")
                RuntimeAccess.connect(context)
                RuntimeAccess.profile().create(type, name, source, ageSecretKey)
            }
            .getOrThrow()

    suspend fun cloneProfile(uuid: UUID): UUID =
        safeApiCall(TAG, "cloneProfile") {
                Timber.d("Cloning profile: uuid=$uuid")
                RuntimeAccess.connect(context)
                RuntimeAccess.profile().clone(uuid)
            }
            .getOrThrow()

    suspend fun deleteProfile(uuid: UUID) {
        safeApiCall(TAG, "deleteProfile") {
                Timber.d("Deleting profile: uuid=$uuid")
                RuntimeAccess.connect(context)
                RuntimeAccess.profile().delete(uuid)
            }
            .getOrThrow()
    }

    suspend fun queryAllProfiles(): List<Profile> =
        withContext(Dispatchers.IO) {
            safeApiCall(TAG, "queryAllProfiles") {
                    RuntimeAccess.connect(context)
                    RuntimeAccess.profile().queryAll()
                }
                .getOrThrow()
        }

    suspend fun queryActiveProfile(): Profile? =
        withContext(Dispatchers.IO) {
            safeApiCall(TAG, "queryActiveProfile") {
                    RuntimeAccess.connect(context)
                    RuntimeAccess.profile().queryActive()
                }
                .getOrThrow()
        }

    suspend fun queryProfileByUUID(uuid: UUID): Profile? =
        withContext(Dispatchers.IO) {
            safeApiCall(TAG, "queryProfileByUUID") {
                    RuntimeAccess.connect(context)
                    RuntimeAccess.profile().queryByUUID(uuid)
                }
                .getOrThrow()
        }

    suspend fun setActiveProfile(uuid: UUID) {
        withContext(Dispatchers.IO) {
            safeApiCall(TAG, "setActiveProfile") {
                    val startedAt = System.currentTimeMillis()
                    Timber.d("Setting active profile: uuid=$uuid")
                    RuntimeAccess.connect(context)

                    val profile =
                        RuntimeAccess.profile().queryByUUID(uuid)
                            ?: throw IllegalArgumentException("Profile not found: $uuid")

                    RuntimeAccess.profile().setActive(profile)

                    notifyRuntimeOverrideChanged()

                    Timber.d(
                        "Active profile applied: uuid=$uuid cost=${System.currentTimeMillis() - startedAt}ms"
                    )
                }
                .getOrThrow()
        }
    }

    suspend fun clearActiveProfile(profile: Profile) {
        safeApiCall(TAG, "clearActiveProfile") {
                Timber.d("Clearing active profile: uuid=${profile.uuid}")
                RuntimeAccess.connect(context)
                RuntimeAccess.profile().clearActive(profile)
                notifyRuntimeOverrideChanged()
            }
            .getOrThrow()
    }

    suspend fun reorderProfiles(uuids: List<UUID>) {
        safeApiCall(TAG, "reorderProfiles") {
                Timber.d("Reordering profiles: count=${uuids.size}")
                RuntimeAccess.connect(context)
                RuntimeAccess.profile().reorder(uuids)
            }
            .getOrThrow()
    }

    suspend fun updateProfile(
        uuid: UUID,
        callback: FetchObserver? = null,
    ): ProfileUpdateReport =
        safeApiCall(TAG, "updateProfile") {
                Timber.d("Updating profile: uuid=$uuid")
                RuntimeAccess.connect(context)
                RuntimeAccess.profile().update(uuid, callback)
            }
            .getOrThrow()

    suspend fun patchProfile(uuid: UUID, patch: ProfilePatch) {
        safeApiCall(TAG, "patchProfile") {
                Timber.d("Patching profile: uuid=$uuid")
                RuntimeAccess.connect(context)
                RuntimeAccess.profile()
                    .patch(
                        uuid = uuid,
                        name = patch.name,
                        source = patch.source,
                        interval = patch.interval,
                        updateAgeSecretKey = patch.updateAgeSecretKey,
                        ageSecretKey = patch.ageSecretKey,
                    )
            }
            .getOrThrow()
    }

    private fun notifyRuntimeOverrideChanged() {
        appContext.sendBroadcast(
            Intent(Intents.actionOverrideChanged(appContext.packageName))
                .setPackage(appContext.packageName)
        )
    }

    companion object {
        private const val TAG = "ProfilesRepository"
    }
}
