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
 * Copyright (c)  YumeLira 2025 - Present
 *
 */

package com.github.yumelira.yumebox.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val UnsupportedProxyGroupTypes = setOf("relay")

fun ConfigurationOverride.withoutUnsupportedProxyGroups(): ConfigurationOverride {
    val removedNames = findUnsupportedProxyGroupNames(
        proxyGroups,
        proxyGroupsStart,
        proxyGroupsEnd,
    )

    return copy(
        proxyGroups = proxyGroups.withoutProxyGroups(removedNames),
        proxyGroupsStart = proxyGroupsStart.withoutProxyGroups(removedNames),
        proxyGroupsEnd = proxyGroupsEnd.withoutProxyGroups(removedNames),
    ).withoutRulesTargetingUnsupportedProxyGroups(removedNames)
}

private fun findUnsupportedProxyGroupNames(
    vararg proxyGroupLists: List<Map<String, JsonElement>>?,
): Set<String> {
    val groups = proxyGroupLists.flatMap { groups -> groups.orEmpty() }
    val removedNames = linkedSetOf<String>()

    while (true) {
        val nextRemovedNames = groups
            .filter { group -> group.isUnsupportedProxyGroup() || group.hasOnlyRemovedProxyMembers(removedNames) }
            .mapNotNullTo(linkedSetOf()) { group -> group.stringField("name")?.trim()?.takeIf(String::isNotBlank) }
            .filterNotTo(linkedSetOf()) { name -> name in removedNames }

        if (nextRemovedNames.isEmpty()) {
            break
        }

        removedNames += nextRemovedNames
    }

    return removedNames
}

private fun List<Map<String, JsonElement>>?.withoutProxyGroups(
    removedNames: Set<String>,
): List<Map<String, JsonElement>>? {
    return this
        ?.filterNot { group -> group.stringField("name")?.trim() in removedNames || group.isUnsupportedProxyGroup() }
        ?.map { group -> group.removingProxyMembers(removedNames) }
        ?.ifEmpty { null }
}

private fun Map<String, JsonElement>.isUnsupportedProxyGroup(): Boolean {
    val groupType = stringField("type")
        ?.trim()
        ?.lowercase()
    return groupType in UnsupportedProxyGroupTypes
}

private fun Map<String, JsonElement>.hasOnlyRemovedProxyMembers(removedNames: Set<String>): Boolean {
    if (removedNames.isEmpty()) return false
    if (stringListField("use").isNotEmpty()) return false
    if (booleanField("include-all") || booleanField("include-all-proxies") || booleanField("include-all-providers")) {
        return false
    }
    val proxies = stringListField("proxies")
    return proxies.isNotEmpty() && proxies.all { proxy -> proxy in removedNames }
}

private fun Map<String, JsonElement>.removingProxyMembers(removedNames: Set<String>): Map<String, JsonElement> {
    if (removedNames.isEmpty()) return this
    val proxies = stringListField("proxies")
    if (proxies.isEmpty() || proxies.none { proxy -> proxy in removedNames }) {
        return this
    }
    val cleanedProxies = proxies.filterNot { proxy -> proxy in removedNames }
    return buildMap {
        putAll(this@removingProxyMembers)
        if (cleanedProxies.isEmpty()) {
            remove("proxies")
        } else {
            put("proxies", JsonArray(cleanedProxies.map(::JsonPrimitive)))
        }
    }
}

private fun ConfigurationOverride.withoutRulesTargetingUnsupportedProxyGroups(
    removedNames: Set<String>,
): ConfigurationOverride {
    if (removedNames.isEmpty()) return this
    return copy(
        rules = rules.withoutRulesTargeting(removedNames),
        rulesStart = rulesStart.withoutRulesTargeting(removedNames),
        rulesEnd = rulesEnd.withoutRulesTargeting(removedNames),
    )
}

private fun List<String>?.withoutRulesTargeting(removedNames: Set<String>): List<String>? {
    return this
        ?.filterNot { rule -> rule.targetName() in removedNames }
        ?.ifEmpty { null }
}

private fun String.targetName(): String? {
    val tokens = splitRuleTokens()
    if (tokens.isEmpty()) return null
    val targetIndex = if (tokens.first().equals("MATCH", ignoreCase = true)) 1 else 2
    return tokens.getOrNull(targetIndex)?.trim()?.takeIf(String::isNotBlank)
}

private fun String.splitRuleTokens(): List<String> {
    if (isBlank()) return emptyList()

    val tokens = mutableListOf<String>()
    val currentToken = StringBuilder()
    var parenthesesDepth = 0

    forEach { char ->
        when (char) {
            '(' -> {
                parenthesesDepth += 1
                currentToken.append(char)
            }

            ')' -> {
                parenthesesDepth = (parenthesesDepth - 1).coerceAtLeast(0)
                currentToken.append(char)
            }

            ',' -> {
                if (parenthesesDepth == 0) {
                    tokens += currentToken.toString().trim()
                    currentToken.clear()
                } else {
                    currentToken.append(char)
                }
            }

            else -> currentToken.append(char)
        }
    }

    tokens += currentToken.toString().trim()
    return tokens.filter(String::isNotBlank)
}

private fun Map<String, JsonElement>.stringField(key: String): String? {
    return (get(key) as? JsonPrimitive)?.contentOrNull
}

private fun Map<String, JsonElement>.booleanField(key: String): Boolean {
    return (get(key) as? JsonPrimitive)?.booleanOrNull == true
}

private fun Map<String, JsonElement>.stringListField(key: String): List<String> {
    return when (val element = get(key)) {
        is JsonArray -> element.mapNotNull { item ->
            (item as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
        }
        is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let(::listOf).orEmpty()
        else -> emptyList()
    }
}
