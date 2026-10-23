package com.agenticmessenger.crypto

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

object Ed25519Service {

    /**
     * Generates a new Ed25519 keypair (32-byte public key, 32-byte private key).
     */
    fun generateKeyPair(random: SecureRandom = SecureRandom()): Ed25519KeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(random))
        val keyPair = generator.generateKeyPair()
        val privParams = keyPair.private as Ed25519PrivateKeyParameters
        val pubParams = keyPair.public as Ed25519PublicKeyParameters
        return Ed25519KeyPair(
            publicKey = pubParams.encoded,
            privateKey = privParams.encoded
        )
    }

    /**
     * Signs a message using an Ed25519 private key.
     * Returns a 64-byte Ed25519 signature.
     */
    fun sign(privateKey: ByteArray, message: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Ed25519 private key must be 32 bytes (got ${privateKey.size})" }
        val privParams = Ed25519PrivateKeyParameters(privateKey, 0)
        val signer = Ed25519Signer()
        signer.init(true, privParams)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    /**
     * Signs a UTF-8 string payload or envelope hash.
     */
    fun sign(privateKey: ByteArray, message: String): ByteArray {
        return sign(privateKey, message.toByteArray(Charsets.UTF_8))
    }

    /**
     * Signs a message, returning hex-encoded signature.
     */
    fun signHex(privateKeyHex: String, message: ByteArray): String {
        return sign(privateKeyHex.hexToByteArray(), message).toHexString()
    }

    /**
     * Verifies an Ed25519 signature against a public key and message.
     * Returns true if valid, false if invalid or corrupted.
     */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) {
            return false
        }
        return try {
            val pubParams = Ed25519PublicKeyParameters(publicKey, 0)
            val verifier = Ed25519Signer()
            verifier.init(false, pubParams)
            verifier.update(message, 0, message.size)
            verifier.verifySignature(signature)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies a UTF-8 string payload against an Ed25519 signature.
     */
    fun verify(publicKey: ByteArray, message: String, signature: ByteArray): Boolean {
        return verify(publicKey, message.toByteArray(Charsets.UTF_8), signature)
    }

    /**
     * Verifies a hex-encoded public key and signature against a message.
     */
    fun verifyHex(publicKeyHex: String, message: ByteArray, signatureHex: String): Boolean {
        return try {
            verify(publicKeyHex.hexToByteArray(), message, signatureHex.hexToByteArray())
        } catch (_: Exception) {
            false
        }
    }
}
