package com.agenticmessenger.relay.storage

import com.agenticmessenger.core.protocol.BlobMetadata
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class EncryptedBlobStorage(
    private val baseDirectory: File,
    private val maxBlobSizeBytes: Long = 50L * 1024 * 1024 // 50 MB default
) : BlobStorage {

    init {
        if (!baseDirectory.exists()) {
            baseDirectory.mkdirs()
        }
    }

    override fun store(
        bytes: ByteArray,
        preferredBlobId: String?,
        expectedSha256: String?,
        baseUrl: String
    ): BlobMetadata {
        if (bytes.size > maxBlobSizeBytes) {
            throw IllegalArgumentException("Blob size ${bytes.size} exceeds maximum allowed size of $maxBlobSizeBytes bytes.")
        }

        val calculatedSha256 = sha256Hex(bytes)
        if (expectedSha256 != null && !calculatedSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
            throw IllegalArgumentException("SHA-256 checksum mismatch: expected $expectedSha256 but computed $calculatedSha256")
        }

        val blobId = preferredBlobId ?: "b_${UUID.randomUUID().toString().replace("-", "")}"
        val sanitizedId = sanitizeBlobId(blobId)
        val file = File(baseDirectory, sanitizedId)

        FileOutputStream(file).use { out ->
            out.write(bytes)
            out.flush()
        }

        return BlobMetadata(
            blobId = sanitizedId,
            sha256 = calculatedSha256,
            sizeBytes = bytes.size.toLong(),
            downloadUrl = "$baseUrl/$sanitizedId",
            uploadedAt = System.currentTimeMillis()
        )
    }

    /**
     * Retrieves stored ciphertext bytes and its SHA-256 hash.
     * Returns null if blob is not found.
     */
    override fun retrieve(blobId: String): Pair<ByteArray, String>? {
        val sanitizedId = sanitizeBlobId(blobId)
        val file = File(baseDirectory, sanitizedId)
        if (!file.exists() || !file.isFile) {
            return null
        }

        val bytes = FileInputStream(file).use { it.readBytes() }
        val hash = sha256Hex(bytes)
        return Pair(bytes, hash)
    }

    override fun delete(blobId: String): Boolean {
        val sanitizedId = sanitizeBlobId(blobId)
        val file = File(baseDirectory, sanitizedId)
        return if (file.exists()) file.delete() else false
    }

    override fun exists(blobId: String): Boolean {
        val sanitizedId = sanitizeBlobId(blobId)
        return File(baseDirectory, sanitizedId).exists()
    }

    override fun countBlobs(): Int {
        return baseDirectory.listFiles()?.filter { it.isFile }?.size ?: 0
    }

    private fun sanitizeBlobId(blobId: String): String {
        // Prevent path traversal
        val cleaned = blobId.replace(Regex("[^a-zA-Z0-9_-]"), "")
        require(cleaned.isNotEmpty()) { "Invalid blob ID: $blobId" }
        return cleaned
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
