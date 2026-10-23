package com.agenticmessenger.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RelayFrame {

    /**
     * Client registers its active blinded mailbox token with the relay.
     */
    @Serializable
    @SerialName("REGISTER")
    data class Register(
        val mailboxId: String,
        val authToken: String? = null,
        val clientVersion: String? = "1.0.0"
    ) : RelayFrame()

    /**
     * Server confirms registration of the mailbox token.
     */
    @Serializable
    @SerialName("REGISTERED")
    data class Registered(
        val mailboxId: String,
        val status: String = "OK",
        val timestamp: Long = System.currentTimeMillis()
    ) : RelayFrame()

    /**
     * Client dispatches an encrypted envelope to one or more recipient mailboxes.
     * The server operates as a zero-knowledge broker and never sees plaintext.
     */
    @Serializable
    @SerialName("SEND")
    data class Send(
        val messageId: String,
        val destinationMailboxes: List<String>,
        val payloadCiphertext: String,
        val sourceMailbox: String? = null,
        val ttlSeconds: Long? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : RelayFrame()

    /**
     * Server confirms receipt and routing disposition of the sent message.
     */
    @Serializable
    @SerialName("SENT")
    data class Sent(
        val messageId: String,
        val directRecipients: List<String>,
        val queuedRecipients: List<String>,
        val timestamp: Long = System.currentTimeMillis()
    ) : RelayFrame()

    /**
     * Server delivers an encrypted envelope to an active recipient mailbox.
     */
    @Serializable
    @SerialName("DELIVER")
    data class Deliver(
        val messageId: String,
        val destinationMailbox: String,
        val sourceMailbox: String? = null,
        val payloadCiphertext: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : RelayFrame()

    /**
     * Recipient client sends a delivery acknowledgment back to the server.
     * Upon receiving this ACK, the server permanently and immediately purges
     * the ciphertext from the store-and-forward queue.
     */
    @Serializable
    @SerialName("ACK")
    data class Ack(
        val messageId: String,
        val mailboxId: String,
        val signedAck: String? = null
    ) : RelayFrame()

    /**
     * Server confirms message has been ACKed and purged.
     */
    @Serializable
    @SerialName("ACK_CONFIRM")
    data class AckConfirm(
        val messageId: String,
        val mailboxId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : RelayFrame()

    /**
     * Reconnecting client requests pending offline messages stored in queue.
     */
    @Serializable
    @SerialName("FETCH_OFFLINE")
    data class FetchOffline(
        val mailboxId: String,
        val limit: Int = 100
    ) : RelayFrame()

    /**
     * Server delivers batch of offline queued messages.
     */
    @Serializable
    @SerialName("OFFLINE_BATCH")
    data class OfflineBatch(
        val mailboxId: String,
        val messages: List<Deliver>,
        val remainingCount: Int = 0
    ) : RelayFrame()

    /**
     * Keepalive ping frame.
     */
    @Serializable
    @SerialName("PING")
    data class Ping(
        val nonce: Long = System.currentTimeMillis()
    ) : RelayFrame()

    /**
     * Keepalive pong response frame.
     */
    @Serializable
    @SerialName("PONG")
    data class Pong(
        val nonce: Long
    ) : RelayFrame()

    /**
     * Recipient unshielded a sealed message for their local AI assistant.
     * Informational audit receipt routed to original sender's mailbox.
     * Sender client updates its status indicator ("Disclosed by recipient to local AI").
     * The sender's copy itself is NEVER unshielded.
     */
    @Serializable
    @SerialName("DISCLOSURE_RECEIPT")
    data class DisclosureReceipt(
        val messageId: String,
        val sourceMailbox: String,            // recipient mailbox who unshielded
        val destinationMailbox: String,       // original sender mailbox
        val timestamp: Long = System.currentTimeMillis(),
        val notice: String = "Unshielded by recipient for local AI assistant"
    ) : RelayFrame()

    /**
     * Delivery signal confirming recipient device received the envelope.
     */
    @Serializable
    @SerialName("DELIVERY_RECEIPT")
    data class DeliveryReceipt(
        val messageId: String,
        val sourceMailbox: String,
        val destinationMailbox: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : RelayFrame()

    /**
     * Viewed signal confirming recipient opened and viewed the turn.
     * Recipient can opt out of transmitting this signal.
     */
    @Serializable
    @SerialName("VIEWED_RECEIPT")
    data class ViewedReceipt(
        val messageId: String,
        val sourceMailbox: String,
        val destinationMailbox: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : RelayFrame()

    /**
     * Protocol or server error frame.
     */
    @Serializable
    @SerialName("ERROR")
    data class Error(
        val code: String,
        val message: String,
        val referenceId: String? = null
    ) : RelayFrame()
}

/**
 * Key package registry payload for MLS pre-keys and device credentials.
 */
@Serializable
data class KeyPackageRecord(
    val identityHash: String,
    val deviceKeyId: String,
    val endpointType: String,
    val publicKeyPackage: String,
    val signatureHex: String? = null,
    val publishedAt: Long = System.currentTimeMillis()
)

/**
 * Encrypted blob upload response metadata.
 */
@Serializable
data class BlobMetadata(
    val blobId: String,
    val sha256: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val uploadedAt: Long = System.currentTimeMillis()
)
