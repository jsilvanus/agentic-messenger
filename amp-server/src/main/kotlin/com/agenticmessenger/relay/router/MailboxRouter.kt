package com.agenticmessenger.relay.router

import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class MailboxRouter {

    private val logger = LoggerFactory.getLogger(MailboxRouter::class.java)

    // Maps mailbox_id -> Set of active WebSocket sessions
    private val mailboxToSessions = ConcurrentHashMap<String, CopyOnWriteArraySet<DefaultWebSocketSession>>()

    // Reverse map: session -> Set of mailbox_ids registered on this connection
    private val sessionToMailboxes = ConcurrentHashMap<DefaultWebSocketSession, CopyOnWriteArraySet<String>>()

    private val lock = Mutex()

    /**
     * Registers a WebSocket session for a blinded mailbox token.
     */
    suspend fun register(mailboxId: String, session: DefaultWebSocketSession) {
        lock.withLock {
            val sessions = mailboxToSessions.computeIfAbsent(mailboxId) { CopyOnWriteArraySet() }
            sessions.add(session)

            val mailboxes = sessionToMailboxes.computeIfAbsent(session) { CopyOnWriteArraySet() }
            mailboxes.add(mailboxId)

            logger.info("Registered session for mailbox '{}'. Active connections for mailbox: {}", mailboxId, sessions.size)
        }
    }

    /**
     * Unregisters a specific mailbox for a session.
     */
    suspend fun unregister(mailboxId: String, session: DefaultWebSocketSession) {
        lock.withLock {
            mailboxToSessions[mailboxId]?.remove(session)
            if (mailboxToSessions[mailboxId]?.isEmpty() == true) {
                mailboxToSessions.remove(mailboxId)
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
                    mailboxToSessions[mb]?.remove(session)
                    if (mailboxToSessions[mb]?.isEmpty() == true) {
                        mailboxToSessions.remove(mb)
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
}
