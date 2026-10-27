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

package com.github.yumelira.yumebox.data.controller

import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.data.model.AccessControlMode
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import kotlinx.coroutines.CancellationException

class AccessControlController(
    private val store: NetworkSettingsStore,
    isRunning: () -> Boolean,
    private val resolveActiveMode: () -> RunMode?,
    private val restartProxy: suspend (RunMode) -> Unit,
    private val beforeRestart: suspend (RunMode) -> Unit = {},
) {
    private val restarter =
        DebouncedProxyRestarter(
            timerName = "access_control_apply_packages",
            debounceMillis = 350L,
            isRunning = isRunning,
        )

    fun setAccessControlMode(mode: AccessControlMode) {
        if (store.accessControlMode.value == mode) return
        store.accessControlMode.set(mode)
        scheduleApply()
    }

    fun applyPackages(packages: Set<String>) {
        if (store.accessControlPackages.value == packages) return
        store.accessControlPackages.set(packages)
        scheduleApply()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun scheduleApply() {
        restarter.schedule {
            val targetMode = resolveActiveMode() ?: store.runMode.value
            if (store.accessControlMode.value == AccessControlMode.ALLOW_ALL) return@schedule

            try {
                beforeRestart(targetMode)
                restartProxy(targetMode)
            } catch (
                error:
                Exception
            ) { // best-effort restart: any failure keeps the current session
                // running
                if (error is CancellationException) throw error
            }
        }
    }
}
