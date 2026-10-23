package com.agenticmessenger.crypto

import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.core.model.PrivacyLevel
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object DualCompartmentEncryptor {

    private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val AES_KEY_SIZE_BYTES = 32 // 256 bits
    private const val GCM_IV_SIZE_BYTES = 12  // 96 bits
    private val KEK_INFO = "amp-dual-compartment-kek".toByteArray(Charsets.UTF_8)

    /**
     * Performs dual-compartment envelope encryption.
     *
     * 1. Generates an ephemeral AES-256 message key (K_m) and random 96-bit IV.
     * 2. Encrypts the payload with AES-256-GCM.
     * 3. Wraps K_m for each recipient according to the privacy policy:
     *    - When AGENT_ACCESSIBLE: K_m is wrapped for human device and agent keys.
     *    - When HUMAN_ONLY: K_m is strictly wrapped for human device keys. Agent compartments
     *      receive a cryptographically sealed tombstone (HUMAN_ONLY_SEALED) with no key material.
     */
    fun encrypt(
        payload: ByteArray,
        policy: PrivacyLevel,
        recipients: List<RecipientKey>,
        aad: ByteArray? = null,
        random: SecureRandom = SecureRandom()
    ): DualCompartmentEnvelope {
        // 1. Generate ephemeral symmetric message key K_m and message IV
        val messageKey = ByteArray(AES_KEY_SIZE_BYTES).also { random.nextBytes(it) }
        val messageIv = ByteArray(GCM_IV_SIZE_BYTES).also { random.nextBytes(it) }

        // 2. Encrypt payload with AES-256-GCM
        val ciphertext = encryptAesGcm(messageKey, messageIv, payload, aad)

        // 3. Generate ephemeral X25519 keypair for key encapsulation
        val ephemeralKeyPair = X25519Service.generateKeyPair(random)

        // 4. Wrap K_m for each recipient compartment
        val compartments = recipients.map { recipient ->
            val canAccess = when (recipient.endpointType) {
                EndpointType.HUMAN_DIRECT -> true
                EndpointType.AGENT_DELEGATE, EndpointType.SYSTEM_SERVICE -> policy == PrivacyLevel.AGENT_ACCESSIBLE
            }

            if (canAccess) {
                val wrappedKey = wrapKey(
                    messageKey = messageKey,
                    ephemeralPrivateKey = ephemeralKeyPair.privateKey,
                    ephemeralPublicKey = ephemeralKeyPair.publicKey,
                    recipientPublicKey = recipient.x25519PublicKey,
                    random = random
                )
                RecipientCompartment(
                    recipientId = recipient.recipientId,
                    endpointType = recipient.endpointType,
                    wrappedKey = wrappedKey,
                    tombstone = null
                )
            } else {
                // Cryptographically sealed tombstone: no wrapped key is emitted
                RecipientCompartment(
                    recipientId = recipient.recipientId,
                    endpointType = recipient.endpointType,
                    wrappedKey = null,
                    tombstone = SealedTombstone()
                )
            }
        }

        return DualCompartmentEnvelope(
            version = 1,
            policy = policy,
            ephemeralPublicKey = ephemeralKeyPair.publicKeyHex(),
            iv = messageIv.toHexString(),
            ciphertext = ciphertext.toHexString(),
            compartments = compartments
        )
    }

    /**
     * Convenience overload for UTF-8 string plaintext.
     */
    fun encrypt(
        plaintext: String,
        policy: PrivacyLevel,
        recipients: List<RecipientKey>,
        aad: ByteArray? = null,
        random: SecureRandom = SecureRandom()
    ): DualCompartmentEnvelope {
        return encrypt(plaintext.toByteArray(Charsets.UTF_8), policy, recipients, aad, random)
    }

    /**
     * Convenience overload with separate human and agent recipient lists.
     */
    fun encrypt(
        payload: ByteArray,
        policy: PrivacyLevel,
        humanRecipients: List<Pair<String, ByteArray>>,
        agentRecipients: List<Pair<String, ByteArray>> = emptyList(),
        aad: ByteArray? = null,
        random: SecureRandom = SecureRandom()
    ): DualCompartmentEnvelope {
        val recipients = humanRecipients.map {
            RecipientKey(it.first, EndpointType.HUMAN_DIRECT, it.second)
        } + agentRecipients.map {
            RecipientKey(it.first, EndpointType.AGENT_DELEGATE, it.second)
        }
        return encrypt(payload, policy, recipients, aad, random)
    }

    /**
     * Decrypts a dual-compartment envelope for a given recipient using their private key.
     *
     * @throws NoSuchElementException if the recipient is not found in the envelope.
     * @throws HumanOnlySealedException if the envelope is HUMAN_ONLY and the recipient is an agent leaf.
     * @throws javax.crypto.AEADBadTagException if the private key or ciphertext has been tampered with.
     */
    fun decrypt(
        envelope: DualCompartmentEnvelope,
        recipientId: String,
        recipientPrivateKey: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        val compartment = envelope.getCompartment(recipientId)
            ?: throw NoSuchElementException("Recipient '$recipientId' not found in envelope compartments")

        if (compartment.isSealed || compartment.wrappedKey == null) {
            val type = compartment.tombstone?.type ?: "HUMAN_ONLY_SEALED"
            val notice = compartment.tombstone?.notice ?: "Payload sealed cryptographically for human recipient."
            throw HumanOnlySealedException(
                tombstone = compartment.tombstone,
                message = "$type: $notice"
            )
        }

        val ephemeralPublicKey = envelope.ephemeralPublicKey.hexToByteArray()
        val messageKey = unwrapKey(
            wrappedKeySlot = compartment.wrappedKey,
            ephemeralPublicKey = ephemeralPublicKey,
            recipientPrivateKey = recipientPrivateKey
        )

        val messageIv = envelope.iv.hexToByteArray()
        val ciphertext = envelope.ciphertext.hexToByteArray()
        return decryptAesGcm(messageKey, messageIv, ciphertext, aad)
    }

    /**
     * Decrypts and decodes UTF-8 string plaintext.
     */
    fun decryptToString(
        envelope: DualCompartmentEnvelope,
        recipientId: String,
        recipientPrivateKey: ByteArray,
        aad: ByteArray? = null
    ): String {
        return String(decrypt(envelope, recipientId, recipientPrivateKey, aad), Charsets.UTF_8)
    }

    /**
     * AES-256-GCM symmetric encryption with 128-bit authentication tag.
     */
    fun encryptAesGcm(
        key: ByteArray,
        iv: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        require(key.size == AES_KEY_SIZE_BYTES) { "AES-256 key must be 32 bytes (got ${key.size})" }
        require(iv.size == GCM_IV_SIZE_BYTES) { "AES-GCM IV must be 12 bytes (96 bits) (got ${iv.size})" }

        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        if (aad != null && aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(plaintext)
    }

    /**
     * AES-256-GCM symmetric decryption with 128-bit authentication tag verification.
     */
    fun decryptAesGcm(
        key: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        require(key.size == AES_KEY_SIZE_BYTES) { "AES-256 key must be 32 bytes (got ${key.size})" }
        require(iv.size == GCM_IV_SIZE_BYTES) { "AES-GCM IV must be 12 bytes (96 bits) (got ${iv.size})" }

        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        if (aad != null && aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(ciphertext)
    }

    /**
     * HKDF-SHA256 key derivation function (RFC 5869).
     */
    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray?,
        info: ByteArray?,
        length: Int = AES_KEY_SIZE_BYTES
    ): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(ikm, salt, info))
        val okm = ByteArray(length)
        hkdf.generateBytes(okm, 0, length)
        return okm
    }

    private fun wrapKey(
        messageKey: ByteArray,
        ephemeralPrivateKey: ByteArray,
        ephemeralPublicKey: ByteArray,
        recipientPublicKey: ByteArray,
        random: SecureRandom
    ): WrappedKeySlot {
        val sharedSecret = X25519Service.computeSharedSecret(ephemeralPrivateKey, recipientPublicKey)
        val kek = hkdfSha256(
            ikm = sharedSecret,
            salt = ephemeralPublicKey,
            info = KEK_INFO,
            length = AES_KEY_SIZE_BYTES
        )
        val wrapIv = ByteArray(GCM_IV_SIZE_BYTES).also { random.nextBytes(it) }
        val encryptedKey = encryptAesGcm(kek, wrapIv, messageKey)
        return WrappedKeySlot(
            iv = wrapIv.toHexString(),
            encryptedKey = encryptedKey.toHexString()
        )
    }

    private fun unwrapKey(
        wrappedKeySlot: WrappedKeySlot,
        ephemeralPublicKey: ByteArray,
        recipientPrivateKey: ByteArray
    ): ByteArray {
        val sharedSecret = X25519Service.computeSharedSecret(recipientPrivateKey, ephemeralPublicKey)
        val kek = hkdfSha256(
            ikm = sharedSecret,
            salt = ephemeralPublicKey,
            info = KEK_INFO,
            length = AES_KEY_SIZE_BYTES
        )
        val wrapIv = wrappedKeySlot.iv.hexToByteArray()
        val encryptedKey = wrappedKeySlot.encryptedKey.hexToByteArray()
        return decryptAesGcm(kek, wrapIv, encryptedKey)
    }
}
