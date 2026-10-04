package com.chattlyx.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.phonenumber.E164
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Phone-entry state (AUTH-02). */
data class PhoneEntryState(
    val selectedCountry: Country = LAUNCH_COUNTRIES.first(),
    val numberInput: String = "",
    val normalizedE164: String? = null,
    val validationErrorRes: Int? = null,
    val submitting: Boolean = false,
)

@HiltViewModel
class PhoneEntryViewModel @Inject constructor(
    @Dispatcher(ChattlyxDispatcher.DEFAULT) private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(PhoneEntryState())
    val state: StateFlow<PhoneEntryState> = _state.asStateFlow()

    fun selectCountry(country: Country) {
        _state.update { it.copy(selectedCountry = country, validationErrorRes = null) }
    }

    /** Preselects the SIM/network region when the platform provides one. */
    fun applyDetectedIso(networkIso: String) {
        if (networkIso.isBlank()) return
        val detected = findCountryByIso(networkIso)
        _state.update {
            if (it.numberInput.isEmpty()) it.copy(selectedCountry = detected) else it
        }
    }

    fun onNumberChange(number: String) {
        val filtered = number.filter { it.isDigit() || it == ' ' || it == '-' }
        _state.update { it.copy(numberInput = filtered, validationErrorRes = null) }
    }

    /**
     * Validates locally via E164 and reports the normalised number; returns
     * null when invalid so the UI shows an inline error.
     */
    fun normalisedNumberOrNull(): String? {
        val current = _state.value
        val e164 = E164.normalize(current.numberInput, current.selectedCountry.dialCode)
        if (e164 == null) {
            _state.update { it.copy(validationErrorRes = R.string.onboarding_phone_invalid) }
            return null
        }
        _state.update { it.copy(normalizedE164 = e164) }
        return e164
    }

    /** Marks the OTP request as in flight (request itself happens in OTP step). */
    fun setSubmitting(submitting: Boolean) {
        _state.update { it.copy(submitting = submitting) }
    }

    /** Confirms consent + starts the OTP request for the normalised number. */
    fun onContinue(onValid: (String) -> Unit) {
        val e164 = normalisedNumberOrNull() ?: return
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(submitting = true) }
            onValid(e164)
        }
    }
}
