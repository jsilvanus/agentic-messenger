package com.agenticmessenger.ui.presenter

import com.agenticmessenger.client.AmpSession
import com.agenticmessenger.client.identity.ClientIdentity
import com.agenticmessenger.client.model.MessageDirection
import com.agenticmessenger.client.model.MessageStatus
import com.agenticmessenger.client.network.AmpConnectionState
import com.agenticmessenger.client.storage.SqliteStorage
import com.agenticmessenger.core.crypto.BlindedMailbox
import com.agenticmessenger.core.model.AssertionLevel
import com.agenticmessenger.core.model.AttachmentPayload
import com.agenticmessenger.core.model.AttachmentType
import com.agenticmessenger.core.model.EndpointIdentifier
import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.core.model.ProvenanceRecord
import com.agenticmessenger.core.model.TypedAnnotation
import com.agenticmessenger.agent.AmpAgentService
import dev.aidos.amp.AmpConversationTurn
import com.agenticmessenger.ui.ai.OpenAiClient
import com.agenticmessenger.ui.ai.OpenAiConfig
import com.agenticmessenger.ui.ai.runAssistantTurn
import com.agenticmessenger.ui.ai.OpenAiMessage
import com.agenticmessenger.ui.model.AudioPlaybackUiState
import com.agenticmessenger.ui.model.ContactFilter
import com.agenticmessenger.ui.model.ContactUiModel
import com.agenticmessenger.ui.model.ContactsUiState
import com.agenticmessenger.ui.model.ConversationUiState
import com.agenticmessenger.ui.model.ConversationViewMode
import com.agenticmessenger.ui.model.DisclosureStatus
import com.agenticmessenger.ui.model.LoginUiState
import com.agenticmessenger.ui.model.MeshProvenanceType
import com.agenticmessenger.ui.model.PresenceState
import com.agenticmessenger.ui.model.TimelineItemUiModel
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presenter for Login & Mailbox initialization.
 */
class LoginPresenter(
    private val scope: CoroutineScope,
    var identity: ClientIdentity? = null,
    var storage: SqliteStorage? = null,
    var session: AmpSession? = null
) {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        computeMailbox()
    }

    fun onPrincipalIdChanged(principalId: String) {
        _state.update { it.copy(principalId = principalId) }
    }

    fun onDeviceKeyIdChanged(deviceKeyId: String) {
        _state.update { it.copy(deviceKeyId = deviceKeyId) }
        computeMailbox()
    }

    fun onEpochKeyHexChanged(epochKeyHex: String) {
        _state.update { it.copy(epochKeyHex = epochKeyHex) }
        computeMailbox()
    }

    fun onRelayEndpointChanged(endpoint: String) {
        _state.update { it.copy(relayEndpoint = endpoint) }
    }

    fun connectToRelay() {
        scope.launch {
            _state.update { it.copy(isConnecting = true, errorMessage = null) }
            try {
                val currentIdentity = identity ?: ClientIdentity.generate(
                    deviceId = _state.value.deviceKeyId,
                    principalId = _state.value.principalId,
                    epochKeyHex = _state.value.epochKeyHex
                ).also { identity = it }

                val currentStorage = storage ?: SqliteStorage.inMemory().also { storage = it }

                val activeSession = session ?: AmpSession(
                    identity = currentIdentity,
                    storage = currentStorage,
                    relayWsUrl = _state.value.relayEndpoint,
                    scope = scope
                ).also { session = it }

                activeSession.start()

                scope.launch {
                    activeSession.connectionState.collect { connState ->
                        when (connState) {
                            AmpConnectionState.CONNECTED -> {
                                _state.update { it.copy(isConnecting = false, isConnected = true, errorMessage = null) }
                            }
                            AmpConnectionState.CONNECTING, AmpConnectionState.RECONNECTING -> {
                                _state.update { it.copy(isConnecting = true) }
                            }
                            AmpConnectionState.DISCONNECTED, AmpConnectionState.CLOSED -> {
                                _state.update { it.copy(isConnecting = false, isConnected = false) }
                            }
                        }
                    }
                }
                _state.update { it.copy(isConnecting = false, isConnected = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isConnecting = false, errorMessage = e.message) }
            }
        }
    }

    private fun computeMailbox() {
        val s = _state.value
        try {
            val mailbox = BlindedMailbox.compute(s.epochKeyHex, s.deviceKeyId)
            _state.update { it.copy(calculatedMailboxId = mailbox) }
        } catch (_: Exception) {
            // Ignore temporary malformed hex while user types
        }
    }
}

/**
 * Presenter for Contacts Directory list.
 */
class ContactsPresenter(
    private val scope: CoroutineScope,
    initialContacts: List<ContactUiModel> = emptyList()
) {
    private val _state = MutableStateFlow(
        ContactsUiState(contacts = initialContacts)
    )
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun onFilterChanged(filter: ContactFilter) {
        _state.update { it.copy(activeFilter = filter) }
    }

    fun selectContact(contactId: String?) {
        _state.update { it.copy(selectedContactId = contactId) }
    }

    fun addContact(contact: ContactUiModel) {
        _state.update { it.copy(contacts = it.contacts + contact) }
    }

    fun filteredContacts(): List<ContactUiModel> {
        val s = _state.value
        return s.contacts.filter { contact ->
            val matchesQuery = s.searchQuery.isBlank() ||
                    contact.displayName.contains(s.searchQuery, ignoreCase = true) ||
                    contact.handle.contains(s.searchQuery, ignoreCase = true)

            val matchesFilter = when (s.activeFilter) {
                ContactFilter.ALL -> true
                ContactFilter.AIDOS_ACTIVE -> contact.isAidosActive
                ContactFilter.ACTION_REQUIRED -> contact.pendingDisclosureCount > 0
            }

            matchesQuery && matchesFilter
        }
    }
}

/**
 * Presenter for active Conversation thread.
 */

/**
 * Adapter bridging [OpenAiClient] to Aidos [ModelAdapter] for backwards compatibility and mock testing.
 */
class OpenAiClientModelAdapter(
    private val client: OpenAiClient,
    private val configProvider: () -> OpenAiConfig
) : dev.aidos.kernel.ModelAdapter {
    override val providerId: String = "openai-client"
    override val modelId: String get() = configProvider().model
    override val modelVersion: String = "1.0"
    override val contextWindow: Int = 4096
    override val isLocal: Boolean = true
    override fun supportsNativeToolCalls(): Boolean = false

    override suspend fun invoke(request: dev.aidos.kernel.ModelRequest): Result<dev.aidos.kernel.ModelResponse> {
        val messages = request.messages.mapNotNull { turn ->
            when (turn) {
                is dev.aidos.kernel.Turn.System -> OpenAiMessage("system", turn.content)
                is dev.aidos.kernel.Turn.User -> OpenAiMessage("user", turn.content.filterIsInstance<dev.aidos.kernel.ContentBlock.Text>().joinToString(" ") { it.text })
                is dev.aidos.kernel.Turn.Assistant -> turn.text?.let { OpenAiMessage("assistant", it) }
                is dev.aidos.kernel.Turn.ToolResult -> OpenAiMessage("user", turn.result.content.filterIsInstance<dev.aidos.kernel.ContentBlock.Text>().joinToString(" ") { it.text })
            }
        }
        val res = client.chatCompletion(configProvider(), messages)
        return res.map { text ->
            dev.aidos.kernel.ModelResponse(
                outputs = listOf(dev.aidos.kernel.TextOutput(text)),
                stopReason = dev.aidos.kernel.StopReason.END_TURN,
                usage = null,
                model = null
            )
        }
    }
}

class ConversationPresenter(
    private val scope: CoroutineScope,
    private val currentUserId: String,
    private val currentDeviceKeyId: String,
    initialContact: ContactUiModel? = null,
    initialItems: List<TimelineItemUiModel> = emptyList(),
    val session: AmpSession? = null,
    val openAiClient: OpenAiClient = OpenAiClient(),
    var openAiConfig: OpenAiConfig = OpenAiConfig(),
    var contacts: List<ContactUiModel> = emptyList(),
    customAgentService: AmpAgentService? = null
) {
    val agentService: AmpAgentService = customAgentService ?: AmpAgentService(
        customAdapter = OpenAiClientModelAdapter(openAiClient) { openAiConfig },
        contactLookup = { query ->
            val all = (listOfNotNull(_state.value.contact) + contacts).distinctBy { it.mailboxId }
            all.firstOrNull { it.displayName.contains(query, true) || it.handle.contains(query, true) }?.let {
                com.agenticmessenger.agent.ContactDescriptor(it.contactId, it.displayName, it.mailboxId, it.principalId, it.x25519PublicKeyHex)
            }
        },
        messageFetcher = { mailboxId, limit ->
            session?.messageRepo?.getMessages(mailboxId, limit)?.map { stored ->
                AmpConversationTurn(
                    role = if (stored.senderId == currentUserId) "user" else "assistant",
                    text = stored.plaintext,
                    senderId = stored.senderId
                )
            } ?: emptyList()
        },
        messageSender = { recipient, text, ghostwrite ->
            if (session != null) {
                val endpointType = if (ghostwrite) EndpointType.HUMAN_DIRECT else EndpointType.AGENT_DELEGATE
                val annotationsJson = if (ghostwrite) {
                    """[{"type":"ghostwritten_turn","agent_confidence":0.99}]"""
                } else {
                    """[{"type":"orchestrator_turn","agent_confidence":0.99}]"""
                }
                session.sendMessage(
                    recipientMailboxToken = recipient.mailboxId,
                    recipientPrincipalId = recipient.principalId,
                    recipientX25519PublicKeyHex = recipient.x25519PublicKeyHex.ifEmpty { "e2ee_mock_key" },
                    plaintext = text,
                    policy = PrivacyLevel.AGENT_ACCESSIBLE,
                    conversationId = recipient.mailboxId,
                    recipientEndpointType = endpointType,
                    annotationsJson = annotationsJson
                )
                true
            } else false
        }
    )
    private val _state = MutableStateFlow(
        ConversationUiState(
            contact = initialContact,
            items = initialItems
        )
    )
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var observeJob: Job? = null

    init {
        if (session != null && initialContact != null) {
            observeSessionConversation(initialContact.mailboxId)
        }
    }

    fun updateAiConfig(config: OpenAiConfig) {
        this.openAiConfig = config
    }

    fun setContact(contact: ContactUiModel) {
        _state.update { it.copy(contact = contact) }
        if (session != null) {
            observeSessionConversation(contact.mailboxId)
        }
    }

    private val hiddenSttIds = mutableSetOf<String>()

    private fun observeSessionConversation(mailboxId: String) {
        observeJob?.cancel()
        observeJob = scope.launch {
            session?.observeConversation(mailboxId)?.collect { storedMessages ->
                val mappedMessages = storedMessages.map { stored ->
                    val isSender = stored.senderId == currentUserId
                    val annotations = stored.annotationsJson ?: ""
                    val isGhostwritten = annotations.contains("ghostwritten_turn")
                    val isAgentGenerated = annotations.contains("orchestrator_turn") ||
                                           annotations.contains("agent_delegate") ||
                                           annotations.contains("assistant_response")
                    val isShieldedVoice = annotations.contains("is_shielded_voice") || annotations.contains("VOICE_NOTE")
                    val isUnshieldedForAi = annotations.contains("unshielded_for_ai")
                    val isDisclosureReceipt = annotations.contains("disclosure_receipt_received")
                    val isHumanOnly = annotations.contains("HUMAN_ONLY") || isShieldedVoice || _state.value.isHumanOnlyE2EEMode

                    val (endpointType, provenanceType) = when {
                        isGhostwritten -> EndpointType.HUMAN_DIRECT to MeshProvenanceType.HUMAN_DIRECT
                        isAgentGenerated -> EndpointType.AGENT_DELEGATE to MeshProvenanceType.AGENT_RUNNER
                        isHumanOnly -> EndpointType.HUMAN_DIRECT to MeshProvenanceType.HUMAN_ONLY_E2EE
                        else -> EndpointType.HUMAN_DIRECT to MeshProvenanceType.HUMAN_DIRECT
                    }

                    val audioPlayback = if (isShieldedVoice) {
                        AudioPlaybackUiState(
                            audioUrl = "voice_${stored.id}",
                            durationMs = 4200L,
                            currentPositionMs = 0L,
                            isPlaying = false,
                            amplitudes = listOf(0.2f, 0.5f, 0.8f, 0.4f, 0.9f, 0.6f, 0.3f)
                        )
                    } else null

                    TimelineItemUiModel.Message(
                        id = stored.id,
                        timestamp = stored.timestampMillis,
                        sender = EndpointIdentifier(
                            principalId = stored.senderId,
                            endpointType = endpointType,
                            deviceKeyId = if (isSender) currentDeviceKeyId else "peer"
                        ),
                        provenanceType = provenanceType,
                        provenanceRecord = ProvenanceRecord(
                            assertionLevel = if (isAgentGenerated && !isGhostwritten) AssertionLevel.AGENT_SYNTHESIZED else AssertionLevel.ORIGINAL_HUMAN,
                            signerKeyId = if (isSender) currentDeviceKeyId else "peer_signer",
                            signatureHex = stored.relayEnvelopeId ?: "e2ee_verified"
                        ),
                        privacyLevel = if (isHumanOnly) PrivacyLevel.HUMAN_ONLY else PrivacyLevel.AGENT_ACCESSIBLE,
                        naturalText = stored.plaintext,
                        cueWords = if (isHumanOnly) emptyList() else listOf(stored.plaintext.take(15)),
                        audioPlayback = audioPlayback,
                        isDelivered = stored.status == MessageStatus.DELIVERED || stored.status == MessageStatus.VIEWED || stored.status == MessageStatus.SENT_RELAY,
                        isRead = stored.status == MessageStatus.VIEWED,
                        isViewed = stored.status == MessageStatus.VIEWED,
                        attachments = if (isShieldedVoice) {
                            listOf(
                                AttachmentPayload(
                                    id = "att_${stored.id}",
                                    fileName = "voice_memo.ogg",
                                    mimeType = "audio/ogg",
                                    sizeBytes = 14200L,
                                    sha256Hex = "sha256_mock",
                                    type = AttachmentType.VOICE_NOTE,
                                    durationMs = 4200L
                                )
                            )
                        } else emptyList(),
                        isShieldedVoice = isShieldedVoice,
                        shieldedSttTranscript = if (isShieldedVoice) stored.plaintext else null,
                        isSttVisible = !hiddenSttIds.contains(stored.id),
                        isUnshieldedForAi = isUnshieldedForAi,
                        disclosureReceiptReceived = isDisclosureReceipt
                    )
                }
                val nonMessages = _state.value.items.filter { it !is TimelineItemUiModel.Message }
                _state.update { it.copy(items = mappedMessages + nonMessages) }

                // Automatically dispatch ViewedReceipt for inbound messages if user has not opted out
                if (_state.value.sendViewedReceipts && session != null) {
                    storedMessages.filter { it.direction == MessageDirection.INBOUND && it.status != MessageStatus.VIEWED }.forEach { inbound ->
                        session.markMessageViewed(inbound.id, mailboxId)
                    }
                }
            }
        }
    }

    fun toggleSendViewedReceipts() {
        _state.update { current ->
            val next = !current.sendViewedReceipts
            session?.isSendViewedReceiptsEnabled = next
            current.copy(sendViewedReceipts = next)
        }
    }

    fun onViewModeChanged(mode: ConversationViewMode) {
        _state.update { it.copy(viewMode = mode) }
    }

    fun onInputDraftChanged(draft: String) {
        _state.update { it.copy(inputDraft = draft) }
    }

    fun toggleHumanOnlyMode() {
        _state.update { it.copy(isHumanOnlyE2EEMode = !it.isHumanOnlyE2EEMode) }
    }

    fun sendMessage() {
        val text = _state.value.inputDraft.trim()
        if (text.isEmpty()) return

        val isHumanOnly = _state.value.isHumanOnlyE2EEMode
        val now = System.currentTimeMillis()
        val contact = _state.value.contact

        val isAiCommand = openAiConfig.isEnabled && !isHumanOnly &&
                (text.startsWith("@aidos", ignoreCase = true) || text.startsWith("@ai", ignoreCase = true))
        val aiPrompt = if (isAiCommand) {
            text.substringAfter(" ").trim()
        } else null

        if (session != null && contact != null) {
            _state.update { it.copy(inputDraft = "") }
            scope.launch {
                session.sendMessage(
                    recipientMailboxToken = contact.mailboxId,
                    recipientPrincipalId = contact.principalId,
                    recipientX25519PublicKeyHex = contact.x25519PublicKeyHex,
                    plaintext = text,
                    policy = if (isHumanOnly) PrivacyLevel.HUMAN_ONLY else PrivacyLevel.AGENT_ACCESSIBLE,
                    conversationId = contact.mailboxId
                )

                if (isAiCommand && !aiPrompt.isNullOrBlank()) {
                    askAi(aiPrompt)
                }
            }
        } else {
            val newMessage = TimelineItemUiModel.Message(
                id = "msg_$now",
                timestamp = now,
                sender = EndpointIdentifier(
                    principalId = currentUserId,
                    endpointType = EndpointType.HUMAN_DIRECT,
                    deviceKeyId = currentDeviceKeyId
                ),
                provenanceType = if (isHumanOnly) MeshProvenanceType.HUMAN_ONLY_E2EE else MeshProvenanceType.HUMAN_DIRECT,
                provenanceRecord = ProvenanceRecord(
                    assertionLevel = AssertionLevel.ORIGINAL_HUMAN,
                    signerKeyId = currentDeviceKeyId,
                    signatureHex = "mock_sig_hex"
                ),
                privacyLevel = if (isHumanOnly) PrivacyLevel.HUMAN_ONLY else PrivacyLevel.AGENT_ACCESSIBLE,
                naturalText = text,
                cueWords = if (isHumanOnly) emptyList() else listOf(text.take(15))
            )

            _state.update {
                it.copy(
                    items = it.items + newMessage,
                    inputDraft = ""
                )
            }

            if (isAiCommand && !aiPrompt.isNullOrBlank()) {
                askAi(aiPrompt)
            }
        }
    }

    /**
     * Queries the configured OpenAI-compatible API endpoint (Ollama / Aidos)
     * and posts an AGENT_RUNNER Turn to the conversation.
     */
    fun askAi(promptOverride: String? = null) {
        val userPrompt = promptOverride?.trim() ?: _state.value.inputDraft.trim()
        if (userPrompt.isEmpty()) return

        if (promptOverride == null) {
            _state.update { it.copy(inputDraft = "") }
        }

        scope.launch {
            // Context window: up to 6 recent turns mapped to Aidos conversation turns
            // Sealed turns are strictly quarantined from AI models unless recipient explicitly unshielded them
            val recentTurns = _state.value.items
                .filterIsInstance<TimelineItemUiModel.Message>()
                .filter { it.privacyLevel == PrivacyLevel.AGENT_ACCESSIBLE || it.isUnshieldedForAi }
                .takeLast(6)
                .map { turn ->
                    val role = if (turn.provenanceType == MeshProvenanceType.AGENT_RUNNER) "assistant" else "user"
                    AmpConversationTurn(
                        role = role,
                        text = turn.naturalText,
                        senderId = turn.sender.principalId
                    )
                }

            // Models are Aidos's: the turn runs in the desktop Aidos app, which decides where inference may go.
            val result = agentService.runAssistantTurn(openAiConfig, userPrompt, recentTurns)

            val (replyText, cueWords) = result.fold(
                onSuccess = { turnResult ->
                    val cues = mutableListOf<String>()
                    for (toolCall in turnResult.executedToolCalls) {
                        cues.add(toolCall.toolName)
                    }
                    if (cues.isEmpty()) {
                        cues.add(turnResult.replyText.take(15))
                    }
                    Pair(turnResult.replyText, cues)
                },
                onFailure = { err ->
                    Pair("⚠️ [Aidos AI Error: " + (err.message ?: "Endpoint unreachable") + "]", listOf("Error"))
                }
            )

            val now = System.currentTimeMillis()
            val contact = _state.value.contact

            if (session != null && contact != null) {
                session.sendMessage(
                    recipientMailboxToken = contact.mailboxId,
                    recipientPrincipalId = contact.principalId,
                    recipientX25519PublicKeyHex = contact.x25519PublicKeyHex,
                    plaintext = replyText,
                    policy = PrivacyLevel.AGENT_ACCESSIBLE,
                    conversationId = contact.mailboxId,
                    recipientEndpointType = EndpointType.AGENT_DELEGATE,
                    annotationsJson = """[{"type":"assistant_response","confidence":0.98}]"""
                )
            } else {
                val aiMsg = TimelineItemUiModel.Message(
                    id = "ai_$now",
                    timestamp = now,
                    sender = EndpointIdentifier(
                        principalId = "$currentUserId:aidos",
                        endpointType = EndpointType.AGENT_DELEGATE,
                        deviceKeyId = "aidos_runner"
                    ),
                    provenanceType = MeshProvenanceType.AGENT_RUNNER,
                    provenanceRecord = ProvenanceRecord(
                        assertionLevel = AssertionLevel.AGENT_SYNTHESIZED,
                        signerKeyId = "aidos_runner",
                        signatureHex = "ai_provenance_sig"
                    ),
                    privacyLevel = PrivacyLevel.AGENT_ACCESSIBLE,
                    naturalText = replyText,
                    cueWords = cueWords,
                    annotations = listOf(TypedAnnotation(type = "assistant_response", confidence = 0.98)),
                    localInferenceNote = "Generated via " + openAiConfig.baseUrl + " (" + openAiConfig.model + ")"
                )
                _state.update { it.copy(items = it.items + aiMsg) }
            }
        }
    }

    fun approveDisclosure(requestId: String) {
        _state.update { s ->
            val updated = s.items.map { item ->
                if (item is TimelineItemUiModel.ProgressiveDisclosureRequest && item.requestId == requestId) {
                    item.copy(status = DisclosureStatus.APPROVED)
                } else item
            }
            s.copy(items = updated)
        }
    }

    fun declineDisclosure(requestId: String) {
        _state.update { s ->
            val updated = s.items.map { item ->
                if (item is TimelineItemUiModel.ProgressiveDisclosureRequest && item.requestId == requestId) {
                    item.copy(status = DisclosureStatus.DECLINED)
                } else item
            }
            s.copy(items = updated)
        }
    }

    fun confirmCalendarAction(proposalId: String) {
        _state.update { s ->
            val updated = s.items.map { item ->
                if (item is TimelineItemUiModel.CalendarProposal && item.id == proposalId) {
                    item.copy(isActionTaken = true)
                } else item
            }
            s.copy(items = updated)
        }
    }

    fun toggleSttVisibility(messageId: String) {
        if (hiddenSttIds.contains(messageId)) {
            hiddenSttIds.remove(messageId)
        } else {
            hiddenSttIds.add(messageId)
        }
        _state.update { s ->
            val updated = s.items.map { item ->
                if (item is TimelineItemUiModel.Message && item.id == messageId) {
                    item.copy(isSttVisible = !hiddenSttIds.contains(messageId))
                } else item
            }
            s.copy(items = updated)
        }
    }

    fun unshieldMessageForAi(messageId: String) {
        scope.launch {
            if (session != null) {
                val contact = _state.value.contact
                session.unshieldMessageForAi(messageId, contact?.mailboxId)
            } else {
                _state.update { s ->
                    val updated = s.items.map { item ->
                        if (item is TimelineItemUiModel.Message && item.id == messageId) {
                            item.copy(isUnshieldedForAi = true)
                        } else item
                    }
                    s.copy(items = updated)
                }
            }
        }
    }

    fun sendSealedVoiceNote(audioBase64: String, durationMs: Long, transcript: String) {
        val contact = _state.value.contact
        val voiceAttachment = AttachmentPayload(
            id = UUID.randomUUID().toString(),
            fileName = "voice_${System.currentTimeMillis()}.ogg",
            mimeType = "audio/ogg",
            sizeBytes = (audioBase64.length * 3L / 4L).coerceAtLeast(1024L),
            sha256Hex = "voice_sha256",
            type = AttachmentType.VOICE_NOTE,
            durationMs = durationMs,
            inlineBase64 = audioBase64
        )

        if (session != null && contact != null) {
            scope.launch {
                session.sendMessage(
                    recipientMailboxToken = contact.mailboxId,
                    recipientPrincipalId = contact.principalId,
                    recipientX25519PublicKeyHex = contact.x25519PublicKeyHex,
                    plaintext = transcript,
                    policy = PrivacyLevel.HUMAN_ONLY,
                    conversationId = contact.mailboxId,
                    recipientEndpointType = EndpointType.HUMAN_DIRECT,
                    attachments = listOf(voiceAttachment),
                    isShieldedVoice = true,
                    shieldedSttTranscript = transcript
                )
            }
        } else {
            val now = System.currentTimeMillis()
            val msgId = "voice_$now"
            val newMsg = TimelineItemUiModel.Message(
                id = msgId,
                timestamp = now,
                sender = EndpointIdentifier(currentUserId, EndpointType.HUMAN_DIRECT, currentDeviceKeyId),
                provenanceType = MeshProvenanceType.HUMAN_ONLY_E2EE,
                provenanceRecord = ProvenanceRecord(
                    assertionLevel = AssertionLevel.ORIGINAL_HUMAN,
                    signerKeyId = currentDeviceKeyId,
                    signatureHex = "mock_enclave_sig"
                ),
                privacyLevel = PrivacyLevel.HUMAN_ONLY,
                naturalText = transcript,
                cueWords = emptyList(),
                audioPlayback = AudioPlaybackUiState(
                    audioUrl = "voice_$msgId",
                    durationMs = durationMs,
                    currentPositionMs = 0L,
                    isPlaying = false,
                    amplitudes = listOf(0.2f, 0.5f, 0.8f, 0.4f, 0.9f, 0.6f, 0.3f)
                ),
                attachments = listOf(voiceAttachment),
                isShieldedVoice = true,
                shieldedSttTranscript = transcript,
                isSttVisible = true
            )
            _state.update { it.copy(items = it.items + newMsg) }
        }
    }

    fun sendAttachment(file: AttachmentPayload, note: String = "") {
        val isHumanOnly = _state.value.isHumanOnlyE2EEMode
        val contact = _state.value.contact
        val annotationsJson = if (isHumanOnly) """[{"type":"HUMAN_ONLY"}]""" else """[{"type":"AGENT_ACCESSIBLE"}]"""
        if (session != null && contact != null) {
            scope.launch {
                session.sendMessage(
                    recipientMailboxToken = contact.mailboxId,
                    recipientPrincipalId = contact.principalId,
                    recipientX25519PublicKeyHex = contact.x25519PublicKeyHex,
                    plaintext = note.ifBlank { "Sent attachment: ${file.fileName}" },
                    policy = if (isHumanOnly) PrivacyLevel.HUMAN_ONLY else PrivacyLevel.AGENT_ACCESSIBLE,
                    conversationId = contact.mailboxId,
                    recipientEndpointType = EndpointType.HUMAN_DIRECT,
                    annotationsJson = annotationsJson,
                    attachments = listOf(file)
                )
            }
        } else {
            val now = System.currentTimeMillis()
            val msgId = "file_$now"
            val newMsg = TimelineItemUiModel.Message(
                id = msgId,
                timestamp = now,
                sender = EndpointIdentifier(currentUserId, EndpointType.HUMAN_DIRECT, currentDeviceKeyId),
                provenanceType = if (isHumanOnly) MeshProvenanceType.HUMAN_ONLY_E2EE else MeshProvenanceType.HUMAN_DIRECT,
                provenanceRecord = ProvenanceRecord(
                    assertionLevel = AssertionLevel.ORIGINAL_HUMAN,
                    signerKeyId = currentDeviceKeyId,
                    signatureHex = "mock_sig"
                ),
                privacyLevel = if (isHumanOnly) PrivacyLevel.HUMAN_ONLY else PrivacyLevel.AGENT_ACCESSIBLE,
                naturalText = note.ifBlank { "Sent attachment: ${file.fileName}" },
                cueWords = emptyList(),
                attachments = listOf(file)
            )
            _state.update { it.copy(items = it.items + newMsg) }
        }
    }
}
