package com.agenticmessenger.relay.pubsub

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class LocalPubSubBroker : PubSubBroker {

    private val logger = LoggerFactory.getLogger(LocalPubSubBroker::class.java)
    private val subscribers = ConcurrentHashMap<String, CopyOnWriteArraySet<MessageListener>>()

    override suspend fun publish(mailboxId: String, payloadJson: String): Long {
        val listeners = subscribers[mailboxId]
        if (listeners.isNullOrEmpty()) {
            return 0L
        }

        var deliveredCount = 0L
        for (listener in listeners) {
            try {
                listener(mailboxId, payloadJson)
                deliveredCount++
            } catch (e: Exception) {
                logger.error("Error dispatching pubsub message for mailbox '{}': {}", mailboxId, e.message, e)
            }
        }
        return deliveredCount
    }

    override suspend fun subscribe(mailboxId: String, listener: MessageListener): Closeable {
        val listeners = subscribers.computeIfAbsent(mailboxId) { CopyOnWriteArraySet() }
        listeners.add(listener)
        return Closeable {
            listeners.remove(listener)
            if (listeners.isEmpty()) {
                subscribers.remove(mailboxId, listeners)
            }
        }
    }

    override suspend fun unsubscribe(mailboxId: String) {
        subscribers.remove(mailboxId)
    }

    override fun close() {
        subscribers.clear()
    }

    fun hasSubscribers(mailboxId: String): Boolean {
        val listeners = subscribers[mailboxId]
        return !listeners.isNullOrEmpty()
    }
}
