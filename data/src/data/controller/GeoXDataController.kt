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

@file:Suppress("SpellCheckingInspection")

/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumelira.yumebox.data.controller

import android.content.Context
import android.net.Uri
import com.github.yumelira.yumebox.core.model.GeoFileType
import com.github.yumelira.yumebox.core.util.runtimeHomeDir
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

private const val MIN_GEO_FILE_SIZE_BYTES = 64 * 1024L
private const val GEO_UPDATE_RECORD_FILE = "geo-update.properties"
private val MANAGED_GEO_FILES = listOf("geoip.metadb", "geosite.dat", "country.mmdb", "ASN.mmdb")
private val GEO_FILE_TYPE_BY_NAME = mapOf(
    "geoip.metadb" to GeoFileType.GeoIP,
    "geosite.dat" to GeoFileType.GeoSite,
    "ASN.mmdb" to GeoFileType.ASN,
    "country.mmdb" to GeoFileType.Country,
)

enum class GeoXUpdateSource {
    Online,
    Local,
}

data class GeoXUpdateRecord(
    val timestamp: Long,
    val source: GeoXUpdateSource,
)

data class GeoXCacheEntry(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

class GeoXDataController(
    private val context: Context,
    private val validateGeo: (File, GeoFileType) -> com.github.yumelira.yumebox.core.model.GeoValidationResult,
) {
    private val runtimeHome: File get() = context.runtimeHomeDir
    private val fallbackDir: File get() = runtimeHome.resolve("geo-fallback")
    private val fallbackOldDir: File get() = runtimeHome.resolve("geo-fallback-old")
    private val cacheDir: File get() = runtimeHome.resolve("geo-cache")
    private val updateRecordFile: File get() = runtimeHome.resolve(GEO_UPDATE_RECORD_FILE)

    fun ensureGeoFiles() {
        runtimeHome.mkdirs()
        fallbackDir.mkdirs()
        fallbackOldDir.mkdirs()
        cacheDir.mkdirs()

        MANAGED_GEO_FILES.forEach { filename ->
            ensureBundledOldFallback(filename)
            val active = runtimeHome.resolve(filename)
            if (!isGeoFileUsable(active, filename, deep = false)) {
                restoreBestFallback(filename)
            }
        }
        cleanupBrokenTemporaryFiles()
    }

    fun promoteDownloadedGeoFile(filename: String): Boolean {
        val active = runtimeHome.resolve(filename)
        if (!isGeoFileUsable(active, filename, deep = true)) {
            active.delete()
            restoreBestFallback(filename)
            return false
        }

        return promoteGeoFile(active, filename, GeoXUpdateSource.Online)
    }

    fun importGeoFile(filename: String, sourceUri: Uri): Boolean {
        if (filename !in MANAGED_GEO_FILES) return false
        runtimeHome.mkdirs()
        val tempFile = File(runtimeHome, "$filename.${System.currentTimeMillis()}.import.tmp")
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: return false
            if (!isGeoFileUsable(tempFile, filename, deep = true)) {
                tempFile.delete()
                return false
            }
            promoteGeoFile(tempFile, filename, GeoXUpdateSource.Local)
        } catch (error: Exception) {
            tempFile.delete()
            Timber.w(error, "Failed to import geo file: %s", filename)
            false
        }
    }

    private fun promoteGeoFile(source: File, filename: String, updateSource: GeoXUpdateSource): Boolean {
        if (!isGeoFileUsable(source, filename, deep = true)) {
            source.delete()
            restoreBestFallback(filename)
            return false
        }

        val active = runtimeHome.resolve(filename)
        if (source.canonicalPath != active.canonicalPath) {
            replaceFile(source, active)
        }

        fallbackDir.mkdirs()
        cacheDir.mkdirs()
        ensureBundledOldFallback(filename)

        val currentFallback = fallbackDir.resolve(filename)
        if (isGeoFileUsable(currentFallback, filename, deep = false) && !sameContent(currentFallback, active)) {
            moveToHistoryCache(currentFallback, filename)
        } else if (currentFallback.exists()) {
            currentFallback.delete()
        }

        copyFile(active, currentFallback)
        recordGeoFileUpdate(filename, updateSource)
        return true
    }

    fun getGeoFileUpdateRecords(): Map<String, GeoXUpdateRecord> {
        val records = loadUpdateRecords()
        return MANAGED_GEO_FILES.mapNotNull { filename ->
            val timestamp = records.getProperty(filename)?.toLongOrNull()?.takeIf { it > 0L } ?: return@mapNotNull null
            val source = records.getProperty("$filename.source")
                ?.let { value -> GeoXUpdateSource.entries.firstOrNull { it.name == value } }
                ?: GeoXUpdateSource.Online
            filename to GeoXUpdateRecord(timestamp, source)
        }.toMap()
    }

    fun getGeoFileUpdateTimes(): Map<String, Long> {
        return getGeoFileUpdateRecords().mapValues { it.value.timestamp }
    }

    fun listHistoryCache(): List<GeoXCacheEntry> {
        return cacheDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                GeoXCacheEntry(
                    name = it.name,
                    path = it.absolutePath,
                    sizeBytes = it.length(),
                    lastModified = it.lastModified(),
                )
            }
            .orEmpty()
    }

    fun deleteHistoryCache(paths: Collection<String>): Int {
        return paths.count { path ->
            val file = File(path)
            isInside(file, cacheDir) && file.isFile && file.delete()
        }
    }

    fun restoreBestFallback(filename: String): Boolean {
        val active = runtimeHome.resolve(filename)
        val candidates = listOf(
            fallbackDir.resolve(filename),
            fallbackOldDir.resolve(filename),
        )
        candidates.firstOrNull { isGeoFileUsable(it, filename, deep = false) }?.let { source ->
            copyFile(source, active)
            return true
        }
        return extractBundledGeoFile(filename, active).also { restored ->
            if (restored) ensureBundledOldFallback(filename)
        }
    }

    fun isGeoFileUsable(file: File, filename: String, deep: Boolean): Boolean {
        if (!isBasicGeoFileUsable(file)) return false
        if (!deep) return true
        val type = GEO_FILE_TYPE_BY_NAME[filename] ?: return true
        return runCatching {
            val result = validateGeo(file, type)
            if (!result.valid) {
                Timber.w(
                    "Geo file validation failed: %s, path=%s, size=%d, message=%s",
                    filename,
                    file.absolutePath,
                    file.length(),
                    result.message,
                )
            }
            result.valid
        }.onFailure {
            Timber.w(it, "Native Geo validation failed, fallback basic check used: %s", filename)
        }.getOrDefault(true)
    }

    private fun ensureBundledOldFallback(filename: String) {
        val old = fallbackOldDir.resolve(filename)
        if (isGeoFileUsable(old, filename, deep = false)) return
        fallbackOldDir.mkdirs()
        extractBundledGeoFile(filename, old)
    }

    private fun isBasicGeoFileUsable(file: File): Boolean {
        if (!file.isFile || file.length() < MIN_GEO_FILE_SIZE_BYTES) return false
        return !looksLikeHttpErrorBody(file)
    }

    private fun looksLikeHttpErrorBody(file: File): Boolean {
        return runCatching {
            val buffer = ByteArray(512)
            val read = file.inputStream().buffered().use { input -> input.read(buffer) }
            if (read <= 0) return@runCatching true

            val head = String(buffer, 0, read, Charsets.UTF_8)
                .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
                .lowercase()
            head.startsWith("<!doctype") ||
                head.startsWith("<html") ||
                head.startsWith("not found") ||
                head.startsWith("404") ||
                head.contains("<title>404") ||
                head.contains("rate limit")
        }.getOrDefault(false)
    }

    private fun extractBundledGeoFile(filename: String, targetFile: File): Boolean {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.asset.tmp")
        return try {
            targetFile.parentFile?.mkdirs()
            if (tempFile.exists()) tempFile.delete()
            val extracted = extractCompressedAssetIfExists("$filename.xz", tempFile) ||
                extractRawAssetIfExists(filename, tempFile)
            if (!extracted || !isBasicGeoFileUsable(tempFile)) {
                tempFile.delete()
                return false
            }
            replaceFile(tempFile, targetFile)
            true
        } catch (error: Exception) {
            tempFile.delete()
            Timber.w(error, "Failed to extract bundled geo file: %s", filename)
            false
        }
    }

    private fun extractRawAssetIfExists(assetName: String, targetFile: File): Boolean {
        return try {
            context.assets.open(assetName).use { input ->
                targetFile.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun extractCompressedAssetIfExists(assetName: String, targetFile: File): Boolean {
        return try {
            context.assets.open(assetName).use { input ->
                org.tukaani.xz.XZInputStream(input.buffered()).use { xzInput ->
                    targetFile.outputStream().buffered().use { output -> xzInput.copyTo(output) }
                }
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun moveToHistoryCache(file: File, filename: String) {
        val safeTimestamp = System.currentTimeMillis()
        val cacheFile = cacheDir.resolve("$filename.$safeTimestamp")
        replaceFile(file, cacheFile)
    }

    private fun recordGeoFileUpdate(filename: String, source: GeoXUpdateSource) {
        runCatching {
            runtimeHome.mkdirs()
            val records = loadUpdateRecords()
            records.setProperty(filename, System.currentTimeMillis().toString())
            records.setProperty("$filename.source", source.name)
            val tempFile = File(runtimeHome, "$GEO_UPDATE_RECORD_FILE.${System.currentTimeMillis()}.tmp")
            tempFile.outputStream().buffered().use { output ->
                records.store(output, null)
            }
            replaceFile(tempFile, updateRecordFile)
        }.onFailure {
            Timber.w(it, "Failed to record geo file update: %s", filename)
        }
    }

    private fun loadUpdateRecords(): Properties {
        val records = Properties()
        if (updateRecordFile.isFile) {
            runCatching {
                updateRecordFile.inputStream().buffered().use(records::load)
            }.onFailure {
                Timber.w(it, "Failed to load geo file update records")
            }
        }
        return records
    }

    private fun copyFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            val temp = File(target.parentFile, "${target.name}.${System.currentTimeMillis()}.copy.tmp")
            temp.outputStream().use { output -> input.copyTo(output) }
            replaceFile(temp, target)
        }
    }

    private fun replaceFile(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sameContent(first: File, second: File): Boolean {
        return first.isFile && second.isFile && first.length() == second.length()
    }

    private fun cleanupBrokenTemporaryFiles() {
        runtimeHome.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".download") || file.name.endsWith(".import.tmp") || file.name.endsWith(".asset.tmp") || file.name.endsWith(".copy.tmp"))) {
                file.delete()
            }
        }
    }

    private fun isInside(file: File, parent: File): Boolean {
        return runCatching {
            val canonicalFile = file.canonicalFile
            val canonicalParent = parent.canonicalFile
            canonicalFile.path.startsWith(canonicalParent.path + File.separator)
        }.getOrDefault(false)
    }
}
