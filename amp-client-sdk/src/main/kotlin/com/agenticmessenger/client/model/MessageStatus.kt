package com.agenticmessenger.client.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus {
    PENDING_OUTBOX,
    SENDING,
    SENT_RELAY,
    DELIVERED,
    VIEWED,
    FAILED
}
