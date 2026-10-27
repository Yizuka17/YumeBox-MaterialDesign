/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
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

package com.github.yumelira.yumebox.data.model

import kotlinx.serialization.Serializable
import com.github.yumelira.yumebox.core.model.withoutUnsupportedProxyGroups

@Serializable
data class OverrideConfig(
    val id: String,
    val name: String,
    val description: String? = null,
    val contentType: OverrideContentType,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val config: com.github.yumelira.yumebox.core.model.ConfigurationOverride
        get() = com.github.yumelira.yumebox.core.util.YamlCodec.decode(
            com.github.yumelira.yumebox.core.model.ConfigurationOverride.serializer(), content,
        ).withoutUnsupportedProxyGroups()
    val isSystem: Boolean get() = BuiltInOverrideCatalog.isBuiltIn(id)

    constructor(
        id: String, name: String, description: String? = null,
        config: com.github.yumelira.yumebox.core.model.ConfigurationOverride,
        isSystem: Boolean = false, createdAt: Long, updatedAt: Long,
    ) : this(id, name, description, OverrideContentType.Yaml,
        com.github.yumelira.yumebox.core.model.encodeConfigurationOverride(config), createdAt, updatedAt)

    fun copy(
        config: com.github.yumelira.yumebox.core.model.ConfigurationOverride,
        id: String = this.id, name: String = this.name, description: String? = this.description,
        isSystem: Boolean = this.isSystem, createdAt: Long = this.createdAt,
        updatedAt: Long = this.updatedAt,
    ): OverrideConfig = OverrideConfig(id, name, description, config, isSystem, createdAt, updatedAt)

    val fileName: String
        get() = "$id.${contentType.extension}"
}
