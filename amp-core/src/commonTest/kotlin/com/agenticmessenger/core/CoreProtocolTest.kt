package com.agenticmessenger.core

import com.agenticmessenger.core.crypto.BlindedMailbox
import com.agenticmessenger.core.model.*
import com.agenticmessenger.core.protocol.RelayFrame
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoreProtocolTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    @Test
    fun testBlindedMailboxDeterministicHmac() {
        val epochKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val deviceId = "device_phone_jack_001"

        val mailbox1 = BlindedMailbox.compute(epochKey, deviceId)
        val mailbox2 = BlindedMailbox.compute(epochKey, deviceId)

        assertEquals(mailbox1, mailbox2)
        assertEquals(64, mailbox1.length) // SHA256 hex is 64 chars

        val diffDevice = BlindedMailbox.compute(epochKey, "device_phone_john_002")
        assertTrue(mailbox1 != diffDevice)
    }

    @Test
    fun testUniversalEnvelopeSerialization() {
        val envelope = UniversalEnvelope(
            envelopeId = "env_123",
            conversationId = "conv_jack_john",
            epoch = 1L,
            timestamp = 1729600000000L,
            routing = RoutingHeader(
                sender = EndpointIdentifier(
                    principalId = "did:aidos:jack",
                    endpointType = EndpointType.HUMAN_DIRECT,
                    deviceKeyId = "dev_jack_phone"
                ),
                recipients = listOf(
                    RecipientTarget(
                        mailboxId = "mb_recipient_john_123",
                        endpoint = EndpointIdentifier(
                            principalId = "did:aidos:john",
                            endpointType = EndpointType.HUMAN_DIRECT,
                            deviceKeyId = "dev_john_phone"
                        )
                    )
                )
            ),
            provenance = ProvenanceRecord(
                assertionLevel = AssertionLevel.ORIGINAL_HUMAN,
                signerKeyId = "dev_jack_phone",
                signatureHex = "deadbeef"
            ),
            policy = InformationBoundaryPolicy(
                privacyLevel = PrivacyLevel.AGENT_ACCESSIBLE,
                progressiveDisclosure = ProgressiveDisclosureRules(
                    allowDirectInference = true,
                    allowedTopics = listOf("DELAY_REASON", "VEHICLE_ISSUE"),
                    escrowedTopics = listOf("ESTIMATED_ARRIVAL_TIME")
                )
            ),
            payload = EnvelopePayload(
                naturalLanguage = "My tire blew out on highway 101. Running 20 min late.",
                structuredAnnotations = listOf(
                    TypedAnnotation(
                        type = "cause",
                        confidence = 0.98,
                        attributes = mapOf("issue" to "flat_tire")
                    )
                )
            )
        )

        val serialized = json.encodeToString(envelope)
        val deserialized = json.decodeFromString<UniversalEnvelope>(serialized)

        assertEquals(envelope.envelopeId, deserialized.envelopeId)
        assertEquals(envelope.routing.sender.principalId, deserialized.routing.sender.principalId)
        assertEquals(envelope.policy.privacyLevel, deserialized.policy.privacyLevel)
        assertEquals(1, deserialized.payload.structuredAnnotations.size)
        assertEquals("flat_tire", deserialized.payload.structuredAnnotations[0].attributes["issue"])
    }

    @Test
    fun testUniversalEnvelopeWithAttachments() {
        val voiceAttachment = AttachmentPayload(
            id = "att_voice_001",
            fileName = "voice_note.ogg",
            mimeType = "audio/ogg",
            sizeBytes = 14200L,
            sha256Hex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            type = AttachmentType.VOICE_NOTE,
            durationMs = 4200L,
            inlineBase64 = "T2dnUwACAAAAAAAAAAAAAAA..."
        )

        val fileAttachment = AttachmentPayload(
            id = "att_pdf_002",
            fileName = "contract.pdf",
            mimeType = "application/pdf",
            sizeBytes = 1048576L,
            sha256Hex = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            type = AttachmentType.DOCUMENT,
            blobUri = "amp://blobs/att_pdf_002",
            blobDecryptionKeyHex = "0123456789abcdef0123456789abcdef"
        )

        val envelope = UniversalEnvelope(
            envelopeId = "env_voice_sealed",
            conversationId = "conv_alice_bob",
            epoch = 2L,
            timestamp = 1729600500000L,
            routing = RoutingHeader(
                sender = EndpointIdentifier(
                    principalId = "did:aidos:alice",
                    endpointType = EndpointType.HUMAN_DIRECT,
                    deviceKeyId = "key_alice_01"
                ),
                recipients = listOf(
                    RecipientTarget(
                        mailboxId = "mb_bob_secret"
                    )
                )
            ),
            provenance = ProvenanceRecord(
                assertionLevel = AssertionLevel.ORIGINAL_HUMAN,
                signerKeyId = "key_alice_01",
                signatureHex = "sig_valid"
            ),
            policy = InformationBoundaryPolicy(
                privacyLevel = PrivacyLevel.HUMAN_ONLY
            ),
            payload = EnvelopePayload(
                naturalLanguage = "Here is the confidential voice memo and contract.",
                attachments = listOf(voiceAttachment, fileAttachment)
            )
        )

        val serialized = json.encodeToString(envelope)
        val deserialized = json.decodeFromString<UniversalEnvelope>(serialized)

        assertEquals(2, deserialized.payload.attachments.size)
        val firstAtt = deserialized.payload.attachments[0]
        assertEquals(AttachmentType.VOICE_NOTE, firstAtt.type)
        assertEquals(4200L, firstAtt.durationMs)
        assertEquals("voice_note.ogg", firstAtt.fileName)

        val secondAtt = deserialized.payload.attachments[1]
        assertEquals(AttachmentType.DOCUMENT, secondAtt.type)
        assertEquals("contract.pdf", secondAtt.fileName)
        assertEquals("amp://blobs/att_pdf_002", secondAtt.blobUri)
    }

    @Test
    fun testRelayFramePolymorphism() {
        val register: RelayFrame = RelayFrame.Register(mailboxId = "mb_test_999")
        val registerJson = json.encodeToString(register)
        val decodedRegister = json.decodeFromString<RelayFrame>(registerJson)
        assertTrue(decodedRegister is RelayFrame.Register)
        assertEquals("mb_test_999", decodedRegister.mailboxId)

        val send: RelayFrame = RelayFrame.Send(
            messageId = "msg_001",
            destinationMailboxes = listOf("mb_a", "mb_b"),
            payloadCiphertext = "encrypted_base64_data",
            ttlSeconds = 86400L
        )
        val sendJson = json.encodeToString(send)
        val decodedSend = json.decodeFromString<RelayFrame>(sendJson)
        assertTrue(decodedSend is RelayFrame.Send)
        assertEquals("msg_001", decodedSend.messageId)
        assertEquals(2, decodedSend.destinationMailboxes.size)

        val disclosure: RelayFrame = RelayFrame.DisclosureReceipt(
            messageId = "msg_sealed_01",
            sourceMailbox = "mb_recipient",
            destinationMailbox = "mb_sender",
            notice = "Unshielded by recipient for local AI assistant"
        )
        val disclosureJson = json.encodeToString(disclosure)
        val decodedDisclosure = json.decodeFromString<RelayFrame>(disclosureJson)
        assertTrue(decodedDisclosure is RelayFrame.DisclosureReceipt)
        assertEquals("msg_sealed_01", decodedDisclosure.messageId)
        assertEquals("mb_recipient", decodedDisclosure.sourceMailbox)
        assertEquals("mb_sender", decodedDisclosure.destinationMailbox)

        val delivery: RelayFrame = RelayFrame.DeliveryReceipt(
            messageId = "msg_002",
            sourceMailbox = "mb_recipient",
            destinationMailbox = "mb_sender"
        )
        val deliveryJson = json.encodeToString(delivery)
        val decodedDelivery = json.decodeFromString<RelayFrame>(deliveryJson)
        assertTrue(decodedDelivery is RelayFrame.DeliveryReceipt)
        assertEquals("msg_002", decodedDelivery.messageId)

        val viewed: RelayFrame = RelayFrame.ViewedReceipt(
            messageId = "msg_002",
            sourceMailbox = "mb_recipient",
            destinationMailbox = "mb_sender"
        )
        val viewedJson = json.encodeToString(viewed)
        val decodedViewed = json.decodeFromString<RelayFrame>(viewedJson)
        assertTrue(decodedViewed is RelayFrame.ViewedReceipt)
        assertEquals("msg_002", decodedViewed.messageId)
    }
}
