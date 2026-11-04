package com.github.yumelira.yumebox.screen.acg

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import com.github.yumelira.yumebox.R
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.presentation.theme.DEFAULT_ACG_WALLPAPER_THEME_SEED_ARGB
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

enum class WallpaperProfile(val key: String, val defaultApi: String, val defaultWallpaper: Int) {
    Portrait("portrait", "https://www.loliapi.com/acg/pe/", R.drawable.wallpaper_mobile),
    Tablet("tablet", "https://www.loliapi.com/acg/pc/", R.drawable.wallpaper_tablet),
}

data class WallpaperCrop(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f)

internal fun defaultWallpaperCrop(profile: WallpaperProfile) =
    WallpaperCrop(x = if (profile == WallpaperProfile.Portrait) -1f else 0f)

data class WallpaperOptions(
    val apiEnabled: Boolean = true,
    val apiUrl: String,
    val poolEnabled: Boolean = false,
    val pool: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val current: String = "",
    val day: String = "",
    val staticImage: String = "",
    val zoom: Float = 1f,
    val biasX: Float = 0f,
    val biasY: Float = 0f,
    val samplesInitialized: Boolean = false,
    val crops: Map<String, WallpaperCrop> = emptyMap(),
)

/** Files use paths relative to one backed-up directory, so restoration is portable. */
class AcgWallpaperManager(private val context: Context, val settings: AppSettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root get() = File(context.filesDir, "acg_wallpapers")
    private val stateLock = Any()
    private var sampleCropsInitialized = false
    private val refreshLocks = WallpaperProfile.entries.associateWith { Mutex() }
    private val _options = MutableStateFlow(readOptions())
    val options: StateFlow<Map<WallpaperProfile, WallpaperOptions>> = _options.asStateFlow()
    private val _refreshing = MutableStateFlow<Set<WallpaperProfile>>(emptySet())
    val refreshing = _refreshing.asStateFlow()
    private val _activeProfile = MutableStateFlow(WallpaperProfile.Portrait)
    val activeProfile = _activeProfile.asStateFlow()
    private val _displayedSources = MutableStateFlow<Map<WallpaperProfile, String>>(emptyMap())
    val displayedSources = _displayedSources.asStateFlow()

    fun reportDisplayedSource(profile: WallpaperProfile, source: String) {
        _displayedSources.update { if (it[profile] == source) it else it + (profile to source) }
    }
    private val _heroFrames = MutableStateFlow<Map<AcgFrameKey, androidx.compose.ui.unit.IntSize>>(emptyMap())
    internal val heroFrames = _heroFrames.asStateFlow()

    fun reportActiveProfile(profile: WallpaperProfile) { _activeProfile.value = profile }

    internal fun reportHeroFrame(key: AcgFrameKey, size: androidx.compose.ui.unit.IntSize) {
        if (size.width > 0 && size.height > 0) _heroFrames.update {
            if (it[key] == size) it else (it - key + (key to size)).entries.toList().takeLast(12).associate { entry ->
                entry.key to entry.value
            }
        }
    }
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()

    init {
        scope.launch {
            settings.acgWallpaperConfigJson.state.collect {
                synchronized(stateLock) { _options.value = readOptions() }
                try { initializeSamples() }
                catch (error: Exception) { android.util.Log.w("AcgWallpaper", "Unable to initialize wallpaper samples", error) }
            }
        }
    }

    private fun bundledSample(profile: WallpaperProfile): String =
        context.resources.openRawResource(profile.defaultWallpaper).use { saveImage(profile, "pool", it) }

    private fun initializeSamples() = synchronized(stateLock) {
        WallpaperProfile.entries.forEach { profile ->
            val old = readOptions().getValue(profile)
            if (!old.samplesInitialized) {
                val pool = old.pool.ifEmpty { listOf(bundledSample(profile)) }
                val newSample = if (old.pool.isEmpty()) pool.firstOrNull() else null
                update(profile) {
                    it.copy(pool = pool, samplesInitialized = true,
                        crops = if (newSample != null) it.crops + (newSample to defaultWallpaperCrop(profile)) else it.crops)
                }
            }
            if (!sampleCropsInitialized && profile == WallpaperProfile.Portrait) {
                val current = readOptions().getValue(profile)
                // Older installs already have the bundled pool sample under a generated name.
                // Preserve any explicit crop; only initialize untouched copies of the default art.
                val bytes = context.resources.openRawResource(profile.defaultWallpaper).use { it.readBytes() }
                val untouched = current.pool.filter { path ->
                    path !in current.crops && file(path)?.let {
                        it.isFile && it.length() == bytes.size.toLong() && it.readBytes().contentEquals(bytes)
                    } == true
                }
                if (untouched.isNotEmpty()) update(profile) {
                    it.copy(crops = it.crops + untouched.associateWith { defaultWallpaperCrop(profile) })
                }
            }
        }
        sampleCropsInitialized = true
    }

    private fun readOptions(): Map<WallpaperProfile, WallpaperOptions> {
        val json = runCatching { JSONObject(settings.acgWallpaperConfigJson.value) }.getOrDefault(JSONObject())
        return WallpaperProfile.entries.associateWith { profile ->
            val obj = json.optJSONObject(profile.key) ?: JSONObject()
            fun paths(key: String): List<String> = obj.optJSONArray(key)?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(::validPath) }.distinct().take(100)
            }.orEmpty()
            val pool = paths("pool")
            val history = paths("history").take(10)
            val crops = obj.optJSONObject("crops")
            WallpaperOptions(
                apiEnabled = obj.optBoolean("apiEnabled", true), apiUrl = obj.optString("apiUrl", profile.defaultApi),
                poolEnabled = obj.optBoolean("poolEnabled", false), pool = pool, history = history,
                current = obj.optString("current"), day = obj.optString("day"), staticImage = obj.optString("staticImage"),
                zoom = obj.optDouble("zoom", 1.0).toFloat().takeIf { it.isFinite() }?.coerceIn(1f, 5f) ?: 1f,
                biasX = obj.optDouble("biasX", 0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f,
                biasY = obj.optDouble("biasY", 0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f,
                samplesInitialized = obj.optBoolean("samplesInitialized", false),
                crops = (pool + history).mapNotNull { path ->
                    crops?.optJSONObject(path)?.let { crop ->
                        fun finite(key: String, default: Float) =
                            crop.optDouble(key, default.toDouble()).toFloat().takeIf(Float::isFinite) ?: default
                        path to WallpaperCrop(finite("zoom", 1f).coerceIn(1f, 5f),
                            finite("x", 0f).coerceIn(-1f, 1f), finite("y", 0f).coerceIn(-1f, 1f))
                    }
                }.toMap(),
            )
        }
    }

    private fun update(profile: WallpaperProfile, transform: (WallpaperOptions) -> WallpaperOptions) = synchronized(stateLock) {
        val values = readOptions().toMutableMap()
        values[profile] = transform(values.getValue(profile))
        val json = JSONObject()
        values.forEach { (key, option) ->
            json.put(key.key, JSONObject().apply {
                put("apiEnabled", option.apiEnabled); put("apiUrl", option.apiUrl); put("poolEnabled", option.poolEnabled)
                put("pool", JSONArray(option.pool)); put("history", JSONArray(option.history))
                put("current", option.current); put("day", option.day); put("staticImage", option.staticImage)
                put("zoom", option.zoom); put("biasX", option.biasX); put("biasY", option.biasY)
                put("samplesInitialized", option.samplesInitialized)
                put("crops", JSONObject().apply {
                    option.crops.forEach { (path, crop) ->
                        put(path, JSONObject().put("zoom", crop.zoom.toDouble())
                            .put("x", crop.x.toDouble()).put("y", crop.y.toDouble()))
                    }
                })
            })
        }
        settings.acgWallpaperConfigJson.set(json.toString())
        _options.value = values
    }

    fun setApi(profile: WallpaperProfile, enabled: Boolean? = null, url: String? = null) {
        if (url != null) require(validApiUrl(url)) { "请输入有效的HTTP或HTTPS图片API" }
        update(profile) { it.copy(apiEnabled = enabled ?: it.apiEnabled, apiUrl = url?.trim() ?: it.apiUrl, day = "") }
    }

    fun setPoolEnabled(profile: WallpaperProfile, enabled: Boolean) = update(profile) { it.copy(poolEnabled = enabled, day = "") }

    fun fileUri(path: String): String? = file(path)?.takeIf { it.isFile && it.length() > 0 }?.toUri()?.toString()

    fun displayUri(profile: WallpaperProfile, daily: Boolean = settings.acgDailyWallpaperEnabled.value): String {
        val option = options.value.getValue(profile)
        if (daily) fileUri(option.current)?.let { return it }
        fileUri(option.staticImage)?.let { return it }
        // Old single-wallpaper settings remain a fallback for upgrades and legacy JSON restores.
        if (profile == WallpaperProfile.Portrait) {
            settings.acgWallpaperUri.value.takeIf { it.isNotBlank() }?.let { return it }
        }
        return "android.resource://${context.packageName}/${profile.defaultWallpaper}"
    }

    fun setPoolCrop(profile: WallpaperProfile, path: String, crop: WallpaperCrop) {
        update(profile) {
            require((path in it.pool || path in it.history) && fileUri(path) != null)
            it.copy(crops = it.crops + (path to crop))
        }
    }

    suspend fun setStatic(profile: WallpaperProfile, uri: String, zoom: Float, x: Float, y: Float): String = withContext(Dispatchers.IO) {
        val saved = importImage(profile, "static", uri)
        val previous = options.value.getValue(profile).staticImage
        update(profile) { it.copy(staticImage = saved, zoom = zoom.coerceIn(1f, 5f), biasX = x.coerceIn(-1f, 1f), biasY = y.coerceIn(-1f, 1f)) }
        if (previous != saved) file(previous)?.delete()
        checkNotNull(fileUri(saved))
    }

    fun clearStatic(profile: WallpaperProfile) {
        val previous = options.value.getValue(profile).staticImage
        update(profile) { it.copy(staticImage = "", zoom = 1f, biasX = 0f, biasY = 0f) }
        file(previous)?.delete()
    }

    suspend fun resetDefaults() = withContext(Dispatchers.IO) {
        settings.acgDailyWallpaperEnabled.set(false)
        WallpaperProfile.entries.forEach(::clearStatic)
        settings.acgWallpaperUri.set("")
        settings.acgWallpaperZoom.set(1f)
        settings.acgWallpaperBiasX.set(0f)
        settings.acgWallpaperBiasY.set(0f)
        settings.acgWallpaperSeedColorArgb.set(DEFAULT_ACG_WALLPAPER_THEME_SEED_ARGB)
        WallpaperProfile.entries.forEach { resetRotation(it) }
    }

    private suspend fun resetRotation(profile: WallpaperProfile) = withContext(Dispatchers.IO) {
        val sample = bundledSample(profile)
        val removed = synchronized(stateLock) {
            val old = readOptions().getValue(profile)
            update(profile) {
                WallpaperOptions(apiUrl = profile.defaultApi, pool = listOf(sample), samplesInitialized = true,
                    crops = mapOf(sample to defaultWallpaperCrop(profile)), staticImage = it.staticImage,
                    zoom = it.zoom, biasX = it.biasX, biasY = it.biasY)
            }
            (old.pool + old.history + old.current).distinct()
        }
        removed.filter { it.startsWith("${profile.key}/pool/") || it.startsWith("${profile.key}/history/") }
            .forEach { file(it)?.delete() }
    }

    suspend fun addToPool(profile: WallpaperProfile, uris: List<String>) = withContext(Dispatchers.IO) {
        require(options.value.getValue(profile).pool.size + uris.size <= 100) { "图片池最多保存100张图片" }
        val staged = mutableListOf<String>()
        try {
            uris.forEach { staged += importImage(profile, "pool", it) }
            update(profile) { old ->
                require(old.pool.size + staged.size <= 100)
                old.copy(pool = old.pool + staged, day = "")
            }
        } catch (error: Throwable) {
            staged.forEach { file(it)?.delete() }
            throw error
        }
    }

    suspend fun removeFromPool(profile: WallpaperProfile, path: String) = withContext(Dispatchers.IO) {
        update(profile) { it.copy(pool = it.pool - path, crops = it.crops - path, current = it.current.takeUnless { value -> value == path }.orEmpty(), day = "") }
        if (path.startsWith("${profile.key}/pool/")) file(path)?.delete()
    }

    suspend fun removeFromCache(profile: WallpaperProfile, path: String) = withContext(Dispatchers.IO) {
        if (!path.startsWith("${profile.key}/history/")) return@withContext
        update(profile) { it.copy(history = it.history - path, crops = it.crops - path,
            current = it.current.takeUnless { value -> value == path }.orEmpty(), day = "") }
        file(path)?.delete()
    }

    /** Exactly one selection per profile/day; simultaneous lifecycle/settings refreshes share a lock. */
    suspend fun refresh(profile: WallpaperProfile, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        refreshLocks.getValue(profile).withLock {
            if (!settings.acgDailyWallpaperEnabled.value) return@withLock false
            val today = LocalDate.now().toString()
            val option = options.value.getValue(profile)
            if (!force && option.day == today && fileUri(option.current) != null) return@withLock true
            synchronized(stateLock) { _refreshing.value += profile }
            try {
                val pool = option.pool.filter { fileUri(it) != null }.takeIf { option.poolEnabled }.orEmpty()
                fun choose(paths: List<String>): String? {
                    val others = paths.filter { it != option.current }
                    return (others.ifEmpty { paths }).randomOrNull()
                }
                val preferPool = pool.isNotEmpty() && (!option.apiEnabled || Random.nextFloat() < 0.25f)
                var downloaded: String? = null
                val selected = if (preferPool) choose(pool) else if (option.apiEnabled) {
                    try { download(profile, option.apiUrl).also { downloaded = it } }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { null }
                } else null
                val fallback = selected ?: choose(pool) ?: choose(option.history.filter { fileUri(it) != null })
                    ?: option.current.takeIf { fileUri(it) != null } ?: option.staticImage.takeIf { fileUri(it) != null }
                if (fallback == null) return@withLock false
                // Settings may have changed while the network request was running. Do not publish an obsolete source.
                val published = synchronized(stateLock) {
                    val latest = readOptions().getValue(profile)
                    if (!settings.acgDailyWallpaperEnabled.value || latest.apiUrl != option.apiUrl ||
                        latest.apiEnabled != option.apiEnabled || latest.poolEnabled != option.poolEnabled || latest.pool != option.pool ||
                        latest.history != option.history || latest.current != option.current) {
                        false
                    } else {
                        update(profile) { current ->
                            val history = downloaded?.let { (listOf(it) + current.history).distinct().take(10) } ?: current.history
                            current.copy(current = fallback, day = today, history = history,
                                crops = current.crops.filterKeys { it in history || it in current.pool })
                        }
                        true
                    }
                }
                if (!published) {
                    downloaded?.let { file(it)?.delete() }
                    return@withLock false
                }
                val kept = options.value.getValue(profile).history.toSet()
                option.history.filterNot(kept::contains).forEach { file(it)?.delete() }
                true
            } finally { synchronized(stateLock) { _refreshing.value -= profile } }
        }
    }

    private fun importImage(profile: WallpaperProfile, kind: String, uri: String): String {
        val source = context.contentResolver.openInputStream(uri.toUri()) ?: error("无法读取图片")
        return source.use { saveImage(profile, kind, it) }
    }

    private fun saveImage(profile: WallpaperProfile, kind: String, input: InputStream): String {
        val path = "${profile.key}/$kind/${UUID.randomUUID()}.img"
        val target = checkNotNull(file(path)); target.parentFile!!.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            temporary.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024); var total = 0
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= 20 * 1024 * 1024) { "图片不能超过20 MB" }; out.write(buffer, 0, count)
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000L) { "无法识别图片格式或图片尺寸过大" }
            check(temporary.renameTo(target)) { "无法保存图片" }
            return path
        } finally { temporary.delete() }
    }

    private fun download(profile: WallpaperProfile, api: String): String {
        require(validApiUrl(api))
        var url = api.trim()
        // Direct images and redirects work by default; common JSON image APIs can return url/imgurl/data.url.
        repeat(3) {
            client.newCall(Request.Builder().url(url).header("Accept", "image/*, application/json").build()).execute().use { response ->
                check(response.isSuccessful) { "壁纸API返回${response.code}" }
                val body = response.body
                require(body.contentLength() <= 20L * 1024 * 1024)
                if (body.contentType()?.subtype?.contains("json") == true) {
                    val bytes = body.byteStream().use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer); if (count < 0) break
                            require(output.size() + count <= 64 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    url = findImageUrl(org.json.JSONTokener(bytes.toString(Charsets.UTF_8)).nextValue()) ?: error("API没有返回图片地址")
                    require(validApiUrl(url))
                } else {
                    return saveImage(profile, "history", body.byteStream())
                }
            }
        }
        error("壁纸API跳转次数过多")
    }

    private fun findImageUrl(value: Any?, depth: Int = 0): String? {
        if (depth > 4) return null
        if (value is String && validApiUrl(value)) return value
        if (value is JSONObject) for (key in listOf("url", "imgurl", "image", "pic", "data", "images")) findImageUrl(value.opt(key), depth + 1)?.let { return it }
        if (value is JSONArray && value.length() > 0) return findImageUrl(value.opt(0), depth + 1)
        return null
    }

    private fun validPath(path: String): Boolean = path.isNotBlank() && path.none { it.isISOControl() } && !path.startsWith('/') && !path.contains('\\') && path.split('/').none { it == ".." || it == "." || ':' in it }
    private fun file(path: String): File? = if (!validPath(path)) null else runCatching {
        File(root, path).takeIf { it.canonicalPath.startsWith(root.canonicalPath + File.separator) }
    }.getOrNull()

    companion object {
        fun validApiUrl(value: String): Boolean = runCatching {
            val uri = java.net.URI(value.trim()); uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}
