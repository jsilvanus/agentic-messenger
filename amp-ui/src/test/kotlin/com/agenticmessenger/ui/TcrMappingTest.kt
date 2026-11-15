package com.agenticmessenger.ui

import com.agenticmessenger.core.model.AssertionLevel
import com.agenticmessenger.core.model.AttachmentPayload
import com.agenticmessenger.core.model.AttachmentType
import com.agenticmessenger.core.model.EndpointIdentifier
import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.core.model.ProvenanceRecord
import com.agenticmessenger.core.model.TypedAnnotation
import com.agenticmessenger.ui.model.MeshProvenanceType
import com.agenticmessenger.ui.model.TimelineItemUiModel
import com.agenticmessenger.ui.model.toTcr
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Amp's envelope types and the TCR's are separate copies (the TCR cannot depend on amp-core), so
 * every enum constant and field must survive the mapping. Adding a constant to one side without the
 * other fails here instead of at runtime in `valueOf`.
 */
class TcrMappingTest {

    @Test fun everyCoreEnumConstantHasATcrCounterpart() {
        for (e in EndpointType.entries) dev.aidos.tcr.model.EndpointType.valueOf(e.name)
        for (e in PrivacyLevel.entries) dev.aidos.tcr.model.PrivacyLevel.valueOf(e.name)
        for (e in AssertionLevel.entries) dev.aidos.tcr.model.AssertionLevel.valueOf(e.name)
        for (e in AttachmentType.entries) dev.aidos.tcr.model.AttachmentType.valueOf(e.name)
        assertEquals(EndpointType.entries.size, dev.aidos.tcr.model.EndpointType.entries.size)
        assertEquals(PrivacyLevel.entries.size, dev.aidos.tcr.model.PrivacyLevel.entries.size)
        assertEquals(AssertionLevel.entries.size, dev.aidos.tcr.model.AssertionLevel.entries.size)
        assertEquals(AttachmentType.entries.size, dev.aidos.tcr.model.AttachmentType.entries.size)
    }

    @Test fun messageMappingKeepsEveryField() {
        val msg = TimelineItemUiModel.Message(
            id = "m1", timestamp = 42L,
            sender = EndpointIdentifier("did:aidos:maria", EndpointType.AGENT_DELEGATE, "key-1"),
            provenanceType = MeshProvenanceType.AGENT_RUNNER,
            provenanceRecord = ProvenanceRecord(AssertionLevel.AGENT_SYNTHESIZED, "signer", "ab", "parent"),
            privacyLevel = PrivacyLevel.HUMAN_ONLY,
            naturalText = "Hei", cueWords = listOf("a"),
            annotations = listOf(TypedAnnotation("cue", 0.5, mapOf("k" to "v"))),
            localInferenceNote = "note",
            attachments = listOf(AttachmentPayload("a1", "f.ogg", "audio/ogg", 9, "00", AttachmentType.VOICE_NOTE, durationMs = 1000)),
            isShieldedVoice = true, shieldedSttTranscript = "t", isSttVisible = false,
            isUnshieldedForAi = true, disclosureReceiptReceived = true, isViewed = true, isRead = false, isDelivered = false,
        )
        val t = msg.toTcr()
        assertEquals("m1", t.id)
        assertEquals(42L, t.timestamp)
        assertEquals(dev.aidos.tcr.model.EndpointType.AGENT_DELEGATE, t.sender.endpointType)
        assertEquals("did:aidos:maria", t.sender.principalId)
        assertEquals("key-1", t.sender.deviceKeyId)
        assertEquals(dev.aidos.tcr.model.AssertionLevel.AGENT_SYNTHESIZED, t.provenanceRecord.assertionLevel)
        assertEquals("parent", t.provenanceRecord.parentEnvelopeId)
        assertEquals(dev.aidos.tcr.model.PrivacyLevel.HUMAN_ONLY, t.privacyLevel)
        assertEquals(listOf("a"), t.cueWords)
        assertEquals(mapOf("k" to "v"), t.annotations.single().attributes)
        assertEquals(dev.aidos.tcr.model.AttachmentType.VOICE_NOTE, t.attachments.single().type)
        assertEquals(1000L, t.attachments.single().durationMs)
        assertEquals(listOf(true, true, true, false, false, false), listOf(
            t.isShieldedVoice, t.isUnshieldedForAi, t.disclosureReceiptReceived, t.isSttVisible, t.isRead, t.isDelivered,
        ))
        assertEquals(true, t.isViewed)
        assertEquals("note", t.localInferenceNote)
    }

    @Test fun disclosureRequestMappingKeepsEveryField() {
        val item = TimelineItemUiModel.ProgressiveDisclosureRequest(
            id = "d1", timestamp = 7L, requestId = "r1",
            requester = EndpointIdentifier("did:aidos:agent", EndpointType.AGENT_DELEGATE, "k"),
            topicKey = "calendar", promptText = "Share?", proposedPayloadSnippet = "Tue 10:00",
        )
        val t = item.toTcr() as dev.aidos.tcr.model.TimelineItemUiModel.ProgressiveDisclosureRequest
        assertEquals("d1", t.id)
        assertEquals(7L, t.timestamp)
        assertEquals("r1", t.requestId)
        assertEquals(dev.aidos.tcr.model.EndpointType.AGENT_DELEGATE, t.requester.endpointType)
        assertEquals("calendar", t.topicKey)
        assertEquals("Share?", t.promptText)
        assertEquals("Tue 10:00", t.proposedPayloadSnippet)
        assertEquals(item.status, t.status)
    }

    @Test fun calendarProposalMappingKeepsEveryField() {
        val item = TimelineItemUiModel.CalendarProposal(
            id = "c1", timestamp = 9L, eventTitle = "Lunch", proposedTime = "12:00",
            conflictSummary = "Overlaps", isActionTaken = true,
        )
        val t = item.toTcr() as dev.aidos.tcr.model.TimelineItemUiModel.CalendarProposal
        assertEquals("c1", t.id)
        assertEquals(9L, t.timestamp)
        assertEquals("Lunch", t.eventTitle)
        assertEquals("12:00", t.proposedTime)
        assertEquals("Overlaps", t.conflictSummary)
        assertEquals(true, t.isActionTaken)
    }

    @Test fun timelineMappingDispatchesOnTheItemKind() {
        val items = listOf<TimelineItemUiModel>(
            TimelineItemUiModel.CalendarProposal("c", 1L, "x", "y"),
            TimelineItemUiModel.ProgressiveDisclosureRequest(
                "d", 2L, "r", EndpointIdentifier("p", EndpointType.HUMAN_DIRECT, "k"), "t", "q",
            ),
        )
        val mapped = items.map { it.toTcr() }
        assertEquals(listOf("c", "d"), mapped.map { it.id })
        assertEquals(listOf(1L, 2L), mapped.map { it.timestamp })
    }
}
