package com.agenticmessenger.relay.router

import com.agenticmessenger.relay.pubsub.PubSubBroker
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class MailboxRouter(
    var pubSubBroker: PubSubBroker? = null
) : Closeable {

    private val logger = LoggerFactory.getLogger(MailboxRouter::class.java)

    // Maps mailbox_id -> Set of active WebSocket sessions
    private val mailboxToSessions = ConcurrentHashMap<String, CopyOnWriteArraySet<DefaultWebSocketSession>>()

    // Reverse map: session -> Set of mailbox_ids registered on this connection
    private val sessionToMailboxes = ConcurrentHashMap<DefaultWebSocketSession, CopyOnWriteArraySet<String>>()

    // Maps mailbox_id -> Closeable subscription on pubSubBroker
    private val mailboxSubscriptions = ConcurrentHashMap<String, Closeable>()

    private val lock = Mutex()

    /**
     * Registers a WebSocket session for a blinded mailbox token.
     */
    suspend fun register(mailboxId: String, session: DefaultWebSocketSession) {
        lock.withLock {
            val sessions = mailboxToSessions.computeIfAbsent(mailboxId) { CopyOnWriteArraySet() }
            val isFirst = sessions.isEmpty()
            sessions.add(session)

            val mailboxes = sessionToMailboxes.computeIfAbsent(session) { CopyOnWriteArraySet() }
            mailboxes.add(mailboxId)

            val broker = pubSubBroker
            if (isFirst && broker != null && !mailboxSubscriptions.containsKey(mailboxId)) {
                try {
                    val sub = broker.subscribe(mailboxId) { mb, payloadJson ->
                        dispatch(mb, payloadJson)
                    }
                    mailboxSubscriptions[mailboxId] = sub
                    logger.debug("Subscribed PubSubBroker for mailbox '{}'", mailboxId)
                } catch (e: Exception) {
                    logger.error("Failed to subscribe PubSubBroker for mailbox '{}': {}", mailboxId, e.message, e)
                }
            }

            logger.info("Registered session for mailbox '{}'. Active connections for mailbox: {}", mailboxId, sessions.size)
        }
    }

    /**
     * Unregisters a specific mailbox for a session.
     */
    suspend fun unregister(mailboxId: String, session: DefaultWebSocketSession) {
        lock.withLock {
            val sessions = mailboxToSessions[mailboxId]
            sessions?.remove(session)
            if (sessions.isNullOrEmpty()) {
                mailboxToSessions.remove(mailboxId)
                val sub = mailboxSubscriptions.remove(mailboxId)
                try {
                    sub?.close()
                } catch (_: Exception) {}
                try {
                    pubSubBroker?.unsubscribe(mailboxId)
                } catch (_: Exception) {}
            }
            sessionToMailboxes[session]?.remove(mailboxId)
        }
    }

    /**
     * Cleans up all mailbox registrations associated with a disconnected WebSocket session.
     */
    suspend fun unregisterSession(session: DefaultWebSocketSession) {
        lock.withLock {
            val mailboxes = sessionToMailboxes.remove(session)
            if (mailboxes != null) {
                for (mb in mailboxes) {
                    val sessions = mailboxToSessions[mb]
                    sessions?.remove(session)
                    if (sessions.isNullOrEmpty()) {
                        mailboxToSessions.remove(mb)
                        val sub = mailboxSubscriptions.remove(mb)
                        try {
                            sub?.close()
                        } catch (_: Exception) {}
                        try {
                            pubSubBroker?.unsubscribe(mb)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    /**
     * Checks whether any active WebSocket connection is listening on the given mailbox token.
     */
    fun isOnline(mailboxId: String): Boolean {
        val sessions = mailboxToSessions[mailboxId]
        return !sessions.isNullOrEmpty()
    }

    /**
     * Returns all active WebSocket sessions for a given mailbox token.
     */
    fun getSessions(mailboxId: String): List<DefaultWebSocketSession> {
        val sessions = mailboxToSessions[mailboxId] ?: return emptyList()
        return sessions.toList()
    }

    /**
     * Count of currently connected mailboxes.
     */
    fun activeMailboxCount(): Int = mailboxToSessions.size

    /**
     * Count of active WebSocket connections.
     */
    fun activeConnectionCount(): Int = sessionToMailboxes.size

    /**
     * Dispatches a raw text frame to all active sessions for a mailbox.
     * Returns true if successfully delivered to at least one active session.
     */
    suspend fun dispatch(mailboxId: String, text: String): Boolean {
        val sessions = getSessions(mailboxId)
        if (sessions.isEmpty()) return false

        var delivered = false
        for (s in sessions) {
            try {
                s.send(Frame.Text(text))
                delivered = true
            } catch (e: Exception) {
                logger.warn("Failed sending frame to session for mailbox '{}': {}", mailboxId, e.message)
            }
        }
        return delivered
    }

    override fun close() {
        for ((_, sub) in mailboxSubscriptions) {
            try {
                sub.close()
            } catch (_: Exception) {}
        }
        mailboxSubscriptions.clear()
        mailboxToSessions.clear()
        sessionToMailboxes.clear()
    }
}
