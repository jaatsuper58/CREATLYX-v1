package com.chattlyx.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii tokens (Section 5.5): 8 / 12 / 18 / 28 dp. */
val ChattlyxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

/** Chat bubble: 18 dp radius with a 4 dp "tail" corner near the sender edge. */
val BubbleIncomingShape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = 4.dp,
    bottomEnd = 18.dp,
)

val BubbleOutgoingShape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = 18.dp,
    bottomEnd = 4.dp,
)

/** Bubbles without a tail keep all corners round (grouped messages). */
val BubbleGroupedShape = RoundedCornerShape(18.dp)
