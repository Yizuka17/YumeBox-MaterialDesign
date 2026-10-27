package com.github.yumelira.yumebox.data.store

import com.github.yumelira.yumebox.data.model.OverrideContentType
import kotlinx.serialization.Serializable

@Serializable
data class OverrideConfigBackupEntry(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Long = 0L,
    val content: String,
    val contentType: OverrideContentType = OverrideContentType.Yaml,
)
