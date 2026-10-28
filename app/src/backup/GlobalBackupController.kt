package com.github.yumelira.yumebox.backup

import android.content.Context
import android.net.Uri
import com.github.yumelira.yumebox.BuildInfo
import com.github.yumelira.yumebox.core.util.YamlCodec
import com.github.yumelira.yumebox.data.controller.UserSettingsBackupController
import com.github.yumelira.yumebox.data.model.MetadataIndex
import com.github.yumelira.yumebox.data.store.OverrideConfigStore
import com.github.yumelira.yumebox.data.store.ProfileBindingStore
import com.github.yumelira.yumebox.data.store.TrafficStatisticsStore
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.data.store.FeatureStore
import com.github.yumelira.yumebox.substore.SubStoreService
import com.github.yumelira.yumebox.substore.SubStoreServiceController
import com.github.yumelira.yumebox.substore.SubStoreServiceRequest
import com.github.yumelira.yumebox.data.store.room.TrafficStatisticsBackup
import com.github.yumelira.yumebox.runtime.api.Intents
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.runtime.service.config.ServiceStore
import com.github.yumelira.yumebox.runtime.service.profile.Imported
import com.github.yumelira.yumebox.runtime.service.profile.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
private data class BackupEntry(val path: String, val size: Long, val sha256: String)
@Serializable
private data class BackupManifest(
    val format: String = "YumeBoxGlobalBackup",
    val version: Int = 1,
    val appVersion: String = BuildInfo.BASE_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val profileCount: Int,
    val overrideCount: Int,
    val entries: List<BackupEntry>,
)
@Serializable
private data class ProfileBackup(val records: List<Imported>, val order: List<String>, val active: String?)

data class PreparedGlobalBackup internal constructor(
    val directory: File,
    val profileCount: Int,
    val overrideCount: Int,
    val legacySettingsOnly: Boolean,
)

/** Portable logical metadata + original files. No APKs, native binaries, caches or OS grants. */
class GlobalBackupController(
    private val context: Context,
    private val settings: UserSettingsBackupController,
    private val overrides: OverrideConfigStore,
    private val bindings: ProfileBindingStore,
    private val statistics: TrafficStatisticsStore,
    private val proxy: ProxyFacade,
    private val network: NetworkSettingsStore,
    private val features: FeatureStore,
) {
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val roots = listOf("imported", "overrides", "acg_wallpaper", "acg", "Sub-Store/data", "logs", "profileInstalled")
    private val globalFiles = setOf("settings.json", "profiles.json", "statistics.json", "updates.json")

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        lock.withLock {
            val stage = withSubStorePaused { snapshot() }
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    ZipOutputStream(output.buffered()).use { zip ->
                        stage.walkTopDown().filter(File::isFile).forEach { file ->
                            zip.putNextEntry(ZipEntry(file.relativeTo(stage).invariantSeparatorsPath))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: error("无法写入全局备份")
            } finally { stage.deleteRecursively() }
        }
    }

    suspend fun prepareImport(uri: Uri): PreparedGlobalBackup = withContext(Dispatchers.IO) {
        lock.withLock {
            val stage = staging()
            try {
                val source = context.contentResolver.openInputStream(uri)?.buffered() ?: error("无法读取备份")
                source.use { input ->
                    input.mark(4)
                    val first = input.read(); val second = input.read()
                    input.reset()
                    if (first != 'P'.code || second != 'K'.code) {
                        val legacy = input.readBytesLimited(64L * 1024 * 1024).toString(Charsets.UTF_8)
                        settings.validateBackup(legacy)
                        File(stage, "settings.json").writeText(legacy)
                        return@withLock PreparedGlobalBackup(stage, 0, 0, true)
                    }
                    ZipInputStream(input).use { zip ->
                        val seen = mutableSetOf<String>()
                        var total = 0L
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            require(!entry.isDirectory && seen.add(entry.name) && seen.size <= 20000) { "备份文件条目无效或重复" }
                            require(entry.name == "manifest.json" || allowed(entry.name)) { "备份包含不支持的路径" }
                            val target = safeResolve(stage, entry.name)
                            target.parentFile!!.mkdirs()
                            target.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var length = 0L
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    length += count; total += count
                                    require(length <= 512L * 1024 * 1024 && total <= 2L * 1024 * 1024 * 1024) { "备份体积过大" }
                                    output.write(buffer, 0, count)
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                }
                val manifest = validate(stage)
                PreparedGlobalBackup(stage, manifest.profileCount, manifest.overrideCount, false)
            } catch (error: Throwable) {
                stage.deleteRecursively()
                throw error
            }
        }
    }

    suspend fun restore(prepared: PreparedGlobalBackup) = withContext(Dispatchers.IO + NonCancellable) {
        lock.withLock {
            require(prepared.directory.canonicalFile.parentFile == context.cacheDir.canonicalFile &&
                prepared.directory.name.startsWith("global-backup-")) { "备份准备状态无效" }
            if (prepared.legacySettingsOnly) {
                settings.importFromJson(File(prepared.directory, "settings.json").readText())
                return@withLock
            }
            validate(prepared.directory)
            val wasRunning = proxy.runtimeSnapshot.value.running
            var previous: File? = null
            var retainRecovery = false
            try {
                if (wasRunning) proxy.stopProxy()
                withSubStorePaused {
                    previous = snapshot()
                    try { apply(prepared.directory) }
                    catch (error: Throwable) {
                        try { previous?.let { apply(it) } } catch (rollback: Throwable) {
                            retainRecovery = true
                            error.addSuppressed(rollback)
                            android.util.Log.e("GlobalBackup", "Rollback failed; recovery snapshot retained at ${previous?.name}", rollback)
                        }
                        throw error
                    }
                }
            } finally {
                if (!retainRecovery) previous?.deleteRecursively()
                if (wasRunning) {
                    runCatching { proxy.startProxy(network.runMode.value) }
                        .onFailure { android.util.Log.w("GlobalBackup", "Unable to resume proxy after restore", it) }
                }
            }
        }
    }

    fun discard(prepared: PreparedGlobalBackup) {
        val directory = prepared.directory.canonicalFile
        if (directory.parentFile == context.cacheDir.canonicalFile && directory.name.startsWith("global-backup-")) directory.deleteRecursively()
    }

    private suspend fun <T> withSubStorePaused(block: suspend () -> T): T {
        val active = SubStoreServiceController.snapshot.value.isActive
        if (active) {
            withContext(Dispatchers.Main) { context.stopService(android.content.Intent(context, SubStoreService::class.java)) }
            withTimeout(10_000) { SubStoreServiceController.snapshot.first { !it.isActive } }
        }
        try { return block() }
        finally {
            if (active) withContext(Dispatchers.Main + NonCancellable) {
                SubStoreServiceController.startService(context, SubStoreServiceRequest(
                    frontendPort = features.frontendPort.value,
                    backendPort = features.backendPort.value,
                    allowLan = features.allowLanAccess.value,
                    autoCloseMinutes = com.github.yumelira.yumebox.substore.model.AutoCloseMode.entries
                        .getOrNull(features.subStoreAutoCloseMode.value)?.minutes,
                ))
            }
        }
    }

    private suspend fun snapshot(): File {
        val stage = staging()
        try {
            File(stage, "settings.json").writeText(settings.exportToJson())
            val profiles = ProfileBackup(ProfileStore.loadImported(), ProfileStore.loadProfileOrder().map(UUID::toString), ServiceStore().activeProfile?.toString())
            File(stage, "profiles.json").writeText(json.encodeToString(ProfileBackup.serializer(), profiles))
            File(stage, "statistics.json").writeText(json.encodeToString(TrafficStatisticsBackup.serializer(), statistics.exportBackup()))
            val preferences = context.getSharedPreferences("release_updates", Context.MODE_PRIVATE)
            File(stage, "updates.json").writeText(buildJsonObject {
                preferences.all.forEach { (key, value) -> when (value) {
                    is Boolean -> put(key, value)
                    is Long -> put(key, value)
                    is Int -> put(key, value)
                    is String -> put(key, value)
                } }
            }.toString())
            roots.forEach { root -> copyUserTree(File(context.filesDir, root), File(stage, "files/$root")) }
            val geo = File(context.filesDir, "mihomo")
            geo.listFiles().orEmpty().filter { it.isFile && it.extension in setOf("mmdb", "metadb", "dat", "properties") }
                .forEach { file -> file.copyTo(File(stage, "files/mihomo/${file.name}").apply { parentFile!!.mkdirs() }) }
            require(ProfileStore.loadImported() == profiles.records) { "配置正在更新，请稍后重新备份" }
            val entries = stage.walkTopDown().filter(File::isFile).map { file ->
                BackupEntry(file.relativeTo(stage).invariantSeparatorsPath, file.length(), digest(file))
            }.toList()
            require(entries.size <= 20000 && entries.all { it.size <= 512L * 1024 * 1024 } && entries.sumOf { it.size } <= 2L * 1024 * 1024 * 1024) { "备份体积过大" }
            val count = overrides.getUserConfigs().size
            File(stage, "manifest.json").writeText(json.encodeToString(BackupManifest.serializer(), BackupManifest(
                profileCount = profiles.records.size, overrideCount = count, entries = entries,
            )))
            return stage
        } catch (error: Throwable) { stage.deleteRecursively(); throw error }
    }

    private fun validate(stage: File): BackupManifest {
        val manifestFile = File(stage, "manifest.json")
        require(manifestFile.isFile && manifestFile.length() <= 8 * 1024 * 1024) { "缺少有效的全局备份清单" }
        val manifest = json.decodeFromString(BackupManifest.serializer(), manifestFile.readText())
        require(manifest.format == "YumeBoxGlobalBackup" && manifest.version == 1) { "不支持的全局备份版本" }
        require(manifest.entries.map { it.path }.toSet().size == manifest.entries.size) { "备份清单含重复条目" }
        val actual = stage.walkTopDown().filter { it.isFile }.map { it.relativeTo(stage).invariantSeparatorsPath }.filter { it != "manifest.json" }.toSet()
        require(actual == manifest.entries.map { it.path }.toSet() && actual.containsAll(globalFiles)) { "备份文件不完整" }
        manifest.entries.forEach { entry ->
            require(allowed(entry.path)) { "备份路径无效" }
            val file = safeResolve(stage, entry.path)
            require(file.isFile && file.length() == entry.size && digest(file) == entry.sha256) { "备份校验失败：${entry.path}" }
        }
        settings.validateBackup(File(stage, "settings.json").readText())
        val profiles = json.decodeFromString(ProfileBackup.serializer(), File(stage, "profiles.json").readText())
        require(profiles.records.map { it.uuid }.toSet().size == profiles.records.size && profiles.records.size == manifest.profileCount) { "配置列表无效" }
        profiles.order.forEach(UUID::fromString)
        profiles.active?.let { active -> require(profiles.records.any { it.uuid.toString() == active }) { "活动配置缺失" } }
        val stats = json.decodeFromString(TrafficStatisticsBackup.serializer(), File(stage, "statistics.json").readText())
        require(stats.apps.map { it.dateMillis to it.appKey }.toSet().size == stats.apps.size &&
            stats.routes.map { Triple(it.dateMillis, it.appKey, it.routeKey) }.toSet().size == stats.routes.size &&
            stats.hours.map { it.hourStartMillis }.toSet().size == stats.hours.size &&
            stats.apps.all { it.totalUpload >= 0 && it.totalDownload >= 0 } &&
            stats.routes.all { it.totalUpload >= 0 && it.totalDownload >= 0 } &&
            stats.hours.all { it.totalUpload >= 0 && it.totalDownload >= 0 }) { "流量统计记录无效" }
        json.parseToJsonElement(File(stage, "updates.json").readText()).jsonObject
        File(stage, "files/overrides/metadata.yaml").takeIf(File::isFile)?.let { YamlCodec.decode(MetadataIndex.serializer(), it.readText()) }
        return manifest
    }

    private suspend fun apply(stage: File) {
        val profiles = json.decodeFromString(ProfileBackup.serializer(), File(stage, "profiles.json").readText())
        roots.forEach { root ->
            val target = safeResolve(context.filesDir, root)
            check(target.deleteRecursively()) { "无法替换备份目录：$root" }
            copyUserTree(File(stage, "files/$root"), target)
        }
        File(context.filesDir, "mihomo").listFiles().orEmpty()
            .filter { it.isFile && it.extension in setOf("mmdb", "metadb", "dat", "properties") }
            .forEach { check(it.delete()) { "无法替换 Geo 数据：${it.name}" } }
        File(stage, "files/mihomo").listFiles().orEmpty().forEach { source ->
            source.copyTo(safeResolve(context.filesDir, "mihomo/${source.name}").apply { parentFile!!.mkdirs() }, overwrite = true)
        }
        ProfileStore.saveImported(profiles.records)
        ProfileStore.saveProfileOrder(profiles.order.map(UUID::fromString))
        ServiceStore().activeProfile = profiles.active?.let(UUID::fromString)
        settings.importFromJson(File(stage, "settings.json").readText())
        statistics.restoreBackup(json.decodeFromString(TrafficStatisticsBackup.serializer(), File(stage, "statistics.json").readText()))
        val preferences = context.getSharedPreferences("release_updates", Context.MODE_PRIVATE).edit().clear()
        json.parseToJsonElement(File(stage, "updates.json").readText()).jsonObject.forEach { (key, value) ->
            val primitive = value.jsonPrimitive
            when {
                primitive.isString -> preferences.putString(key, primitive.content)
                primitive.booleanOrNull != null -> preferences.putBoolean(key, primitive.boolean)
                primitive.longOrNull != null -> preferences.putLong(key, primitive.long)
            }
        }
        check(preferences.commit()) { "恢复更新设置失败" }
        bindings.reloadFromDisk()
        overrides.reloadFromDisk()
        context.sendBroadcast(android.content.Intent(Intents.ACTION_PROFILE_CHANGED).setPackage(context.packageName))
    }

    private fun allowed(path: String): Boolean = path in globalFiles || roots.any { path == "files/$it" || path.startsWith("files/$it/") } ||
        (path.startsWith("files/mihomo/") && path.removePrefix("files/mihomo/").let { '/' !in it && it.substringAfterLast('.') in setOf("mmdb", "metadb", "dat", "properties") })

    private fun safeResolve(root: File, path: String): File {
        require(!path.startsWith('/') && '\\' !in path && ':' !in path && path.split('/').none { it == ".." || it.isEmpty() }) { "备份路径无效" }
        return File(root, path).canonicalFile.also { require(it.toPath().startsWith(root.canonicalFile.toPath()) && it != root.canonicalFile) { "备份路径越界" } }
    }
    private fun staging(): File = File(context.cacheDir, "global-backup-${UUID.randomUUID()}").apply { check(mkdirs()) }
    private fun copyUserTree(source: File, target: File) {
        if (!source.exists()) return
        require(!java.nio.file.Files.isSymbolicLink(source.toPath())) { "备份不支持符号链接" }
        if (source.isFile) {
            target.parentFile!!.mkdirs()
            source.copyTo(target, overwrite = true)
            return
        }
        source.walkTopDown().onEnter { directory ->
            require(!java.nio.file.Files.isSymbolicLink(directory.toPath())) { "备份不支持符号链接" }
            true
        }.filter(File::isFile).forEach { file ->
            require(!java.nio.file.Files.isSymbolicLink(file.toPath())) { "备份不支持符号链接" }
            val destination = safeResolve(target, file.relativeTo(source).invariantSeparatorsPath)
            destination.parentFile!!.mkdirs()
            file.copyTo(destination, overwrite = true)
        }
    }
    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun java.io.InputStream.readBytesLimited(limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024); var total = 0L
        while (true) { val count = read(buffer); if (count < 0) break; total += count; require(total <= limit) { "备份体积过大" }; output.write(buffer, 0, count) }
        return output.toByteArray()
    }
}
