package com.github.yumelira.yumebox.data.store.room

@kotlinx.serialization.Serializable
data class TrafficStatisticsBackup(
    val apps: List<AppTrafficDailyEntity> = emptyList(),
    val routes: List<RouteTrafficDailyEntity> = emptyList(),
    val hours: List<TrafficHourlyEntity> = emptyList(),
)
