package com.agenticmessenger.client.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageDirection {
    INBOUND,
    OUTBOUND
}
