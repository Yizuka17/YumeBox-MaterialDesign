package com.github.yumelira.yumebox.runtime.service.core

import android.content.Context
import com.github.yumelira.yumebox.core.model.GeoFileType
import com.github.yumelira.yumebox.core.model.GeoValidationResult
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

object GeoFileValidator {
    fun validate(context: Context, file: File, type: GeoFileType): GeoValidationResult = runCatching {
        val process = ProcessBuilder(
            CoreArtifacts.shell(context).absolutePath, CoreArtifacts.LIBRARY_OPTION,
            CoreArtifacts.previewLibrary(context).absolutePath,
            "--validate-geo", file.absolutePath, "--geo-type", type.name,
        ).redirectErrorStream(true).start()
        try {
            check(process.waitFor(20, TimeUnit.SECONDS)) { "Geo validation timed out" }
            val result = process.inputStream.bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }.decodeFromString<GeoValidationResult>(result.trim())
        } finally { process.destroy() }
    }.getOrElse { GeoValidationResult(false, it.message) }
}
