package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-performance, zero-lag Icy Frosted Glass Modifier.
 * Uses hardware-accelerated gradient reflections and translucent backdrops
 * to simulate crystalline frosty glass edges without expensive runtime blur shaders.
 */
fun Modifier.icyFrostedSurface(
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = IceGlassBg,
    borderColor: Color = IceFrostBorder,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = listOf(
                backgroundColor,
                backgroundColor.copy(alpha = (backgroundColor.alpha * 0.92f).coerceIn(0f, 1f))
            )
        )
    )
    .border(
        BorderStroke(
            borderWidth,
            Brush.linearGradient(
                colors = listOf(
                    borderColor,
                    borderColor.copy(alpha = 0.25f),
                    IceVioletBorder.copy(alpha = 0.20f)
                )
            )
        ),
        shape
    )

/**
 * Frosted Icy Pill Modifier for chips, badges, and action pills.
 */
fun Modifier.icyFrostedPill(
    cornerRadius: Dp = 20.dp,
    isActive: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = if (isActive) IceFrostBorderActive else IceFrostBorder
    val bgColor = if (isActive) IceGlassLight.copy(alpha = 0.35f) else IceGlassLight.copy(alpha = 0.15f)
    return this
        .clip(shape)
        .background(bgColor)
        .border(
            BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(borderColor, IceVioletBorder.copy(alpha = 0.3f))
                )
            ),
            shape
        )
}
