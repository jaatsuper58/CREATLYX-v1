package com.chattlyx.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.core.designsystem.theme.LocalChattlyxColors

/** Message lifecycle states rendered as ticks (MSG-02). */
enum class DeliveryTickState { PENDING, SENT, DELIVERED, READ, FAILED }

/**
 * Hand-drawn ticks (no icon dependency): clock = pending, single check =
 * sent, double check = delivered, double check in accent = read, red exclamation
 * path = failed.
 */
@Composable
fun DeliveryTicks(
    state: DeliveryTickState,
    modifier: Modifier = Modifier,
) {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = LocalChattlyxColors.current.readReceipt
    val error = MaterialTheme.colorScheme.error

    val tickColor = when (state) {
        DeliveryTickState.READ -> accent
        DeliveryTickState.FAILED -> error
        else -> neutral
    }

    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = Stroke(width = size.width / 10f, cap = StrokeCap.Round)

        when (state) {
            DeliveryTickState.PENDING -> {
                drawCircle(color = neutral, style = Stroke(width = size.width / 12f))
                drawLine(
                    color = neutral,
                    start = Offset(size.width / 2f, size.height / 4f),
                    end = Offset(size.width / 2f, size.height / 2f),
                    strokeWidth = size.width / 12f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = neutral,
                    start = Offset(size.width / 2f, size.height / 2f),
                    end = Offset(size.width * 0.68f, size.height * 0.62f),
                    strokeWidth = size.width / 12f,
                    cap = StrokeCap.Round,
                )
            }

            DeliveryTickState.SENT -> drawTick(this, stroke, tickColor, offsetX = 0.18f)

            DeliveryTickState.DELIVERED, DeliveryTickState.READ -> {
                drawTick(this, stroke, tickColor, offsetX = 0.02f)
                drawTick(this, stroke, tickColor, offsetX = 0.30f)
            }

            DeliveryTickState.FAILED -> {
                drawLine(
                    color = tickColor,
                    start = Offset(size.width / 2f, size.height * 0.18f),
                    end = Offset(size.width / 2f, size.height * 0.62f),
                    strokeWidth = size.width / 9f,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = tickColor,
                    radius = size.width / 12f,
                    center = Offset(size.width / 2f, size.height * 0.82f),
                )
            }
        }
    }
}

private fun drawTick(
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    stroke: Stroke,
    color: Color,
    offsetX: Float,
) {
    with(drawScope) {
        val w = size.width
        val h = size.height
        val dx = w * offsetX
        drawLine(
            color = color,
            start = Offset(dx + w * 0.05f, h * 0.55f),
            end = Offset(dx + w * 0.22f, h * 0.75f),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
        drawLine(
            color = color,
            start = Offset(dx + w * 0.22f, h * 0.75f),
            end = Offset(dx + w * 0.55f, h * 0.30f),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeliveryTicksPreview() {
    ChattlyxTheme {
        Row {
            DeliveryTickState.entries.forEach { state ->
                DeliveryTicks(state = state)
            }
        }
    }
}
