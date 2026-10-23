package com.agenticmessenger.client.model

import com.agenticmessenger.core.model.PrivacyLevel
import kotlinx.serialization.Serializable

@Serializable
data class IncomingDecryptedMessage(
    val messageId: String,
    val conversationId: String,
    val senderPrincipalId: String,
    val plaintext: String,
    val policy: PrivacyLevel,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceMailbox: String? = null
)
