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

@file:Suppress("UnusedSymbol")

package com.github.yumelira.yumebox.core.util


import android.annotation.SuppressLint
import com.github.yumelira.yumebox.core.model.Traffic

private val IEC_UNITS = arrayOf("Bytes", "KiB", "MiB", "GiB")

@SuppressLint("DefaultLocale")
private fun trafficString(scaled: Long): String {
    val s = scaleBytes(scaled, maxRank = 3)
    return if (s.rank == 0) "$scaled Bytes"
    else String.format("%.2f %s", s.value, IEC_UNITS[s.rank])
}

fun decodeTrafficValue(value: Long): Long {
    val type = (value ushr 30) and 0x3
    val payload = value and 0x3FFFFFFFL

    return when (type) {
        0L -> payload
        1L -> (payload * 1024L) / 100L
        2L -> (payload * 1024L * 1024L) / 100L
        3L -> (payload * 1024L * 1024L * 1024L) / 100L
        else -> 0L
    }
}

fun encodeTrafficValue(bytes: Long): Long {
    val value = bytes.coerceAtLeast(0L)
    return when {
        value < 0x40000000L -> value // type 0: raw bytes
        value < 0x40000000L * 1024L / 100L -> // type 1: KiB/100
            (1L shl 30) or ((value * 100L / 1024L) and 0x3FFFFFFFL)

        value < 0x40000000L * 1024L * 1024L / 100L -> // type 2: MiB/100
            (2L shl 30) or ((value * 100L / (1024L * 1024L)) and 0x3FFFFFFFL)

        else -> // type 3: GiB/100
            (3L shl 30) or ((value * 100L / (1024L * 1024L * 1024L)).coerceAtMost(0x3FFFFFFFL))
    }
}

private fun scaleTraffic(value: Long): Long = decodeTrafficValue(value)

fun Traffic.trafficUpload(): String = trafficString(scaleTraffic(this ushr 32))

fun Traffic.trafficDownload(): String = trafficString(scaleTraffic(this and 0xFFFFFFFFL))

fun Traffic.trafficTotal(): String {
    val upload = scaleTraffic(this ushr 32)
    val download = scaleTraffic(this and 0xFFFFFFFFL)

    return trafficString(upload + download)
}
