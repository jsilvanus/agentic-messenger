package com.agenticmessenger.mcp

import dev.aidos.amp.AmpToolDefinition
import fi.italeino.mcp.binder.ProviderHost
import fi.italeino.mcp.binder.TransportLimits

/**
 * Amp's side of the MCP Binder transport, free of Android so it can be tested on a JVM. The Android
 * service hands this [host] to the Binder library, which supplies the verified caller identity.
 *
 * Two independent gates, both deny by default:
 * - who may connect at all: [ApprovalPolicy] (the user approved that package and signing certificate);
 * - what a connected caller may do: [AmpMcpServer] publishes only the allow-listed chat tools, and
 *   by default never lets a caller send as the human ([allowSendAsHuman]).
 *
 * Every session gets its own [AmpMcpServer], so one caller's session never shares state with another's.
 */
class AmpMcpBinderProvider(
    private val tools: () -> List<AmpToolDefinition>,
    approvals: CallerApprovalStore,
    onPendingApproval: (ApprovedCaller) -> Unit = {},
    exposed: Set<String> = AmpMcpServer.CHAT_TOOLS,
    allowSendAsHuman: Boolean = false,
    limits: TransportLimits = TransportLimits(),
    maxSessions: Int = 4,
) {
    val host: ProviderHost = ProviderHost(ApprovalPolicy(approvals, onPendingApproval), limits, maxSessions) {
        AmpMcpServer(tools(), exposed, allowSendAsHuman = allowSendAsHuman).server
    }
}
