/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * YumeBox is distributed in the hope that it will be useful,
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

package com.github.yumelira.yumebox.data.store.room

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "traffic_hourly", primaryKeys = ["hour_start_millis"])
data class TrafficHourlyEntity(
    @ColumnInfo(name = "hour_start_millis") val hourStartMillis: Long,
    @ColumnInfo(name = "total_upload") val totalUpload: Long = 0L,
    @ColumnInfo(name = "total_download") val totalDownload: Long = 0L,
)
