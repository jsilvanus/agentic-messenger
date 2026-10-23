package com.agenticmessenger.relay.service

import com.agenticmessenger.core.protocol.RelayFrame
import com.agenticmessenger.relay.queue.StoreAndForwardQueue
import com.agenticmessenger.relay.router.MailboxRouter
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class RelayService(
    val router: MailboxRouter,
    val queue: StoreAndForwardQueue,
    val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = false }
) {

    private val logger = LoggerFactory.getLogger(RelayService::class.java)

    /**
     * Processes an incoming frame from an active WebSocket session.
     * Returns an optional response frame to send back to the caller.
     */
    suspend fun handleFrame(session: DefaultWebSocketSession, frame: RelayFrame): RelayFrame? {
        return when (frame) {
            is RelayFrame.Register -> {
                router.register(frame.mailboxId, session)
                logger.info("Mailbox registered: {}", frame.mailboxId)

                // Check if there are pending offline messages waiting for this mailbox
                val pendingCount = queue.countPending(frame.mailboxId)
                if (pendingCount > 0) {
                    logger.info("Mailbox '{}' has {} pending offline messages waiting", frame.mailboxId, pendingCount)
                    // Auto-deliver pending messages upon reconnection
                    deliverPendingMessages(session, frame.mailboxId)
                }

                RelayFrame.Registered(mailboxId = frame.mailboxId)
            }

            is RelayFrame.Send -> {
                val direct = mutableListOf<String>()
                val queued = mutableListOf<String>()

                for (destMailbox in frame.destinationMailboxes) {
                    if (router.isOnline(destMailbox)) {
                        val deliverFrame = RelayFrame.Deliver(
                            messageId = frame.messageId,
                            destinationMailbox = destMailbox,
                            sourceMailbox = frame.sourceMailbox,
                            payloadCiphertext = frame.payloadCiphertext,
                            timestamp = frame.timestamp
                        )
                        val frameText = json.encodeToString<RelayFrame>(deliverFrame)
                        val delivered = router.dispatch(destMailbox, frameText)
                        if (delivered) {
                            direct.add(destMailbox)
                            // Also enqueue briefly in store-and-forward until recipient sends ACK,
                            // or allow immediate delivery.
                            // In high-reliability mode: we enqueue so if recipient crashes before ACK,
                            // message isn't lost.
                            queue.enqueue(
                                messageId = frame.messageId,
                                destinationMailbox = destMailbox,
                                sourceMailbox = frame.sourceMailbox,
                                payloadCiphertext = frame.payloadCiphertext,
                                ttlSeconds = frame.ttlSeconds ?: (14L * 24 * 3600)
                            )
                        } else {
                            // Direct dispatch failed, fall back to offline queue
                            queue.enqueue(
                                messageId = frame.messageId,
                                destinationMailbox = destMailbox,
                                sourceMailbox = frame.sourceMailbox,
                                payloadCiphertext = frame.payloadCiphertext,
                                ttlSeconds = frame.ttlSeconds ?: (14L * 24 * 3600)
                            )
                            queued.add(destMailbox)
                        }
                    } else {
                        // Recipient is offline, enqueue in ephemeral store-and-forward
                        queue.enqueue(
                            messageId = frame.messageId,
                            destinationMailbox = destMailbox,
                            sourceMailbox = frame.sourceMailbox,
                            payloadCiphertext = frame.payloadCiphertext,
                            ttlSeconds = frame.ttlSeconds ?: (14L * 24 * 3600)
                        )
                        queued.add(destMailbox)
                    }
                }

                RelayFrame.Sent(
                    messageId = frame.messageId,
                    directRecipients = direct,
                    queuedRecipients = queued
                )
            }

            is RelayFrame.Ack -> {
                // Permanently purge ciphertext from database
                val purged = queue.acknowledge(frame.messageId, frame.mailboxId)
                logger.debug("Purged message '{}' for mailbox '{}': {}", frame.messageId, frame.mailboxId, purged)
                RelayFrame.AckConfirm(messageId = frame.messageId, mailboxId = frame.mailboxId)
            }

            is RelayFrame.FetchOffline -> {
                val pending = queue.fetchPending(frame.mailboxId, frame.limit)
                val deliverList = pending.map {
                    RelayFrame.Deliver(
                        messageId = it.messageId,
                        destinationMailbox = it.destinationMailbox,
                        sourceMailbox = it.sourceMailbox,
                        payloadCiphertext = it.payloadCiphertext,
                        timestamp = it.createdAt
                    )
                }
                val totalRemaining = queue.countPending(frame.mailboxId) - deliverList.size
                RelayFrame.OfflineBatch(
                    mailboxId = frame.mailboxId,
                    messages = deliverList,
                    remainingCount = maxOf(0, totalRemaining)
                )
            }

            is RelayFrame.Ping -> {
                RelayFrame.Pong(nonce = frame.nonce)
            }

            else -> {
                logger.warn("Unexpected incoming frame type: {}", frame::class.simpleName)
                RelayFrame.Error(
                    code = "UNEXPECTED_FRAME",
                    message = "Frame type ${frame::class.simpleName} cannot be handled as an inbound request"
                )
            }
        }
    }

    /**
     * Drains and delivers pending messages for a newly connected/registered session.
     */
    private suspend fun deliverPendingMessages(session: DefaultWebSocketSession, mailboxId: String) {
        val pending = queue.fetchPending(mailboxId, limit = 100)
        for (item in pending) {
            val deliverFrame = RelayFrame.Deliver(
                messageId = item.messageId,
                destinationMailbox = item.destinationMailbox,
                sourceMailbox = item.sourceMailbox,
                payloadCiphertext = item.payloadCiphertext,
                timestamp = item.createdAt
            )
            val jsonText = json.encodeToString<RelayFrame>(deliverFrame)
            try {
                session.send(Frame.Text(jsonText))
            } catch (e: Exception) {
                logger.warn("Failed sending pending message '{}' to mailbox '{}': {}", item.messageId, mailboxId, e.message)
                break
            }
        }
    }

    suspend fun onSessionDisconnected(session: DefaultWebSocketSession) {
        router.unregisterSession(session)
    }
}
