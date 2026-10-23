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
    }
}
