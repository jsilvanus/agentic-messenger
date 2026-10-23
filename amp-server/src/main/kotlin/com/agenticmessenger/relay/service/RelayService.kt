package com.agenticmessenger.relay.service

import com.agenticmessenger.core.protocol.RelayFrame
import com.agenticmessenger.relay.pubsub.PubSubBroker
import com.agenticmessenger.relay.queue.StoreAndForwardQueue
import com.agenticmessenger.relay.router.MailboxRouter
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class RelayService(
    private val router: MailboxRouter,
    private val queue: StoreAndForwardQueue,
    private val pubSubBroker: PubSubBroker? = null
) {
    private val logger = LoggerFactory.getLogger(RelayService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private var activeBroker: PubSubBroker? = pubSubBroker

    fun setPubSubBroker(broker: PubSubBroker?) {
        this.activeBroker = broker
    }

    suspend fun handleFrame(session: DefaultWebSocketServerSession, frame: RelayFrame): RelayFrame? {
        return when (frame) {
            is RelayFrame.Register -> {
                router.register(frame.mailboxId, session)
                logger.debug("Registered mailbox '{}'", frame.mailboxId)

                // Drain and deliver any pending offline messages queued for this mailbox
                deliverPendingMessages(session, frame.mailboxId)

                RelayFrame.Registered(mailboxId = frame.mailboxId)
            }

            is RelayFrame.Send -> {
                val direct = mutableListOf<String>()
                val queued = mutableListOf<String>()

                for (destMailbox in frame.destinationMailboxes) {
                    val deliverFrame = RelayFrame.Deliver(
                        messageId = frame.messageId,
                        destinationMailbox = destMailbox,
                        sourceMailbox = frame.sourceMailbox,
                        payloadCiphertext = frame.payloadCiphertext,
                        timestamp = frame.timestamp
                    )
                    val frameText = json.encodeToString<RelayFrame>(deliverFrame)

                    // Always persist in store-and-forward queue for durability until delivery ACK
                    queue.enqueue(
                        messageId = frame.messageId,
                        destinationMailbox = destMailbox,
                        sourceMailbox = frame.sourceMailbox,
                        payloadCiphertext = frame.payloadCiphertext,
                        ttlSeconds = frame.ttlSeconds ?: (14L * 24 * 3600)
                    )

                    val broker = activeBroker
                    if (broker != null) {
                        // Real-time distributed routing via PubSubBroker
                        val subscriberCount = broker.publish(destMailbox, frameText)
                        if (subscriberCount > 0 || router.isOnline(destMailbox)) {
                            direct.add(destMailbox)
                        } else {
                            queued.add(destMailbox)
                        }
                    } else {
                        // Local standalone fallback without pubsub
                        if (router.isOnline(destMailbox)) {
                            val delivered = router.dispatch(destMailbox, frameText)
                            if (delivered) {
                                direct.add(destMailbox)
                            } else {
                                queued.add(destMailbox)
                            }
                        } else {
                            queued.add(destMailbox)
                        }
                    }
                }

                RelayFrame.Sent(
                    messageId = frame.messageId,
                    directRecipients = direct,
                    queuedRecipients = queued
                )
            }

            is RelayFrame.Ack -> {
                // Permanently purge ciphertext from shared database / queue
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

            is RelayFrame.DisclosureReceipt -> {
                // Route receipt to original sender's mailbox
                val frameText = json.encodeToString<RelayFrame>(frame)
                val broker = activeBroker
                if (broker != null) {
                    broker.publish(frame.destinationMailbox, frameText)
                } else if (router.isOnline(frame.destinationMailbox)) {
                    router.dispatch(frame.destinationMailbox, frameText)
                }
                null
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
