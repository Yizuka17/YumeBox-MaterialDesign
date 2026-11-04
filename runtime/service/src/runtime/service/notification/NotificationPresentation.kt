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

package com.github.yumelira.yumebox.runtime.service.notification

import com.github.yumelira.yumebox.common.util.formatBytes
import com.github.yumelira.yumebox.common.util.formatSpeed
import com.github.yumelira.yumebox.runtime.service.profile.Imported
import com.github.yumelira.yumebox.data.model.NotificationDisplay
import com.github.yumelira.yumebox.data.model.NotificationValues
import java.time.Instant
import java.time.ZoneId
import com.github.yumelira.yumebox.core.locale.YumeTxt

/** What the ongoing notification renders. */
internal sealed class NotificationPresentation {
    abstract val title: String
    abstract val content: String
    abstract val expandedText: String
    abstract val right: String

    /** Traffic figures; also feeds the Super Island payload. */
    data class Running(
        override val title: String,
        override val content: String,
        override val expandedText: String,
        val compactTraffic: String,
        val currentNode: String?,
        override val right: String = "",
    ) : NotificationPresentation()

    /** No traffic figures (traffic notification disabled). */
    data class Status(
        override val title: String,
        override val content: String,
        override val expandedText: String,
        override val right: String = "",
    ) : NotificationPresentation()
}

internal object NotificationPresentationFactory {
    fun createRunning(
        profileName: String,
        profile: Imported?,
        currentNode: String?,
        trafficNow: Long,
        display: NotificationDisplay,
        forIsland: Boolean,
    ): NotificationPresentation.Running {
        val usageLine = buildUsageLine(profile)
        val node = currentNode ?: YumeTxt.Service.Notification.NoNode
        val traffic = buildCompactTrafficLine(trafficNow)
        val values = NotificationValues(
            profileName, YumeTxt.Service.Notification.Running, node, traffic,
            profile?.expire?.takeIf { it > 0L }?.let(::expireDate).orEmpty(),
        )
        val first = display.first.resolve(values, profileName)
        val second = display.second.resolve(values, usageLine)
        val right = display.right.resolve(values, if (forIsland) traffic else "")
        val defaultExpanded = listOf(second, YumeTxt.Service.Notification.CurrentNode.format(node))
        return NotificationPresentation.Running(
            title = first,
            content = second,
            expandedText = defaultExpanded.filter(String::isNotBlank).joinToString("\n"),
            compactTraffic = right,
            currentNode = currentNode,
            right = if (forIsland) "" else right,
        )
    }

    fun createStatus(profileName: String, status: String): NotificationPresentation.Status =
        NotificationPresentation.Status(profileName, status, status)

    fun configureStatus(
        profileName: String,
        profile: Imported?,
        display: NotificationDisplay,
    ): NotificationPresentation.Status {
        val status = YumeTxt.Service.Notification.Running
        val values = NotificationValues(profileName, status, "", "",
            profile?.expire?.takeIf { it > 0L }?.let(::expireDate).orEmpty())
        val title = display.first.resolve(values, profileName)
        val content = display.second.resolve(values, status)
        return NotificationPresentation.Status(title, content, content, display.right.resolve(values, ""))
    }

    /**
     * Values only, no labels, so the island can render it: `1.2 GB / 100 GB | 2026-08-31`. Falls back
     * to the profile name when the profile carries no subscription information.
     */
    private fun buildUsageLine(profile: Imported?): String {
        val used = profile?.let { (it.upload + it.download).coerceAtLeast(0L) } ?: 0L
        val total = profile?.total ?: 0L
        val usage =
            when {
                total > 0L -> "${formatBytes(used)} / ${formatBytes(total)}"
                used > 0L -> formatBytes(used)
                else -> profile?.name?.takeIf { it.isNotBlank() }.orEmpty()
            }
        val expire = profile?.expire?.takeIf { it > 0L }?.let { expireDate(it) }
        return listOfNotNull(usage.takeIf { it.isNotEmpty() }, expire).joinToString(" | ")
    }

    private fun expireDate(expireAt: Long): String =
        Instant.ofEpochMilli(expireAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun buildCompactTrafficLine(trafficNow: Long): String {
        val upNow = decodeTrafficHalf(trafficNow ushr 32)
        val downNow = decodeTrafficHalf(trafficNow and 0xFFFFFFFFL)
        return formatSpeed((upNow + downNow).coerceAtLeast(0L))
    }

    private fun decodeTrafficHalf(encoded: Long): Long {
        val type = (encoded ushr 30) and 0x3L
        val payload = encoded and 0x3FFFFFFFL
        return when (type.toInt()) {
            0 -> payload
            1 -> (payload * 1024L) / 100L
            2 -> (payload * 1024L * 1024L) / 100L
            3 -> (payload * 1024L * 1024L * 1024L) / 100L
            else -> 0L
        }
    }
}
