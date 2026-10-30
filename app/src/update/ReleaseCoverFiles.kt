package com.github.yumelira.yumebox.update

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class DownloadedCover(val file: File, val mimeType: String, val name: String)

internal object ReleaseCoverFiles {
    private const val MAX_BYTES = 20L * 1024 * 1024
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS).build()

    suspend fun download(context: Context, source: String): DownloadedCover = withContext(Dispatchers.IO) {
        require(source.startsWith("https://")) { "封面地址无效" }
        val directory = File(context.cacheDir, "release-covers").apply { mkdirs() }
        val key = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = File(directory, key)
        if (!file.isFile) {
            val temporary = File.createTempFile("cover-", ".part", directory)
            try {
                client.newCall(Request.Builder().url(source).build()).execute().use { response ->
                    check(response.isSuccessful) { "下载图片失败：HTTP ${response.code}" }
                    require(response.body.contentLength() <= MAX_BYTES) { "封面图片过大" }
                    response.body.byteStream().use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var received = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                received += count
                                require(received <= MAX_BYTES) { "封面图片过大" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                require(file.exists() || temporary.renameTo(file)) { "无法保存封面缓存" }
            } finally {
                temporary.delete()
            }
        }
        try { describe(file, "YumeBox-cover") }
        catch (e: Exception) { file.delete(); throw e }
    }

    suspend fun localImage(context: Context, source: String): DownloadedCover = withContext(Dispatchers.IO) {
        val uri = Uri.parse(source)
        require(uri.scheme == "file")
        val file = File(checkNotNull(uri.path)).canonicalFile
        val root = File(context.filesDir, "acg_wallpapers").canonicalFile
        require(file.path.startsWith(root.path + File.separator) && file.isFile && file.length() <= MAX_BYTES)
        describe(file, "YumeBox-wallpaper")
    }

    private fun describe(file: File, prefix: String): DownloadedCover {
        val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, info)
        if (info.outWidth <= 0 || info.outHeight <= 0) {
            error("下载的文件不是可保存的图片")
        }
        val mime = info.outMimeType ?: "image/jpeg"
        val extension = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heif", "image/heic" -> "heic"
            "image/avif" -> "avif"
            else -> "jpg"
        }
        return DownloadedCover(file, mime, "$prefix-${System.currentTimeMillis()}.$extension")
    }

    suspend fun saveToGallery(context: Context, cover: DownloadedCover) = withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= 29)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, cover.name)
            put(MediaStore.Images.Media.MIME_TYPE, cover.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/YumeBox")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建相册图片")
        try {
            write(context, cover, uri)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    suspend fun write(context: Context, cover: DownloadedCover, uri: Uri) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri) ?: error("无法写入图片")
        output.use { sink -> cover.file.inputStream().use { it.copyTo(sink) } }
    }
}
