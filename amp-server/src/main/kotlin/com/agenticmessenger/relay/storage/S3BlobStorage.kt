package com.agenticmessenger.relay.storage

import com.agenticmessenger.core.protocol.BlobMetadata
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.*
import java.io.Closeable
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

data class S3StorageConfig(
    val endpoint: String = "http://localhost:7070",
    val bucket: String = "amp-blobs",
    val region: String = "us-east-1",
    val accessKey: String = "myaccesskey",
    val secretKey: String = "mysecretkey",
    val forcePathStyle: Boolean = true,
    val maxBlobSizeBytes: Long = 50L * 1024 * 1024 // 50 MB
)

class S3BlobStorage(
    private val config: S3StorageConfig,
    private val s3Client: S3Client = createS3Client(config)
) : BlobStorage, Closeable {

    private val logger = LoggerFactory.getLogger(S3BlobStorage::class.java)

    init {
        ensureBucketExists()
    }

    private fun ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(config.bucket).build())
            logger.info("S3 Bucket '{}' verified on endpoint '{}'", config.bucket, config.endpoint)
        } catch (e: NoSuchBucketException) {
            logger.info("Bucket '{}' does not exist. Creating it now...", config.bucket)
            createBucket()
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) {
                logger.info("Bucket '{}' not found (404). Creating it now...", config.bucket)
                createBucket()
            } else {
                logger.warn("Could not verify bucket '{}': {}", config.bucket, e.message)
            }
        } catch (e: Exception) {
            logger.warn("Failed to check bucket '{}' on startup: {}", config.bucket, e.message)
        }
    }

    private fun createBucket() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(config.bucket).build())
            logger.info("S3 Bucket '{}' successfully created.", config.bucket)
        } catch (e: Exception) {
            logger.error("Failed to create S3 bucket '{}': {}", config.bucket, e.message)
        }
    }

    override fun store(
        bytes: ByteArray,
        preferredBlobId: String?,
        expectedSha256: String?,
        baseUrl: String
    ): BlobMetadata {
        if (bytes.size > config.maxBlobSizeBytes) {
            throw IllegalArgumentException("Blob size ${bytes.size} exceeds maximum allowed size of ${config.maxBlobSizeBytes} bytes.")
        }

        val calculatedSha256 = sha256Hex(bytes)
        if (expectedSha256 != null && !calculatedSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
            throw IllegalArgumentException("SHA-256 checksum mismatch: expected $expectedSha256 but computed $calculatedSha256")
        }

        val blobId = preferredBlobId ?: "b_${UUID.randomUUID().toString().replace("-", "")}"
        val sanitizedId = sanitizeBlobId(blobId)

        val putRequest = PutObjectRequest.builder()
            .bucket(config.bucket)
            .key(sanitizedId)
            .contentType("application/octet-stream")
            .metadata(mapOf("sha256" to calculatedSha256))
            .build()

        s3Client.putObject(putRequest, RequestBody.fromBytes(bytes))

        return BlobMetadata(
            blobId = sanitizedId,
            sha256 = calculatedSha256,
            sizeBytes = bytes.size.toLong(),
            downloadUrl = "$baseUrl/$sanitizedId",
            uploadedAt = System.currentTimeMillis()
        )
    }

    override fun retrieve(blobId: String): Pair<ByteArray, String>? {
        val sanitizedId = sanitizeBlobId(blobId)
        return try {
            val getRequest = GetObjectRequest.builder()
                .bucket(config.bucket)
                .key(sanitizedId)
                .build()

            val responseBytes = s3Client.getObject(getRequest)
            val bytes = responseBytes.readAllBytes()
            val metaSha = responseBytes.response().metadata()["sha256"]
            val finalSha = metaSha ?: sha256Hex(bytes)
            Pair(bytes, finalSha)
        } catch (_: NoSuchKeyException) {
            null
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) null else throw e
        }
    }

    override fun delete(blobId: String): Boolean {
        val sanitizedId = sanitizeBlobId(blobId)
        return try {
            if (!exists(sanitizedId)) return false
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(config.bucket)
                    .key(sanitizedId)
                    .build()
            )
            true
        } catch (e: Exception) {
            logger.warn("Failed to delete blob '{}' from S3: {}", sanitizedId, e.message)
            false
        }
    }

    override fun exists(blobId: String): Boolean {
        val sanitizedId = sanitizeBlobId(blobId)
        return try {
            s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(config.bucket)
                    .key(sanitizedId)
                    .build()
            )
            true
        } catch (_: NoSuchKeyException) {
            false
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) false else throw e
        }
    }

    override fun countBlobs(): Int {
        return try {
            val list = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket(config.bucket)
                    .build()
            )
            list.keyCount() ?: 0
        } catch (e: Exception) {
            logger.warn("Failed counting S3 blobs: {}", e.message)
            0
        }
    }

    override fun close() {
        try {
            s3Client.close()
        } catch (_: Exception) {}
    }

    private fun sanitizeBlobId(blobId: String): String {
        val cleaned = blobId.replace(Regex("[^a-zA-Z0-9_-]"), "")
        require(cleaned.isNotEmpty()) { "Invalid blob ID: $blobId" }
        return cleaned
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun createS3Client(config: S3StorageConfig): S3Client {
            val credentials = AwsBasicCredentials.create(config.accessKey, config.secretKey)
            val s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(config.forcePathStyle)
                .build()

            return S3Client.builder()
                .endpointOverride(URI.create(config.endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(config.region))
                .serviceConfiguration(s3Config)
                .build()
        }
    }
}
