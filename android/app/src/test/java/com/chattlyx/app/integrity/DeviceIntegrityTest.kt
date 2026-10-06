package com.chattlyx.app.integrity

import io.mockk.mockk
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** SAF hardening: integrity binding decisions + nonce format. */
class DeviceIntegrityTest {

    private val context = mockk<android.content.Context>()
    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun `disabled build binds the noop provider`() = runTest {
        val provider = createDeviceIntegrityProvider(
            context = context,
            enabled = false,
            cloudProjectNumber = 123L,
            ioDispatcher = dispatcher,
        )
        assertIs<NoopDeviceIntegrityProvider>(provider)
        assertNull(provider.requestToken(IntegrityNonce.generate()))
    }

    @Test
    fun `enabled without a cloud project number falls back to noop`() {
        val provider = createDeviceIntegrityProvider(
            context = context,
            enabled = true,
            cloudProjectNumber = 0L,
            ioDispatcher = dispatcher,
        )
        assertIs<NoopDeviceIntegrityProvider>(provider)
    }

    @Test
    fun `nonces are url-safe base64 without padding and unique`() {
        val first = IntegrityNonce.generate()
        val second = IntegrityNonce.generate()

        assertNotEquals(first, second)
        assertTrue(!first.contains("=") && !first.contains("+") && !first.contains("/"))
        assertEquals(32, Base64.getUrlDecoder().decode(first).size)
    }
}
