package com.agenticmessenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.agenticmessenger.ui.ai.OpenAiClient
import com.agenticmessenger.ui.ai.OpenAiConfig
import com.agenticmessenger.ui.model.ContactUiModel
import com.agenticmessenger.ui.model.PresenceState
import com.agenticmessenger.ui.presenter.ConversationPresenter
import com.agenticmessenger.ui.molecular.PersistentRedPill
import com.agenticmessenger.ui.screens.ConversationScreen
import com.agenticmessenger.ui.theme.AmpTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Expandable configuration card for an OpenAI-compatible API endpoint (Ollama, Aidos, vLLM).
 */
@Composable
fun OpenAiConfigCard(
    config: OpenAiConfig,
    onConfigChanged: (OpenAiConfig) -> Unit,
    openAiClient: OpenAiClient,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var hostInput by remember(config.host) { mutableStateOf(config.host) }
    var portInput by remember(config.port) { mutableStateOf(config.port.toString()) }
    var modelInput by remember(config.model) { mutableStateOf(config.model) }
    var isEnabled by remember(config.isEnabled) { mutableStateOf(config.isEnabled) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            ) {
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "🤖 AI Copilot (Ollama / Aidos)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${config.baseUrl} (${config.model})",
                    fontSize = 11.sp,
                    color = if (config.isEnabled) Color(0xFF2E7D32) else Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (testStatus != null) {
                    Text(
                        text = testStatus!!,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (testStatus!!.startsWith("🟢")) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        isEnabled = checked
                        onConfigChanged(
                            config.copy(
                                host = hostInput,
                                port = portInput.toIntOrNull() ?: 11434,
                                model = modelInput,
                                isEnabled = checked
                            )
                        )
                    },
                    modifier = Modifier.height(24.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = {
                            hostInput = it
                            onConfigChanged(config.copy(host = it, port = portInput.toIntOrNull() ?: config.port))
                        },
                        label = { Text("Host (e.g. localhost)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = {
                            portInput = it
                            val parsedPort = it.toIntOrNull() ?: config.port
                            onConfigChanged(config.copy(host = hostInput, port = parsedPort))
                        },
                        label = { Text("Port (11434/8000)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = {
                            modelInput = it
                            onConfigChanged(config.copy(model = it))
                        },
                        label = { Text("Model", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OpenAI Endpoint: ${config.chatCompletionsUrl}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                testStatus = "Connecting..."
                                val current = config.copy(
                                    host = hostInput,
                                    port = portInput.toIntOrNull() ?: 11434,
                                    model = modelInput,
                                    isEnabled = isEnabled
                                )
                                onConfigChanged(current)
                                val result = openAiClient.testConnection(current)
                                testStatus = if (result.isSuccess) {
                                    "🟢 Connected"
                                } else {
                                    "🔴 ${result.exceptionOrNull()?.message?.take(30)}"
                                }
                                isTesting = false
                            }
                        },
                        enabled = !isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(if (isTesting) "Testing..." else "Test Connection", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopMessengerWindow(
    identity: ClientIdentity,
    session: AmpSession,
    presenter: ConversationPresenter,
    aiConfig: OpenAiConfig,
    onAiConfigChanged: (OpenAiConfig) -> Unit,
    openAiClient: OpenAiClient,
    scope: CoroutineScope,
    title: String,
    contacts: List<ContactUiModel> = emptyList()
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

            // OpenAI / Ollama / Aidos Configuration Bar
            OpenAiConfigCard(
                config = aiConfig,
                onConfigChanged = { updated ->
                    onAiConfigChanged(updated)
                    presenter.updateAiConfig(updated)
                },
                openAiClient = openAiClient,
                scope = scope
            )

            // Persistent Red Pill: User-Level Orchestrator (Dictator style)
            PersistentRedPill(
                aiConfig = aiConfig,
                contacts = contacts,
                onSendMessageToContact = { contact, text ->
                    try {
                        session.sendMessage(
                            recipientMailboxToken = contact.mailboxId,
                            recipientPrincipalId = contact.principalId,
                            recipientX25519PublicKeyHex = contact.x25519PublicKeyHex.ifEmpty { "e2ee_mock_key" },
                            plaintext = text,
                            policy = com.agenticmessenger.core.model.PrivacyLevel.AGENT_ACCESSIBLE,
                            conversationId = contact.mailboxId,
                            recipientEndpointType = com.agenticmessenger.core.model.EndpointType.HUMAN_DIRECT,
                            annotationsJson = """[{"type":"orchestrator_turn","confidence":0.99}]"""
                        )
                        true
                    } catch (_: Exception) {
                        false
                    }
                },
                onFetchRecentMessages = { mailboxId, limit ->
                    try {
                        session.messageRepo.getMessages(mailboxId, limit).map { stored ->
                            dev.aidos.amp.AmpConversationTurn(
                                role = if (stored.senderId == identity.principalId) "user" else "assistant",
                                text = stored.plaintext,
                                senderId = stored.senderId
                            )
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            )

            ConversationScreen(
                presenter = presenter,
                currentUserId = identity.principalId,
                onAskAiTrigger = { presenter.askAi() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

fun main() = application {
    val relayUrl = System.getenv("AMP_RELAY_URL") ?: "ws://localhost:8080/ws/relay"
    val scope = rememberCoroutineScope()
    val openAiClient = remember { OpenAiClient() }

    // Shared OpenAI / Ollama / Aidos configuration
    var sharedAiConfig by remember {
        mutableStateOf(
            OpenAiConfig(
                host = System.getenv("OPENAI_HOST") ?: "127.0.0.1",
                port = System.getenv("OPENAI_PORT")?.toIntOrNull() ?: 11434, // Default Ollama port
                model = System.getenv("OPENAI_MODEL") ?: "qwen2.5:7b",
                isEnabled = true
            )
        )
    }

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
            session = aliceSession,
            openAiClient = openAiClient,
            openAiConfig = sharedAiConfig
        )
    }
    val bobPresenter = remember {
        ConversationPresenter(
            scope = scope,
            currentUserId = bobIdentity.principalId,
            currentDeviceKeyId = bobIdentity.deviceId,
            initialContact = aliceContact,
            session = bobSession,
            openAiClient = openAiClient,
            openAiConfig = sharedAiConfig
        )
    }

    // Window 1: Alice (targeting Bob)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Agentic Messenger - Alice (did:aidos:alice)",
        state = rememberWindowState(
            position = WindowPosition(80.dp, 80.dp),
            size = DpSize(540.dp, 800.dp)
        )
    ) {
        DesktopMessengerWindow(
            identity = aliceIdentity,
            session = aliceSession,
            presenter = alicePresenter,
            aiConfig = sharedAiConfig,
            onAiConfigChanged = {
                sharedAiConfig = it
                alicePresenter.updateAiConfig(it)
                bobPresenter.updateAiConfig(it)
            },
            openAiClient = openAiClient,
            scope = scope,
            title = "Alice",
            contacts = listOf(bobContact)
        )
    }

    // Window 2: Bob (targeting Alice)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Agentic Messenger - Bob (did:aidos:bob)",
        state = rememberWindowState(
            position = WindowPosition(660.dp, 80.dp),
            size = DpSize(540.dp, 800.dp)
        )
    ) {
        DesktopMessengerWindow(
            identity = bobIdentity,
            session = bobSession,
            presenter = bobPresenter,
            aiConfig = sharedAiConfig,
            onAiConfigChanged = {
                sharedAiConfig = it
                alicePresenter.updateAiConfig(it)
                bobPresenter.updateAiConfig(it)
            },
            openAiClient = openAiClient,
            scope = scope,
            title = "Bob",
            contacts = listOf(aliceContact)
        )
    }
}

