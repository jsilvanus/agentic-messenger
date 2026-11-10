package com.agenticmessenger.mcp.android

import com.agenticmessenger.agent.AmpAgentService
import com.agenticmessenger.mcp.AmpMcpBinderProvider
import fi.italeino.mcp.binder.ProviderHost
import fi.italeino.mcp.binder.android.McpProviderService

/**
 * Amp's MCP server for other apps on this device (Aidos first). The Binder library captures the
 * caller's verified UID, package and signing certificate; [com.agenticmessenger.mcp.ApprovalPolicy]
 * admits only apps the user approved, and an unknown app is recorded for [ApprovalActivity].
 *
 * Only the chat tools are published, and messages never go out as the human, by default.
 */
class AmpMcpBinderService : McpProviderService() {
    override val host: ProviderHost by lazy {
        val approvals = PrefsCallerApprovals(this)
        AmpMcpBinderProvider(
            tools = { AmpAgentService().toolDefinitions },
            approvals = approvals,
            onPendingApproval = approvals::recordPending,
        ).host
    }
}
