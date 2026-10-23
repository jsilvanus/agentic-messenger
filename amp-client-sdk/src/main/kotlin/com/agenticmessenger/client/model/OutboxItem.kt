package com.agenticmessenger.client.model

import kotlinx.serialization.Serializable

@Serializable
data class OutboxItem(
    val id: String,
    val conversationId: String,
    val recipientMailboxToken: String,
    val recipientX25519PublicKeyHex: String,
    val payloadPlaintext: String,
    val humanOnly: Boolean = false,
    val attempts: Int = 0,
    val lastAttemptMillis: Long = 0L,
    val status: MessageStatus = MessageStatus.PENDING_OUTBOX,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastError: String? = null
)
