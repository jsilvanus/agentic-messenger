package com.agenticmessenger.desktop.model

import androidx.compose.runtime.mutableStateListOf
import com.agenticmessenger.desktop.bridge.DesktopTransportBridge
import com.agenticmessenger.desktop.bridge.InMemoryDesktopLoopbackBridge

/**
 * Manages the lifecycle of active chat window instances spawned by the launcher.
 */
class DesktopWindowManager(
    val bridge: DesktopTransportBridge = InMemoryDesktopLoopbackBridge.shared
) {
    val activeSessions = mutableStateListOf<ChatSession>()

    /**
     * Spawns both Window A (Alice) and Window B (Bob) side-by-side.
     */
    fun launchAliceAndBob() {
        // Only launch if not already running
        if (activeSessions.none { it.id == "session_alice" }) {
            launchAlice(x = 60, y = 80)
        }
        if (activeSessions.none { it.id == "session_bob" }) {
            launchBob(x = 560, y = 80)
        }
    }

    fun launchAlice(x: Int = 100, y: Int = 100): ChatSession {
        val session = ChatSession(
            id = "session_alice",
            identity = DesktopIdentity.ALICE,
            targetPeer = DesktopIdentity.BOB,
            initialX = x,
            initialY = y,
            bridge = bridge
        )
        activeSessions.add(session)
        return session
    }

    fun launchBob(x: Int = 600, y: Int = 100): ChatSession {
        val session = ChatSession(
            id = "session_bob",
            identity = DesktopIdentity.BOB,
            targetPeer = DesktopIdentity.ALICE,
            initialX = x,
            initialY = y,
            bridge = bridge
        )
        activeSessions.add(session)
        return session
    }

    fun launchCustom(
        name: String,
        handle: String,
        deviceKeyId: String,
        targetPeer: DesktopIdentity,
        x: Int = 300,
        y: Int = 200
    ): ChatSession {
        val identity = DesktopIdentity.create(
            id = name.lowercase().replace(" ", "_"),
            displayName = name,
            handle = handle,
            deviceKeyId = deviceKeyId
        )
        val session = ChatSession(
            id = "session_${identity.id}_${System.currentTimeMillis()}",
            identity = identity,
            targetPeer = targetPeer,
            initialX = x,
            initialY = y,
            bridge = bridge
        )
        activeSessions.add(session)
        return session
    }

    fun closeSession(sessionId: String) {
        val session = activeSessions.find { it.id == sessionId }
        if (session != null) {
            session.close()
            activeSessions.remove(session)
        }
    }

    fun closeAll() {
        activeSessions.forEach { it.close() }
        activeSessions.clear()
    }
}
