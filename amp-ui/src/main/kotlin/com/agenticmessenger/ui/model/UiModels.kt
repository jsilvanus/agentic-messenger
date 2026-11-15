package com.agenticmessenger.ui.model

import com.agenticmessenger.core.model.AssertionLevel
import com.agenticmessenger.core.model.AttachmentPayload
import com.agenticmessenger.core.model.EndpointIdentifier
import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.core.model.ProvenanceRecord
import com.agenticmessenger.core.model.TypedAnnotation
import kotlinx.serialization.Serializable

// D46 (amended): the presentation-only models below are owned by aidos-tcr; Amp keeps aliases so
// call sites don't change. Only the types that carry Amp core envelope types (timeline items,
// conversation state) stay here, and are mapped to TCR models in TcrMapping.kt.
typealias PresenceState = dev.aidos.tcr.model.PresenceState
typealias MeshProvenanceType = dev.aidos.tcr.model.MeshProvenanceType
typealias ContactUiModel = dev.aidos.tcr.model.ContactUiModel
typealias ConversationViewMode = dev.aidos.tcr.model.ConversationViewMode
typealias AudioPlaybackUiState = dev.aidos.tcr.model.AudioPlaybackUiState
typealias DisclosureStatus = dev.aidos.tcr.model.DisclosureStatus
typealias LoginUiState = dev.aidos.tcr.model.LoginUiState
typealias ContactsUiState = dev.aidos.tcr.model.ContactsUiState
typealias ContactFilter = dev.aidos.tcr.model.ContactFilter

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
        val isRead: Boolean = true,
        val isViewed: Boolean = false,
        val attachments: List<AttachmentPayload> = emptyList(),
        val isShieldedVoice: Boolean = false,
        val shieldedSttTranscript: String? = null,
        val isSttVisible: Boolean = true,
        val isUnshieldedForAi: Boolean = false,
        val disclosureReceiptReceived: Boolean = false
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
    val isShieldedSttOptOutGlobal: Boolean = false,
    val sendViewedReceipts: Boolean = true,
    val errorMessage: String? = null
)
