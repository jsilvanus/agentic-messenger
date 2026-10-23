package com.agenticmessenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.agenticmessenger.client.AmpSession
import com.agenticmessenger.client.identity.ClientIdentity
import com.agenticmessenger.client.network.AmpConnectionState
import com.agenticmessenger.client.storage.SqliteStorage
import com.agenticmessenger.ui.model.ContactUiModel
import com.agenticmessenger.ui.model.PresenceState
import com.agenticmessenger.ui.presenter.ConversationPresenter
import com.agenticmessenger.ui.screens.ConversationScreen
import com.agenticmessenger.ui.theme.AmpTheme

@Composable
fun DesktopMessengerWindow(
    identity: ClientIdentity,
    session: AmpSession,
    presenter: ConversationPresenter,
    title: String
) {
    val connectionState by session.connectionState.collectAsState()
    val isConnected = connectionState == AmpConnectionState.CONNECTED

    AmpTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Profile name ("Alice"), connection status badge (🟢 Connected / 🔴 Offline), own Mailbox ID
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "🟢 Connected" else "🔴 Offline ($connectionState)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
                Text(
                    text = "Mailbox: ${identity.mailboxToken.take(12)}…",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }

            ConversationScreen(
                presenter = presenter,
                currentUserId = identity.principalId,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

fun main() = application {
    val relayUrl = System.getenv("AMP_RELAY_URL") ?: "ws://localhost:8080/ws/relay"
    val scope = rememberCoroutineScope()

    // 1. Identities
    val aliceIdentity = remember { ClientIdentity.generate("alice_desktop_device", "did:aidos:alice") }
    val bobIdentity = remember { ClientIdentity.generate("bob_desktop_device", "did:aidos:bob") }

    // 2. Storage & Sessions
    val (aliceSession, bobSession) = remember {
        val aStorage = SqliteStorage.inMemory()
        val bStorage = SqliteStorage.inMemory()
        val aSession = AmpSession(identity = aliceIdentity, storage = aStorage, relayWsUrl = relayUrl)
        val bSession = AmpSession(identity = bobIdentity, storage = bStorage, relayWsUrl = relayUrl)
        aSession.start()
        bSession.start()
        aSession to bSession
    }

    DisposableEffect(Unit) {
        onDispose {
            aliceSession.close()
            bobSession.close()
        }
    }

    // 3. Contacts
    val bobContact = remember {
        ContactUiModel(
            contactId = "bob",
            displayName = "Bob",
            handle = "did:aidos:bob",
            mailboxId = bobIdentity.mailboxToken,
            presence = PresenceState.ONLINE,
            isAidosActive = true,
            principalId = "did:aidos:bob",
            x25519PublicKeyHex = bobIdentity.x25519PublicKeyHex
        )
    }
    val aliceContact = remember {
        ContactUiModel(
            contactId = "alice",
            displayName = "Alice",
            handle = "did:aidos:alice",
            mailboxId = aliceIdentity.mailboxToken,
            presence = PresenceState.ONLINE,
            isAidosActive = true,
            principalId = "did:aidos:alice",
            x25519PublicKeyHex = aliceIdentity.x25519PublicKeyHex
        )
    }

    // 4. Presenters
    val alicePresenter = remember {
        ConversationPresenter(
            scope = scope,
            currentUserId = aliceIdentity.principalId,
            currentDeviceKeyId = aliceIdentity.deviceId,
            initialContact = bobContact,
            session = aliceSession
        )
    }
    val bobPresenter = remember {
        ConversationPresenter(
            scope = scope,
            currentUserId = bobIdentity.principalId,
            currentDeviceKeyId = bobIdentity.deviceId,
            initialContact = aliceContact,
            session = bobSession
        )
    }

    // Window 1: Alice (targeting Bob)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Agentic Messenger - Alice (did:aidos:alice)",
        state = rememberWindowState(
            position = WindowPosition(80.dp, 100.dp),
            size = DpSize(520.dp, 750.dp)
        )
    ) {
        DesktopMessengerWindow(
            identity = aliceIdentity,
            session = aliceSession,
            presenter = alicePresenter,
            title = "Alice"
        )
    }

    // Window 2: Bob (targeting Alice)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Agentic Messenger - Bob (did:aidos:bob)",
        state = rememberWindowState(
            position = WindowPosition(620.dp, 100.dp),
            size = DpSize(520.dp, 750.dp)
        )
    ) {
        DesktopMessengerWindow(
            identity = bobIdentity,
            session = bobSession,
            presenter = bobPresenter,
            title = "Bob"
        )
    }
}
