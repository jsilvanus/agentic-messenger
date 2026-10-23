package com.agenticmessenger.crypto

import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.core.model.PrivacyLevel
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Thrown when an agent or unauthorized entity attempts to decrypt a payload sealed
 * cryptographically under HUMAN_ONLY privacy policy.
 */
class HumanOnlySealedException(
    val tombstone: SealedTombstone? = null,
    message: String = tombstone?.let { "${it.type}: ${it.notice}" }
        ?: "HUMAN_ONLY_SEALED: Payload sealed cryptographically for human recipient."
) : SecurityException(message)

/**
 * Cryptographic tombstone placed in agent recipient compartments when a message is HUMAN_ONLY.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SealedTombstone(
    @EncodeDefault
    val type: String = "HUMAN_ONLY_SEALED",
    @EncodeDefault
    val notice: String = "Payload sealed cryptographically for human recipient."
)

/**
 * Recipient-specific wrapped symmetric key slot.
 * Encrypted using AES-256-GCM with a key derived from X25519 shared secret via HKDF-SHA256.
 */
@Serializable
data class WrappedKeySlot(
    val iv: String,              // Hex-encoded 12-byte IV
    val encryptedKey: String     // Hex-encoded wrapped symmetric key + 16-byte authentication tag
)

/**
 * Compartment for an individual recipient endpoint (human device or agent).
 */
@Serializable
data class RecipientCompartment(
    val recipientId: String,
    val endpointType: EndpointType,
    val wrappedKey: WrappedKeySlot? = null,
    val tombstone: SealedTombstone? = null
) {
    val isSealed: Boolean
        get() = tombstone != null || wrappedKey == null
}

/**
 * Dual-compartment encrypted envelope containing ciphertext and recipient key slots.
 */
@Serializable
data class DualCompartmentEnvelope(
    val version: Int = 1,
    val policy: PrivacyLevel,
    val ephemeralPublicKey: String,      // Hex-encoded 32-byte ephemeral X25519 public key
    val iv: String,                      // Hex-encoded 12-byte (96-bit) IV for AES-256-GCM
    val ciphertext: String,              // Hex-encoded AES-256-GCM ciphertext + 16-byte tag
    val compartments: List<RecipientCompartment>
) {
    fun getCompartment(recipientId: String): RecipientCompartment? =
        compartments.find { it.recipientId == recipientId }

    fun isSealedFor(recipientId: String): Boolean {
        val compartment = getCompartment(recipientId) ?: return false
        return compartment.isSealed
    }
}
