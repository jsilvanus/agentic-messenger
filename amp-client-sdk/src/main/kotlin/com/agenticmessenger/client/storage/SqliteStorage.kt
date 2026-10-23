package com.agenticmessenger.client.storage

import java.sql.Connection
import java.sql.DriverManager

class SqliteStorage(
    val connection: Connection,
    private val ownsConnection: Boolean = false,
    removeOnComplete: Boolean = false
) : AutoCloseable {

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

    val messageRepository: SqliteMessageRepository = SqliteMessageRepository(connection, ownsConnection = false)
    val outboxQueue: SqliteOutboxQueue = SqliteOutboxQueue(connection, ownsConnection = false, removeOnComplete = removeOnComplete)

    override fun close() {
        messageRepository.close()
        outboxQueue.close()
        if (ownsConnection) {
            synchronized(connection) {
                if (!connection.isClosed) {
                    connection.close()
                }
            }
        }
    }

    companion object {
        fun inMemory(removeOnComplete: Boolean = false): SqliteStorage {
            val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
            return SqliteStorage(conn, ownsConnection = true, removeOnComplete = removeOnComplete)
        }

        fun initSchema(connection: Connection) {
            SqliteMessageRepository.initSchema(connection)
            SqliteOutboxQueue.initSchema(connection)
        }
    }
}
