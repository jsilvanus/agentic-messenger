package com.agenticmessenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.molecular.IdentityCard
import com.agenticmessenger.ui.presenter.LoginPresenter

/**
 * Screen: LoginScreen (Compose Multiplatform)
 *
 * Configures cryptographic identity and blind relay connection:
 * - Principal DID input (e.g. did:aidos:jack)
 * - Device Key ID (e.g. key_jack_mobile_01)
 * - Epoch derivation key for blinded routing (HMAC)
 * - Live Blinded Mailbox derivation display
 * - Hardware Enclave / StrongBox status check
 * - Relay WebSocket endpoint configuration
 */
@Composable
fun LoginScreen(
    presenter: LoginPresenter,
    modifier: Modifier = Modifier,
    onLoginSuccess: () -> Unit = {}
) {
    val state by presenter.state.collectAsState()

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Logo & Title
            Text(
                text = "⚡ Agentic Messenger",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Zero-Knowledge Blind Relay & Multi-Party Mesh",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Principal DID
            OutlinedTextField(
                value = state.principalId,
                onValueChange = { presenter.onPrincipalIdChanged(it) },
                label = { Text("Principal DID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Device Key Identifier
            OutlinedTextField(
                value = state.deviceKeyId,
                onValueChange = { presenter.onDeviceKeyIdChanged(it) },
                label = { Text("Device Key Identifier") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Epoch Key Hex
            OutlinedTextField(
                value = state.epochKeyHex,
                onValueChange = { presenter.onEpochKeyHexChanged(it) },
                label = { Text("Epoch Key Hex (Blinded Mailbox Derivation)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Relay Endpoint
            OutlinedTextField(
                value = state.relayEndpoint,
                onValueChange = { presenter.onRelayEndpointChanged(it) },
                label = { Text("Blind Relay Server Endpoint") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Live Computed Identity Card
            IdentityCard(
                principalId = state.principalId,
                deviceKeyId = state.deviceKeyId,
                mailboxId = state.calculatedMailboxId,
                isConnected = state.isConnected,
                isHardwareEnclave = state.isHardwareEnclaveAvailable
            )

            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFEDEB))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Error: ${state.errorMessage}",
                        color = Color(0xFFB3261E),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Connect Button
            Button(
                onClick = {
                    presenter.connectToRelay()
                    if (state.isConnected) onLoginSuccess()
                },
                enabled = !state.isConnecting && state.calculatedMailboxId.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (state.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (state.isConnected) "Enter Messenger Mesh" else "Connect & Initialize Session",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
