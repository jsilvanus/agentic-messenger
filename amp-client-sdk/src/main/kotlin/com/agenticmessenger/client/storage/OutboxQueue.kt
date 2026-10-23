package com.agenticmessenger.client.storage

import com.agenticmessenger.client.model.OutboxItem

interface OutboxQueue : AutoCloseable {
    suspend fun enqueue(item: OutboxItem)
    suspend fun peekPending(limit: Int = 20): List<OutboxItem>
    suspend fun markInFlight(id: String)
    suspend fun markCompleted(id: String)
    suspend fun markFailed(id: String, reason: String)
    suspend fun getPendingCount(): Int
}
