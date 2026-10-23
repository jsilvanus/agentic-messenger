package com.agenticmessenger.client.model

import kotlinx.serialization.Serializable

@Serializable
data class StoredMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val plaintext: String,
    val direction: MessageDirection,
    val status: MessageStatus,
    val timestampMillis: Long,
    val relayEnvelopeId: String? = null,
    val annotationsJson: String? = null
)
