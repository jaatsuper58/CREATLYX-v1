package com.chattlyx.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chattlyx.core.designsystem.theme.ChattlyxTheme

/**
 * Six-digit OTP input (AUTH-03). Hidden native field drives six visible
 * boxes; auto-focuses on entry and reports completion once.
 */
@Composable
fun OtpInputField(
    otp: String,
    onOtpChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = OTP_LENGTH,
    enabled: Boolean = true,
    error: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val fieldValue = remember(otp) { TextFieldValue(otp, TextRange(otp.length)) }

    LaunchedEffect(Unit) {
        if (enabled) focusRequester.requestFocus()
    }

    Row(
        modifier = modifier.semantics { contentDescription = "Verification code" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    val digits = newValue.text.filter(Char::isDigit).take(length)
                    if (digits != otp) onOtpChange(digits)
                    if (digits.length == length) onComplete(digits)
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .matchParentSizeCompat(),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
                cursorBrush = SolidColor(Color.Transparent),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(length) { index ->
                    OtpBox(
                        digit = otp.getOrNull(index)?.toString(),
                        isActive = index == otp.length,
                        error = error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OtpBox(
    digit: String?,
    isActive: Boolean,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        error -> MaterialTheme.colorScheme.error
        isActive -> MaterialTheme.colorScheme.primary
        digit != null -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .height(56.dp)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit.orEmpty(),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier =
    this.then(Modifier.fillMaxWidth().height(56.dp))

const val OTP_LENGTH = 6

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL", locale = "ar")
@Composable
private fun OtpInputFieldPreview() {
    ChattlyxTheme {
        OtpInputField(otp = "123", onOtpChange = {}, onComplete = {}, modifier = Modifier.fillMaxWidth())
    }
}
