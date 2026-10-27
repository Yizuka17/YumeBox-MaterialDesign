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
 */

package com.github.yumelira.yumebox.screen.home

import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.model.Traffic
import com.github.yumelira.yumebox.core.presentation.LoadableState
import com.github.yumelira.yumebox.data.network.IpMonitoringState
import com.github.yumelira.yumebox.runtime.api.Profile
import com.github.yumelira.yumebox.runtime.api.RuntimeOwner
import com.github.yumelira.yumebox.runtime.api.RuntimePhase

enum class HomeProxyControlState {
    Idle,
    Connecting,
    Running,
    Lost,
    Disconnecting;

    val canInteract: Boolean
        get() = this == Idle || this == Running
}

internal enum class PendingTransition {
    None,
    AwaitingPermission,
    Starting,
    Stopping,
}

data class HomeScreenState(
    val controlState: HomeProxyControlState = HomeProxyControlState.Idle,
    val trafficNow: Traffic = 0L,
    val profiles: List<Profile> = emptyList(),
    val profilesLoaded: Boolean = false,
    val hasEnabledProfile: Boolean = false,
    val recommendedProfile: Profile? = null,
    val currentProfile: Profile? = null,
    val selectedServerName: String? = null,
    val selectedServerPing: Int? = null,
    val speedHistory: List<Long> = emptyList(),
    val proxyMode: RunMode = RunMode.VpnService,
    val isRemoteController: Boolean = false,
    val controllerBackendName: String? = null,
    val ipMonitoringState: IpMonitoringState = IpMonitoringState.Loading,
    val uiMessage: String? = null,
    val uiError: String? = null,
    val runtimeStartedAt: Long? = null,
)

data class HomeUiState(
    override val isLoading: Boolean = false,
    val isStartingProxy: Boolean = false,
    val loadingProgress: String? = null,
    override val message: String? = null,
    override val error: String? = null,
) : LoadableState<HomeUiState> {
    override fun withLoading(loading: Boolean): HomeUiState = copy(isLoading = loading)

    override fun withError(error: String?): HomeUiState = copy(error = error)

    override fun withMessage(message: String?): HomeUiState = copy(message = message)
}

sealed interface HomeUiEffect {
    data class ShowMessage(val message: String) : HomeUiEffect

    data class ShowError(val message: String) : HomeUiEffect
}

internal data class HomeProfileSummary(
    val recommendedProfile: Profile?,
    val currentProfile: Profile?,
    val selectedServerName: String?,
    val selectedServerPing: Int?,
    val speedHistory: List<Long>,
)

internal data class HomeRuntimeSummary(
    val proxyMode: RunMode,
    val isRemoteController: Boolean,
    val controllerBackendName: String?,
    val ipMonitoringState: IpMonitoringState,
    val uiState: HomeUiState,
)

internal fun resolveHomeControlState(
    owner: RuntimeOwner,
    phase: RuntimePhase,
    pendingTransition: PendingTransition,
): HomeProxyControlState {
    if (owner == RuntimeOwner.RemoteController && phase == RuntimePhase.Failed) {
        return HomeProxyControlState.Lost
    }
    val phaseStillActive =
        phase != RuntimePhase.Stopping &&
                phase != RuntimePhase.Idle &&
                phase != RuntimePhase.Failed
    if (pendingTransition == PendingTransition.Stopping && phaseStillActive) {
        return HomeProxyControlState.Disconnecting
    }
    return when (phase) {
        RuntimePhase.Running -> HomeProxyControlState.Running
        RuntimePhase.Starting -> HomeProxyControlState.Connecting
        RuntimePhase.Stopping -> HomeProxyControlState.Disconnecting
        RuntimePhase.Idle,
        RuntimePhase.Failed ->
            when (pendingTransition) {
                PendingTransition.AwaitingPermission,
                PendingTransition.Starting -> HomeProxyControlState.Connecting

                PendingTransition.Stopping,
                PendingTransition.None -> HomeProxyControlState.Idle
            }
    }
}

internal fun appendSpeedSample(history: List<Long>, sample: Long, sampleLimit: Int): List<Long> =
    buildList(sampleLimit) {
        repeat((sampleLimit - history.size - 1).coerceAtLeast(0)) { add(0L) }
        addAll(history.takeLast(sampleLimit - 1))
        add(sample)
    }