package com.agenticmessenger.mcp.android

import android.content.Context
import com.agenticmessenger.mcp.ApprovalDecision
import com.agenticmessenger.mcp.ApprovedCaller
import com.agenticmessenger.mcp.CallerApprovalStore

/**
 * The user's decisions about other apps, kept in this app's private preferences.
 *
 * Keyed by package **and** signing certificate. A request from an unknown caller is recorded as
 * pending so [ApprovalActivity] can show it; recording it grants nothing.
 */
class PrefsCallerApprovals(context: Context) : CallerApprovalStore {
    private val prefs = context.applicationContext.getSharedPreferences("amp_mcp_callers", Context.MODE_PRIVATE)

    private fun key(c: ApprovedCaller) = "${c.packageName}|${c.signingCertSha256}"

    override fun decisionFor(caller: ApprovedCaller): ApprovalDecision = when (prefs.getString(key(caller), null)) {
        "A" -> ApprovalDecision.APPROVED
        "D" -> ApprovalDecision.DENIED
        else -> ApprovalDecision.PENDING
    }

    fun approve(caller: ApprovedCaller) = prefs.edit().putString(key(caller), "A").remove(pendingKey(caller)).apply()
    fun deny(caller: ApprovedCaller) = prefs.edit().putString(key(caller), "D").remove(pendingKey(caller)).apply()
    fun revoke(caller: ApprovedCaller) = prefs.edit().remove(key(caller)).apply()

    /** Called from the Binder thread when an undecided app tried to connect. */
    fun recordPending(caller: ApprovedCaller) {
        if (prefs.getString(key(caller), null) == null) prefs.edit().putBoolean(pendingKey(caller), true).apply()
    }

    fun pending(): List<ApprovedCaller> = prefs.all.keys.filter { it.startsWith(PENDING) }
        .mapNotNull { parse(it.removePrefix(PENDING)) }

    fun decided(): List<Pair<ApprovedCaller, ApprovalDecision>> = prefs.all.entries
        .filter { !it.key.startsWith(PENDING) }
        .mapNotNull { (k, v) -> parse(k)?.let { it to if (v == "A") ApprovalDecision.APPROVED else ApprovalDecision.DENIED } }

    private fun pendingKey(c: ApprovedCaller) = PENDING + key(c)

    private fun parse(k: String): ApprovedCaller? =
        k.split('|').takeIf { it.size == 2 }?.let { ApprovedCaller(it[0], it[1]) }

    private companion object {
        const val PENDING = "pending:"
    }
}
