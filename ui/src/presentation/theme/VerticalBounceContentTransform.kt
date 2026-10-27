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

package com.github.yumelira.yumebox.presentation.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/** Vertical layered bounce used by tablet split-shell pane switches. */
fun verticalBounceContentTransform(forward: Boolean): ContentTransform {
    val enterSpring =
        spring<IntOffset>(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessLow,
        )
    val exitSpring =
        spring<IntOffset>(
            dampingRatio = 0.92f,
            stiffness = 280f,
        )
    val fadeInSpec = tween<Float>(durationMillis = 340, easing = FastOutSlowInEasing)
    val fadeOutSpec = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
    val transform =
        if (forward) {
            (slideInVertically(animationSpec = enterSpring) { fullHeight ->
                fullHeight / 10
            } + fadeIn(animationSpec = fadeInSpec)) togetherWith
                    (slideOutVertically(animationSpec = exitSpring) { fullHeight ->
                        -fullHeight / 14
                    } + fadeOut(animationSpec = fadeOutSpec))
        } else {
            (slideInVertically(animationSpec = enterSpring) { fullHeight ->
                -fullHeight / 10
            } + fadeIn(animationSpec = fadeInSpec)) togetherWith
                    (slideOutVertically(animationSpec = exitSpring) { fullHeight ->
                        fullHeight / 14
                    } + fadeOut(animationSpec = fadeOutSpec))
        }
    return ContentTransform(
        targetContentEnter = transform.targetContentEnter,
        initialContentExit = transform.initialContentExit,
        sizeTransform = SizeTransform(clip = false),
    )
}
