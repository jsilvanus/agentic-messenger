package com.agenticmessenger.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class X25519ServiceTest {

    @Test
    fun `keypair generation produces 32-byte keys`() {
        val keyPair = X25519Service.generateKeyPair()
        assertEquals(32, keyPair.publicKey.size)
        assertEquals(32, keyPair.privateKey.size)
        assertEquals(64, keyPair.publicKeyHex().length)
        assertEquals(64, keyPair.privateKeyHex().length)
    }

    @Test
    fun `Alice and Bob derive identical shared secrets`() {
        val alice = X25519Service.generateKeyPair()
        val bob = X25519Service.generateKeyPair()

        // Alice computes Diffie-Hellman secret using Bob's public key
        val aliceShared = X25519Service.computeSharedSecret(alice.privateKey, bob.publicKey)

        // Bob computes Diffie-Hellman secret using Alice's public key
        val bobShared = X25519Service.computeSharedSecret(bob.privateKey, alice.publicKey)

        assertEquals(32, aliceShared.size)
        assertEquals(32, bobShared.size)
        assertContentEquals(aliceShared, bobShared, "Alice and Bob must compute identical 32-byte shared secrets")
    }

    @Test
    fun `hex API derives identical shared secrets`() {
        val alice = X25519Service.generateKeyPair()
        val bob = X25519Service.generateKeyPair()

        val aliceSharedHex = X25519Service.computeSharedSecretHex(alice.privateKeyHex(), bob.publicKeyHex())
        val bobSharedHex = X25519Service.computeSharedSecretHex(bob.privateKeyHex(), alice.publicKeyHex())

        assertEquals(64, aliceSharedHex.length)
        assertEquals(aliceSharedHex, bobSharedHex)
    }

    @Test
    fun `different peer produces completely different shared secret`() {
        val alice = X25519Service.generateKeyPair()
        val bob = X25519Service.generateKeyPair()
        val charlie = X25519Service.generateKeyPair()

        val aliceBob = X25519Service.computeSharedSecret(alice.privateKey, bob.publicKey)
        val aliceCharlie = X25519Service.computeSharedSecret(alice.privateKey, charlie.publicKey)

        assertFalse(
            aliceBob.contentEquals(aliceCharlie),
            "Shared secret with Charlie must not match shared secret with Bob"
        )
    }

    @Test
    fun `invalid key lengths throw IllegalArgumentException`() {
        val keyPair = X25519Service.generateKeyPair()
        assertFailsWith<IllegalArgumentException> {
            X25519Service.computeSharedSecret(ByteArray(16), keyPair.publicKey)
        }
        assertFailsWith<IllegalArgumentException> {
            X25519Service.computeSharedSecret(keyPair.privateKey, ByteArray(16))
        }
    }
}
