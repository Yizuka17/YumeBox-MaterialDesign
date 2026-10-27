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


import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI

fun parseInetSocketAddress(address: String): InetSocketAddress {
    val uri = runCatching { URI("tcp://$address") }
        .getOrElse { throw IllegalArgumentException("Invalid socket address: $address", it) }
    val host = uri.host ?: throw IllegalArgumentException("Socket address requires a host: $address")
    val port = uri.port.takeIf { it in 1..65535 }
        ?: throw IllegalArgumentException("Socket address requires a valid port: $address")
    return InetSocketAddress(InetAddress.getByName(host), port)
}
