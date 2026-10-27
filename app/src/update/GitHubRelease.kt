package com.github.yumelira.yumebox.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    val id: Long = 0,
    @SerialName("tag_name") val tag: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList(),
) {
    fun selectApk(supportedAbis: List<String>, builtin: Boolean = true): GitHubReleaseAsset? =
        assets.filter { it.name.endsWith(".apk", true) && it.state == "uploaded" }
            .mapNotNull { asset ->
                val name = asset.name.lowercase()
                val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "arm64", "armv7")
                val tagged = knownAbis.filter { Regex("(?:^|[-_.])${Regex.escape(it)}(?:[-_.]|$)").containsMatchIn(name) }
                val rank = supportedAbis.indexOfFirst { abi -> abi in tagged || (abi == "arm64-v8a" && "arm64" in tagged) || (abi == "armeabi-v7a" && "armv7" in tagged) }
                if (tagged.isNotEmpty() && rank < 0) return@mapNotNull null
                val geoRank = if (("builtin" in name) == builtin || ("external" !in name && "builtin" !in name)) 0 else 1
                val score = (if (rank >= 0) rank else supportedAbis.size) * 10 + geoRank
                score to asset
            }.sortedBy { it.first }.firstOrNull()?.second
}

@Serializable
data class GitHubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
    val digest: String? = null,
    val state: String = "uploaded",
)

data class UpdateCandidate(val release: GitHubRelease, val asset: GitHubReleaseAsset?) {
    val versionName get() = release.name.ifBlank { release.tag }
    val tag get() = release.tag
    val releaseNotes get() = release.body.replace(Regex("!\\[[^]]*]\\(https://[^ )]+\\)"), "").trim()
    val releaseUrl get() = release.htmlUrl
    val coverUrl: String get() = Regex("!\\[[^]]*]\\((https://[^ )]+)\\)").find(release.body)?.groupValues?.get(1).orEmpty()
    val coverDataUri: String get() = ""
    val cachedCoverUri: String get() = ""
}
data class UpdateDownloadProgress(val downloading: Boolean = false, val received: Long = 0, val total: Long = 0) {
    val fraction: Float? get() = total.takeIf { it > 0 }?.let { (received.toDouble() / it).toFloat().coerceIn(0f, 1f) }
    val isDownloading get() = downloading
    val progress: Int get() = fraction?.times(100)?.toInt() ?: 0
    val message: String get() = if (downloading) "${received / 1024 / 1024} MB" + if (total > 0) " / ${total / 1024 / 1024} MB" else "" else ""
}
