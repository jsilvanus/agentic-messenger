package com.agenticmessenger.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.agenticmessenger.desktop.model.DesktopWindowManager
import com.agenticmessenger.desktop.ui.ChatWindowView
import com.agenticmessenger.desktop.ui.LauncherWindowView

/**
 * Main entry point for Compose Multiplatform Desktop UI (:amp-desktop).
 *
 * Runs the Dual-Window Launcher and renders spawned chat windows (Alice, Bob, etc.)
 * all within a single JVM process.
 */
fun main() = application {
    val windowManager = remember { DesktopWindowManager() }
    var isLauncherVisible by remember { mutableStateOf(true) }

    // Automatically spawn Window A (Alice) and Window B (Bob) on launch for instant testing
    LaunchedEffect(Unit) {
        windowManager.launchAliceAndBob()
    }

    // 1. Launcher Control Window
    if (isLauncherVisible) {
        Window(
            onCloseRequest = {
                if (windowManager.activeSessions.isEmpty()) {
                    exitApplication()
                } else {
                    isLauncherVisible = false
                }
            },
            title = "⚡ AMP — Dual-Window Launcher",
            state = rememberWindowState(
                position = WindowPosition(50.dp, 50.dp),
                size = DpSize(440.dp, 680.dp)
            )
        ) {
            LauncherWindowView(windowManager)
        }
    }

    // 2. Active Chat Windows (Window A: Alice, Window B: Bob, etc.)
    for (session in windowManager.activeSessions) {
        key(session.id) {
            Window(
                onCloseRequest = {
                    windowManager.closeSession(session.id)
                    if (windowManager.activeSessions.isEmpty() && !isLauncherVisible) {
                        exitApplication()
                    }
                },
                title = "💬 ${session.identity.displayName} ➔ ${session.targetPeer.displayName} (AMP)",
                state = session.windowState
            ) {
                ChatWindowView(session)
            }
        }
    }
}
