package com.github.yumelira.yumebox.screen.settings

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

internal object AppFontStorage {
    private const val MAX_FONT_BYTES = 128L * 1024 * 1024

    private fun path(context: Context, selection: String): File? {
        val id = runCatching { UUID.fromString(selection.substringBefore('|')).toString() }.getOrNull()
            ?: return null
        return File(context.filesDir, "app_fonts/$id.font")
    }

    fun displayName(selection: String): String = selection.substringAfter('|', "")

    fun import(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull().orEmpty().filterNot(Char::isISOControl).take(100)
        val selection = "${UUID.randomUUID()}|${name.ifBlank { "Font" }}"
        val target = checkNotNull(path(context, selection))
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open font")
            input.use {
                target.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val read = it.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_FONT_BYTES) { "Font exceeds 128 MiB" }
                        output.write(buffer, 0, read)
                    }
                }
            }
            require(target.length() >= 12)
            val signature = target.inputStream().buffered().use { stream ->
                java.io.DataInputStream(stream).readInt()
            }
            // OpenType with TrueType or CFF outlines.
            require(signature == 0x00010000 || signature == 0x4F54544F || signature == 0x74727565 || signature == 0x74746366)
            checkNotNull(Typeface.Builder(target).build()) { "Unsupported font" }
            return selection
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    fun load(context: Context, selection: String, weight: Int): FontFamily? = runCatching {
        val file = path(context, selection)?.takeIf(File::isFile) ?: return null
        val builder = Typeface.Builder(file)
        if (weight in 1..1000) {
            builder.setWeight(weight)
            weightRange(file)?.let { (min, max) ->
                builder.setFontVariationSettings("'wght' ${weight.toFloat().coerceIn(min, max)}")
            }
        }
        FontFamily(checkNotNull(builder.build()))
    }.getOrNull()

    /** SFNT fvar stores the actual supported range, including non-100 weight stops. */
    internal fun weightRange(file: File): Pair<Float, Float>? = runCatching {
        RandomAccessFile(file, "r").use { data ->
            val signature = data.readInt()
            val offset = if (signature == 0x74746366) {
                data.skipBytes(8)
                data.readInt().toLong() and 0xffffffffL
            } else 0L
            require(offset <= data.length() - 12)
            data.seek(offset + 4)
            val tableCount = data.readUnsignedShort()
            require(tableCount <= 512 && offset + 12 + tableCount * 16L <= data.length())
            data.seek(offset + 12)
            repeat(tableCount) {
                val tag = data.readInt()
                data.skipBytes(4)
                val tableOffset = data.readInt().toLong() and 0xffffffffL
                val tableLength = data.readInt().toLong() and 0xffffffffL
                if (tag == 0x66766172) {
                    require(tableOffset <= data.length() - tableLength && tableLength >= 16)
                    data.seek(tableOffset + 4)
                    val axesOffset = data.readUnsignedShort()
                    data.skipBytes(2)
                    val axes = data.readUnsignedShort()
                    val axisSize = data.readUnsignedShort()
                    require(axisSize >= 20 && axes <= 64 &&
                        axesOffset + axes * axisSize.toLong() <= tableLength)
                    repeat(axes) { axis ->
                        data.seek(tableOffset + axesOffset + axis * axisSize)
                        val axisTag = data.readInt()
                        val minimum = data.readInt() / 65536f
                        data.skipBytes(4)
                        val maximum = data.readInt() / 65536f
                        if (axisTag == 0x77676874 && minimum <= maximum) {
                            return@use minimum to maximum
                        }
                    }
                    return@use null
                }
            }
            null
        }
    }.getOrNull()

    fun remove(context: Context, selection: String) { path(context, selection)?.delete() }
}
