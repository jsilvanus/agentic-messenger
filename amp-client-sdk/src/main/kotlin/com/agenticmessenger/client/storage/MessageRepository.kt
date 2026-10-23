package com.agenticmessenger.client.storage

import com.agenticmessenger.client.model.MessageStatus
import com.agenticmessenger.client.model.StoredMessage
import kotlinx.coroutines.flow.StateFlow

interface MessageRepository : AutoCloseable {
    suspend fun saveMessage(message: StoredMessage)
    suspend fun updateStatus(id: String, status: MessageStatus, relayEnvelopeId: String? = null)
    suspend fun getMessages(conversationId: String, limit: Int = 50, beforeTimestamp: Long? = null): List<StoredMessage>
    fun observeConversation(conversationId: String): StateFlow<List<StoredMessage>>
    suspend fun getMessageById(id: String): StoredMessage?
}
