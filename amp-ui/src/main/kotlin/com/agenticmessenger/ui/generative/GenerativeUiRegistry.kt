package com.agenticmessenger.ui.generative

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.core.model.UniversalEnvelope
import com.agenticmessenger.ui.atomic.PrivacySealBadge
import com.agenticmessenger.ui.atomic.ProvenanceBadge
import com.agenticmessenger.ui.model.DisclosureStatus
import com.agenticmessenger.ui.model.MeshProvenanceType
import com.agenticmessenger.ui.model.TimelineItemUiModel
import com.agenticmessenger.ui.molecular.ApprovalCard
import com.agenticmessenger.ui.molecular.CalendarCopilotCard
import com.agenticmessenger.ui.molecular.MessageBubble

/**
 * Generative UI Component Dispatcher & Renderer.
 *
 * Bridges Universal Agentic Envelopes (UAE) and autonomous agent output
 * to rich, interactive Compose Multiplatform components.
 *
 * Used inside:
 * 1) Aidos Desktop: Infinite Canvas spatial nodes, execution graph inspection, and generative cards.
 * 2) Agentic Messenger Standalone: Dynamic timeline rendering.
 */
object GenerativeUiRegistry {

    /**
     * Map a UniversalEnvelope into its corresponding TimelineItemUiModel.
     */
    fun mapEnvelopeToTimelineItem(envelope: UniversalEnvelope): TimelineItemUiModel {
        val provenanceType = when {
            envelope.policy.privacyLevel == PrivacyLevel.HUMAN_ONLY -> MeshProvenanceType.HUMAN_ONLY_E2EE
            envelope.provenance.assertionLevel == com.agenticmessenger.core.model.AssertionLevel.ORIGINAL_HUMAN -> MeshProvenanceType.HUMAN_DIRECT
            envelope.provenance.assertionLevel == com.agenticmessenger.core.model.AssertionLevel.AGENT_SYNTHESIZED -> MeshProvenanceType.AGENT_RUNNER
            else -> MeshProvenanceType.SYSTEM_SERVICE
        }

        // Check if there is an escrowed topic request in annotations
        val isEscrowRequest = envelope.payload.structuredAnnotations.any { it.type == "escalation_request" }
        if (isEscrowRequest) {
            val req = envelope.payload.structuredAnnotations.first { it.type == "escalation_request" }
            return TimelineItemUiModel.ProgressiveDisclosureRequest(
                id = envelope.envelopeId,
                timestamp = envelope.timestamp,
                requestId = req.attributes["request_id"] ?: envelope.envelopeId,
                requester = envelope.routing.sender,
                topicKey = req.attributes["topic"] ?: "UNKNOWN_TOPIC",
                promptText = envelope.payload.naturalLanguage,
                proposedPayloadSnippet = req.attributes["preview_value"],
                status = DisclosureStatus.PENDING
            )
        }

        // Check if there is a calendar action
        val isCalendarProposal = envelope.payload.structuredAnnotations.any { it.type == "calendar_proposal" }
        if (isCalendarProposal) {
            val cal = envelope.payload.structuredAnnotations.first { it.type == "calendar_proposal" }
            return TimelineItemUiModel.CalendarProposal(
                id = envelope.envelopeId,
                timestamp = envelope.timestamp,
                eventTitle = cal.attributes["title"] ?: "Meeting",
                proposedTime = cal.attributes["time"] ?: "TBD",
                conflictSummary = cal.attributes["conflict"],
                isActionTaken = false
            )
        }

        // Standard Message turn
        val cues = envelope.payload.structuredAnnotations.mapNotNull { it.attributes["cue"] }
        val localNotice = if (envelope.payload.structuredAnnotations.any { it.type == "local_inference" }) {
            "Answered locally from cached envelope (zero network ping)"
        } else null

        return TimelineItemUiModel.Message(
            id = envelope.envelopeId,
            timestamp = envelope.timestamp,
            sender = envelope.routing.sender,
            provenanceType = provenanceType,
            provenanceRecord = envelope.provenance,
            privacyLevel = envelope.policy.privacyLevel,
            naturalText = envelope.payload.naturalLanguage,
            cueWords = cues,
            annotations = envelope.payload.structuredAnnotations,
            localInferenceNote = localNotice
        )
    }

    /**
     * Composable dispatcher rendering any TimelineItemUiModel as an atomic/molecular UI block.
     */
    @Composable
    fun RenderItem(
        item: TimelineItemUiModel,
        currentUserId: String,
        modifier: Modifier = Modifier,
        onApproveDisclosure: (requestId: String) -> Unit = {},
        onDeclineDisclosure: (requestId: String) -> Unit = {},
        onConfirmCalendar: () -> Unit = {},
        onToggleAudio: () -> Unit = {}
    ) {
        when (item) {
            is TimelineItemUiModel.Message -> {
                val isSelf = item.sender.principalId == currentUserId
                MessageBubble(
                    message = item,
                    isSelf = isSelf,
                    modifier = modifier,
                    onToggleAudio = onToggleAudio
                )
            }
            is TimelineItemUiModel.ProgressiveDisclosureRequest -> {
                ApprovalCard(
                    requestId = item.requestId,
                    topicKey = item.topicKey,
                    promptText = item.promptText,
                    proposedValue = item.proposedPayloadSnippet,
                    status = item.status,
                    modifier = modifier,
                    onApprove = onApproveDisclosure,
                    onDecline = onDeclineDisclosure
                )
            }
            is TimelineItemUiModel.CalendarProposal -> {
                CalendarCopilotCard(
                    eventTitle = item.eventTitle,
                    proposedTime = item.proposedTime,
                    conflictSummary = item.conflictSummary,
                    isActionTaken = item.isActionTaken,
                    modifier = modifier,
                    onConfirmAction = onConfirmCalendar
                )
            }
        }
    }
}
