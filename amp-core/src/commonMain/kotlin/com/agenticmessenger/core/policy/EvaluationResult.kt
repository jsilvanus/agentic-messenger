package com.agenticmessenger.core.policy

import kotlinx.serialization.Serializable

/**
 * Result of evaluating an information boundary query against an [com.agenticmessenger.core.model.InformationBoundaryPolicy].
 */
@Serializable
data class EvaluationResult(
    val decision: EvaluationDecision,
    val auditRecord: PolicyAuditRecord
) {
    val matchedRule: String? get() = auditRecord.matchedRule
    val reason: String get() = auditRecord.reason
    val timestamp: Long get() = auditRecord.timestamp

    val isPermittedDirect: Boolean get() = decision == EvaluationDecision.PERMITTED_DIRECT
    val isEscrowRequired: Boolean get() = decision == EvaluationDecision.ESCROW_REQUIRED
    val isDeniedRestricted: Boolean get() = decision == EvaluationDecision.DENIED_RESTRICTED
}
