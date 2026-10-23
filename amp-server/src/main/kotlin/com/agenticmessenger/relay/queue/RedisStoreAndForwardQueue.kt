package com.agenticmessenger.relay.queue

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.net.URI

class RedisStoreAndForwardQueue(
    private val jedisPool: JedisPool,
    private val ownsPool: Boolean = false
) : StoreAndForwardQueue {

    constructor(
        redisUrl: String = "redis://localhost:6379"
    ) : this(
        jedisPool = JedisPool(URI.create(redisUrl)),
        ownsPool = true
    )

    constructor(
        host: String,
        port: Int = 6379,
        password: String? = null,
        timeoutMs: Int = 2000
    ) : this(
        jedisPool = if (password.isNullOrBlank()) {
            JedisPool(JedisPoolConfig(), host, port, timeoutMs)
        } else {
            JedisPool(JedisPoolConfig(), host, port, timeoutMs, password)
        },
        ownsPool = true
    )

    private val logger = LoggerFactory.getLogger(RedisStoreAndForwardQueue::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val keyPrefixMsg = "amp:queue:msg:"
    private val keyPrefixDest = "amp:queue:dest:"
    private val keyExpires = "amp:queue:expires"

    override fun enqueue(
        messageId: String,
        destinationMailbox: String,
        sourceMailbox: String?,
        payloadCiphertext: String,
        ttlSeconds: Long
    ): Boolean {
        val now = System.currentTimeMillis()
        val expiresAt = now + (ttlSeconds * 1000)

        val envelope = QueuedEnvelope(
            messageId = messageId,
            destinationMailbox = destinationMailbox,
            sourceMailbox = sourceMailbox,
            payloadCiphertext = payloadCiphertext,
            createdAt = now,
            expiresAt = expiresAt
        )

        val envelopeJson = json.encodeToString(envelope)
        val msgKey = "$keyPrefixMsg$destinationMailbox:$messageId"
        val destKey = "$keyPrefixDest$destinationMailbox"
        val expiresMember = "$destinationMailbox:$messageId"

        return try {
            jedisPool.resource.use { jedis ->
                val pipe = jedis.pipelined()
                // Set data with TTL safety margin (+1 day past expiresAt)
                val redisTtlSeconds = maxOf(1L, ttlSeconds + 86400L)
                pipe.setex(msgKey, redisTtlSeconds, envelopeJson)
                pipe.zadd(destKey, now.toDouble(), messageId)
                pipe.zadd(keyExpires, expiresAt.toDouble(), expiresMember)
                pipe.sync()
                true
            }
        } catch (e: Exception) {
            logger.error("Failed to enqueue message '$messageId' for '$destinationMailbox': {}", e.message)
            false
        }
    }

    override fun fetchPending(destinationMailbox: String, limit: Int): List<QueuedEnvelope> {
        val destKey = "$keyPrefixDest$destinationMailbox"
        val now = System.currentTimeMillis()
        val results = mutableListOf<QueuedEnvelope>()

        try {
            jedisPool.resource.use { jedis ->
                val messageIds = jedis.zrange(destKey, 0, (limit - 1).toLong())
                if (messageIds.isEmpty()) return emptyList()

                for (msgId in messageIds) {
                    val msgKey = "$keyPrefixMsg$destinationMailbox:$msgId"
                    val envelopeJson = jedis.get(msgKey)
                    if (envelopeJson == null) {
                        // Stale reference, clean up index
                        jedis.zrem(destKey, msgId)
                        jedis.zrem(keyExpires, "$destinationMailbox:$msgId")
                        continue
                    }

                    try {
                        val envelope = json.decodeFromString<QueuedEnvelope>(envelopeJson)
                        if (envelope.expiresAt <= now) {
                            // Expired
                            jedis.del(msgKey)
                            jedis.zrem(destKey, msgId)
                            jedis.zrem(keyExpires, "$destinationMailbox:$msgId")
                        } else {
                            results.add(envelope)
                        }
                    } catch (e: Exception) {
                        logger.error("Error deserializing queued envelope '$msgId': {}", e.message)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch pending messages for '$destinationMailbox': {}", e.message)
        }
        return results
    }

    override fun acknowledge(messageId: String, destinationMailbox: String): Boolean {
        val msgKey = "$keyPrefixMsg$destinationMailbox:$messageId"
        val destKey = "$keyPrefixDest$destinationMailbox"
        val expiresMember = "$destinationMailbox:$messageId"

        return try {
            jedisPool.resource.use { jedis ->
                val pipe = jedis.pipelined()
                val delResponse = pipe.del(msgKey)
                pipe.zrem(destKey, messageId)
                pipe.zrem(keyExpires, expiresMember)
                pipe.sync()
                delResponse.get() > 0
            }
        } catch (e: Exception) {
            logger.error("Failed to acknowledge message '$messageId' for '$destinationMailbox': {}", e.message)
            false
        }
    }

    override fun countPending(destinationMailbox: String): Int {
        val destKey = "$keyPrefixDest$destinationMailbox"
        val now = System.currentTimeMillis()

        return try {
            jedisPool.resource.use { jedis ->
                val messageIds = jedis.zrange(destKey, 0, -1)
                var activeCount = 0
                for (msgId in messageIds) {
                    val msgKey = "$keyPrefixMsg$destinationMailbox:$msgId"
                    val envelopeJson = jedis.get(msgKey)
                    if (envelopeJson != null) {
                        try {
                            val envelope = json.decodeFromString<QueuedEnvelope>(envelopeJson)
                            if (envelope.expiresAt > now) {
                                activeCount++
                            }
                        } catch (_: Exception) {}
                    }
                }
                activeCount
            }
        } catch (e: Exception) {
            logger.error("Failed to count pending messages for '$destinationMailbox': {}", e.message)
            0
        }
    }

    override fun purgeExpired(): Int {
        val now = System.currentTimeMillis().toDouble()
        var purgedCount = 0

        try {
            jedisPool.resource.use { jedis ->
                val expiredMembers = jedis.zrangeByScore(keyExpires, 0.0, now)
                for (member in expiredMembers) {
                    val parts = member.split(":", limit = 2)
                    if (parts.size == 2) {
                        val dest = parts[0]
                        val msgId = parts[1]
                        jedis.del("$keyPrefixMsg$dest:$msgId")
                        jedis.zrem("$keyPrefixDest$dest", msgId)
                    }
                    jedis.zrem(keyExpires, member)
                    purgedCount++
                }
            }
        } catch (e: Exception) {
            logger.error("Failed during purgeExpired: {}", e.message)
        }
        return purgedCount
    }

    override fun totalQueueSize(): Int {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.zcard(keyExpires).toInt()
            }
        } catch (e: Exception) {
            0
        }
    }

    override fun close() {
        if (ownsPool) {
            try {
                jedisPool.close()
            } catch (_: Exception) {}
        }
    }
}
