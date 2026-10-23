package com.agenticmessenger.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

object X25519Service {

    /**
     * Generates a new X25519 keypair for Diffie-Hellman key exchange.
     * Both public and private keys are 32 bytes.
     */
    fun generateKeyPair(random: SecureRandom = SecureRandom()): X25519KeyPair {
        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(random))
        val keyPair = generator.generateKeyPair()
        val privParams = keyPair.private as X25519PrivateKeyParameters
        val pubParams = keyPair.public as X25519PublicKeyParameters
        return X25519KeyPair(
            publicKey = pubParams.encoded,
            privateKey = privParams.encoded
        )
    }

    /**
     * Computes the 32-byte shared Diffie-Hellman secret between a local private key
     * and a remote party's public key (RFC 7748).
     */
    fun computeSharedSecret(privateKey: ByteArray, otherPartyPublicKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "X25519 private key must be 32 bytes (got ${privateKey.size})" }
        require(otherPartyPublicKey.size == 32) { "X25519 public key must be 32 bytes (got ${otherPartyPublicKey.size})" }

        val privParams = X25519PrivateKeyParameters(privateKey, 0)
        val pubParams = X25519PublicKeyParameters(otherPartyPublicKey, 0)
        val agreement = X25519Agreement()
        agreement.init(privParams)
        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(pubParams, sharedSecret, 0)
        return sharedSecret
    }

    /**
     * Computes the hex-encoded shared secret from hex-encoded keys.
     */
    fun computeSharedSecretHex(privateKeyHex: String, otherPartyPublicKeyHex: String): String {
        return computeSharedSecret(
            privateKeyHex.hexToByteArray(),
            otherPartyPublicKeyHex.hexToByteArray()
        ).toHexString()
    }
}
