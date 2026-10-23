package com.agenticmessenger.core.policy

import kotlinx.serialization.Serializable

/**
 * Deterministic verdict returned by [InformationBoundaryEvaluator].
 */
@Serializable
enum class EvaluationDecision {
    /**
     * Topic is permitted under [com.agenticmessenger.core.model.PrivacyLevel.AGENT_ACCESSIBLE]
     * and listed in allowedTopics. Agent can infer and answer directly without human intervention.
     */
    PERMITTED_DIRECT,

    /**
     * Topic matches escrowedTopics. Requires explicit human approval before disclosure.
     */
    ESCROW_REQUIRED,

    /**
     * Access is restricted / denied. Topic is neither allowed nor escrowed,
     * envelope is HUMAN_ONLY, or direct inference is disabled.
     */
    DENIED_RESTRICTED
}
