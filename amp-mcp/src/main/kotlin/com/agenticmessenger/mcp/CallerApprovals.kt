package com.agenticmessenger.mcp

import fi.italeino.mcp.binder.CallerIdentity
import fi.italeino.mcp.binder.CallerPolicy
import java.util.concurrent.ConcurrentHashMap

/** An app, identified the only way Android lets us trust: its package together with its signing certificate. */
data class ApprovedCaller(val packageName: String, val signingCertSha256: String)

enum class ApprovalDecision { APPROVED, DENIED, PENDING }

/**
 * Where the user's decisions about other apps are kept. The Android host backs this with
 * persistent storage and shows [ApprovalPolicy]'s pending requests to the user; nothing here
 * decides on the user's behalf.
 *
 * Decisions are per package **and** certificate: an app reinstalled with a different key is a new,
 * undecided caller, never inherited trust.
 */
interface CallerApprovalStore {
    fun decisionFor(caller: ApprovedCaller): ApprovalDecision
}

/** Simple in-memory store; the Android host replaces it with a persistent one. */
class InMemoryCallerApprovals : CallerApprovalStore {
    private val decisions = ConcurrentHashMap<ApprovedCaller, ApprovalDecision>()

    fun approve(caller: ApprovedCaller) { decisions[caller] = ApprovalDecision.APPROVED }
    fun deny(caller: ApprovedCaller) { decisions[caller] = ApprovalDecision.DENIED }
    fun revoke(caller: ApprovedCaller) { decisions.remove(caller) }

    override fun decisionFor(caller: ApprovedCaller): ApprovalDecision = decisions[caller] ?: ApprovalDecision.PENDING
}

/**
 * Admits a Binder caller only if the user approved it. Deny by default: an unknown caller is
 * refused, and [onPending] is told so the app can ask the user (a notification or a screen). The
 * refused caller has to connect again after the user decides; nothing waits inside the Binder call.
 *
 * Unverified identity (no package or no certificate) is never admitted and never queued for approval,
 * because there would be nothing trustworthy to show the user.
 */
class ApprovalPolicy(
    private val store: CallerApprovalStore,
    private val onPending: (ApprovedCaller) -> Unit = {},
) : CallerPolicy {
    override fun isAllowed(caller: CallerIdentity): Boolean {
        val candidates = caller.packages.flatMap { pkg ->
            caller.signingCertSha256.map { ApprovedCaller(pkg, it.lowercase()) }
        }
        if (candidates.isEmpty()) return false
        val decisions = candidates.map { it to store.decisionFor(it) }
        // Any explicit denial wins; otherwise one approved package+certificate pair is enough.
        if (decisions.any { it.second == ApprovalDecision.DENIED }) return false
        if (decisions.any { it.second == ApprovalDecision.APPROVED }) return true
        decisions.forEach { runCatching { onPending(it.first) } }
        return false
    }
}
