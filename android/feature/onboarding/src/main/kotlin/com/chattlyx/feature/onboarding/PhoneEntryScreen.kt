package com.chattlyx.feature.onboarding

import android.content.res.Configuration
import android.telephony.TelephonyManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.component.ChattlyxButtonVariant
import com.chattlyx.core.designsystem.component.ChattlyxTextField
import com.chattlyx.core.designsystem.theme.ChattlyxTheme

/** AUTH-02: country + number capture with inline E.164 validation. */
@Composable
fun PhoneEntryScreen(
    onNumberSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhoneEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // No-permission region hint from the SIM/network (AUTH-02).
        val iso = runCatching {
            val telephony = context.getSystemService(TelephonyManager::class.java)
            val simIso = telephony.simCountryIso?.takeIf { it.isNotBlank() }
            simIso ?: telephony.networkCountryIso
        }.getOrNull().orEmpty()
        viewModel.applyDetectedIso(iso)
    }

    PhoneEntryContent(
        state = state,
        onCountrySelected = viewModel::selectCountry,
        onNumberChange = viewModel::onNumberChange,
        onContinue = { viewModel.onContinue(onNumberSubmitted) },
        modifier = modifier,
    )
}

@Composable
internal fun PhoneEntryContent(
    state: PhoneEntryState,
    onCountrySelected: (Country) -> Unit,
    onNumberChange: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_phone_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.onboarding_phone_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            CountryPicker(selected = state.selectedCountry, onSelected = onCountrySelected)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.selectedCountry.dialCode,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(56.dp),
                )
                ChattlyxTextField(
                    value = state.numberInput,
                    onValueChange = onNumberChange,
                    label = stringResource(R.string.onboarding_phone_label),
                    placeholder = stringResource(R.string.onboarding_phone_hint),
                    isError = state.validationErrorRes != null,
                    supportingText = state.validationErrorRes?.let { stringResource(it) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            ChattlyxButton(
                text = stringResource(R.string.onboarding_continue),
                onClick = onContinue,
                variant = ChattlyxButtonVariant.FILLED,
                enabled = state.numberInput.isNotBlank(),
                loading = state.submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPicker(
    selected: Country,
    onSelected: (Country) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = "${selected.flag}  ${stringResource(selected.nameRes)}  (${selected.dialCode})",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.onboarding_country_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LAUNCH_COUNTRIES.forEach { country ->
                DropdownMenuItem(
                    text = {
                        Text("${country.flag}  ${stringResource(country.nameRes)}  (${country.dialCode})")
                    },
                    onClick = {
                        onSelected(country)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", showBackground = true, fontScale = 1.6f)
@Preview(name = "RTL", showBackground = true, locale = "ar")
@Composable
private fun PhoneEntryContentPreview() {
    ChattlyxTheme {
        PhoneEntryContent(
            state = PhoneEntryState(numberInput = "98765 43210"),
            onCountrySelected = {},
            onNumberChange = {},
            onContinue = {},
        )
    }
}
