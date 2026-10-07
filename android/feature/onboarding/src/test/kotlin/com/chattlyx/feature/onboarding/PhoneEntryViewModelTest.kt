package com.chattlyx.feature.onboarding

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneEntryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `national number is normalised with the selected dial code`() = runTest {
        val vm = PhoneEntryViewModel(dispatcher)
        vm.onNumberChange("98765 43210")
        assertEquals("+919876543210", vm.normalisedNumberOrNull())
    }

    @Test
    fun `full international format passes through`() = runTest {
        val vm = PhoneEntryViewModel(dispatcher)
        vm.selectCountry(LAUNCH_COUNTRIES.first { it.iso == "US" })
        vm.onNumberChange("+14155552671")
        assertEquals("+14155552671", vm.normalisedNumberOrNull())
    }

    @Test
    fun `too short number raises inline error`() = runTest {
        val vm = PhoneEntryViewModel(dispatcher)
        vm.onNumberChange("123")
        assertNull(vm.normalisedNumberOrNull())

        vm.state.test {
            assertEquals(R.string.onboarding_phone_invalid, awaitItem().validationErrorRes)
        }
    }

    @Test
    fun `detected region preselects country only when number is empty`() = runTest {
        val vm = PhoneEntryViewModel(dispatcher)
        vm.applyDetectedIso("AE")
        vm.state.test {
            assertEquals("AE", awaitItem().selectedCountry.iso)
            cancelAndIgnoreRemainingEvents()
        }

        vm.onNumberChange("555")
        vm.applyDetectedIso("FR")
        vm.state.test {
            assertEquals("AE", awaitItem().selectedCountry.iso)
        }
    }

    @Test
    fun `non digit characters are filtered`() = runTest {
        val vm = PhoneEntryViewModel(dispatcher)
        vm.onNumberChange("98abc76")
        vm.state.test {
            assertEquals("9876", awaitItem().numberInput)
        }
    }
}
