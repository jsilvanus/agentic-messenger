package com.agenticmessenger.client.network

import com.agenticmessenger.client.identity.ClientIdentity
import com.agenticmessenger.client.model.IncomingDecryptedMessage
import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.core.protocol.RelayFrame
import com.agenticmessenger.crypto.DualCompartmentEncryptor
import com.agenticmessenger.crypto.DualCompartmentEnvelope
import com.agenticmessenger.crypto.RecipientKey
import com.agenticmessenger.crypto.hexToByteArray
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Resilient client connection manager to the AMP Blind Relay.
 * Handles WebSocket lifecycle, auto-reconnection with exponential backoff,
 * zero-knowledge mailbox dispatch, and end-to-end payload encryption/decryption.
 */
class AmpRelayClient(
    val serverWsUrl: String,
    val identity: ClientIdentity,
    private val client: HttpClient = createDefaultHttpClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    val autoReconnect: Boolean = true
) : Closeable {

    private val logger = LoggerFactory.getLogger(AmpRelayClient::class.java)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val _connectionState = MutableStateFlow(AmpConnectionState.DISCONNECTED)
    val connectionState: StateFlow<AmpConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<IncomingDecryptedMessage>(extraBufferCapacity = 128)
    val incomingMessages: SharedFlow<IncomingDecryptedMessage> = _incomingMessages.asSharedFlow()

    private val _rawFrames = MutableSharedFlow<RelayFrame>(extraBufferCapacity = 128)
    val rawFrames: SharedFlow<RelayFrame> = _rawFrames.asSharedFlow()

    private val isExplicitlyClosed = AtomicBoolean(false)
    private var connectionJob: Job? = null
    private var activeSession: DefaultClientWebSocketSession? = null

    /**
     * Initiates connection to the blind relay server.
     */
    fun connect() {
        if (isExplicitlyClosed.get()) {
            throw IllegalStateException("AmpRelayClient is closed and cannot be reconnected.")
        }
        if (connectionJob?.isActive == true) return

        connectionJob = scope.launch {
            var attempt = 0
            while (isActive && !isExplicitlyClosed.get()) {
                val state = if (attempt == 0) AmpConnectionState.CONNECTING else AmpConnectionState.RECONNECTING
                _connectionState.value = state

                val urlWithParams = if (serverWsUrl.contains("?")) {
                    "$serverWsUrl&mailbox_token=${identity.mailboxToken}"
                } else {
                    "$serverWsUrl?mailbox_token=${identity.mailboxToken}"
                }

                try {
                    logger.info("Connecting to relay at {} (attempt={})", urlWithParams, attempt)
                    client.webSocket(urlString = urlWithParams) {
                        activeSession = this
                        _connectionState.value = AmpConnectionState.CONNECTED
                        attempt = 0
                        logger.info("Connected to blind relay for mailbox '{}'", identity.mailboxToken)

                        // 1. Send explicit REGISTER frame
                        sendFrame(RelayFrame.Register(mailboxId = identity.mailboxToken))

                        // 2. Launch keepalive ping job
                        val pingJob = launch {
                            while (isActive) {
                                delay(25_000)
                                try {
                                    sendFrame(RelayFrame.Ping(System.currentTimeMillis()))
                                } catch (e: Exception) {
                                    logger.debug("Ping failed: {}", e.message)
                                    break
                                }
                            }
                        }

                        // 3. Process inbound stream
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    handleInboundFrameText(frame.readText())
                                }
                            }
                        } finally {
                            pingJob.cancel()
                            activeSession = null
                        }
                    }
                } catch (e: CancellationException) {
                    logger.debug("Relay connection coroutine cancelled")
                    break
                } catch (e: Exception) {
                    logger.warn("Relay connection error: {}", e.message)
                } finally {
                    activeSession = null
                }

                if (isExplicitlyClosed.get() || !autoReconnect) {
                    _connectionState.value = AmpConnectionState.CLOSED
                    break
                }

                _connectionState.value = AmpConnectionState.RECONNECTING
                attempt++
                val delayMs = minOf(500L * (1L shl minOf(attempt, 5)), 15_000L)
                logger.info("Will attempt reconnect in {}ms", delayMs)
                delay(delayMs)
            }
            if (_connectionState.value != AmpConnectionState.CLOSED) {
                _connectionState.value = AmpConnectionState.DISCONNECTED
            }
        }
    }

    /**
     * Encrypts and transmits a direct end-to-end encrypted message to a peer mailbox.
     *
     * @param recipientMailboxToken The blinded mailbox token of the recipient's relay queue.
     * @param recipientPrincipalId The DID or identifier of the recipient (e.g. "did:aidos:bob").
     * @param recipientX25519PublicKeyHex The 32-byte public key hex of the recipient.
     * @param plaintext The message content to encrypt.
     * @param policy The privacy level (AGENT_ACCESSIBLE or HUMAN_ONLY).
     * @param conversationId Optional conversation ID; defaults to recipient principal ID.
     * @return The unique message ID assigned to this dispatch.
     */
    suspend fun sendDirectMessage(
        recipientMailboxToken: String,
        recipientPrincipalId: String,
        recipientX25519PublicKeyHex: String,
        plaintext: String,
        policy: PrivacyLevel = PrivacyLevel.HUMAN_ONLY,
        conversationId: String = recipientPrincipalId
    ): String {
        val messageId = UUID.randomUUID().toString()

        // 1. Encrypt payload with DualCompartmentEncryptor
        val recipientKey = RecipientKey(
            recipientId = recipientPrincipalId,
            endpointType = EndpointType.HUMAN_DIRECT,
            x25519PublicKey = recipientX25519PublicKeyHex.hexToByteArray()
        )

        // Also add sender self so sender can decrypt their own message history if desired
        val selfRecipientKey = identity.toRecipientKey()
        val recipients = listOf(recipientKey, selfRecipientKey)

        val encryptedEnvelope = DualCompartmentEncryptor.encrypt(
            plaintext = plaintext,
            policy = policy,
            recipients = recipients
        )

        val payloadCiphertext = json.encodeToString(DualCompartmentEnvelope.serializer(), encryptedEnvelope)

        // 2. Wrap into RelayFrame.Send
        val sendFrame = RelayFrame.Send(
            messageId = messageId,
            destinationMailboxes = listOf(recipientMailboxToken),
            payloadCiphertext = payloadCiphertext,
            sourceMailbox = identity.mailboxToken
        )

        // 3. Transmit frame
        sendFrame(sendFrame)
        logger.debug("Dispatched message '{}' to mailbox '{}'", messageId, recipientMailboxToken)
        return messageId
    }

    /**
     * Sends a raw RelayFrame over the active WebSocket session.
     */
    suspend fun sendFrame(frame: RelayFrame) {
        val session = activeSession
            ?: throw IllegalStateException("Not connected to relay server (state=${connectionState.value})")
        val text = json.encodeToString(RelayFrame.serializer(), frame)
        session.send(Frame.Text(text))
    }

    /**
     * Processes raw text received from the relay server.
     */
    private suspend fun handleInboundFrameText(text: String) {
        try {
            val frame = json.decodeFromString(RelayFrame.serializer(), text)
            _rawFrames.emit(frame)

            when (frame) {
                is RelayFrame.Deliver -> {
                    processDeliverFrame(frame)
                }

                is RelayFrame.OfflineBatch -> {
                    for (del in frame.messages) {
                        processDeliverFrame(del)
                    }
                }

                is RelayFrame.Ping -> {
                    sendFrame(RelayFrame.Pong(frame.nonce))
                }

                is RelayFrame.Registered -> {
                    logger.debug("Relay confirmed mailbox registration for '{}'", frame.mailboxId)
                }

                is RelayFrame.Sent -> {
                    logger.debug("Relay confirmed message '{}' sent", frame.messageId)
                }

                is RelayFrame.AckConfirm -> {
                    logger.debug("Relay confirmed ACK for message '{}'", frame.messageId)
                }

                is RelayFrame.Error -> {
                    logger.error("Relay returned error: [{}] {}", frame.code, frame.message)
                }

                else -> {
                    // Other frames (Pong, etc.)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to process inbound frame: {}", e.message)
        }
    }

    private suspend fun processDeliverFrame(deliver: RelayFrame.Deliver) {
        try {
            // Decrypt payload
            val dualEnvelope = json.decodeFromString(DualCompartmentEnvelope.serializer(), deliver.payloadCiphertext)
            val decrypted = DualCompartmentEncryptor.decryptToString(
                envelope = dualEnvelope,
                recipientId = identity.principalId,
                recipientPrivateKey = identity.x25519KeyPair.privateKey
            )

            val incomingMsg = IncomingDecryptedMessage(
                messageId = deliver.messageId,
                conversationId = deliver.sourceMailbox ?: "default",
                senderPrincipalId = deliver.sourceMailbox ?: "unknown",
                plaintext = decrypted,
                policy = dualEnvelope.policy,
                timestamp = deliver.timestamp,
                sourceMailbox = deliver.sourceMailbox
            )

            // Emit to listeners
            _incomingMessages.emit(incomingMsg)

            // Automatically send delivery ACK back to relay to purge message from queue
            sendFrame(RelayFrame.Ack(messageId = deliver.messageId, mailboxId = identity.mailboxToken))
            logger.debug("Delivered & ACKed message '{}'", deliver.messageId)
        } catch (e: Exception) {
            logger.error("Failed to decrypt delivered message '{}': {}", deliver.messageId, e.message)
        }
    }

    override fun close() {
        if (isExplicitlyClosed.compareAndSet(false, true)) {
            _connectionState.value = AmpConnectionState.CLOSED
            scope.launch {
                try {
                    activeSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client closed"))
                } catch (ignored: Exception) {
                }
                connectionJob?.cancel()
            }
        }
    }

    companion object {
        fun createDefaultHttpClient(): HttpClient {
            return HttpClient(CIO) {
                install(WebSockets) {
                    pingIntervalMillis = 30_000
                }
            }
        }
    }
}
