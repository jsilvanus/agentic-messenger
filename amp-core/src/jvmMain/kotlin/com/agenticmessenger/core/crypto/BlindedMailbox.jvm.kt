package com.agenticmessenger.core.crypto

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object BlindedMailbox {

    private const val HMAC_ALGORITHM = "HmacSHA256"

    /**
     * Computes the blinded mailbox token for a given device and epoch key.
     * mailbox_id = HMAC-SHA256(K_epoch, device_id)
     */
    actual fun compute(epochKey: ByteArray, deviceId: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = SecretKeySpec(epochKey, HMAC_ALGORITHM)
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(deviceId.toByteArray(Charsets.UTF_8))
        return hmacBytes.toHexString()
    }

    /**
     * Computes the blinded mailbox token given a hex-encoded epoch key.
     */
    actual fun compute(epochKeyHex: String, deviceId: String): String {
        return compute(hexToBytes(epochKeyHex), deviceId)
    }

    /**
     * Compute contact directory identity hash commitment:
     * identity_hash = SHA256(salt + handle_or_key)
     */
    actual fun computeIdentityCommitment(salt: ByteArray, identifier: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(identifier.toByteArray(Charsets.UTF_8))
        return md.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 != 0) "0$hex" else hex
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
}
