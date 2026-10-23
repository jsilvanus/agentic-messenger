package com.agenticmessenger.relay.queue

import java.io.Closeable
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

data class QueuedEnvelope(
    val messageId: String,
    val destinationMailbox: String,
    val sourceMailbox: String?,
    val payloadCiphertext: String,
    val createdAt: Long,
    val expiresAt: Long
)

class StoreAndForwardQueue(
    private val dbPath: String = "jdbc:sqlite:blind_relay.db"
) : Closeable {

    private val connection: Connection

    init {
        val jdbcUrl = if (dbPath.startsWith("jdbc:sqlite:")) dbPath else "jdbc:sqlite:$dbPath"
        connection = DriverManager.getConnection(jdbcUrl)
        initSchema()
    }

    @Synchronized
    private fun initSchema() {
        connection.createStatement().use { stmt ->
            // Enable WAL mode and foreign keys for high-performance concurrent SQLite access
            stmt.execute("PRAGMA journal_mode = WAL;")
            stmt.execute("PRAGMA synchronous = NORMAL;")

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS ephemeral_messages (
                    message_id TEXT NOT NULL,
                    destination_mailbox TEXT NOT NULL,
                    source_mailbox TEXT,
                    payload_ciphertext TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    PRIMARY KEY (message_id, destination_mailbox)
                );
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_dest_expires 
                ON ephemeral_messages (destination_mailbox, expires_at);
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_expires 
                ON ephemeral_messages (expires_at);
                """.trimIndent()
            )
        }
    }

    /**
     * Enqueues an encrypted ciphertext envelope for an offline recipient.
     * Default TTL is 14 days (14 * 24 * 3600 seconds) as specified in RFC / blueprints.
     */
    @Synchronized
    fun enqueue(
        messageId: String,
        destinationMailbox: String,
        sourceMailbox: String?,
        payloadCiphertext: String,
        ttlSeconds: Long = 14L * 24 * 3600
    ): Boolean {
        val now = System.currentTimeMillis()
        val expiresAt = now + (ttlSeconds * 1000)

        val sql = """
            INSERT OR REPLACE INTO ephemeral_messages 
            (message_id, destination_mailbox, source_mailbox, payload_ciphertext, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, messageId)
                pstmt.setString(2, destinationMailbox)
                pstmt.setString(3, sourceMailbox)
                pstmt.setString(4, payloadCiphertext)
                pstmt.setLong(5, now)
                pstmt.setLong(6, expiresAt)
                pstmt.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Fetches pending unexpired envelopes for a mailbox token, ordered by creation time.
     */
    @Synchronized
    fun fetchPending(destinationMailbox: String, limit: Int = 100): List<QueuedEnvelope> {
        val now = System.currentTimeMillis()
        val sql = """
            SELECT message_id, destination_mailbox, source_mailbox, payload_ciphertext, created_at, expires_at
            FROM ephemeral_messages
            WHERE destination_mailbox = ? AND expires_at > ?
            ORDER BY created_at ASC
            LIMIT ?
        """.trimIndent()

        val results = mutableListOf<QueuedEnvelope>()
        try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, destinationMailbox)
                pstmt.setLong(2, now)
                pstmt.setInt(3, limit)
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    results.add(
                        QueuedEnvelope(
                            messageId = rs.getString("message_id"),
                            destinationMailbox = rs.getString("destination_mailbox"),
                            sourceMailbox = rs.getString("source_mailbox"),
                            payloadCiphertext = rs.getString("payload_ciphertext"),
                            createdAt = rs.getLong("created_at"),
                            expiresAt = rs.getLong("expires_at")
                        )
                    )
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return results
    }

    /**
     * Acknowledges receipt of a message.
     * ZERO-KNOWLEDGE & EPHEMERAL GUARANTEE: Upon receiving delivery ACK,
     * the server permanently and immediately deletes the ciphertext from disk.
     */
    @Synchronized
    fun acknowledge(messageId: String, destinationMailbox: String): Boolean {
        val sql = """
            DELETE FROM ephemeral_messages 
            WHERE message_id = ? AND destination_mailbox = ?
        """.trimIndent()

        return try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, messageId)
                pstmt.setString(2, destinationMailbox)
                pstmt.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Counts the total number of unexpired pending messages for a mailbox token.
     */
    @Synchronized
    fun countPending(destinationMailbox: String): Int {
        val now = System.currentTimeMillis()
        val sql = """
            SELECT COUNT(*) FROM ephemeral_messages
            WHERE destination_mailbox = ? AND expires_at > ?
        """.trimIndent()

        return try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, destinationMailbox)
                pstmt.setLong(2, now)
                val rs = pstmt.executeQuery()
                if (rs.next()) rs.getInt(1) else 0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            0
        }
    }

    /**
     * Background periodic sweep: permanently purges any envelopes past their TTL expiration.
     */
    @Synchronized
    fun purgeExpired(): Int {
        val now = System.currentTimeMillis()
        val sql = "DELETE FROM ephemeral_messages WHERE expires_at <= ?"
        return try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setLong(1, now)
                pstmt.executeUpdate()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            0
        }
    }

    @Synchronized
    fun totalQueueSize(): Int {
        val sql = "SELECT COUNT(*) FROM ephemeral_messages"
        return try {
            connection.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                if (rs.next()) rs.getInt(1) else 0
            }
        } catch (e: SQLException) {
            0
        }
    }

    override fun close() {
        try {
            if (!connection.isClosed) {
                connection.close()
            }
        } catch (_: SQLException) {}
    }
}
