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

package com.github.yumelira.yumebox.data.store.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "app_traffic_daily",
    primaryKeys = ["date_millis", "app_key"],
    indices = [Index("date_millis")],
)
data class AppTrafficDailyEntity(
    @ColumnInfo(name = "date_millis") val dateMillis: Long,
    @ColumnInfo(name = "app_key") val appKey: String,
    @ColumnInfo(name = "package_name") val packageName: String? = null,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "total_upload") val totalUpload: Long = 0L,
    @ColumnInfo(name = "total_download") val totalDownload: Long = 0L,
    @ColumnInfo(name = "last_active_at") val lastActiveAt: Long = 0L,
)
