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



package com.github.yumelira.yumebox.presentation.util

import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.serialization.json.*
import java.util.*

enum class OverrideStructuredObjectType {
    Proxies {
        override val title: String get() = MLang.Override.Structured.Proxies.Title
        override val itemLabel: String get() = MLang.Override.Structured.Proxies.ItemLabel
        override val emptyHint: String get() = MLang.Override.Structured.Proxies.EmptyHint
    },
    ProxyGroups {
        override val title: String get() = MLang.Override.Structured.ProxyGroups.Title
        override val itemLabel: String get() = MLang.Override.Structured.ProxyGroups.ItemLabel
        override val emptyHint: String get() = MLang.Override.Structured.ProxyGroups.EmptyHint
    };

    abstract val title: String
    abstract val itemLabel: String
    abstract val emptyHint: String
}

enum class OverrideStructuredMapType {
    RuleProviders {
        override val title: String get() = MLang.Override.Structured.RuleProviders.Title
        override val itemLabel: String get() = MLang.Override.Structured.RuleProviders.ItemLabel
    },
    ProxyProviders {
        override val title: String get() = MLang.Override.Structured.ProxyProviders.Title
        override val itemLabel: String get() = MLang.Override.Structured.ProxyProviders.ItemLabel
    },
    SubRules {
        override val title: String get() = MLang.Override.Structured.SubRules.Title
        override val itemLabel: String get() = MLang.Override.Structured.SubRules.ItemLabel
    };

    abstract val title: String
    abstract val itemLabel: String
}

enum class OverrideListEditorMode {
    Replace {
        override val label: String get() = MLang.Override.Modifier.Replace
    },
    Merge {
        override val label: String get() = MLang.Override.Modifier.Merge
    },
    Start {
        override val label: String get() = MLang.Override.Modifier.Start
    },
    End {
        override val label: String get() = MLang.Override.Modifier.End
    };

    abstract val label: String
}

data class OverrideListModeValues<T>(
    val replaceValue: T? = null,
    val mergeValue: T? = null,
    val startValue: T? = null,
    val endValue: T? = null,
) {
    fun valueFor(mode: OverrideListEditorMode): T? {
        return when (mode) {
            OverrideListEditorMode.Replace -> replaceValue
            OverrideListEditorMode.Merge -> mergeValue
            OverrideListEditorMode.Start -> startValue
            OverrideListEditorMode.End -> endValue
        }
    }

    fun update(
        mode: OverrideListEditorMode,
        value: T?,
    ): OverrideListModeValues<T> {
        return when (mode) {
            OverrideListEditorMode.Replace -> copy(replaceValue = value)
            OverrideListEditorMode.Merge -> copy(mergeValue = value)
            OverrideListEditorMode.Start -> copy(startValue = value)
            OverrideListEditorMode.End -> copy(endValue = value)
        }
    }
}

data class OverrideRuleDraft(
    val type: String = "DOMAIN-SUFFIX",
    val payload: String = "",
    val target: String = "",
    val extras: List<String> = emptyList(),
    val uiId: String = generateOverrideUiId(),
)

data class OverrideProxyDraft(
    val name: String = "",
    val type: String = "ss",
    val server: String = "",
    val port: Int? = null,
    val ipVersion: String = "",
    val udp: Boolean? = null,
    val interfaceName: String = "",
    val routingMark: Int? = null,
    val tfo: Boolean? = null,
    val mptcp: Boolean? = null,
    val dialerProxy: String = "",
    val extraFields: Map<String, JsonElement> = emptyMap(),
    val uiId: String = generateOverrideUiId(),
)

data class OverrideProxyGroupDraft(
    val name: String = "",
    val type: String = "select",
    val proxies: List<String> = emptyList(),
    val use: List<String> = emptyList(),
    val url: String = "",
    val interval: Int? = null,
    val lazy: Boolean? = null,
    val timeout: Int? = null,
    val maxFailedTimes: Int? = null,
    val disableUdp: Boolean? = null,
    val interfaceName: String = "",
    val routingMark: Int? = null,
    val includeAll: Boolean? = null,
    val includeAllProxies: Boolean? = null,
    val includeAllProviders: Boolean? = null,
    val filter: String = "",
    val excludeFilter: String = "",
    val excludeType: String = "",
    val expectedStatus: String = "",
    val hidden: Boolean? = null,
    val icon: String = "",
    val extraFields: Map<String, JsonElement> = emptyMap(),
    val uiId: String = generateOverrideUiId(),
)

data class OverrideKeyedObjectDraft(
    val key: String = "",
    val fields: Map<String, JsonElement> = emptyMap(),
    val uiId: String = generateOverrideUiId(),
)

data class OverrideSubRuleGroupDraft(
    val name: String = "",
    val rules: List<String> = emptyList(),
    val uiId: String = generateOverrideUiId(),
)

private fun generateOverrideUiId(): String = UUID.randomUUID().toString()

val OverrideRuleTypePresets = listOf(
    "DOMAIN",
    "DOMAIN-SUFFIX",
    "DOMAIN-KEYWORD",
    "DOMAIN-WILDCARD",
    "DOMAIN-REGEX",
    "GEOSITE",
    "IP-CIDR",
    "IP-CIDR6",
    "IP-SUFFIX",
    "IP-ASN",
    "GEOIP",
    "SRC-GEOIP",
    "SRC-IP-ASN",
    "SRC-IP-CIDR",
    "SRC-IP-SUFFIX",
    "DST-PORT",
    "SRC-PORT",
    "IN-PORT",
    "IN-TYPE",
    "IN-USER",
    "IN-NAME",
    "PROCESS-PATH",
    "PROCESS-PATH-WILDCARD",
    "PROCESS-PATH-REGEX",
    "PROCESS-NAME",
    "PROCESS-NAME-WILDCARD",
    "PROCESS-NAME-REGEX",
    "UID",
    "NETWORK",
    "DSCP",
    "RULE-SET",
    "AND",
    "OR",
    "NOT",
    "SUB-RULE",
    "MATCH",
)

val OverrideProxyTypePresets = listOf(
    "direct",
    "dns",
    "http",
    "socks5",
    "ss",
    "ssr",
    "snell",
    "vmess",
    "vless",
    "trojan",
    "anytls",
    "mieru",
    "sudoku",
    "hysteria",
    "hysteria2",
    "tuic",
    "wireguard",
    "ssh",
    "masque",
    "trusttunnel",
)

val OverrideProxyGroupTypePresets = listOf(
    "select",
    "url-test",
    "fallback",
    "load-balance",
)

private val RuleExtraSupportedTypes = setOf(
    "IP-CIDR",
    "IP-CIDR6",
    "IP-SUFFIX",
    "IP-ASN",
    "GEOIP",
)

private val ProxyKnownKeys = setOf(
    "name",
    "type",
    "server",
    "port",
    "ip-version",
    "udp",
    "interface-name",
    "routing-mark",
    "tfo",
    "mptcp",
    "dialer-proxy",
)

private val ProxyGroupKnownKeys = setOf(
    "name",
    "type",
    "proxies",
    "use",
    "url",
    "interval",
    "lazy",
    "timeout",
    "max-failed-times",
    "disable-udp",
    "interface-name",
    "routing-mark",
    "include-all",
    "include-all-proxies",
    "include-all-providers",
    "filter",
    "exclude-filter",
    "exclude-type",
    "expected-status",
    "hidden",
    "icon",
)

fun supportsRuleExtra(ruleType: String): Boolean {
    return RuleExtraSupportedTypes.contains(ruleType.trim().uppercase())
}

fun <T> resolveInitialEditorMode(
    availableModes: List<OverrideListEditorMode>,
    values: OverrideListModeValues<T>,
): OverrideListEditorMode {
    return availableModes.firstOrNull { mode ->
        when (val currentValue = values.valueFor(mode)) {
            null -> false
            is Collection<*> -> currentValue.isNotEmpty()
            is Map<*, *> -> currentValue.isNotEmpty()
            else -> true
        }
    } ?: availableModes.firstOrNull() ?: OverrideListEditorMode.Replace
}

fun <T> reorderDraftList(
    value: List<T>,
    fromIndex: Int,
    toIndex: Int,
): List<T> {
    if (fromIndex !in value.indices) {
        return value.toList()
    }
    val reorderedValues = value.toMutableList()
    val movingValue = reorderedValues.removeAt(fromIndex)
    reorderedValues.add(toIndex.coerceIn(0, reorderedValues.size), movingValue)
    return reorderedValues
}

fun toOrderedJsonElementMap(value: Map<String, JsonElement>): LinkedHashMap<String, JsonElement> {
    val orderedMap = LinkedHashMap<String, JsonElement>(value.size)
    value.forEach { (key, element) ->
        orderedMap[key] = element
    }
    return orderedMap
}

fun toOrderedObjectMap(
    value: Map<String, Map<String, JsonElement>>?,
): LinkedHashMap<String, Map<String, JsonElement>>? {
    return value?.let { source ->
        LinkedHashMap<String, Map<String, JsonElement>>(source.size).apply {
            source.forEach { (key, fields) ->
                put(key, toOrderedJsonElementMap(fields))
            }
        }
    }
}

fun toOrderedSubRuleMap(
    value: Map<String, List<String>>?,
): LinkedHashMap<String, List<String>>? {
    return value?.let { source ->
        LinkedHashMap<String, List<String>>(source.size).apply {
            source.forEach { (key, rules) ->
                put(key, rules.toList())
            }
        }
    }
}

fun parseRuleDrafts(value: List<String>?): List<OverrideRuleDraft> {
    return value.orEmpty().map(::parseRuleDraft)
}

fun formatRuleDrafts(value: List<OverrideRuleDraft>): List<String>? {
    return value
        .map(::formatRuleDraft)
        .map(String::trim)
        .filter(String::isNotBlank)
        .ifEmpty { null }
}

fun parseRuleDraft(rawRule: String): OverrideRuleDraft {
    val parts = splitRuleTokens(rawRule.trim())
    if (parts.isEmpty()) {
        return OverrideRuleDraft()
    }
    val type = parts.first()
    if (type.equals("MATCH", ignoreCase = true)) {
        return OverrideRuleDraft(
            type = type.uppercase(),
            target = parts.getOrNull(1).orEmpty(),
            extras = parts.drop(2),
        )
    }
    return OverrideRuleDraft(
        type = type.uppercase(),
        payload = parts.getOrNull(1).orEmpty(),
        target = parts.getOrNull(2).orEmpty(),
        extras = parts.drop(3),
    )
}

fun formatRuleDraft(draft: OverrideRuleDraft): String {
    val normalizedType = draft.type.trim().uppercase()
    if (normalizedType.isBlank()) {
        return ""
    }
    val cleanedExtras = draft.extras
        .map(String::trim)
        .filter(String::isNotBlank)

    if (normalizedType == "MATCH") {
        return buildList {
            add(normalizedType)
            draft.target.trim().takeIf(String::isNotBlank)?.let(::add)
            addAll(cleanedExtras)
        }.joinToString(",")
    }

    return buildList {
        add(normalizedType)
        draft.payload.trim().takeIf(String::isNotBlank)?.let(::add)
        draft.target.trim().takeIf(String::isNotBlank)?.let(::add)
        addAll(cleanedExtras)
    }.joinToString(",")
}

fun splitRuleTokens(rawRule: String): List<String> {
    if (rawRule.isBlank()) {
        return emptyList()
    }
    val tokens = mutableListOf<String>()
    val currentToken = StringBuilder()
    var parenthesesDepth = 0

    rawRule.forEach { char ->
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
    return tokens.filter { it.isNotEmpty() }
}

fun parseProxyDrafts(value: List<Map<String, JsonElement>>?): List<OverrideProxyDraft> {
    return value.orEmpty().map(::parseProxyDraft)
}

fun formatProxyDrafts(value: List<OverrideProxyDraft>): List<Map<String, JsonElement>>? {
    return value
        .map(::formatProxyDraft)
        .filter(Map<String, JsonElement>::isNotEmpty)
        .ifEmpty { null }
}

fun parseProxyDraft(value: Map<String, JsonElement>): OverrideProxyDraft {
    return OverrideProxyDraft(
        name = value.stringField("name").orEmpty(),
        type = value.stringField("type").orEmpty().ifBlank { "ss" },
        server = value.stringField("server").orEmpty(),
        port = value.intField("port"),
        ipVersion = value.stringField("ip-version").orEmpty(),
        udp = value.booleanField("udp"),
        interfaceName = value.stringField("interface-name").orEmpty(),
        routingMark = value.intField("routing-mark"),
        tfo = value.booleanField("tfo"),
        mptcp = value.booleanField("mptcp"),
        dialerProxy = value.stringField("dialer-proxy").orEmpty(),
        extraFields = toOrderedJsonElementMap(value.filterKeys { it !in ProxyKnownKeys }),
    )
}

fun formatProxyDraft(value: OverrideProxyDraft): Map<String, JsonElement> {
    return linkedMapOf<String, JsonElement>().apply {
        putAll(value.extraFields.filterKeys(String::isNotBlank))
        putStringField("name", value.name)
        putStringField("type", value.type)
        putStringField("server", value.server)
        putIntField("port", value.port)
        putStringField("ip-version", value.ipVersion)
        putBooleanField("udp", value.udp)
        putStringField("interface-name", value.interfaceName)
        putIntField("routing-mark", value.routingMark)
        putBooleanField("tfo", value.tfo)
        putBooleanField("mptcp", value.mptcp)
        putStringField("dialer-proxy", value.dialerProxy)
    }
}

fun parseProxyGroupDrafts(value: List<Map<String, JsonElement>>?): List<OverrideProxyGroupDraft> {
    return value.orEmpty().map(::parseProxyGroupDraft)
}

fun formatProxyGroupDrafts(value: List<OverrideProxyGroupDraft>): List<Map<String, JsonElement>>? {
    return value
        .map(::formatProxyGroupDraft)
        .filter(Map<String, JsonElement>::isNotEmpty)
        .ifEmpty { null }
}

fun parseProxyGroupDraft(value: Map<String, JsonElement>): OverrideProxyGroupDraft {
    return OverrideProxyGroupDraft(
        name = value.stringField("name").orEmpty(),
        type = value.stringField("type").orEmpty().ifBlank { "select" },
        proxies = value.stringListField("proxies"),
        use = value.stringListField("use"),
        url = value.stringField("url").orEmpty(),
        interval = value.intField("interval"),
        lazy = value.booleanField("lazy"),
        timeout = value.intField("timeout"),
        maxFailedTimes = value.intField("max-failed-times"),
        disableUdp = value.booleanField("disable-udp"),
        interfaceName = value.stringField("interface-name").orEmpty(),
        routingMark = value.intField("routing-mark"),
        includeAll = value.booleanField("include-all"),
        includeAllProxies = value.booleanField("include-all-proxies"),
        includeAllProviders = value.booleanField("include-all-providers"),
        filter = value.stringField("filter").orEmpty(),
        excludeFilter = value.stringField("exclude-filter").orEmpty(),
        excludeType = value.stringField("exclude-type").orEmpty(),
        expectedStatus = value.stringField("expected-status").orEmpty(),
        hidden = value.booleanField("hidden"),
        icon = value.stringField("icon").orEmpty(),
        extraFields = toOrderedJsonElementMap(value.filterKeys { it !in ProxyGroupKnownKeys }),
    )
}

fun formatProxyGroupDraft(value: OverrideProxyGroupDraft): Map<String, JsonElement> {
    return linkedMapOf<String, JsonElement>().apply {
        putAll(value.extraFields.filterKeys(String::isNotBlank))
        putStringField("name", value.name)
        putStringField("type", value.type)
        putStringListField("proxies", value.proxies)
        putStringListField("use", value.use)
        putStringField("url", value.url)
        putIntField("interval", value.interval)
        putBooleanField("lazy", value.lazy)
        putIntField("timeout", value.timeout)
        putIntField("max-failed-times", value.maxFailedTimes)
        putBooleanField("disable-udp", value.disableUdp)
        putStringField("interface-name", value.interfaceName)
        putIntField("routing-mark", value.routingMark)
        putBooleanField("include-all", value.includeAll)
        putBooleanField("include-all-proxies", value.includeAllProxies)
        putBooleanField("include-all-providers", value.includeAllProviders)
        putStringField("filter", value.filter)
        putStringField("exclude-filter", value.excludeFilter)
        putStringField("exclude-type", value.excludeType)
        putStringField("expected-status", value.expectedStatus)
        putBooleanField("hidden", value.hidden)
        putStringField("icon", value.icon)
    }
}

fun parseKeyedObjectDrafts(
    value: Map<String, Map<String, JsonElement>>?,
): List<OverrideKeyedObjectDraft> {
    return value.orEmpty().map { (key, fields) ->
        OverrideKeyedObjectDraft(
            key = key,
            fields = toOrderedJsonElementMap(fields),
        )
    }
}

fun formatKeyedObjectDrafts(
    value: List<OverrideKeyedObjectDraft>,
): Map<String, Map<String, JsonElement>>? {
    val linkedMap = linkedMapOf<String, Map<String, JsonElement>>()
    value.forEach { draft ->
        val normalizedKey = draft.key.trim()
        if (normalizedKey.isNotEmpty()) {
            linkedMap[normalizedKey] = toOrderedJsonElementMap(draft.fields)
        }
    }
    return linkedMap.ifEmpty { null }
}

fun parseSubRuleGroupDrafts(
    value: Map<String, List<String>>?,
): List<OverrideSubRuleGroupDraft> {
    return value.orEmpty().map { (key, rules) ->
        OverrideSubRuleGroupDraft(
            name = key,
            rules = rules.toList(),
        )
    }
}

fun formatSubRuleGroupDrafts(
    value: List<OverrideSubRuleGroupDraft>,
): Map<String, List<String>>? {
    val linkedMap = linkedMapOf<String, List<String>>()
    value.forEach { draft ->
        val normalizedName = draft.name.trim()
        if (normalizedName.isNotEmpty()) {
            linkedMap[normalizedName] = draft.rules
                .map(String::trim)
                .filter(String::isNotBlank)
        }
    }
    return linkedMap.ifEmpty { null }
}

private fun MutableMap<String, JsonElement>.putStringField(
    key: String,
    value: String,
) {
    value.trim().takeIf(String::isNotBlank)?.let {
        put(key, JsonPrimitive(it))
    }
}

private fun MutableMap<String, JsonElement>.putIntField(
    key: String,
    value: Int?,
) {
    value?.let {
        put(key, JsonPrimitive(it))
    }
}

private fun MutableMap<String, JsonElement>.putBooleanField(
    key: String,
    value: Boolean?,
) {
    value?.let {
        put(key, JsonPrimitive(it))
    }
}

private fun MutableMap<String, JsonElement>.putStringListField(
    key: String,
    value: List<String>,
) {
    val cleanedValue = value.map(String::trim).filter(String::isNotBlank)
    if (cleanedValue.isNotEmpty()) {
        put(key, JsonArray(cleanedValue.map(::JsonPrimitive)))
    }
}

private fun Map<String, JsonElement>.stringField(key: String): String? {
    val element = get(key) ?: return null
    return if (element is JsonPrimitive && element.isString) {
        element.content
    } else {
        element.toString()
    }
}

private fun Map<String, JsonElement>.intField(key: String): Int? {
    return get(key)?.jsonPrimitive?.intOrNull
}

private fun Map<String, JsonElement>.booleanField(key: String): Boolean? {
    return get(key)?.jsonPrimitive?.booleanOrNull
}

private fun Map<String, JsonElement>.stringListField(key: String): List<String> {
    val element = get(key) ?: return emptyList()
    return when (element) {
        is JsonArray -> element.jsonArray.mapNotNull { item ->
            val primitive = item as? JsonPrimitive ?: return@mapNotNull null
            primitive.content.trim().takeIf(String::isNotBlank)
        }

        is JsonPrimitive -> listOf(element.content).filter(String::isNotBlank)
        else -> emptyList()
    }
}
