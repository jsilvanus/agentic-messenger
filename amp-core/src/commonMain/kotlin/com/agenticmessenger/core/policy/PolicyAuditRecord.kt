package com.agenticmessenger.core.policy

import com.agenticmessenger.core.model.TypedAnnotation
import kotlinx.serialization.Serializable

/**
 * Immutable audit record capturing the deterministic boundary evaluation verdict and rationale.
 */
@Serializable
data class PolicyAuditRecord(
    val timestamp: Long,
    val decision: EvaluationDecision,
    val matchedRule: String?,
    val reason: String,
    val queryTopic: String? = null,
    val contextAnnotation: TypedAnnotation? = null
)
