package com.chattlyx.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Original outline glyphs drawn for ChattlyX (master spec Section 5.5: ship
 * only used glyphs; no icon-font/extended-icon dependency). Placeholder-grade
 * artwork — final brand icons replace these paths without API changes.
 */
object ChattlyxIcons {

    private const val STROKE = 1.8f

    val Chat: ImageVector by lazy {
        outlineIcon("ChattlyxChat") {
            // Rounded speech bubble with a left tail.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(5f, 5f)
                lineTo(19f, 5f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 7f)
                lineTo(21f, 14f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 16f)
                lineTo(11f, 16f)
                lineTo(7f, 20f)
                lineTo(7f, 16f)
                lineTo(5f, 16f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 14f)
                lineTo(3f, 7f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 5f)
                close()
            }
        }
    }

    val Call: ImageVector by lazy {
        outlineIcon("ChattlyxCall") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 4f)
                lineTo(8f, 4f)
                lineTo(9.5f, 8f)
                lineTo(7.5f, 9.5f)
                curveTo(8.5f, 12f, 10.5f, 14f, 13f, 15f)
                lineTo(14.5f, 13f)
                lineTo(18.5f, 14.5f)
                lineTo(18.5f, 17.5f)
                curveTo(18.5f, 18.5f, 17.5f, 19.5f, 16.5f, 19.5f)
                curveTo(9.5f, 19f, 5f, 14.5f, 4.5f, 7.5f)
                curveTo(4.5f, 6.5f, 4.5f, 4f, 5f, 4f)
                close()
            }
        }
    }

    val Contacts: ImageVector by lazy {
        outlineIcon("ChattlyxContacts") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Head
                moveTo(12f, 4f)
                arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 11f)
                arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 4f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
            ) {
                // Shoulders
                moveTo(4.5f, 20f)
                curveTo(5f, 16.5f, 8f, 14.5f, 12f, 14.5f)
                curveTo(16f, 14.5f, 19f, 16.5f, 19.5f, 20f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        outlineIcon("ChattlyxSettings") {
            // Three sliders — a friendly alternative to a cog.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round) {
                moveTo(4f, 7f); lineTo(20f, 7f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 17f); lineTo(20f, 17f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE) {
                moveTo(9f, 5f); arcTo(2f, 2f, 0f, true, false, 9f, 9f); arcTo(2f, 2f, 0f, true, false, 9f, 5f); close()
                moveTo(15f, 10f); arcTo(2f, 2f, 0f, true, false, 15f, 14f); arcTo(2f, 2f, 0f, true, false, 15f, 10f); close()
                moveTo(7f, 15f); arcTo(2f, 2f, 0f, true, false, 7f, 19f); arcTo(2f, 2f, 0f, true, false, 7f, 15f); close()
            }
        }
    }

    val Lock: ImageVector by lazy {
        outlineIcon("ChattlyxLock") {
            // Shackle.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round) {
                moveTo(8f, 10f)
                lineTo(8f, 7f)
                arcTo(4f, 4f, 0f, false, false, 16f, 7f)
                lineTo(16f, 10f)
            }
            // Body.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineJoin = StrokeJoin.Round) {
                moveTo(6f, 10f)
                lineTo(18f, 10f)
                lineTo(18f, 20f)
                lineTo(6f, 20f)
                close()
            }
        }
    }

    val PushPin: ImageVector by lazy {
        outlineIcon("ChattlyxPushPin") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 3.5f)
                lineTo(15f, 3.5f)
                lineTo(15f, 9f)
                lineTo(17.5f, 11.5f)
                lineTo(17.5f, 13f)
                lineTo(13f, 13f)
                lineTo(13f, 19f)
                lineTo(12f, 20.5f)
                lineTo(11f, 19f)
                lineTo(11f, 13f)
                lineTo(6.5f, 13f)
                lineTo(6.5f, 11.5f)
                lineTo(9f, 9f)
                close()
            }
        }
    }

    val NotificationsOff: ImageVector by lazy {
        outlineIcon("ChattlyxNotificationsOff") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Bell
                moveTo(6.5f, 16f)
                lineTo(6.5f, 10.5f)
                arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17.5f, 10.5f)
                lineTo(17.5f, 16f)
                lineTo(19.5f, 18f)
                lineTo(4.5f, 18f)
                close()
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round) {
                // Clapper
                moveTo(10.5f, 20.5f)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 13.5f, 20.5f)
                // Slash
                moveTo(4f, 4f)
                lineTo(20f, 20f)
            }
        }
    }

    /** MED-* composer attach affordance. */
    val Paperclip: ImageVector by lazy {
        outlineIcon("ChattlyxPaperclip") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(20f, 11f)
                lineTo(12.5f, 18.5f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.9f, 12.9f)
                lineTo(13.5f, 6.3f)
                arcTo(2.4f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16.9f, 9.7f)
                lineTo(10.5f, 16f)
            }
        }
    }

    /** MED-03 voice note recording. */
    val Mic: ImageVector by lazy {
        outlineIcon("ChattlyxMic") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.5f, 5.5f)
                lineTo(14.5f, 11f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9.5f, 11f)
                lineTo(9.5f, 5.5f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 3f)
                close()
                moveTo(6.5f, 11f)
                arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17.5f, 11f)
                moveTo(12f, 16.5f)
                lineTo(12f, 20f)
            }
        }
    }

    /** MED-01 image bubble placeholder. */
    val Image: ImageVector by lazy {
        outlineIcon("ChattlyxImage") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 5f)
                lineTo(20f, 5f)
                lineTo(20f, 19f)
                lineTo(4f, 19f)
                close()
                moveTo(4f, 15f)
                lineTo(9f, 10f)
                lineTo(13f, 14f)
                lineTo(16f, 11f)
                lineTo(20f, 15f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
            ) {
                moveTo(8.5f, 8.5f)
                arcTo(0.8f, 0.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9.3f, 9.3f)
                arcTo(0.8f, 0.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.5f, 10.1f)
                arcTo(0.8f, 0.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7.7f, 9.3f)
                arcTo(0.8f, 0.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.5f, 8.5f)
                close()
            }
        }
    }

    /** MED-03 generic document bubble. */
    val FileDoc: ImageVector by lazy {
        outlineIcon("ChattlyxFileDoc") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 3f)
                lineTo(14f, 3f)
                lineTo(18f, 7f)
                lineTo(18f, 21f)
                lineTo(6f, 21f)
                close()
                moveTo(14f, 3f)
                lineTo(14f, 7f)
                lineTo(18f, 7f)
            }
        }
    }

    /** MED-04 tap-to-download affordance. */
    val Download: ImageVector by lazy {
        outlineIcon("ChattlyxDownload") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4f)
                lineTo(12f, 15f)
                moveTo(8f, 11f)
                lineTo(12f, 15f)
                lineTo(16f, 11f)
                moveTo(5f, 19f)
                lineTo(19f, 19f)
            }
        }
    }

    /** MED-03 voice playback. */
    val Play: ImageVector by lazy {
        outlineIcon("ChattlyxPlay") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8f, 5.5f)
                lineTo(19f, 12f)
                lineTo(8f, 18.5f)
                close()
            }
        }
    }

    /** MED-03 voice recording stop / playback pause. */
    val Stop: ImageVector by lazy {
        outlineIcon("ChattlyxStop") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 7f)
                lineTo(17f, 7f)
                lineTo(17f, 17f)
                lineTo(7f, 17f)
                close()
            }
        }
    }

    private fun outlineIcon(
        name: String,
        builder: ImageVector.Builder.() -> Unit,
    ): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(builder).build()
}
