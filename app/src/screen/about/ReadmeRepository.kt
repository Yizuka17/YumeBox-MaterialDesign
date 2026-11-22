package com.github.yumelira.yumebox.screen.about

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

internal const val README_REPOSITORY = "https://github.com/Yizuka17/YumeBox-MaterialDesign"
internal val readmePaths = setOf("README.md", "docs/README.md", "docs/README_ZH_HANS.md")

internal fun readmeLink(path: String, link: String): URI =
    URI(README_REPOSITORY + "/blob/Yume/" + path).resolve(link).normalize()

internal fun localReadmePath(uri: URI): String? =
    uri.path?.removePrefix("/Yizuka17/YumeBox-MaterialDesign/blob/Yume/")
        ?.takeIf { uri.host == "github.com" && it in readmePaths }

internal class ReadmeRepository(private val context: Context) {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()
    private fun cache(path: String): File {
        require(path in readmePaths)
        return File(context.cacheDir, "readme/" + path.replace('/', '_'))
    }
    suspend fun stored(path: String): String = withContext(Dispatchers.IO) {
        runCatching { cache(path).takeIf { it.isFile }?.readText() }.getOrNull()
            ?: context.assets.open("readme/" + path).bufferedReader().use { it.readText() }
    }
    suspend fun refresh(path: String, force: Boolean): String = withContext(Dispatchers.IO) {
        require(path in readmePaths)
        val url = "https://api.github.com/repos/Yizuka17/YumeBox-MaterialDesign/contents/" + path +
            "?ref=Yume" + if (force) "&refresh=" + System.currentTimeMillis() else ""
        val request = Request.Builder().url(url).header("Cache-Control", "no-cache")
            .header("User-Agent", "YumeBoxMD3")
            .header("Accept", "application/vnd.github.raw+json")
            .header("X-GitHub-Api-Version", "2022-11-28").build()
        val markdown = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Readme HTTP " + response.code }
            require(response.body.contentLength() <= 1024 * 1024)
            response.body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 1024 * 1024)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        }
        require(markdown.isNotBlank())
        val destination = cache(path).apply { parentFile?.mkdirs() }
        val staging = File.createTempFile("readme-", ".md", destination.parentFile)
        try {
            staging.writeText(markdown)
            java.nio.file.Files.move(staging.toPath(), destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } finally { staging.delete() }
        markdown
    }
}
