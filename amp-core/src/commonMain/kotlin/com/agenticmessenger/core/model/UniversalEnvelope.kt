package com.agenticmessenger.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class EndpointType {
    HUMAN_DIRECT,
    AGENT_DELEGATE,
    SYSTEM_SERVICE
}

@Serializable
data class EndpointIdentifier(
    val principalId: String,        // e.g. "did:aidos:jack"
    val endpointType: EndpointType,
    val deviceKeyId: String         // e.g. "key_jack_mobile_01"
)

@Serializable
data class RecipientTarget(
    val mailboxId: String,          // Blinded routing token: HMAC(epoch, device_id)
    val endpoint: EndpointIdentifier? = null
)

@Serializable
data class RoutingHeader(
    val sender: EndpointIdentifier,
    val recipients: List<RecipientTarget>
)

@Serializable
enum class PrivacyLevel {
    AGENT_ACCESSIBLE,
    HUMAN_ONLY
}

@Serializable
data class ProgressiveDisclosureRules(
    val allowDirectInference: Boolean = true,
    val allowedTopics: List<String> = emptyList(),
    val escrowedTopics: List<String> = emptyList()
)

@Serializable
data class InformationBoundaryPolicy(
    val privacyLevel: PrivacyLevel,
    val progressiveDisclosure: ProgressiveDisclosureRules? = null
)

@Serializable
enum class AssertionLevel {
    ORIGINAL_HUMAN,
    AGENT_SYNTHESIZED,
    SYSTEM_VERIFIED
}

@Serializable
data class ProvenanceRecord(
    val assertionLevel: AssertionLevel,
    val signerKeyId: String,
    val signatureHex: String,
    val parentEnvelopeId: String? = null
)

@Serializable
data class TypedAnnotation(
    val type: String,               // "status", "cause", "eta", "location", etc.
    val confidence: Double,
    val attributes: Map<String, String> = emptyMap()
)

@Serializable
enum class AttachmentType {
    VOICE_NOTE,
    DOCUMENT,
    IMAGE,
    GENERIC_FILE
}

@Serializable
data class AttachmentPayload(
    val id: String,                         // UUID
    val fileName: String,
    val mimeType: String,                   // e.g. "audio/ogg", "audio/wav", "application/pdf"
    val sizeBytes: Long,
    val sha256Hex: String,
    val type: AttachmentType = AttachmentType.GENERIC_FILE,
    val durationMs: Long? = null,           // for audio/voice notes
    val inlineBase64: String? = null,       // for inline payload (voice notes, small files < 1MB)
    val blobUri: String? = null,            // for out-of-band encrypted blobs (larger files)
    val blobDecryptionKeyHex: String? = null // symmetric key if hosted out-of-band
)

@Serializable
data class DisclosureReceiptRecord(
    val messageId: String,
    val discloserPrincipalId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val notice: String = "Unshielded by recipient for local AI assistant"
)

@Serializable
data class EnvelopePayload(
    val naturalLanguage: String,
    val structuredAnnotations: List<TypedAnnotation> = emptyList(),
    val attachments: List<AttachmentPayload> = emptyList()
)

@Serializable
data class UniversalEnvelope(
    val envelopeId: String,
    val conversationId: String,
    val epoch: Long,
    val timestamp: Long,            // Milliseconds since Unix epoch
    val routing: RoutingHeader,
    val provenance: ProvenanceRecord,
    val policy: InformationBoundaryPolicy,
    val payload: EnvelopePayload
)
