package com.agenticmessenger.crypto

import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.core.model.PrivacyLevel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import kotlin.test.*

class DualCompartmentEncryptorTest {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Test
    fun `AGENT_ACCESSIBLE message can be decrypted by human phone, laptop, and agent delegate`() {
        // Setup endpoints: John Phone, John Laptop, John Agent
        val phoneKeys = X25519Service.generateKeyPair()
        val laptopKeys = X25519Service.generateKeyPair()
        val agentKeys = X25519Service.generateKeyPair()

        val recipients = listOf(
            RecipientKey("john-phone", EndpointType.HUMAN_DIRECT, phoneKeys.publicKey),
            RecipientKey("john-laptop", EndpointType.HUMAN_DIRECT, laptopKeys.publicKey),
            RecipientKey("john-agent", EndpointType.AGENT_DELEGATE, agentKeys.publicKey)
        )

        val secretMessage = "Flight UA429 departs at 14:30. Hotel booked at Grand Central."

        val envelope = DualCompartmentEncryptor.encrypt(
            plaintext = secretMessage,
            policy = PrivacyLevel.AGENT_ACCESSIBLE,
            recipients = recipients
        )

        assertEquals(PrivacyLevel.AGENT_ACCESSIBLE, envelope.policy)
        assertEquals(3, envelope.compartments.size)

        // Verify none of the compartments are sealed
        assertFalse(envelope.isSealedFor("john-phone"))
        assertFalse(envelope.isSealedFor("john-laptop"))
        assertFalse(envelope.isSealedFor("john-agent"))

        // 1. Human Phone decrypts
        val decryptedPhone = DualCompartmentEncryptor.decryptToString(
            envelope = envelope,
            recipientId = "john-phone",
            recipientPrivateKey = phoneKeys.privateKey
        )
        assertEquals(secretMessage, decryptedPhone)

        // 2. Human Laptop decrypts
        val decryptedLaptop = DualCompartmentEncryptor.decryptToString(
            envelope = envelope,
            recipientId = "john-laptop",
            recipientPrivateKey = laptopKeys.privateKey
        )
        assertEquals(secretMessage, decryptedLaptop)

        // 3. Agent decrypts
        val decryptedAgent = DualCompartmentEncryptor.decryptToString(
            envelope = envelope,
            recipientId = "john-agent",
            recipientPrivateKey = agentKeys.privateKey
        )
        assertEquals(secretMessage, decryptedAgent)
    }

    @Test
    fun `HUMAN_ONLY message can be decrypted by human device, but agent FAILS with sealed tombstone`() {
        val phoneKeys = X25519Service.generateKeyPair()
        val agentKeys = X25519Service.generateKeyPair()

        val recipients = listOf(
            RecipientKey("john-phone", EndpointType.HUMAN_DIRECT, phoneKeys.publicKey),
            RecipientKey("john-agent", EndpointType.AGENT_DELEGATE, agentKeys.publicKey)
        )

        val confidentialHumanNote = "CONFIDENTIAL: Internal password reset code is 938210. Do not share with AI assistant."

        val envelope = DualCompartmentEncryptor.encrypt(
            plaintext = confidentialHumanNote,
            policy = PrivacyLevel.HUMAN_ONLY,
            recipients = recipients
        )

        assertEquals(PrivacyLevel.HUMAN_ONLY, envelope.policy)

        // Human compartment has wrapped key
        assertFalse(envelope.isSealedFor("john-phone"))
        assertNotNull(envelope.getCompartment("john-phone")?.wrappedKey)

        // Agent compartment is SEALED
        assertTrue(envelope.isSealedFor("john-agent"))
        assertNull(envelope.getCompartment("john-agent")?.wrappedKey)
        val tombstone = envelope.getCompartment("john-agent")?.tombstone
        assertNotNull(tombstone)
        assertEquals("HUMAN_ONLY_SEALED", tombstone.type)

        // 1. Human decrypts successfully
        val decryptedHuman = DualCompartmentEncryptor.decryptToString(
            envelope = envelope,
            recipientId = "john-phone",
            recipientPrivateKey = phoneKeys.privateKey
        )
        assertEquals(confidentialHumanNote, decryptedHuman)

        // 2. Agent attempt to decrypt MUST throw HumanOnlySealedException
        val sealedException = assertFailsWith<HumanOnlySealedException> {
            DualCompartmentEncryptor.decrypt(
                envelope = envelope,
                recipientId = "john-agent",
                recipientPrivateKey = agentKeys.privateKey
            )
        }
        assertTrue(sealedException.message?.contains("HUMAN_ONLY_SEALED") == true)

        // 3. Even if agent attempts cryptographic fraud by impersonating phone recipientId with agent private key,
        // it MUST fail with AEAD tag exception (cryptographic impossibility to recover Km)
        assertFailsWith<GeneralSecurityException> {
            DualCompartmentEncryptor.decrypt(
                envelope = envelope,
                recipientId = "john-phone",
                recipientPrivateKey = agentKeys.privateKey
            )
        }
    }

    @Test
    fun `tampered ciphertext throws GeneralSecurityException`() {
        val phoneKeys = X25519Service.generateKeyPair()
        val recipients = listOf(RecipientKey("phone", EndpointType.HUMAN_DIRECT, phoneKeys.publicKey))
        val message = "Authenticity check message"

        val envelope = DualCompartmentEncryptor.encrypt(
            plaintext = message,
            policy = PrivacyLevel.HUMAN_ONLY,
            recipients = recipients
        )

        // Corrupt ciphertext
        val cipherBytes = envelope.ciphertext.hexToByteArray()
        cipherBytes[0] = (cipherBytes[0].toInt() xor 0x01).toByte()
        val tamperedEnvelope = envelope.copy(ciphertext = cipherBytes.toHexString())

        assertFailsWith<GeneralSecurityException> {
            DualCompartmentEncryptor.decrypt(
                envelope = tamperedEnvelope,
                recipientId = "phone",
                recipientPrivateKey = phoneKeys.privateKey
            )
        }
    }

    @Test
    fun `tampered AAD throws GeneralSecurityException`() {
        val phoneKeys = X25519Service.generateKeyPair()
        val recipients = listOf(RecipientKey("phone", EndpointType.HUMAN_DIRECT, phoneKeys.publicKey))
        val message = "Payload with authenticated headers"
        val aad = "envelopeId=env_12345;epoch=42".toByteArray(Charsets.UTF_8)

        val envelope = DualCompartmentEncryptor.encrypt(
            payload = message.toByteArray(Charsets.UTF_8),
            policy = PrivacyLevel.AGENT_ACCESSIBLE,
            recipients = recipients,
            aad = aad
        )

        // Decrypt with correct AAD succeeds
        val decrypted = DualCompartmentEncryptor.decryptToString(
            envelope = envelope,
            recipientId = "phone",
            recipientPrivateKey = phoneKeys.privateKey,
            aad = aad
        )
        assertEquals(message, decrypted)

        // Decrypt with tampered AAD fails
        val tamperedAad = "envelopeId=env_12345;epoch=43".toByteArray(Charsets.UTF_8)
        assertFailsWith<GeneralSecurityException> {
            DualCompartmentEncryptor.decrypt(
                envelope = envelope,
                recipientId = "phone",
                recipientPrivateKey = phoneKeys.privateKey,
                aad = tamperedAad
            )
        }
    }

    @Test
    fun `unknown recipient throws NoSuchElementException`() {
        val phoneKeys = X25519Service.generateKeyPair()
        val recipients = listOf(RecipientKey("phone-01", EndpointType.HUMAN_DIRECT, phoneKeys.publicKey))
        val envelope = DualCompartmentEncryptor.encrypt(
            plaintext = "Test",
            policy = PrivacyLevel.HUMAN_ONLY,
            recipients = recipients
        )

        assertFailsWith<NoSuchElementException> {
            DualCompartmentEncryptor.decrypt(
                envelope = envelope,
                recipientId = "phone-99",
                recipientPrivateKey = phoneKeys.privateKey
            )
        }
    }

    @Test
    fun `JSON serialization round-trip retains dual compartment structure`() {
        val phoneKeys = X25519Service.generateKeyPair()
        val agentKeys = X25519Service.generateKeyPair()
        val recipients = listOf(
            RecipientKey("device:phone", EndpointType.HUMAN_DIRECT, phoneKeys.publicKey),
            RecipientKey("agent:aidos", EndpointType.AGENT_DELEGATE, agentKeys.publicKey)
        )

        val originalEnvelope = DualCompartmentEncryptor.encrypt(
            plaintext = "Wire format test",
            policy = PrivacyLevel.HUMAN_ONLY,
            recipients = recipients
        )

        val jsonString = json.encodeToString(originalEnvelope)
        assertTrue(jsonString.contains("HUMAN_ONLY_SEALED"))

        val deserialized = json.decodeFromString<DualCompartmentEnvelope>(jsonString)
        assertEquals(originalEnvelope, deserialized)

        // Deserialized envelope decrypts correctly for human
        val decrypted = DualCompartmentEncryptor.decryptToString(
            envelope = deserialized,
            recipientId = "device:phone",
            recipientPrivateKey = phoneKeys.privateKey
        )
        assertEquals("Wire format test", decrypted)

        // Deserialized envelope still throws HumanOnlySealedException for agent
        assertFailsWith<HumanOnlySealedException> {
            DualCompartmentEncryptor.decrypt(
                envelope = deserialized,
                recipientId = "agent:aidos",
                recipientPrivateKey = agentKeys.privateKey
            )
        }
    }
}
