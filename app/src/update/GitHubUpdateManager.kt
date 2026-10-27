package com.github.yumelira.yumebox.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.github.yumelira.yumebox.BuildInfo
import com.github.yumelira.yumebox.core.util.AppVisibilityTracker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class GitHubUpdateManager @JvmOverloads constructor(
    context: Context,
    private val currentVersion: String = BuildInfo.BASE_VERSION,
    private val currentVersionCode: Long = BuildInfo.VERSION_CODE.toLong(),
    private val buildTimestamp: Long = BuildInfo.BUILD_TIMESTAMP,
    private val buildCommit: String = BuildInfo.BUILD_COMMIT,
    providedClient: OkHttpClient? = null,
) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("release_updates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val checkMutex = Mutex()
    private val downloadMutex = Mutex()
    private val client = providedClient ?: OkHttpClient.Builder()
        .cache(Cache(File(this.context.cacheDir, "release-http"), 2L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
    private val _candidate = MutableStateFlow<UpdateCandidate?>(null)
    val candidate = _candidate.asStateFlow()
    private val _progress = MutableStateFlow(UpdateDownloadProgress())
    val progress = _progress.asStateFlow()
    @Volatile private var downloadCall: Call? = null
    @Volatile private var cancelled = false
    var autoCheck: Boolean
        get() = preferences.getBoolean("auto", true)
        set(value) { preferences.edit().putBoolean("auto", value).apply() }
    var includePrereleases: Boolean
        get() = preferences.getBoolean("prereleases", false)
        set(value) { preferences.edit().putBoolean("prereleases", value).apply(); _candidate.value = null }

    suspend fun checkForUpdate(manual: Boolean = true): UpdateCandidate? = withContext(Dispatchers.IO) {
        checkMutex.withLock {
            val endpoint = if (includePrereleases) "$API/releases?per_page=30" else "$API/releases/latest"
            val request = Request.Builder().url(endpoint)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "YumeBoxMD3/${BuildInfo.BASE_VERSION}")
                .header("Cache-Control", if (manual) "no-cache" else "max-age=21600").build()
            val releases = client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withLock null.also { _candidate.value = null }
                if (!response.isSuccessful) throw IOException(if (response.code == 403 || response.code == 429) "GitHub 请求受限，请稍后重试" else "GitHub HTTP ${response.code}")
                val body = response.body.string()
                if (includePrereleases) json.decodeFromString<List<GitHubRelease>>(body) else listOf(json.decodeFromString<GitHubRelease>(body))
            }
            val current = ReleaseVersion.parse(currentVersion) ?: error("当前版本信息无效")
            val release = releases.filter { item ->
                if (item.draft || (!includePrereleases && item.prerelease) || item.publishedAt == null) return@filter false
                val parsed = ReleaseVersion.parse(item.tag)
                if (parsed != null) parsed > current
                else includePrereleases && item.prerelease &&
                    item.assets.none { buildCommit.length >= 7 && buildCommit in it.name } &&
                    runCatching { java.time.Instant.parse(item.publishedAt).epochSecond > buildTimestamp + 300 }.getOrDefault(false)
            }.maxByOrNull { it.publishedAt.orEmpty() }
            preferences.edit().putLong("last_check", System.currentTimeMillis()).apply()
            val result = release?.let { UpdateCandidate(it, it.selectApk(Build.SUPPORTED_ABIS.toList())) }
            _candidate.value = result?.takeUnless { !manual && preferences.getLong("ignored", -1) == it.release.id }
            result
        }
    }

    fun startAutoCheck(scope: CoroutineScope) {
        scope.launch {
            AppVisibilityTracker.isForeground.collectLatest { foreground ->
                if (!foreground) return@collectLatest
                delay(5_000)
                while (isActive) {
                    if (autoCheck && System.currentTimeMillis() - preferences.getLong("last_check", 0) >= 6 * 60 * 60 * 1000L) {
                        try { checkForUpdate(manual = false) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                    }
                    delay(30 * 60 * 1000L)
                }
            }
        }
    }

    fun dismiss(ignore: Boolean = false) {
        if (ignore) _candidate.value?.let { preferences.edit().putLong("ignored", it.release.id).apply() }
        _candidate.value = null
    }

    fun cancelDownload() { cancelled = true; downloadCall?.cancel() }

    suspend fun downloadAndInstall(candidate: UpdateCandidate) = withContext(Dispatchers.IO) {
        if (!downloadMutex.tryLock()) throw IOException("下载正在进行")
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(directory, "release-${candidate.release.id}.apk")
        val partial = File(directory, "release-${candidate.release.id}.part")
        try {
            val asset = candidate.asset ?: throw IOException("没有适合当前设备的 APK，请打开发布页下载")
            require(asset.url.startsWith("https://github.com/$REPOSITORY/releases/download/")) { "发布附件地址无效" }
            if (file.isFile) {
                val valid = runCatching { validateApk(file) }.isSuccess && (asset.size <= 0 || file.length() == asset.size)
                if (valid) {
                    withContext(Dispatchers.Main) { openInstaller(file) }
                    return@withContext
                }
                file.delete()
            }
            cancelled = false
            _progress.value = UpdateDownloadProgress(downloading = true, total = asset.size)
            val call = client.newCall(Request.Builder().url(asset.url).build())
            downloadCall = call
            val hash = MessageDigest.getInstance("SHA-256")
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("下载失败：HTTP ${response.code}")
                val total = response.body.contentLength().takeIf { it > 0 } ?: asset.size
                response.body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024); var received = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            if (cancelled) throw IOException("下载已取消")
                            val size = input.read(buffer); if (size < 0) break
                            output.write(buffer, 0, size); hash.update(buffer, 0, size); received += size
                            _progress.value = UpdateDownloadProgress(true, received, total)
                        }
                        if (total > 0 && received != total) throw IOException("下载不完整，请重试")
                    }
                }
            }
            asset.digest?.takeIf { it.startsWith("sha256:") }?.let { expected ->
                val actual = hash.digest().joinToString("") { "%02x".format(it) }
                require(actual.equals(expected.substringAfter(':'), true)) { "安装包 SHA-256 校验失败" }
            }
            validateApk(partial)
            if (cancelled) throw IOException("下载已取消")
            if (file.exists()) file.delete()
            check(partial.renameTo(file)) { "无法保存安装包" }
            withContext(Dispatchers.Main) {
                openInstaller(file)
            }
        } catch (e: IOException) {
            if (cancelled) throw IOException("下载已取消") else throw e
        } finally {
            downloadCall = null
            partial.delete()
            _progress.value = UpdateDownloadProgress()
            downloadMutex.unlock()
        }
    }

    @Suppress("DEPRECATION")
    private fun validateApk(file: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: error("文件不是有效的 APK")
        val installed = pm.getPackageInfo(context.packageName, flags)
        require(archive.packageName == context.packageName) { "安装包不属于 YumeBox MD3" }
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(code > currentVersionCode) { "安装包版本不高于当前版本" }
        val old = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        val new = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.signingCertificateHistory else archive.signatures
        require(!old.isNullOrEmpty() && !new.isNullOrEmpty() && old.all { signer -> new.any { it == signer } }) { "安装包签名不同，请前往发布页查看安装说明" }
    }

    private fun openInstaller(file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            throw IOException("允许安装应用后返回，再次点击安装即可；安装包已保存")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    companion object {
        const val REPOSITORY = "Yizuka17/YumeBox-MaterialDesign"
        const val RELEASES_URL = "https://github.com/$REPOSITORY/releases"
        private const val API = "https://api.github.com/repos/$REPOSITORY"
    }
}
