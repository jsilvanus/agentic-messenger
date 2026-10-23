package com.agenticmessenger.client.identity

import com.agenticmessenger.core.crypto.BlindedMailbox
import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.crypto.Ed25519KeyPair
import com.agenticmessenger.crypto.Ed25519Service
import com.agenticmessenger.crypto.RecipientKey
import com.agenticmessenger.crypto.X25519KeyPair
import com.agenticmessenger.crypto.X25519Service

/**
 * Local cryptographic identity for an AMP client instance.
 * Holds Ed25519 signature keypair and X25519 key agreement keypair.
 */
data class ClientIdentity(
    val deviceId: String,
    val principalId: String,
    val endpointType: EndpointType = EndpointType.HUMAN_DIRECT,
    val ed25519KeyPair: Ed25519KeyPair,
    val x25519KeyPair: X25519KeyPair,
    val epochKeyHex: String = DEFAULT_EPOCH_KEY_HEX
) {
    /**
     * Blinded mailbox token used by the relay server for zero-knowledge routing:
     * mailbox_id = HMAC-SHA256(epoch_key, device_id)
     */
    val mailboxToken: String by lazy {
        BlindedMailbox.compute(epochKeyHex, deviceId)
    }

    val ed25519PublicKeyHex: String get() = ed25519KeyPair.publicKeyHex()
    val x25519PublicKeyHex: String get() = x25519KeyPair.publicKeyHex()

    fun toRecipientKey(): RecipientKey = RecipientKey(
        recipientId = principalId,
        endpointType = endpointType,
        x25519PublicKey = x25519KeyPair.publicKey
    )

    companion object {
        const val DEFAULT_EPOCH_KEY_HEX = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        fun generate(
            deviceId: String,
            principalId: String,
            endpointType: EndpointType = EndpointType.HUMAN_DIRECT,
            epochKeyHex: String = DEFAULT_EPOCH_KEY_HEX
        ): ClientIdentity {
            val ed25519 = Ed25519Service.generateKeyPair()
            val x25519 = X25519Service.generateKeyPair()
            return ClientIdentity(
                deviceId = deviceId,
                principalId = principalId,
                endpointType = endpointType,
                ed25519KeyPair = ed25519,
                x25519KeyPair = x25519,
                epochKeyHex = epochKeyHex
            )
        }
    }
}
