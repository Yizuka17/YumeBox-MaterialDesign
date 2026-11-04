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

package com.github.yumelira.yumebox.presentation.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material Design 3 semantic icon catalog for YumeBox UI.
 *
 * New MD3/MD3E screens should depend on these semantic entries instead of importing
 * individual Material icon assets directly. The historical [Yume] icon set is kept
 * as legacy/brand assets and can still be used by old or special pages.
 */
object AppMd3Icons {
    object Navigation {
        val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
        val Forward: ImageVector = Icons.Rounded.KeyboardArrowRight
        val DownAngle: ImageVector = AppCustomIcons.DownAngle
    }

    object Action {
        val Add: ImageVector = Icons.Rounded.AddCircleOutline
        val Check: ImageVector = Icons.Rounded.Check
        val Close: ImageVector = Icons.Rounded.Close
        val Cancel: ImageVector = Close
        val Copy: ImageVector = Icons.Rounded.ContentCopy
        val Delete: ImageVector = Icons.Rounded.Delete
        val Edit: ImageVector = Icons.Rounded.Edit
        val List: ImageVector = Icons.AutoMirrored.Rounded.ViewList
        val Refresh: ImageVector = Icons.Rounded.Refresh
        val Save: ImageVector = Icons.Rounded.Save
        val Search: ImageVector = Icons.Rounded.Search
        val Share: ImageVector = Icons.Rounded.Share
        val ThemeColor: ImageVector = Icons.Rounded.Palette
        val Settings: ImageVector = Icons.Rounded.Settings
        val Sort: ImageVector = Icons.Rounded.UnfoldMore
        val SpeedTest: ImageVector = Icons.Rounded.Speed
        val Sync: ImageVector = Icons.Rounded.Sync
        val Undo: ImageVector = Icons.AutoMirrored.Rounded.Undo
    }

    object Shell {
        val NavigateForward: ImageVector = Navigation.Forward
        val AddProfile: ImageVector = Icons.Rounded.AddCircleOutline
        val UpdateProfiles: ImageVector = Icons.Rounded.Sync
        val OpenHome: ImageVector = Icons.Rounded.Home
        val OpenProxy: ImageVector = Icons.Rounded.SwapVert
        val OpenProfiles: ImageVector = Icons.AutoMirrored.Rounded.ViewList
        val OpenProfileConfig: ImageVector = Icons.Rounded.Description
        val OpenSettings: ImageVector = Icons.Rounded.Settings
        val PackageComplete: ImageVector = Icons.Rounded.CheckCircle
        val StartProxy: ImageVector = Icons.Rounded.PlayArrow
        val StopProxy: ImageVector = Icons.Rounded.Stop
    }

    object Proxy {
        val DelayTest: ImageVector = Icons.Rounded.Speed
        val CloudTest: ImageVector = Icons.Rounded.CloudQueue
        val Panel: ImageVector = Icons.Rounded.Public
        val Profiles: ImageVector = Icons.Rounded.Folder
    }

    object Connection {
        val SortBy: ImageVector = Icons.AutoMirrored.Rounded.Sort
        val SearchConnection: ImageVector = Action.Search
    }

    object Home {
        val ProxyModeVpn: ImageVector = Icons.Rounded.FlightTakeoff
        val ProxyModeTun: ImageVector = Icons.Rounded.VpnKey
        val ProxyModeHttp: ImageVector = Icons.Rounded.Wifi
        val StatusIdle: ImageVector = Icons.Rounded.RocketLaunch
        val StatusWaiting: ImageVector = Icons.Rounded.Pending
        val StatusRunning: ImageVector = Icons.Rounded.Speed
    }

    object Settings {
        val Theme: ImageVector = Icons.Rounded.Palette
        val App: ImageVector = Icons.Rounded.Settings
        val Network: ImageVector = Icons.Rounded.Dns
        val Override: ImageVector = Icons.Rounded.Tune
        val MetaFeatures: ImageVector = Icons.Rounded.Hub
        val Lab: ImageVector = Icons.Rounded.Science
        val ExportBackup: ImageVector = Icons.Rounded.Backup
        val ImportBackup: ImageVector = Icons.Rounded.CloudSync
        val AppDataManagement: ImageVector = Icons.Rounded.Folder
        val Logs: ImageVector = Icons.AutoMirrored.Rounded.ShowChart
        val About: ImageVector = Icons.Rounded.Info
    }

    object Security {
        val Enabled: ImageVector = Icons.Rounded.Security
    }

    object Onboarding {
        val Permission: ImageVector = Icons.Rounded.Security
        val Notification: ImageVector = Icons.AutoMirrored.Rounded.Message
        val AppList: ImageVector = Icons.AutoMirrored.Rounded.ViewList
        val Privacy: ImageVector = Icons.Rounded.Security
        val Complete: ImageVector = Icons.Rounded.CheckCircle
        val Theme: ImageVector = Icons.Rounded.Palette
        val Community: ImageVector = Icons.AutoMirrored.Rounded.Message
    }

    object Status {
        val RecordingStart: ImageVector = Icons.Rounded.PlayArrow
        val RecordingStop: ImageVector = Icons.Rounded.PowerSettingsNew
    }

    object Editor {
        val Format: ImageVector = Icons.Rounded.AutoFixHigh
        val FormatStructured: ImageVector = Icons.AutoMirrored.Rounded.FormatAlignLeft
        val Save: ImageVector = Action.Save
    }
}
