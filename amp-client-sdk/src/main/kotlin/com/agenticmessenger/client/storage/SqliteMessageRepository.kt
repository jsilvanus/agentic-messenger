package com.agenticmessenger.client.storage

import com.agenticmessenger.client.model.MessageDirection
import com.agenticmessenger.client.model.MessageStatus
import com.agenticmessenger.client.model.StoredMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap

class SqliteMessageRepository(
    private val connection: Connection,
    private val ownsConnection: Boolean = false
) : MessageRepository {

    constructor(dbPath: String = "jdbc:sqlite:amp_client.db") : this(
        DriverManager.getConnection(if (dbPath.startsWith("jdbc:sqlite:")) dbPath else "jdbc:sqlite:$dbPath"),
        ownsConnection = true
    )

    private val conversationFlows = ConcurrentHashMap<String, MutableStateFlow<List<StoredMessage>>>()

    init {
        initSchema(connection)
    }

    override suspend fun saveMessage(message: StoredMessage) = withContext(Dispatchers.IO) {
        synchronized(connection) {
            connection.prepareStatement(
                """
                INSERT OR REPLACE INTO messages (
                    id, conversation_id, sender_id, recipient_id, plaintext,
                    direction, status, timestamp_millis, relay_envelope_id, annotations_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, message.id)
                stmt.setString(2, message.conversationId)
                stmt.setString(3, message.senderId)
                stmt.setString(4, message.recipientId)
                stmt.setString(5, message.plaintext)
                stmt.setString(6, message.direction.name)
                stmt.setString(7, message.status.name)
                stmt.setLong(8, message.timestampMillis)
                stmt.setString(9, message.relayEnvelopeId)
                stmt.setString(10, message.annotationsJson)
                stmt.executeUpdate()
            }
        }
        notifyConversationUpdated(message.conversationId)
    }

    override suspend fun updateStatus(id: String, status: MessageStatus, relayEnvelopeId: String?) = withContext(Dispatchers.IO) {
        val conversationId: String? = synchronized(connection) {
            var convId: String? = null
            connection.prepareStatement("SELECT conversation_id FROM messages WHERE id = ?").use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        convId = rs.getString("conversation_id")
                    }
                }
            }

            val sql = if (relayEnvelopeId != null) {
                "UPDATE messages SET status = ?, relay_envelope_id = ? WHERE id = ?"
            } else {
                "UPDATE messages SET status = ? WHERE id = ?"
            }
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, status.name)
                if (relayEnvelopeId != null) {
                    stmt.setString(2, relayEnvelopeId)
                    stmt.setString(3, id)
                } else {
                    stmt.setString(2, id)
                }
                stmt.executeUpdate()
            }
            convId
        }

        if (conversationId != null) {
            notifyConversationUpdated(conversationId)
        }
    }

    override suspend fun getMessages(
        conversationId: String,
        limit: Int,
        beforeTimestamp: Long?
    ): List<StoredMessage> = withContext(Dispatchers.IO) {
        getMessagesSync(conversationId, limit, beforeTimestamp)
    }

    override fun observeConversation(conversationId: String): StateFlow<List<StoredMessage>> {
        return conversationFlows.computeIfAbsent(conversationId) {
            val initial = getMessagesSync(conversationId, limit = 50)
            MutableStateFlow(initial)
        }.asStateFlow()
    }

    override suspend fun getMessageById(id: String): StoredMessage? = withContext(Dispatchers.IO) {
        synchronized(connection) {
            connection.prepareStatement(
                """
                SELECT id, conversation_id, sender_id, recipient_id, plaintext,
                       direction, status, timestamp_millis, relay_envelope_id, annotations_json
                FROM messages WHERE id = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        mapRowToStoredMessage(rs)
                    } else null
                }
            }
        }
    }

    internal fun getMessagesSync(
        conversationId: String,
        limit: Int = 50,
        beforeTimestamp: Long? = null
    ): List<StoredMessage> {
        val effectiveLimit = if (limit <= 0) 50 else limit
        return synchronized(connection) {
            val sql = if (beforeTimestamp != null) {
                """
                SELECT id, conversation_id, sender_id, recipient_id, plaintext,
                       direction, status, timestamp_millis, relay_envelope_id, annotations_json
                FROM messages
                WHERE conversation_id = ? AND timestamp_millis < ?
                ORDER BY timestamp_millis DESC, id DESC
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT id, conversation_id, sender_id, recipient_id, plaintext,
                       direction, status, timestamp_millis, relay_envelope_id, annotations_json
                FROM messages
                WHERE conversation_id = ?
                ORDER BY timestamp_millis DESC, id DESC
                LIMIT ?
                """.trimIndent()
            }

            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, conversationId)
                if (beforeTimestamp != null) {
                    stmt.setLong(2, beforeTimestamp)
                    stmt.setInt(3, effectiveLimit)
                } else {
                    stmt.setInt(2, effectiveLimit)
                }

                stmt.executeQuery().use { rs ->
                    val list = mutableListOf<StoredMessage>()
                    while (rs.next()) {
                        list.add(mapRowToStoredMessage(rs))
                    }
                    // Reverse to present in chronological order (oldest to newest)
                    list.reverse()
                    list
                }
            }
        }
    }

    private fun notifyConversationUpdated(conversationId: String) {
        val flow = conversationFlows[conversationId] ?: return
        val updated = getMessagesSync(conversationId, limit = 50)
        flow.value = updated
    }

    private fun mapRowToStoredMessage(rs: java.sql.ResultSet): StoredMessage {
        return StoredMessage(
            id = rs.getString("id"),
            conversationId = rs.getString("conversation_id"),
            senderId = rs.getString("sender_id"),
            recipientId = rs.getString("recipient_id"),
            plaintext = rs.getString("plaintext"),
            direction = MessageDirection.valueOf(rs.getString("direction")),
            status = MessageStatus.valueOf(rs.getString("status")),
            timestampMillis = rs.getLong("timestamp_millis"),
            relayEnvelopeId = rs.getString("relay_envelope_id"),
            annotationsJson = rs.getString("annotations_json")
        )
    }

    override fun close() {
        conversationFlows.clear()
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
                        CREATE TABLE IF NOT EXISTS messages (
                            id TEXT PRIMARY KEY,
                            conversation_id TEXT NOT NULL,
                            sender_id TEXT NOT NULL,
                            recipient_id TEXT NOT NULL,
                            plaintext TEXT NOT NULL,
                            direction TEXT NOT NULL,
                            status TEXT NOT NULL,
                            timestamp_millis INTEGER NOT NULL,
                            relay_envelope_id TEXT,
                            annotations_json TEXT
                        );
                        """.trimIndent()
                    )
                    stmt.execute(
                        "CREATE INDEX IF NOT EXISTS idx_messages_conv_time ON messages (conversation_id, timestamp_millis);"
                    )
                }
            }
        }
    }
}
