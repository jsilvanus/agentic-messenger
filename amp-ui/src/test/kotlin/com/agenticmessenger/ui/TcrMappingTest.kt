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
}
