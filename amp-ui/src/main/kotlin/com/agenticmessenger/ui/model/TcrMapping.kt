package com.agenticmessenger.ui.model

import com.agenticmessenger.core.model.AssertionLevel
import com.agenticmessenger.core.model.AttachmentPayload
import com.agenticmessenger.core.model.AttachmentType
import com.agenticmessenger.core.model.EndpointIdentifier
import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.core.model.ProvenanceRecord
import com.agenticmessenger.core.model.TypedAnnotation
import dev.aidos.tcr.model.AssertionLevel as TcrAssertionLevel
import dev.aidos.tcr.model.AttachmentPayload as TcrAttachmentPayload
import dev.aidos.tcr.model.AttachmentType as TcrAttachmentType
import dev.aidos.tcr.model.EndpointIdentifier as TcrEndpointIdentifier
import dev.aidos.tcr.model.EndpointType as TcrEndpointType
import dev.aidos.tcr.model.PrivacyLevel as TcrPrivacyLevel
import dev.aidos.tcr.model.ProvenanceRecord as TcrProvenanceRecord
import dev.aidos.tcr.model.TimelineItemUiModel as TcrTimelineItem
import dev.aidos.tcr.model.TypedAnnotation as TcrTypedAnnotation

// Amp's envelope types (amp-core) and the TCR's presentation copies are structurally identical.
// The TCR cannot depend on amp-core, so Amp maps at the UI boundary (D46 amended).

internal fun EndpointIdentifier.toTcr() =
    TcrEndpointIdentifier(principalId, TcrEndpointType.valueOf(endpointType.name), deviceKeyId)

internal fun ProvenanceRecord.toTcr() =
    TcrProvenanceRecord(TcrAssertionLevel.valueOf(assertionLevel.name), signerKeyId, signatureHex, parentEnvelopeId)

internal fun PrivacyLevel.toTcr() = TcrPrivacyLevel.valueOf(name)

internal fun TypedAnnotation.toTcr() = TcrTypedAnnotation(type, confidence, attributes)

internal fun AttachmentPayload.toTcr() = TcrAttachmentPayload(
    id = id, fileName = fileName, mimeType = mimeType, sizeBytes = sizeBytes, sha256Hex = sha256Hex,
    type = TcrAttachmentType.valueOf(type.name), durationMs = durationMs, inlineBase64 = inlineBase64,
    blobUri = blobUri, blobDecryptionKeyHex = blobDecryptionKeyHex,
)

internal fun TimelineItemUiModel.Message.toTcr() = TcrTimelineItem.Message(
    id = id, timestamp = timestamp, sender = sender.toTcr(), provenanceType = provenanceType,
    provenanceRecord = provenanceRecord.toTcr(), privacyLevel = privacyLevel.toTcr(), naturalText = naturalText,
    cueWords = cueWords, annotations = annotations.map { it.toTcr() }, localInferenceNote = localInferenceNote,
    audioPlayback = audioPlayback, isDelivered = isDelivered, isRead = isRead, isViewed = isViewed,
    attachments = attachments.map { it.toTcr() }, isShieldedVoice = isShieldedVoice,
    shieldedSttTranscript = shieldedSttTranscript, isSttVisible = isSttVisible,
    isUnshieldedForAi = isUnshieldedForAi, disclosureReceiptReceived = disclosureReceiptReceived,
)
