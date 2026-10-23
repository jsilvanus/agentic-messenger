package com.agenticmessenger.core.policy

import com.agenticmessenger.core.model.InformationBoundaryPolicy
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.core.model.TypedAnnotation
import com.agenticmessenger.core.model.UniversalEnvelope
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Zero-LLM deterministic Information Boundary Policy (IBP) Evaluator.
 *
 * Enforces strict, rule-based progressive disclosure constraints before any query
 * or annotation is processed or disclosed by an AI agent.
 */
class InformationBoundaryEvaluator(
    private val recordAuditLog: Boolean = true
) {
    private val _auditLog = CopyOnWriteArrayList<PolicyAuditRecord>()

    /**
     * Read-only view of recorded audit log records.
     */
    val auditLog: List<PolicyAuditRecord>
        get() = _auditLog.toList()

    /**
     * Clears recorded audit log.
     */
    fun clearAuditLog() {
        _auditLog.clear()
    }

    /**
     * Evaluates an Information Boundary Policy against a requested topic and optional context annotation.
     */
    fun evaluate(
        policy: InformationBoundaryPolicy,
        queryTopic: String,
        contextAnnotation: TypedAnnotation? = null,
        timestamp: Long = System.currentTimeMillis()
    ): EvaluationResult {
        val result = evaluateInternal(policy, queryTopic, contextAnnotation, timestamp)
        if (recordAuditLog) {
            _auditLog.add(result.auditRecord)
        }
        return result
    }

    /**
     * Overload to evaluate directly against a [UniversalEnvelope].
     */
    fun evaluate(
        envelope: UniversalEnvelope,
        queryTopic: String,
        contextAnnotation: TypedAnnotation? = null,
        timestamp: Long = System.currentTimeMillis()
    ): EvaluationResult {
        return evaluate(envelope.policy, queryTopic, contextAnnotation, timestamp)
    }

    /**
     * Evaluates and returns only the decision enum.
     */
    fun evaluateDecision(
        policy: InformationBoundaryPolicy,
        queryTopic: String,
        contextAnnotation: TypedAnnotation? = null
    ): EvaluationDecision = evaluate(policy, queryTopic, contextAnnotation).decision

    /**
     * Evaluates and returns only the decision enum for a UniversalEnvelope.
     */
    fun evaluateDecision(
        envelope: UniversalEnvelope,
        queryTopic: String,
        contextAnnotation: TypedAnnotation? = null
    ): EvaluationDecision = evaluate(envelope.policy, queryTopic, contextAnnotation).decision

    companion object {
        private val DEFAULT_EVALUATOR = InformationBoundaryEvaluator(recordAuditLog = false)

        /**
         * Pure deterministic evaluation without maintaining an audit log instance.
         */
        fun evaluate(
            policy: InformationBoundaryPolicy,
            queryTopic: String,
            contextAnnotation: TypedAnnotation? = null,
            timestamp: Long = System.currentTimeMillis()
        ): EvaluationResult {
            return DEFAULT_EVALUATOR.evaluate(policy, queryTopic, contextAnnotation, timestamp)
        }

        fun evaluate(
            envelope: UniversalEnvelope,
            queryTopic: String,
            contextAnnotation: TypedAnnotation? = null,
            timestamp: Long = System.currentTimeMillis()
        ): EvaluationResult {
            return DEFAULT_EVALUATOR.evaluate(envelope.policy, queryTopic, contextAnnotation, timestamp)
        }

        fun evaluateDecision(
            policy: InformationBoundaryPolicy,
            queryTopic: String,
            contextAnnotation: TypedAnnotation? = null
        ): EvaluationDecision = evaluate(policy, queryTopic, contextAnnotation).decision

        fun evaluateDecision(
            envelope: UniversalEnvelope,
            queryTopic: String,
            contextAnnotation: TypedAnnotation? = null
        ): EvaluationDecision = evaluate(envelope.policy, queryTopic, contextAnnotation).decision

        /**
         * Normalizes a topic string: trims whitespace.
         */
        fun normalizeTopic(topic: String): String = topic.trim()

        /**
         * Tests whether a candidate topic matches a rule pattern.
         * Supports exact matches (case-insensitive) and hierarchical wildcard patterns (e.g. "TRAVEL.*").
         */
        fun matchesTopic(rulePattern: String, candidateTopic: String): Boolean {
            val rule = normalizeTopic(rulePattern)
            val candidate = normalizeTopic(candidateTopic)

            if (rule.isEmpty() || candidate.isEmpty()) {
                return false
            }

            if (rule.equals(candidate, ignoreCase = true)) {
                return true
            }

            if (!rule.contains("*")) {
                return false
            }

            // Convert glob pattern to regex: escape regex characters except '*'
            val regexPattern = buildString {
                append("^")
                for (c in rule) {
                    when (c) {
                        '*' -> append(".*")
                        '.', '(', ')', '[', ']', '{', '}', '+', '?', '^', '$', '|', '\\' -> {
                            append('\\').append(c)
                        }
                        else -> append(c)
                    }
                }
                append("$")
            }

            val regex = Regex(regexPattern, RegexOption.IGNORE_CASE)
            if (regex.matches(candidate)) {
                return true
            }

            // If rule ends with ".*", also match the base topic root (e.g. "TRAVEL.*" matches "TRAVEL")
            if (rule.endsWith(".*")) {
                val base = rule.removeSuffix(".*")
                if (base.equals(candidate, ignoreCase = true)) {
                    return true
                }
            }

            return false
        }

        private fun evaluateInternal(
            policy: InformationBoundaryPolicy,
            queryTopic: String,
            contextAnnotation: TypedAnnotation?,
            timestamp: Long
        ): EvaluationResult {
            val trimmedQuery = queryTopic.trim()
            val effectiveTopic = if (trimmedQuery.isNotEmpty()) {
                trimmedQuery
            } else {
                contextAnnotation?.type?.trim().orEmpty()
            }

            // 1. Strict Privacy Level check: HUMAN_ONLY rejects all AI agent queries immediately
            if (policy.privacyLevel == PrivacyLevel.HUMAN_ONLY) {
                val record = PolicyAuditRecord(
                    timestamp = timestamp,
                    decision = EvaluationDecision.DENIED_RESTRICTED,
                    matchedRule = null,
                    reason = "Envelope privacy level is HUMAN_ONLY; AI agent access is strictly prohibited",
                    queryTopic = queryTopic,
                    contextAnnotation = contextAnnotation
                )
                return EvaluationResult(EvaluationDecision.DENIED_RESTRICTED, record)
            }

            // 2. Blank topic check
            if (effectiveTopic.isEmpty()) {
                val record = PolicyAuditRecord(
                    timestamp = timestamp,
                    decision = EvaluationDecision.DENIED_RESTRICTED,
                    matchedRule = null,
                    reason = "Query topic is blank and no valid annotation type provided",
                    queryTopic = queryTopic,
                    contextAnnotation = contextAnnotation
                )
                return EvaluationResult(EvaluationDecision.DENIED_RESTRICTED, record)
            }

            // 3. Progressive disclosure rules check
            val rules = policy.progressiveDisclosure
            if (rules == null) {
                val record = PolicyAuditRecord(
                    timestamp = timestamp,
                    decision = EvaluationDecision.DENIED_RESTRICTED,
                    matchedRule = null,
                    reason = "No progressive disclosure rules defined; topic '$effectiveTopic' is restricted",
                    queryTopic = queryTopic,
                    contextAnnotation = contextAnnotation
                )
                return EvaluationResult(EvaluationDecision.DENIED_RESTRICTED, record)
            }

            // 4. Escrow check on context annotation (if provided) to prevent leaking escrowed data
            if (contextAnnotation != null && contextAnnotation.type.isNotBlank()) {
                val annotationType = contextAnnotation.type.trim()
                val matchedAnnotationEscrow = rules.escrowedTopics.firstOrNull { matchesTopic(it, annotationType) }
                if (matchedAnnotationEscrow != null) {
                    val record = PolicyAuditRecord(
                        timestamp = timestamp,
                        decision = EvaluationDecision.ESCROW_REQUIRED,
                        matchedRule = matchedAnnotationEscrow,
                        reason = "Context annotation type '$annotationType' matches escrowed topic rule '$matchedAnnotationEscrow'; explicit human approval required",
                        queryTopic = queryTopic,
                        contextAnnotation = contextAnnotation
                    )
                    return EvaluationResult(EvaluationDecision.ESCROW_REQUIRED, record)
                }
            }

            // 5. Escrow check on the query topic
            val matchedEscrowRule = rules.escrowedTopics.firstOrNull { matchesTopic(it, effectiveTopic) }
            if (matchedEscrowRule != null) {
                val record = PolicyAuditRecord(
                    timestamp = timestamp,
                    decision = EvaluationDecision.ESCROW_REQUIRED,
                    matchedRule = matchedEscrowRule,
                    reason = "Topic '$effectiveTopic' matches escrowed topic rule '$matchedEscrowRule'; explicit human approval required before disclosure",
                    queryTopic = queryTopic,
                    contextAnnotation = contextAnnotation
                )
                return EvaluationResult(EvaluationDecision.ESCROW_REQUIRED, record)
            }

            // 6. Allowed topics check
            val matchedAllowedRule = rules.allowedTopics.firstOrNull { matchesTopic(it, effectiveTopic) }
            if (matchedAllowedRule != null) {
                if (!rules.allowDirectInference) {
                    val record = PolicyAuditRecord(
                        timestamp = timestamp,
                        decision = EvaluationDecision.DENIED_RESTRICTED,
                        matchedRule = matchedAllowedRule,
                        reason = "Topic '$effectiveTopic' matches allowed rule '$matchedAllowedRule', but direct inference is disabled by policy",
                        queryTopic = queryTopic,
                        contextAnnotation = contextAnnotation
                    )
                    return EvaluationResult(EvaluationDecision.DENIED_RESTRICTED, record)
                }

                val record = PolicyAuditRecord(
                    timestamp = timestamp,
                    decision = EvaluationDecision.PERMITTED_DIRECT,
                    matchedRule = matchedAllowedRule,
                    reason = "Topic '$effectiveTopic' matches allowed topic rule '$matchedAllowedRule' with direct inference permitted",
                    queryTopic = queryTopic,
                    contextAnnotation = contextAnnotation
                )
                return EvaluationResult(EvaluationDecision.PERMITTED_DIRECT, record)
            }

            // 7. Unlisted topic -> DENIED_RESTRICTED
            val record = PolicyAuditRecord(
                timestamp = timestamp,
                decision = EvaluationDecision.DENIED_RESTRICTED,
                matchedRule = null,
                reason = "Topic '$effectiveTopic' is neither in allowedTopics nor escrowedTopics; disclosure restricted",
                queryTopic = queryTopic,
                contextAnnotation = contextAnnotation
            )
            return EvaluationResult(EvaluationDecision.DENIED_RESTRICTED, record)
        }
    }
}
