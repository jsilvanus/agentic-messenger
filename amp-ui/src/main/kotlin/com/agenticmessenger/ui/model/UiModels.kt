package com.agenticmessenger.ui.model

import com.agenticmessenger.core.model.AssertionLevel
import com.agenticmessenger.core.model.EndpointIdentifier
import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.core.model.ProvenanceRecord
import com.agenticmessenger.core.model.TypedAnnotation
import kotlinx.serialization.Serializable

/**
 * Presence state of an endpoint or contact.
 */
enum class PresenceState {
    ONLINE,
    BUSY,
    IDLE,
    OFFLINE
}

/**
 * Visual styling and role classification for 4-party mesh provenance.
 */
enum class MeshProvenanceType {
    HUMAN_DIRECT,       // 👤 Jack or John typed/spoke directly
    AGENT_RUNNER,       // 🤖 Personal Aidos assistant / delegated runner
    HUMAN_ONLY_E2EE,    // 🔒 Cryptographically sealed from AI assistants
    SYSTEM_SERVICE      // ⚙️ System audit or relay confirmation
}

/**
 * UI representation of a contact in the mesh directory.
 */
@Serializable
data class ContactUiModel(
    val contactId: String,
    val displayName: String,
    val handle: String,
    val mailboxId: String,
    val presence: PresenceState = PresenceState.OFFLINE,
    val isAidosActive: Boolean = false,
    val lastSeenTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val pendingDisclosureCount: Int = 0,
    val latestCueBadge: String? = null,
    val latestSnippet: String? = null,
    val principalId: String = handle,
    val x25519PublicKeyHex: String = ""
)

/**
 * Dual-view toggle state for conversation timeline.
 */
enum class ConversationViewMode {
    NATURAL_STREAM,     // Full conversational natural language transcripts
    CUE_WORDS_TICKER    // Ultra-condensed semantic cue badges (e.g. [Late] [Breakdown] [ETA 35m])
}

/**
 * Audio waveform playback state for voice messages.
 */
@Serializable
data class AudioPlaybackUiState(
    val audioUrl: String? = null,
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val amplitudes: List<Float> = emptyList() // 0.0f .. 1.0f normalized
)

/**
 * Sealed class representing items in the conversation timeline stream.
 */
sealed class TimelineItemUiModel {
    abstract val id: String
    abstract val timestamp: Long

    /**
     * Standard conversational message turn.
     */
    data class Message(
        override val id: String,
        override val timestamp: Long,
        val sender: EndpointIdentifier,
        val provenanceType: MeshProvenanceType,
        val provenanceRecord: ProvenanceRecord,
        val privacyLevel: PrivacyLevel,
        val naturalText: String,
        val cueWords: List<String> = emptyList(),
        val annotations: List<TypedAnnotation> = emptyList(),
        val localInferenceNote: String? = null,
        val audioPlayback: AudioPlaybackUiState? = null,
        val isDelivered: Boolean = true,
        val isRead: Boolean = true
    ) : TimelineItemUiModel()

    /**
     * Progressive disclosure authorization card (1-tap approval).
     */
    data class ProgressiveDisclosureRequest(
        override val id: String,
        override val timestamp: Long,
        val requestId: String,
        val requester: EndpointIdentifier,
        val topicKey: String,
        val promptText: String,
        val proposedPayloadSnippet: String? = null,
        val status: DisclosureStatus = DisclosureStatus.PENDING
    ) : TimelineItemUiModel()

    /**
     * Inline Calendar Co-pilot interaction card.
     */
    data class CalendarProposal(
        override val id: String,
        override val timestamp: Long,
        val eventTitle: String,
        val proposedTime: String,
        val conflictSummary: String? = null,
        val isActionTaken: Boolean = false
    ) : TimelineItemUiModel()
}

/**
 * Status of progressive disclosure request.
 */
enum class DisclosureStatus {
    PENDING,
    APPROVED,
    DECLINED
}

/**
 * State for Login & Identity Screen.
 */
data class LoginUiState(
    val principalId: String = "did:aidos:user",
    val deviceKeyId: String = "key_dev_01",
    val epochKeyHex: String = "0123456789abcdef0123456789abcdef",
    val calculatedMailboxId: String = "",
    val relayEndpoint: String = "ws://localhost:8080/relay",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isHardwareEnclaveAvailable: Boolean = true,
    val errorMessage: String? = null
)

/**
 * State for Contacts Directory Screen.
 */
data class ContactsUiState(
    val currentUserMailboxId: String = "",
    val contacts: List<ContactUiModel> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: ContactFilter = ContactFilter.ALL,
    val isLoading: Boolean = false,
    val selectedContactId: String? = null
)

enum class ContactFilter {
    ALL,
    AIDOS_ACTIVE,
    ACTION_REQUIRED
}

/**
 * State for Conversation Screen.
 */
data class ConversationUiState(
    val conversationId: String = "",
    val contact: ContactUiModel? = null,
    val viewMode: ConversationViewMode = ConversationViewMode.NATURAL_STREAM,
    val items: List<TimelineItemUiModel> = emptyList(),
    val inputDraft: String = "",
    val isHumanOnlyE2EEMode: Boolean = false,
    val isAidosCoPilotTyping: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null
)
