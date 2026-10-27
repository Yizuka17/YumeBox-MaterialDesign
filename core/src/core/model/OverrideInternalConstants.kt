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

package com.github.yumelira.yumebox.core.model

object OverrideInternalConstants {
    const val CUSTOM_ROUTING_OVERRIDE_ID = "__custom_routing__"
    const val CUSTOM_ROUTING_FILE_NAME = "custom-routing.yaml"

    /** Stable id prefix for APK-bundled built-in overrides (not user-owned, not reordered). */
    const val BUILTIN_OVERRIDE_PREFIX = "builtin-"

    fun isBuiltInOverrideId(id: String): Boolean = id.startsWith(BUILTIN_OVERRIDE_PREFIX)
}
