package com.agenticmessenger.crypto

import com.agenticmessenger.core.model.EndpointType

/**
 * Key pair for Ed25519 digital signatures.
 * Raw 32-byte public key and 32-byte private key.
 */
data class Ed25519KeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    fun publicKeyHex(): String = publicKey.toHexString()
    fun privateKeyHex(): String = privateKey.toHexString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Ed25519KeyPair) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

/**
 * Key pair for X25519 Diffie-Hellman key agreement.
 * Raw 32-byte public key and 32-byte private key.
 */
data class X25519KeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    fun publicKeyHex(): String = publicKey.toHexString()
    fun privateKeyHex(): String = privateKey.toHexString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is X25519KeyPair) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

/**
 * Recipient public key endpoint descriptor for dual-compartment envelope encryption.
 */
data class RecipientKey(
    val recipientId: String,
    val endpointType: EndpointType,
    val x25519PublicKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecipientKey) return false
        return recipientId == other.recipientId &&
                endpointType == other.endpointType &&
                x25519PublicKey.contentEquals(other.x25519PublicKey)
    }

    override fun hashCode(): Int {
        var result = recipientId.hashCode()
        result = 31 * result + endpointType.hashCode()
        result = 31 * result + x25519PublicKey.contentHashCode()
        return result
    }
}

// Utility conversions
internal fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

internal fun String.hexToByteArray(): ByteArray {
    val clean = if (length % 2 != 0) "0$this" else this
    val len = clean.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                Character.digit(clean[i + 1], 16)).toByte()
        i += 2
    }
    return data
}
