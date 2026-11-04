package com.github.yumelira.yumebox.data.model

import org.json.JSONObject

enum class NotificationField {
    Default, Profile, Status, Node, Traffic, Expiry, Custom, Hidden,
}

data class NotificationSlot(
    val field: NotificationField = NotificationField.Default,
    val text: String = "",
) {
    fun resolve(values: NotificationValues, default: String): String = when (field) {
        NotificationField.Default -> default
        NotificationField.Profile -> values.profile
        NotificationField.Status -> values.status
        NotificationField.Node -> values.node
        NotificationField.Traffic -> values.traffic
        NotificationField.Expiry -> values.expiry
        NotificationField.Custom -> text.trim()
        NotificationField.Hidden -> ""
    }
}

data class NotificationValues(
    val profile: String,
    val status: String,
    val node: String,
    val traffic: String,
    val expiry: String,
)

data class NotificationDisplay(
    val first: NotificationSlot = NotificationSlot(),
    val second: NotificationSlot = NotificationSlot(),
    val right: NotificationSlot = NotificationSlot(),
) {
    fun encode(): String = JSONObject().apply {
        listOf("first" to first, "second" to second, "right" to right).forEach { (key, slot) ->
            put(key, JSONObject().put("field", slot.field.name).put("text", slot.text))
        }
    }.toString()

    companion object {
        fun decode(text: String): NotificationDisplay {
            val json = runCatching { JSONObject(text) }.getOrNull()
            fun slot(key: String): NotificationSlot {
                val obj = json?.optJSONObject(key) ?: return NotificationSlot()
                val field = NotificationField.entries.firstOrNull { it.name == obj.optString("field") }
                    ?: NotificationField.Default
                return NotificationSlot(field, obj.optString("text").take(64))
            }
            return NotificationDisplay(slot("first"), slot("second"), slot("right"))
        }
    }
}
