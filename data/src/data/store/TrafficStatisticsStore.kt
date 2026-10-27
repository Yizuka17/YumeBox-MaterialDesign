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

@file:Suppress("DuplicatedCode", "UnusedSymbol")

package com.github.yumelira.yumebox.data.store


import com.github.yumelira.yumebox.data.model.*
import com.github.yumelira.yumebox.data.store.room.AppTrafficDelta
import com.github.yumelira.yumebox.data.store.room.RouteTrafficDelta
import com.github.yumelira.yumebox.data.store.room.TrafficStatisticsDao
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import com.github.yumelira.yumebox.data.store.room.AppTrafficDailyEntity
import com.github.yumelira.yumebox.data.store.room.RouteTrafficDailyEntity
import java.util.*

/**
 * Facade over traffic statistics persistence.
 *
 * Daily per-app / per-route aggregation lives in Room (see [TrafficStatisticsDao]); each record is
 * a row-level accumulate rather than a whole-history JSON rewrite. The only state that stays in
 * MMKV is the collector's resume baseline (last total upload/download + profile id), kept
 * synchronous.
 */
class TrafficStatisticsStore(
    private val mmkv: MMKV,
    private val dao: TrafficStatisticsDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val legacyImport = CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
        try {
            val marker = "fork_stats_room_imported_v1"
            if (mmkv.decodeBool(marker, false)) return@async
            val json = Json { ignoreUnknownKeys = true }
            fun read(key: String): JsonObject = mmkv.decodeString(key)?.takeIf(String::isNotBlank)
                ?.let { json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())
            fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: 0L
            fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
            val apps = read(KEY_DAILY_APP_SUMMARIES).map { (day, value) ->
                val summary = value.jsonObject
                val date = summary.long("dateMillis").takeIf { it > 0 } ?: day.toLong()
                summary["appUsages"]?.jsonObject.orEmpty().map { (key, entry) ->
                    val usage = entry.jsonObject
                    AppTrafficDailyEntity(date, key, usage.string("packageName").takeIf(String::isNotBlank),
                        usage.string("appName"), usage.long("totalUpload"), usage.long("totalDownload"), usage.long("lastActiveAt"))
                }
            }.flatten()
            val routes = read(KEY_DAILY_ROUTE_SUMMARIES).map { (day, value) ->
                val summary = value.jsonObject
                val date = summary.long("dateMillis").takeIf { it > 0 } ?: day.toLong()
                summary["routeUsagesByApp"]?.jsonObject.orEmpty().map { (app, entries) ->
                    entries.jsonObject.map { (route, entry) ->
                        val usage = entry.jsonObject
                        RouteTrafficDailyEntity(date, app, route, usage.string("routeLabel"),
                            usage.long("totalUpload"), usage.long("totalDownload"), usage.long("lastActiveAt"))
                    }
                }.flatten()
            }.flatten()
            dao.importLegacy(apps, routes)
            mmkv.encode(marker, true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            timber.log.Timber.w(error, "Legacy statistics import failed; source retained for recovery")
        }
    }

    // region writes (Room row-level accumulate)

    suspend fun recordAppTrafficBatch(timestamp: Long, records: List<AppTrafficDeltaRecord>) {
        legacyImport.await()
        if (records.isEmpty()) return

        val dayKey = startOfDay(timestamp)
        val appDeltas = ArrayList<AppTrafficDelta>(records.size)
        val routeDeltas = ArrayList<RouteTrafficDelta>(records.size)

        records.forEach { record ->
            if (record.uploadDelta <= 0L && record.downloadDelta <= 0L) return@forEach

            appDeltas +=
                AppTrafficDelta(
                    dateMillis = dayKey,
                    appKey = record.appKey,
                    packageName = record.packageName,
                    appName = record.appName,
                    uploadDelta = record.uploadDelta,
                    downloadDelta = record.downloadDelta,
                    lastActiveAt = timestamp,
                )
            routeDeltas +=
                RouteTrafficDelta(
                    dateMillis = dayKey,
                    appKey = record.appKey,
                    routeKey =
                        record.routeKey?.takeIf(String::isNotBlank)
                            ?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_KEY,
                    routeLabel =
                        record.routeLabel?.takeIf(String::isNotBlank)
                            ?: TrafficStatisticsBuckets.UNATTRIBUTED_ROUTE_NAME,
                    uploadDelta = record.uploadDelta,
                    downloadDelta = record.downloadDelta,
                    lastActiveAt = timestamp,
                )
        }

        if (appDeltas.isEmpty()) return

        dao.recordBatch(
            hourStartMillis = startOfHour(timestamp),
            hourlyUploadDelta = appDeltas.sumOf(AppTrafficDelta::uploadDelta),
            hourlyDownloadDelta = appDeltas.sumOf(AppTrafficDelta::downloadDelta),
            appDeltas = appDeltas,
            routeDeltas = routeDeltas,
            retentionCutoffMillis = now() - (MAX_APP_DAYS_TO_KEEP * DAY_MS),
        )
    }

    // endregion

    // region reads (SQL aggregation)

    fun getAppUsagesFlow(range: StatisticsTimeRange): Flow<List<AppTrafficUsage>> =
        flow { legacyImport.await(); emitAll(dao.getAppUsagesFlow(rangeCutoff(range))) }

    fun getDailyTotalsFlow(range: StatisticsTimeRange): Flow<List<DailyTrafficSummary>> =
        flow { legacyImport.await(); emitAll(dao.getDailyTotalsFlow(rangeCutoff(range))) }

    fun getTodayHourlyTotalsFlow(): Flow<List<HourlyTrafficSummary>> =
        dao.getHourlyTotalsFlow(startOfDay(now()))

    suspend fun getAppUsagesSorted(range: StatisticsTimeRange): List<AppTrafficUsage> =
        legacyImport.await().let { dao.getAppUsagesSorted(rangeCutoff(range)) }

    suspend fun getAppRouteUsages(
        appKey: String,
        range: StatisticsTimeRange,
    ): List<AppRouteTrafficUsage> {
        legacyImport.await()
        if (appKey.isBlank()) return emptyList()
        return dao.getAppRouteUsages(appKey, rangeCutoff(range))
    }

    // endregion

    // region clear

    suspend fun clearAll() {
        legacyImport.await()
        dao.clearAll()
        mmkv.removeValueForKey(KEY_DAILY_APP_SUMMARIES)
        mmkv.removeValueForKey(KEY_DAILY_ROUTE_SUMMARIES)
        mmkv.removeValueForKey(KEY_LAST_TRAFFIC_UPLOAD)
        mmkv.removeValueForKey(KEY_LAST_TRAFFIC_DOWNLOAD)
        mmkv.removeValueForKey(KEY_LAST_PROFILE_ID)
    }

    // endregion

    // region resume baseline (synchronous MMKV scalars)

    fun getLastTrafficUpload(): Long =
        mmkv.decodeLong(KEY_LAST_TRAFFIC_UPLOAD, NO_PERSISTED_TRAFFIC)

    fun getLastTrafficDownload(): Long =
        mmkv.decodeLong(KEY_LAST_TRAFFIC_DOWNLOAD, NO_PERSISTED_TRAFFIC)

    fun getLastProfileId(): String? = mmkv.decodeString(KEY_LAST_PROFILE_ID)

    /**
     * Persists the resume baseline scalars. [forcePersist] is retained for source compatibility
     * with the collector but no longer schedules a flush — Room/MMKV writes are already immediate.
     */
    fun setLastTraffic(
        upload: Long,
        download: Long,
        profileId: String?,
        @Suppress("UNUSED_PARAMETER") forcePersist: Boolean = false,
    ) {
        mmkv.encode(KEY_LAST_TRAFFIC_UPLOAD, upload)
        mmkv.encode(KEY_LAST_TRAFFIC_DOWNLOAD, download)
        if (profileId.isNullOrBlank()) {
            mmkv.removeValueForKey(KEY_LAST_PROFILE_ID)
        } else {
            mmkv.encode(KEY_LAST_PROFILE_ID, profileId)
        }
    }

    /** No-op: writes are now immediate (Room row-level upsert + synchronous MMKV scalars). */
    fun flushNow() = Unit

    // endregion

    // region helpers

    private fun startOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun startOfHour(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun rangeCutoff(range: StatisticsTimeRange): Long =
        startOfDay(now()) - ((range.days - 1) * DAY_MS)

    // endregion

    companion object {
        private const val KEY_DAILY_APP_SUMMARIES = "daily_app_summaries_v2"
        private const val KEY_DAILY_ROUTE_SUMMARIES = "daily_route_summaries_v2"
        private const val KEY_LAST_TRAFFIC_UPLOAD = "last_traffic_upload_v2"
        private const val KEY_LAST_TRAFFIC_DOWNLOAD = "last_traffic_download_v2"
        private const val KEY_LAST_PROFILE_ID = "last_profile_id_v2"
        private const val KEY_STATS_SCHEMA_VERSION = "traffic_stats_schema_version"
        private const val LEGACY_KEY_DAILY_SUMMARIES = "daily_summaries"
        private const val LEGACY_KEY_PROFILE_USAGES = "profile_usages"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val MAX_APP_DAYS_TO_KEEP = 90
        private const val NO_PERSISTED_TRAFFIC = -1L
    }
}
