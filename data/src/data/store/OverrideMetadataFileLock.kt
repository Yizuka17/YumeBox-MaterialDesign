/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * YumeBox is distributed in the hope that it will be useful,
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

package com.github.yumelira.yumebox.data.store

import com.github.yumelira.yumebox.core.util.YamlCodec
import com.github.yumelira.yumebox.data.model.MetadataIndex
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Shared monitor for the two stores that read and rewrite overrides/metadata.yaml. */
internal object OverrideMetadataFileLock {
    val monitor = Any()
}

/**
 * Decode outcome for metadata.yaml.
 *
 * Corrupt must never be replaced by an empty index write — that permanently wipes overrides.
 */
internal sealed class MetadataIndexLoad {
    data class Ok(val index: MetadataIndex) : MetadataIndexLoad()

    /** File missing or zero-length — safe to treat as empty and allow first write. */
    data object Missing : MetadataIndexLoad()

    data class Corrupt(val cause: Throwable) : MetadataIndexLoad()
}

internal object OverrideMetadataIO {
    fun load(file: File): MetadataIndexLoad {
        runCatching { migrateForkMetadata(file) }.exceptionOrNull()?.let { error ->
            Timber.w(error, "Legacy override migration failed; original files retained")
            return MetadataIndexLoad.Corrupt(error)
        }
        if (!file.exists() || file.length() == 0L) {
            return MetadataIndexLoad.Missing
        }
        return runCatching {
            MetadataIndexLoad.Ok(
                YamlCodec.decode(MetadataIndex.serializer(), file.readText())
            )
        }
            .getOrElse { error ->
                Timber.w(error, "Failed to decode override metadata: %s", file.absolutePath)
                MetadataIndexLoad.Corrupt(error)
            }
    }

    /** Import the fork's JSON storage once, leaving all legacy files available for rollback. */
    private fun migrateForkMetadata(file: File) = synchronized(OverrideMetadataFileLock.monitor) {
        val directory = file.parentFile ?: return@synchronized
        val legacy = File(directory, "metadata.json")
        val marker = File(directory, ".fork-json-imported-v1")
        if (!legacy.isFile || marker.exists()) return@synchronized
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(MetadataIndex.serializer(), legacy.readText())
        val current = if (file.isFile && file.length() > 0) {
            YamlCodec.decode(MetadataIndex.serializer(), file.readText())
        } else MetadataIndex()
        val configs = current.configs.toMutableMap()
        old.configs.forEach { (id, metadata) ->
            require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid legacy override ID" }
            if (id.startsWith("preset-") || id in configs) return@forEach
            val source = File(directory, "configs/$id.json")
            if (!source.isFile) return@forEach
            val content = source.readText()
            YamlCodec.validate(content)
            writeTextAtomic(File(directory, "configs/$id.yaml"), content)
            configs[id] = metadata.copy(id = id, contentType = com.github.yumelira.yumebox.data.model.OverrideContentType.Yaml)
        }
        val customId = com.github.yumelira.yumebox.core.model.OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID
        val custom = File(directory, "internal/custom-routing.json")
        if (custom.isFile && customId !in configs) {
            val content = custom.readText()
            YamlCodec.validate(content)
            writeTextAtomic(File(directory, "configs/$customId.yaml"), content)
            configs[customId] = com.github.yumelira.yumebox.data.model.OverrideMetadata(
                id = customId, name = "custom-routing.yaml", createdAt = custom.lastModified(), updatedAt = custom.lastModified(),
            )
        }
        val bindings = old.profileChains.mapValues { (profileId, binding) ->
            binding.copy(profileId = profileId, overrideIds = binding.overrideIds.map {
                if (it.startsWith("preset-")) "builtin-official-mrs" else it
            }.distinct())
        } + current.profileChains
        save(file, current.copy(configs = configs, profileChains = bindings))
        writeTextAtomic(marker, "Imported legacy JSON without deleting source files.\n")
    }

    /**
     * Index usable for read-only display. Corrupt/missing → empty shell (does not touch disk).
     */
    fun loadForRead(file: File): MetadataIndex =
        when (val loaded = load(file)) {
            is MetadataIndexLoad.Ok -> loaded.index
            MetadataIndexLoad.Missing,
            is MetadataIndexLoad.Corrupt -> MetadataIndex()
        }

    /**
     * Index for mutation. Corrupt non-empty file throws so callers cannot save an empty wipe.
     */
    fun loadForMutation(file: File): MetadataIndex =
        when (val loaded = load(file)) {
            is MetadataIndexLoad.Ok -> loaded.index
            MetadataIndexLoad.Missing -> MetadataIndex()
            is MetadataIndexLoad.Corrupt ->
                throw IOException(
                    "Refuse to mutate corrupt override metadata: ${file.absolutePath}",
                    loaded.cause,
                )
        }

    fun save(file: File, index: MetadataIndex) {
        val parent = file.parentFile ?: error("metadata file has no parent: ${file.path}")
        parent.mkdirs()
        val encoded = YamlCodec.encode(MetadataIndex.serializer(), index)
        writeTextAtomic(file, encoded)
    }

    fun writeTextAtomic(file: File, text: String) {
        val parent = file.parentFile ?: error("target file has no parent: ${file.path}")
        check(parent.exists() || parent.mkdirs()) { "Failed to create directory: ${parent.path}" }
        val tmp = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            FileOutputStream(tmp).bufferedWriter().use { writer ->
                writer.write(text)
                writer.flush()
            }
            FileOutputStream(tmp, true).use { output -> output.fd.sync() }
            val tmpPath = tmp.toPath()
            val targetPath = file.toPath()
            try {
                Files.move(
                    tmpPath,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Exception) {
            runCatching { tmp.delete() }
            throw error
        }
    }
}
