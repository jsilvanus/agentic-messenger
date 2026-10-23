package com.agenticmessenger.relay

import com.agenticmessenger.relay.queue.StoreAndForwardQueue
import com.agenticmessenger.relay.registry.KeyPackageRegistry
import com.agenticmessenger.relay.router.MailboxRouter
import com.agenticmessenger.relay.service.RelayService
import com.agenticmessenger.relay.storage.BlobStorage
import com.agenticmessenger.relay.storage.EncryptedBlobStorage
import com.agenticmessenger.relay.storage.S3BlobStorage
import com.agenticmessenger.relay.storage.S3StorageConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory
import java.io.File

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("BlindRelayLauncher")

    val port = System.getenv("RELAY_PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("RELAY_HOST") ?: "0.0.0.0"
    val dbPath = System.getenv("RELAY_DB_PATH") ?: "blind_relay.db"
    val storageBackend = System.getenv("STORAGE_BACKEND")?.lowercase() ?: "filesystem"

    val blobStorage: BlobStorage = if (storageBackend == "s3") {
        val endpoint = System.getenv("S3_ENDPOINT") ?: "http://localhost:7070"
        val bucket = System.getenv("S3_BUCKET") ?: "amp-blobs"
        val region = System.getenv("S3_REGION") ?: "us-east-1"
        val accessKey = System.getenv("S3_ACCESS_KEY") ?: "myaccesskey"
        val secretKey = System.getenv("S3_SECRET_KEY") ?: "mysecretkey"
        val forcePathStyle = System.getenv("S3_FORCE_PATH_STYLE")?.toBooleanStrictOrNull() ?: true

        logger.info("Using S3-compatible blob storage backend at '{}' (bucket: '{}')", endpoint, bucket)
        val s3Config = S3StorageConfig(
            endpoint = endpoint,
            bucket = bucket,
            region = region,
            accessKey = accessKey,
            secretKey = secretKey,
            forcePathStyle = forcePathStyle
        )
        S3BlobStorage(s3Config)
    } else {
        val blobDirPath = System.getenv("RELAY_BLOB_DIR") ?: "data/blobs"
        val blobDir = File(blobDirPath)
        if (!blobDir.exists()) {
            blobDir.mkdirs()
        }
        logger.info("Using filesystem blob storage backend at '{}'", blobDirPath)
        EncryptedBlobStorage(blobDir)
    }

    logger.info("Initializing Agentic Messenger Blind Relay on {}:{}", host, port)
    logger.info("Using SQLite database at '{}'", dbPath)

    val router = MailboxRouter()
    val queue = StoreAndForwardQueue(dbPath)
    val keyRegistry = KeyPackageRegistry(dbPath)
    val relayService = RelayService(router, queue)

    embeddedServer(Netty, port = port, host = host) {
        configureRelayServer(
            router = router,
            queue = queue,
            blobStorage = blobStorage,
            keyRegistry = keyRegistry,
            relayService = relayService
        )
    }.start(wait = true)
}
