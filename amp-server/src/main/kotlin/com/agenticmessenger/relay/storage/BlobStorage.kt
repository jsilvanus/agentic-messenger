package com.agenticmessenger.relay.storage

import com.agenticmessenger.core.protocol.BlobMetadata

interface BlobStorage {

    /**
     * Stores client-encrypted ciphertext bytes without inspecting or interpreting them.
     * Computes SHA-256 for zero-knowledge integrity verification.
     */
    fun store(
        bytes: ByteArray,
        preferredBlobId: String? = null,
        expectedSha256: String? = null,
        baseUrl: String = "/blobs"
    ): BlobMetadata

    /**
     * Retrieves stored ciphertext bytes and its SHA-256 hash.
     * Returns null if blob is not found.
     */
    fun retrieve(blobId: String): Pair<ByteArray, String>?

    /**
     * Deletes a blob permanently.
     */
    fun delete(blobId: String): Boolean

    /**
     * Checks if a blob exists.
     */
    fun exists(blobId: String): Boolean

    /**
     * Returns the total count of blobs currently held in storage.
     */
    fun countBlobs(): Int
}
