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

@file:Suppress("UnusedSymbol", "MayBeConstant")

package com.github.yumelira.yumebox.presentation.theme

object TrafficChartConfig {
    private const val firstSpeedBound = 0.5 * 1024 * 1024
    private const val secondSpeedBound = 5.0 * 1024 * 1024
    private const val thirdSpeedBound = 40.0 * 1024 * 1024

    const val minimumVisibleFraction = 0.02f

    const val defaultSampleLimit = 24

    fun calculateBarFraction(speedBytes: Long): Float =
        when {
            speedBytes <= 0 -> minimumVisibleFraction
            speedBytes < firstSpeedBound -> {
                val ratio = (speedBytes / firstSpeedBound).coerceIn(0.0, 1.0)
                (ratio * 0.4).toFloat().coerceAtLeast(minimumVisibleFraction)
            }

            speedBytes < secondSpeedBound -> {
                val ratio =
                    ((speedBytes - firstSpeedBound) / (secondSpeedBound - firstSpeedBound))
                        .coerceIn(0.0, 1.0)
                (0.4 + ratio * 0.3).toFloat()
            }

            speedBytes < thirdSpeedBound -> {
                val ratio =
                    ((speedBytes - secondSpeedBound) / (thirdSpeedBound - secondSpeedBound))
                        .coerceIn(0.0, 1.0)
                (0.7 + ratio * 0.3).toFloat()
            }

            else -> 1.0f
        }
}
