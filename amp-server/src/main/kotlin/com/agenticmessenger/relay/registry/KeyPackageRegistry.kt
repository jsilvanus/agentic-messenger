package com.agenticmessenger.relay.registry

import com.agenticmessenger.core.protocol.KeyPackageRecord
import java.io.Closeable
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class KeyPackageRegistry(
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
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS key_packages (
                    identity_hash TEXT NOT NULL,
                    device_key_id TEXT NOT NULL,
                    endpoint_type TEXT NOT NULL,
                    public_key_package TEXT NOT NULL,
                    signature_hex TEXT,
                    published_at INTEGER NOT NULL,
                    PRIMARY KEY (identity_hash, device_key_id)
                );
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_key_identity 
                ON key_packages (identity_hash);
                """.trimIndent()
            )
        }
    }

    /**
     * Publishes or updates an endpoint's MLS KeyPackage / device certificate.
     */
    @Synchronized
    fun publishKeyPackage(record: KeyPackageRecord): Boolean {
        val sql = """
            INSERT OR REPLACE INTO key_packages 
            (identity_hash, device_key_id, endpoint_type, public_key_package, signature_hex, published_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, record.identityHash)
                pstmt.setString(2, record.deviceKeyId)
                pstmt.setString(3, record.endpointType)
                pstmt.setString(4, record.publicKeyPackage)
                pstmt.setString(5, record.signatureHex)
                pstmt.setLong(6, record.publishedAt)
                pstmt.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Retrieves all published KeyPackages associated with an identity hash commitment.
     */
    @Synchronized
    fun getKeyPackages(identityHash: String): List<KeyPackageRecord> {
        val sql = """
            SELECT identity_hash, device_key_id, endpoint_type, public_key_package, signature_hex, published_at
            FROM key_packages
            WHERE identity_hash = ?
            ORDER BY published_at DESC
        """.trimIndent()

        val results = mutableListOf<KeyPackageRecord>()
        try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, identityHash)
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    results.add(
                        KeyPackageRecord(
                            identityHash = rs.getString("identity_hash"),
                            deviceKeyId = rs.getString("device_key_id"),
                            endpointType = rs.getString("endpoint_type"),
                            publicKeyPackage = rs.getString("public_key_package"),
                            signatureHex = rs.getString("signature_hex"),
                            publishedAt = rs.getLong("published_at")
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
     * Removes an endpoint key (e.g., when a device is revoked).
     */
    @Synchronized
    fun revokeDeviceKey(identityHash: String, deviceKeyId: String): Boolean {
        val sql = "DELETE FROM key_packages WHERE identity_hash = ? AND device_key_id = ?"
        return try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, identityHash)
                pstmt.setString(2, deviceKeyId)
                pstmt.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            false
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
