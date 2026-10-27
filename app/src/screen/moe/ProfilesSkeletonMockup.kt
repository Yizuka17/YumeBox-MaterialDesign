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

@file:Suppress("FunctionName")

package com.github.yumelira.yumebox.screen.moe


import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ProfilesSkeletonMockup(modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    val palette = mockupPalette()

    val transition = rememberInfiniteTransition(label = "profiles_sheet")
    val sheetProgress by
    transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 3600
                        0f at 0
                        0f at 900
                        1f at 1300 using FastOutSlowInEasing
                        1f at 2900
                        0f at 3300 using FastOutSlowInEasing
                        0f at 3600
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "sheet_progress",
    )

    MockupPhoneFrame(palette = palette, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.maskStrong)
                )
                AddButtonGlyph()
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { index ->
                    ProfileCardMock(
                        index = index,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colorScheme.surface)
                        .border(1.dp, palette.frameBorder, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(4) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (it == 2) colorScheme.primary else palette.mask)
                    )
                }
            }
        }

        Box(modifier = Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(15.dp))) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.40f * sheetProgress))
            )

            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.62f)
                        .graphicsLayer { translationY = (1f - sheetProgress) * size.height }
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(colorScheme.surface)
                        .border(
                            1.dp,
                            palette.frameBorder,
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        )
                        .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .width(26.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(palette.mask)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(palette.maskStrong)
                        )
                        Box(
                            Modifier
                                .width(56.dp)
                                .height(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(palette.maskStrong)
                        )
                        Box(
                            Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(palette.maskStrong)
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.frameBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .width(46.dp)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(palette.maskStrong)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .width(34.dp)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(palette.mask)
                                )
                                Box(
                                    Modifier
                                        .width(7.dp)
                                        .height(11.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(palette.mask)
                                )
                            }
                        }
                    }
                    repeat(3) { index ->
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.recessed)
                                    .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(if (index == 2) 0.42f else 0.5f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(palette.mask)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCardMock(index: Int, modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    val opacity = AppTheme.opacity
    val palette = mockupPalette()

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.cardVariant)
                .border(1.dp, palette.frameBorder, RoundedCornerShape(12.dp))
                .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(9.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(palette.maskStrong)
                    )
                    Box(
                        Modifier
                            .width(26.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(palette.maskStrong)
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .width(22.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (index == 0) colorScheme.primary else palette.maskStrong)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(1.5.dp),
                        contentAlignment =
                            if (index == 0) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        Box(Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(colorScheme.surface))
                    }
                }
            }

            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(palette.maskStrong)
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(palette.maskStrong)
                )
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colorScheme.onSurface.copy(alpha = opacity.surfaceSoft))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(palette.maskStrong))
                    Box(Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(palette.maskStrong))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (index > 0) {
                        Box(
                            Modifier
                                .width(26.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colorScheme.primary.copy(alpha = opacity.subtleStrong))
                        )
                    }
                    Box(
                        Modifier
                            .width(26.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.maskStrong)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddButtonGlyph(modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme

    val transition = rememberInfiniteTransition(label = "add_button")
    val tapScale by
    transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 3600
                        1f at 0
                        1f at 700
                        0.85f at 850 using FastOutSlowInEasing
                        1f at 1000
                        1f at 3600
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "add_tap_scale",
    )
    val rippleScale by
    transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 2.2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "add_ripple_scale",
    )
    val rippleAlpha by
    transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "add_ripple_alpha",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .graphicsLayer {
                        scaleX = rippleScale
                        scaleY = rippleScale
                        alpha = rippleAlpha
                    }
                    .size(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(colorScheme.primary)
        )

        Box(
            modifier =
                Modifier
                    .graphicsLayer {
                        scaleX = tapScale
                        scaleY = tapScale
                    }
                    .size(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier
                .width(8.dp)
                .height(2.dp)
                .background(colorScheme.onPrimary))
            Box(Modifier
                .width(2.dp)
                .height(8.dp)
                .background(colorScheme.onPrimary))
        }
    }
}
