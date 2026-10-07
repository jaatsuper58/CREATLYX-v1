package com.chattlyx.data.messaging

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.NamedParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Placeholder session cipher behaviour (Phase 2, pre-libsignal): symmetric
 * shared secrets between static identity keys, tamper detection via GCM.
 */
class SessionCipherTest {

    private class FakeIdentity(private val pair: KeyPair) : IdentityKeys {
        override fun keyPair(): KeyPair = pair
    }

    private fun x25519(): KeyPair =
        KeyPairGenerator.getInstance("XDH").apply {
            initialize(NamedParameterSpec.X25519)
        }.generateKeyPair()

    private val encoder = Base64.getEncoder()

    @Test
    fun `alice encrypts, bob decrypts with identity keys`() {
        val aliceKeys = x25519()
        val bobKeys = x25519()

        val alice = SessionCipher(FakeIdentity(aliceKeys))
        val bob = SessionCipher(FakeIdentity(bobKeys))

        val bobPublic = encoder.encodeToString(bobKeys.public.encoded)
        val alicePublic = encoder.encodeToString(aliceKeys.public.encoded)

        val blob = alice.encrypt(bobPublic, "are we still on?".encodeToByteArray())
        val plaintext = bob.decrypt(alicePublic, blob)

        assertEquals("are we still on?", String(plaintext))
    }

    @Test
    fun `ciphertext does not contain the plaintext`() {
        val aliceKeys = x25519()
        val bobKeys = x25519()
        val alice = SessionCipher(FakeIdentity(aliceKeys))

        val blob = alice.encrypt(
            encoder.encodeToString(bobKeys.public.encoded),
            "secret meeting at 9".encodeToByteArray(),
        )
        assertTrue(!String(blob).contains("secret meeting"))
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val aliceKeys = x25519()
        val bobKeys = x25519()
        val alice = SessionCipher(FakeIdentity(aliceKeys))
        val bob = SessionCipher(FakeIdentity(bobKeys))

        val blob = alice.encrypt(
            encoder.encodeToString(bobKeys.public.encoded),
            "attack at dawn".encodeToByteArray(),
        )
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()

        assertFailsWith<Exception> {
            bob.decrypt(encoder.encodeToString(aliceKeys.public.encoded), blob)
        }
    }

    @Test
    fun `wrong peer key cannot decrypt`() {
        val aliceKeys = x25519()
        val bobKeys = x25519()
        val malloryKeys = x25519()
        val alice = SessionCipher(FakeIdentity(aliceKeys))
        val mallory = SessionCipher(FakeIdentity(malloryKeys))

        val blob = alice.encrypt(
            encoder.encodeToString(bobKeys.public.encoded),
            "for bob only".encodeToByteArray(),
        )

        assertFailsWith<Exception> {
            mallory.decrypt(encoder.encodeToString(aliceKeys.public.encoded), blob)
        }
    }
}
