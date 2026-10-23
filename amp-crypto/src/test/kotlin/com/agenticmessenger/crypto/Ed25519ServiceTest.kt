package com.agenticmessenger.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ed25519ServiceTest {

    @Test
    fun `keypair generation produces valid 32-byte keys`() {
        val keyPair = Ed25519Service.generateKeyPair()
        assertEquals(32, keyPair.publicKey.size)
        assertEquals(32, keyPair.privateKey.size)
        assertEquals(64, keyPair.publicKeyHex().length)
        assertEquals(64, keyPair.privateKeyHex().length)
    }

    @Test
    fun `signing and verifying byte message succeeds`() {
        val keyPair = Ed25519Service.generateKeyPair()
        val payload = "Hello, Agentic Cryptography!".toByteArray(Charsets.UTF_8)

        val signature = Ed25519Service.sign(keyPair.privateKey, payload)
        assertEquals(64, signature.size)

        val valid = Ed25519Service.verify(keyPair.publicKey, payload, signature)
        assertTrue(valid, "Signature should be valid for the original message")
    }

    @Test
    fun `signing and verifying string message succeeds`() {
        val keyPair = Ed25519Service.generateKeyPair()
        val text = "Critical human escalation: approve transfer of $50,000"

        val signature = Ed25519Service.sign(keyPair.privateKey, text)
        val valid = Ed25519Service.verify(keyPair.publicKey, text, signature)
        assertTrue(valid)
    }

    @Test
    fun `hex signing and verification succeeds`() {
        val keyPair = Ed25519Service.generateKeyPair()
        val payload = "Payload to sign via hex API".toByteArray(Charsets.UTF_8)

        val sigHex = Ed25519Service.signHex(keyPair.privateKeyHex(), payload)
        assertEquals(128, sigHex.length)

        val valid = Ed25519Service.verifyHex(keyPair.publicKeyHex(), payload, sigHex)
        assertTrue(valid)
    }

    @Test
    fun `tampered message is rejected`() {
        val keyPair = Ed25519Service.generateKeyPair()
        val message = "Original Untampered Payload".toByteArray(Charsets.UTF_8)
        val tampered = "Corrupted Payload".toByteArray(Charsets.UTF_8)

        val signature = Ed25519Service.sign(keyPair.privateKey, message)

        val valid = Ed25519Service.verify(keyPair.publicKey, tampered, signature)
        assertFalse(valid, "Tampered payload must fail signature verification")
    }

    @Test
    fun `corrupted signature is rejected`() {
        val keyPair = Ed25519Service.generateKeyPair()
        val message = "Envelope Hash Verification".toByteArray(Charsets.UTF_8)
        val signature = Ed25519Service.sign(keyPair.privateKey, message)

        // Mutate signature byte
        val corruptedSig = signature.copyOf()
        corruptedSig[0] = (corruptedSig[0].toInt() xor 0xFF).toByte()

        val valid = Ed25519Service.verify(keyPair.publicKey, message, corruptedSig)
        assertFalse(valid, "Corrupted signature bytes must fail verification")
    }

    @Test
    fun `verification fails with another keypair`() {
        val alice = Ed25519Service.generateKeyPair()
        val bob = Ed25519Service.generateKeyPair()
        val message = "Signed by Alice".toByteArray(Charsets.UTF_8)

        val signature = Ed25519Service.sign(alice.privateKey, message)

        val validForBob = Ed25519Service.verify(bob.publicKey, message, signature)
        assertFalse(validForBob, "Bob's public key cannot verify Alice's signature")
    }

    @Test
    fun `malformed keys or signatures do not crash verification`() {
        val message = "Safe error handling test".toByteArray(Charsets.UTF_8)
        assertFalse(Ed25519Service.verify(ByteArray(16), message, ByteArray(64)))
        assertFalse(Ed25519Service.verify(ByteArray(32), message, ByteArray(32)))
        assertFalse(Ed25519Service.verifyHex("invalidHex", message, "invalidSig"))
    }
}
