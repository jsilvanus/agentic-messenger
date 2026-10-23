package com.agenticmessenger.core.crypto

expect object BlindedMailbox {

    /**
     * Computes the blinded mailbox token for a given device and epoch key.
     * mailbox_id = HMAC-SHA256(K_epoch, device_id)
     */
    fun compute(epochKey: ByteArray, deviceId: String): String

    /**
     * Computes the blinded mailbox token given a hex-encoded epoch key.
     */
    fun compute(epochKeyHex: String, deviceId: String): String

    /**
     * Compute contact directory identity hash commitment:
     * identity_hash = SHA256(salt + handle_or_key)
     */
    fun computeIdentityCommitment(salt: ByteArray, identifier: String): String
}
