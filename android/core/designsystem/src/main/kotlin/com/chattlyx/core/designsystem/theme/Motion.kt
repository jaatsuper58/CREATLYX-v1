package com.chattlyx.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.staticCompositionLocalOf

/** Motion tokens (Section 5.5): 150-300ms standard easing, springs for send. */
object ChattlyxMotion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 200
    const val LONG_MS = 300

    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> sendSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}

/** When true, shimmer/infinite animations render statically (accessibility). */
val LocalReduceMotion = staticCompositionLocalOf { false }
