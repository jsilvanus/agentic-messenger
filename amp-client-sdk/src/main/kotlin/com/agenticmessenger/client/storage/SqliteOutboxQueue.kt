package com.agenticmessenger.client.storage

import com.agenticmessenger.client.model.MessageStatus
import com.agenticmessenger.client.model.OutboxItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager

class SqliteOutboxQueue(
    private val connection: Connection,
    private val ownsConnection: Boolean = false,
    private val removeOnComplete: Boolean = false
) : OutboxQueue {

    constructor(
        dbPath: String = "jdbc:sqlite:amp_client.db",
        removeOnComplete: Boolean = false
    ) : this(
        DriverManager.getConnection(if (dbPath.startsWith("jdbc:sqlite:")) dbPath else "jdbc:sqlite:$dbPath"),
        ownsConnection = true,
        removeOnComplete = removeOnComplete
    )

    init {
        initSchema(connection)
    }

    override suspend fun enqueue(item: OutboxItem): Unit = withContext(Dispatchers.IO) {
        synchronized(connection) {
            connection.prepareStatement(
                """
                INSERT OR REPLACE INTO outbox (
                    id, conversation_id, recipient_mailbox_token, recipient_public_key_hex,
                    payload_plaintext, human_only, attempts, last_attempt_millis, status,
                    created_at_millis, last_error
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, item.id)
                stmt.setString(2, item.conversationId)
                stmt.setString(3, item.recipientMailboxToken)
                stmt.setString(4, item.recipientX25519PublicKeyHex)
                stmt.setString(5, item.payloadPlaintext)
                stmt.setInt(6, if (item.humanOnly) 1 else 0)
                stmt.setInt(7, item.attempts)
                stmt.setLong(8, item.lastAttemptMillis)
                stmt.setString(9, item.status.name)
                stmt.setLong(10, item.createdAtMillis)
                stmt.setString(11, item.lastError)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun peekPending(limit: Int): List<OutboxItem> = withContext(Dispatchers.IO) {
        val effectiveLimit = if (limit <= 0) 20 else limit
        synchronized(connection) {
            connection.prepareStatement(
                """
                SELECT id, conversation_id, recipient_mailbox_token, recipient_public_key_hex,
                       payload_plaintext, human_only, attempts, last_attempt_millis, status,
                       created_at_millis, last_error
                FROM outbox
                WHERE status = ?
                ORDER BY created_at_millis ASC
                LIMIT ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, MessageStatus.PENDING_OUTBOX.name)
                stmt.setInt(2, effectiveLimit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<OutboxItem>()
                    while (rs.next()) {
                        result.add(mapRowToOutboxItem(rs))
                    }
                    result
                }
            }
        }
    }

    override suspend fun markInFlight(id: String): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        synchronized(connection) {
            connection.prepareStatement(
                """
                UPDATE outbox
                SET status = ?, attempts = attempts + 1, last_attempt_millis = ?
                WHERE id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, MessageStatus.SENDING.name)
                stmt.setLong(2, now)
                stmt.setString(3, id)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun markCompleted(id: String): Unit = withContext(Dispatchers.IO) {
        synchronized(connection) {
            if (removeOnComplete) {
                connection.prepareStatement("DELETE FROM outbox WHERE id = ?").use { stmt ->
                    stmt.setString(1, id)
                    stmt.executeUpdate()
                }
            } else {
                connection.prepareStatement(
                    "UPDATE outbox SET status = ? WHERE id = ?"
                ).use { stmt ->
                    stmt.setString(1, MessageStatus.DELIVERED.name)
                    stmt.setString(2, id)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun markFailed(id: String, reason: String): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        synchronized(connection) {
            connection.prepareStatement(
                """
                UPDATE outbox
                SET status = ?, last_attempt_millis = ?, last_error = ?
                WHERE id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, MessageStatus.FAILED.name)
                stmt.setLong(2, now)
                stmt.setString(3, reason)
                stmt.setString(4, id)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun getPendingCount(): Int = withContext(Dispatchers.IO) {
        synchronized(connection) {
            connection.prepareStatement(
                "SELECT COUNT(*) FROM outbox WHERE status = ?"
            ).use { stmt ->
                stmt.setString(1, MessageStatus.PENDING_OUTBOX.name)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }
    }

    suspend fun getItem(id: String): OutboxItem? = withContext(Dispatchers.IO) {
        synchronized(connection) {
            connection.prepareStatement(
                """
                SELECT id, conversation_id, recipient_mailbox_token, recipient_public_key_hex,
                       payload_plaintext, human_only, attempts, last_attempt_millis, status,
                       created_at_millis, last_error
                FROM outbox WHERE id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        mapRowToOutboxItem(rs)
                    } else null
                }
            }
        }
    }

    suspend fun remove(id: String): Unit = withContext(Dispatchers.IO) {
        synchronized(connection) {
            connection.prepareStatement("DELETE FROM outbox WHERE id = ?").use { stmt ->
                stmt.setString(1, id)
                stmt.executeUpdate()
            }
        }
    }

    private fun mapRowToOutboxItem(rs: java.sql.ResultSet): OutboxItem {
        return OutboxItem(
            id = rs.getString("id"),
            conversationId = rs.getString("conversation_id"),
            recipientMailboxToken = rs.getString("recipient_mailbox_token"),
            recipientX25519PublicKeyHex = rs.getString("recipient_public_key_hex"),
            payloadPlaintext = rs.getString("payload_plaintext"),
            humanOnly = rs.getInt("human_only") == 1,
            attempts = rs.getInt("attempts"),
            lastAttemptMillis = rs.getLong("last_attempt_millis"),
            status = MessageStatus.valueOf(rs.getString("status")),
            createdAtMillis = rs.getLong("created_at_millis"),
            lastError = rs.getString("last_error")
        )
    }

    override fun close() {
        if (ownsConnection) {
            synchronized(connection) {
                if (!connection.isClosed) {
                    connection.close()
                }
            }
        }
    }

    companion object {
        fun initSchema(connection: Connection) {
            synchronized(connection) {
                connection.createStatement().use { stmt ->
                    stmt.execute("PRAGMA journal_mode = WAL;")
                    stmt.execute("PRAGMA synchronous = NORMAL;")
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS outbox (
                            id TEXT PRIMARY KEY,
                            conversation_id TEXT NOT NULL,
                            recipient_mailbox_token TEXT NOT NULL,
                            recipient_public_key_hex TEXT NOT NULL,
                            payload_plaintext TEXT NOT NULL,
                            human_only INTEGER NOT NULL DEFAULT 0,
                            attempts INTEGER NOT NULL DEFAULT 0,
                            last_attempt_millis INTEGER NOT NULL DEFAULT 0,
                            status TEXT NOT NULL,
                            created_at_millis INTEGER NOT NULL,
                            last_error TEXT
                        );
                        """.trimIndent()
                    )
                    stmt.execute(
                        "CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox (status, created_at_millis);"
                    )
                }
            }
        }
    }
}
