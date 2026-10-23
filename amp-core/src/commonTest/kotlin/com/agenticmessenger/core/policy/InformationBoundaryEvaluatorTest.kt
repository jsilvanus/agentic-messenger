package com.agenticmessenger.core.policy

import com.agenticmessenger.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class InformationBoundaryEvaluatorTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val basePolicy = InformationBoundaryPolicy(
        privacyLevel = PrivacyLevel.AGENT_ACCESSIBLE,
        progressiveDisclosure = ProgressiveDisclosureRules(
            allowDirectInference = true,
            allowedTopics = listOf("DELAY_REASON", "VEHICLE_ISSUE", "TRAVEL.*"),
            escrowedTopics = listOf("ESTIMATED_ARRIVAL_TIME", "LIVE_GPS", "TRAVEL.PASSENGER_IDENTITY")
        )
    )

    private fun sampleEnvelope(policy: InformationBoundaryPolicy): UniversalEnvelope {
        return UniversalEnvelope(
            envelopeId = "env_test_01",
            conversationId = "conv_test",
            epoch = 1L,
            timestamp = 1729600000000L,
            routing = RoutingHeader(
                sender = EndpointIdentifier("did:aidos:alice", EndpointType.HUMAN_DIRECT, "dev_alice_01"),
                recipients = listOf(RecipientTarget(mailboxId = "mb_bob_01"))
            ),
            provenance = ProvenanceRecord(
                assertionLevel = AssertionLevel.ORIGINAL_HUMAN,
                signerKeyId = "dev_alice_01",
                signatureHex = "abcdef123456"
            ),
            policy = policy,
            payload = EnvelopePayload(
                naturalLanguage = "Tire puncture on I-90. Running late.",
                structuredAnnotations = listOf(
                    TypedAnnotation(
                        type = "cause",
                        confidence = 0.95,
                        attributes = mapOf("reason" to "flat_tire")
                    )
                )
            )
        )
    }

    @Test
    fun testDirectDisclosureAllowedForAllowedTopics() {
        val evaluator = InformationBoundaryEvaluator()

        // Exact match
        val res1 = evaluator.evaluate(basePolicy, "DELAY_REASON")
        assertEquals(EvaluationDecision.PERMITTED_DIRECT, res1.decision)
        assertTrue(res1.isPermittedDirect)
        assertEquals("DELAY_REASON", res1.matchedRule)
        assertTrue(res1.reason.contains("direct inference permitted"))

        // Case insensitivity & trimming
        val res2 = evaluator.evaluate(basePolicy, "  vehicle_issue  ")
        assertEquals(EvaluationDecision.PERMITTED_DIRECT, res2.decision)
        assertEquals("VEHICLE_ISSUE", res2.matchedRule)
    }

    @Test
    fun testEscrowTriggerForEscrowedTopics() {
        val evaluator = InformationBoundaryEvaluator()

        // Topic in escrowed list
        val result = evaluator.evaluate(basePolicy, "ESTIMATED_ARRIVAL_TIME")
        assertEquals(EvaluationDecision.ESCROW_REQUIRED, result.decision)
        assertTrue(result.isEscrowRequired)
        assertEquals("ESTIMATED_ARRIVAL_TIME", result.matchedRule)
        assertTrue(result.reason.contains("explicit human approval required"))

        // Lowercase query
        val resultLower = evaluator.evaluate(basePolicy, "live_gps")
        assertEquals(EvaluationDecision.ESCROW_REQUIRED, resultLower.decision)
        assertEquals("LIVE_GPS", resultLower.matchedRule)
    }

    @Test
    fun testRejectionWhenPrivacyLevelIsHumanOnly() {
        val evaluator = InformationBoundaryEvaluator()
        val humanOnlyPolicy = basePolicy.copy(privacyLevel = PrivacyLevel.HUMAN_ONLY)

        // Even though DELAY_REASON is in allowedTopics, HUMAN_ONLY takes strict precedence
        val result = evaluator.evaluate(humanOnlyPolicy, "DELAY_REASON")
        assertEquals(EvaluationDecision.DENIED_RESTRICTED, result.decision)
        assertTrue(result.isDeniedRestricted)
        assertNull(result.matchedRule)
        assertTrue(result.reason.contains("HUMAN_ONLY"))
    }

    @Test
    fun testRejectionForUnlistedTopics() {
        val evaluator = InformationBoundaryEvaluator()

        val result = evaluator.evaluate(basePolicy, "CREDIT_CARD_NUMBER")
        assertEquals(EvaluationDecision.DENIED_RESTRICTED, result.decision)
        assertNull(result.matchedRule)
        assertTrue(result.reason.contains("neither in allowedTopics nor escrowedTopics"))
    }

    @Test
    fun testHierarchicalTopicMatching() {
        val evaluator = InformationBoundaryEvaluator()

        // Subtopic matches "TRAVEL.*"
        val resDelay = evaluator.evaluate(basePolicy, "TRAVEL.DELAY")
        assertEquals(EvaluationDecision.PERMITTED_DIRECT, resDelay.decision)
        assertEquals("TRAVEL.*", resDelay.matchedRule)

        val resEta = evaluator.evaluate(basePolicy, "travel.eta")
        assertEquals(EvaluationDecision.PERMITTED_DIRECT, resEta.decision)
        assertEquals("TRAVEL.*", resEta.matchedRule)

        val resMultiLevel = evaluator.evaluate(basePolicy, "TRAVEL.FLIGHT.GATE_CHANGE")
        assertEquals(EvaluationDecision.PERMITTED_DIRECT, resMultiLevel.decision)
        assertEquals("TRAVEL.*", resMultiLevel.matchedRule)

        // Root topic "TRAVEL" matches "TRAVEL.*"
        val resRoot = evaluator.evaluate(basePolicy, "TRAVEL")
        assertEquals(EvaluationDecision.PERMITTED_DIRECT, resRoot.decision)
        assertEquals("TRAVEL.*", resRoot.matchedRule)

        // Unrelated topic does not match
        val resOther = evaluator.evaluate(basePolicy, "FOOD.DINNER")
        assertEquals(EvaluationDecision.DENIED_RESTRICTED, resOther.decision)

        // Specific escrow rule takes priority over wildcard allow
        val resEscrowOverride = evaluator.evaluate(basePolicy, "TRAVEL.PASSENGER_IDENTITY")
        assertEquals(EvaluationDecision.ESCROW_REQUIRED, resEscrowOverride.decision)
        assertEquals("TRAVEL.PASSENGER_IDENTITY", resEscrowOverride.matchedRule)
    }

    @Test
    fun testDirectInferenceDisabledPolicy() {
        val evaluator = InformationBoundaryEvaluator()
        val disabledPolicy = basePolicy.copy(
            progressiveDisclosure = basePolicy.progressiveDisclosure?.copy(allowDirectInference = false)
        )

        val result = evaluator.evaluate(disabledPolicy, "DELAY_REASON")
        assertEquals(EvaluationDecision.DENIED_RESTRICTED, result.decision)
        assertEquals("DELAY_REASON", result.matchedRule)
        assertTrue(result.reason.contains("direct inference is disabled"))
    }

    @Test
    fun testNoProgressiveDisclosureRules() {
        val evaluator = InformationBoundaryEvaluator()
        val emptyPolicy = InformationBoundaryPolicy(
            privacyLevel = PrivacyLevel.AGENT_ACCESSIBLE,
            progressiveDisclosure = null
        )

        val result = evaluator.evaluate(emptyPolicy, "DELAY_REASON")
        assertEquals(EvaluationDecision.DENIED_RESTRICTED, result.decision)
        assertTrue(result.reason.contains("No progressive disclosure rules defined"))
    }

    @Test
    fun testBlankTopicHandlingAndAnnotationFallback() {
        val evaluator = InformationBoundaryEvaluator()

        // Completely blank query without annotation
        val resultBlank = evaluator.evaluate(basePolicy, "   ")
        assertEquals(EvaluationDecision.DENIED_RESTRICTED, resultBlank.decision)
        assertTrue(resultBlank.reason.contains("Query topic is blank"))

        // Blank query with valid annotation fallback
        val annotation = TypedAnnotation(type = "DELAY_REASON", confidence = 0.99)
        val resultFallback = evaluator.evaluate(basePolicy, "", contextAnnotation = annotation)
        assertEquals(EvaluationDecision.PERMITTED_DIRECT, resultFallback.decision)
        assertEquals("DELAY_REASON", resultFallback.matchedRule)
    }

    @Test
    fun testEscrowTriggerFromContextAnnotation() {
        val evaluator = InformationBoundaryEvaluator()

        // Agent requests allowed topic "DELAY_REASON", but context annotation is "LIVE_GPS" (escrowed)
        val escrowedAnnotation = TypedAnnotation(type = "LIVE_GPS", confidence = 0.99)
        val result = evaluator.evaluate(basePolicy, "DELAY_REASON", contextAnnotation = escrowedAnnotation)
        assertEquals(EvaluationDecision.ESCROW_REQUIRED, result.decision)
        assertEquals("LIVE_GPS", result.matchedRule)
        assertTrue(result.reason.contains("Context annotation type 'LIVE_GPS' matches escrowed topic rule"))
    }

    @Test
    fun testPolicyAuditLogGeneration() {
        val evaluator = InformationBoundaryEvaluator(recordAuditLog = true)
        assertEquals(0, evaluator.auditLog.size)

        val fixedTime = 1729650000000L
        val res1 = evaluator.evaluate(basePolicy, "DELAY_REASON", timestamp = fixedTime)
        assertEquals(1, evaluator.auditLog.size)
        assertEquals(EvaluationDecision.PERMITTED_DIRECT, evaluator.auditLog[0].decision)
        assertEquals("DELAY_REASON", evaluator.auditLog[0].matchedRule)
        assertEquals(fixedTime, evaluator.auditLog[0].timestamp)
        assertEquals("DELAY_REASON", evaluator.auditLog[0].queryTopic)

        evaluator.evaluate(basePolicy, "LIVE_GPS")
        evaluator.evaluate(basePolicy, "UNKNOWN_TOPIC")
        assertEquals(3, evaluator.auditLog.size)

        evaluator.clearAuditLog()
        assertEquals(0, evaluator.auditLog.size)
    }

    @Test
    fun testEvaluateUniversalEnvelopeOverload() {
        val envelope = sampleEnvelope(basePolicy)
        val result = InformationBoundaryEvaluator.evaluate(envelope, "TRAVEL.DELAY")
        assertEquals(EvaluationDecision.PERMITTED_DIRECT, result.decision)

        val decisionOnly = InformationBoundaryEvaluator.evaluateDecision(envelope, "LIVE_GPS")
        assertEquals(EvaluationDecision.ESCROW_REQUIRED, decisionOnly)
    }

    @Test
    fun testSerializationOfPolicyAuditTypes() {
        val record = PolicyAuditRecord(
            timestamp = 1729600000000L,
            decision = EvaluationDecision.PERMITTED_DIRECT,
            matchedRule = "TRAVEL.*",
            reason = "Allowed by policy",
            queryTopic = "TRAVEL.ETA"
        )
        val recordJson = json.encodeToString(record)
        val decodedRecord = json.decodeFromString<PolicyAuditRecord>(recordJson)
        assertEquals(record.timestamp, decodedRecord.timestamp)
        assertEquals(record.decision, decodedRecord.decision)
        assertEquals(record.matchedRule, decodedRecord.matchedRule)
        assertEquals(record.queryTopic, decodedRecord.queryTopic)

        val evalResult = EvaluationResult(EvaluationDecision.PERMITTED_DIRECT, record)
        val resultJson = json.encodeToString(evalResult)
        val decodedResult = json.decodeFromString<EvaluationResult>(resultJson)
        assertEquals(evalResult.decision, decodedResult.decision)
        assertEquals(evalResult.matchedRule, decodedResult.matchedRule)
    }
}
