package com.agenticmessenger.relay.pubsub

import java.io.Closeable

typealias MessageListener = suspend (mailboxId: String, payloadJson: String) -> Unit

interface PubSubBroker : Closeable {
    /**
     * Publishes an encrypted payload or control frame JSON to a specific destination mailbox.
     * Returns the count of subscriber nodes / listeners that received the broadcast (> 0 if online).
     */
    suspend fun publish(mailboxId: String, payloadJson: String): Long

    /**
     * Subscribes a listener to receive real-time messages for the given mailbox.
     * Returns a Closeable subscription handle to easily unsubscribe.
     */
    suspend fun subscribe(mailboxId: String, listener: MessageListener): Closeable

    /**
     * Unsubscribes all listeners for the given mailbox.
     */
    suspend fun unsubscribe(mailboxId: String)
}
