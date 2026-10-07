package com.chattlyx.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.chattlyx.core.designsystem.theme.ChattlyxTheme

/**
 * Outlined text field with error + supporting text slots, 12 dp corners.
 * Used by profile setup (AUTH-04), phone entry (AUTH-02), settings forms.
 */
@Composable
fun ChattlyxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL", locale = "ar")
@Composable
private fun ChattlyxTextFieldPreview() {
    ChattlyxTheme {
        Column {
            ChattlyxTextField(
                value = "Asha",
                onValueChange = {},
                label = "Display name",
                modifier = Modifier.fillMaxWidth(),
            )
            ChattlyxTextField(
                value = "",
                onValueChange = {},
                label = "About",
                supportingText = "Required",
                isError = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
