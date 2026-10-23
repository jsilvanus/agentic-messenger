package com.agenticmessenger.relay.pubsub

import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub
import java.io.Closeable
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RedisPubSubBroker(
    private val jedisPool: JedisPool,
    private val ownsPool: Boolean = false
) : PubSubBroker {

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

    private val logger = LoggerFactory.getLogger(RedisPubSubBroker::class.java)
    private val channelPrefix = "amp:mailbox:"
    private val controlChannel = "amp:control:events"

    private val localListeners = ConcurrentHashMap<String, CopyOnWriteArraySet<MessageListener>>()
    private val isRunning = AtomicBoolean(true)
    private val readyLatch = CountDownLatch(1)
    private val subLock = Any()

    private var subscriberJedis: Jedis? = null
    private var subscriberThread: Thread? = null

    private val pubSubListener = object : JedisPubSub() {
        override fun onMessage(channel: String, message: String) {
            if (channel == controlChannel) return
            val mailboxId = if (channel.startsWith(channelPrefix)) {
                channel.removePrefix(channelPrefix)
            } else {
                channel
            }

            val listeners = localListeners[mailboxId]
            if (!listeners.isNullOrEmpty()) {
                for (listener in listeners) {
                    try {
                        kotlinx.coroutines.runBlocking {
                            listener(mailboxId, message)
                        }
                    } catch (e: Exception) {
                        logger.error("Error executing listener for mailbox '{}': {}", mailboxId, e.message, e)
                    }
                }
            }
        }

        override fun onSubscribe(channel: String, subscribedChannels: Int) {
            logger.debug("JedisPubSub subscribed to channel: {} (total: {})", channel, subscribedChannels)
            if (channel == controlChannel) {
                readyLatch.countDown()
            }
        }

        override fun onUnsubscribe(channel: String, subscribedChannels: Int) {
            logger.debug("JedisPubSub unsubscribed from channel: {} (total: {})", channel, subscribedChannels)
        }
    }

    init {
        startSubscriberThread()
    }

    private fun startSubscriberThread() {
        val thread = Thread({
            try {
                jedisPool.resource.use { jedis ->
                    subscriberJedis = jedis
                    logger.info("RedisPubSubBroker starting subscription on channel '{}'", controlChannel)
                    jedis.subscribe(pubSubListener, controlChannel)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    logger.warn("RedisPubSubBroker subscriber loop ended: {}", e.message)
                }
            } finally {
                readyLatch.countDown() // prevent blocking if failed
            }
        }, "amp-redis-pubsub-subscriber")
        thread.isDaemon = true
        thread.start()
        subscriberThread = thread

        // Wait up to 3 seconds for initial connection
        readyLatch.await(3, TimeUnit.SECONDS)
    }

    override suspend fun publish(mailboxId: String, payloadJson: String): Long {
        val channel = "$channelPrefix$mailboxId"
        return try {
            jedisPool.resource.use { jedis ->
                jedis.publish(channel, payloadJson)
            }
        } catch (e: Exception) {
            logger.error("Failed to publish to channel '{}': {}", channel, e.message)
            0L
        }
    }

    override suspend fun subscribe(mailboxId: String, listener: MessageListener): Closeable {
        val channel = "$channelPrefix$mailboxId"
        val listeners = localListeners.computeIfAbsent(mailboxId) { CopyOnWriteArraySet() }
        val isFirst = listeners.isEmpty()
        listeners.add(listener)

        if (isFirst && pubSubListener.isSubscribed) {
            synchronized(subLock) {
                try {
                    pubSubListener.subscribe(channel)
                } catch (e: Exception) {
                    logger.warn("Failed to subscribe RedisPubSub to channel '{}': {}", channel, e.message)
                }
            }
        }

        return Closeable {
            listeners.remove(listener)
            if (listeners.isEmpty()) {
                localListeners.remove(mailboxId)
                if (pubSubListener.isSubscribed) {
                    synchronized(subLock) {
                        try {
                            pubSubListener.unsubscribe(channel)
                        } catch (e: Exception) {
                            logger.warn("Failed to unsubscribe RedisPubSub from channel '{}': {}", channel, e.message)
                        }
                    }
                }
            }
        }
    }

    override suspend fun unsubscribe(mailboxId: String) {
        val channel = "$channelPrefix$mailboxId"
        localListeners.remove(mailboxId)
        if (pubSubListener.isSubscribed) {
            synchronized(subLock) {
                try {
                    pubSubListener.unsubscribe(channel)
                } catch (e: Exception) {
                    logger.warn("Failed to unsubscribe RedisPubSub from channel '{}': {}", channel, e.message)
                }
            }
        }
    }

    override fun close() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                if (pubSubListener.isSubscribed) {
                    pubSubListener.unsubscribe()
                }
            } catch (_: Exception) {}

            try {
                subscriberJedis?.close()
            } catch (_: Exception) {}

            localListeners.clear()

            if (ownsPool) {
                try {
                    jedisPool.close()
                } catch (_: Exception) {}
            }
        }
    }
}
