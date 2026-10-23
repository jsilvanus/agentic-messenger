package com.agenticmessenger.ui

import com.agenticmessenger.core.model.AssertionLevel
import com.agenticmessenger.core.model.EndpointIdentifier
import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.core.model.EnvelopePayload
import com.agenticmessenger.core.model.InformationBoundaryPolicy
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.core.model.ProvenanceRecord
import com.agenticmessenger.core.model.RoutingHeader
import com.agenticmessenger.core.model.TypedAnnotation
import com.agenticmessenger.core.model.UniversalEnvelope
import com.agenticmessenger.ui.generative.GenerativeUiRegistry
import com.agenticmessenger.ui.model.ContactFilter
import com.agenticmessenger.ui.model.ContactUiModel
import com.agenticmessenger.ui.model.ConversationViewMode
import com.agenticmessenger.ui.model.DisclosureStatus
import com.agenticmessenger.ui.model.MeshProvenanceType
import com.agenticmessenger.ui.model.PresenceState
import com.agenticmessenger.ui.model.TimelineItemUiModel
import com.agenticmessenger.ui.presenter.ContactsPresenter
import com.agenticmessenger.ui.presenter.ConversationPresenter
import com.agenticmessenger.ui.presenter.LoginPresenter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AmpUiTest {

    @Test
    fun testLoginPresenterMailboxCalculation() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val presenter = LoginPresenter(scope)
        val initialMailbox = presenter.state.value.calculatedMailboxId
        assertTrue(initialMailbox.isNotEmpty(), "Mailbox ID should be computed on init")

        // Changing device key ID updates mailbox
        presenter.onDeviceKeyIdChanged("key_new_device_99")
        val updatedMailbox = presenter.state.value.calculatedMailboxId
        assertTrue(updatedMailbox.isNotEmpty())
        assertTrue(initialMailbox != updatedMailbox, "Mailbox ID should change with different device ID")

        // Test relay connection
        assertFalse(presenter.state.value.isConnected)
        presenter.connectToRelay()
        testScheduler.advanceUntilIdle()
        assertTrue(presenter.state.value.isConnected)
    }

    @Test
    fun testContactsPresenterFiltering() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val contacts = listOf(
            ContactUiModel(
                contactId = "c1",
                displayName = "Jack",
                handle = "did:aidos:jack",
                mailboxId = "mb_jack",
                presence = PresenceState.ONLINE,
                isAidosActive = true,
                pendingDisclosureCount = 1,
                latestCueBadge = "[Late: Car breakdown]"
            ),
            ContactUiModel(
                contactId = "c2",
                displayName = "Alice",
                handle = "did:aidos:alice",
                mailboxId = "mb_alice",
                presence = PresenceState.OFFLINE,
                isAidosActive = false,
                pendingDisclosureCount = 0
            ),
            ContactUiModel(
                contactId = "c3",
                displayName = "Bob",
                handle = "did:aidos:bob",
                mailboxId = "mb_bob",
                presence = PresenceState.BUSY,
                isAidosActive = true,
                pendingDisclosureCount = 0
            )
        )

        val presenter = ContactsPresenter(scope, initialContacts = contacts)

        // All contacts
        assertEquals(3, presenter.filteredContacts().size)

        // Search filter
        presenter.onSearchQueryChanged("jack")
        assertEquals(1, presenter.filteredContacts().size)
        assertEquals("Jack", presenter.filteredContacts().first().displayName)

        presenter.onSearchQueryChanged("")

        // Aidos active filter
        presenter.onFilterChanged(ContactFilter.AIDOS_ACTIVE)
        assertEquals(2, presenter.filteredContacts().size)

        // Action required filter
        presenter.onFilterChanged(ContactFilter.ACTION_REQUIRED)
        assertEquals(1, presenter.filteredContacts().size)
        assertEquals("Jack", presenter.filteredContacts().first().displayName)
    }

    @Test
    fun testConversationPresenterDualViewAndHumanOnlyMessaging() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val presenter = ConversationPresenter(
            scope = scope,
            currentUserId = "did:aidos:john",
            currentDeviceKeyId = "key_john_phone"
        )

        // Dual view toggle
        assertEquals(ConversationViewMode.NATURAL_STREAM, presenter.state.value.viewMode)
        presenter.onViewModeChanged(ConversationViewMode.CUE_WORDS_TICKER)
        assertEquals(ConversationViewMode.CUE_WORDS_TICKER, presenter.state.value.viewMode)

        // Send normal message
        presenter.onInputDraftChanged("Hey Jack, how is it going?")
        presenter.sendMessage()

        val items = presenter.state.value.items
        assertEquals(1, items.size)
        val normalMsg = items.first() as TimelineItemUiModel.Message
        assertEquals("Hey Jack, how is it going?", normalMsg.naturalText)
        assertEquals(MeshProvenanceType.HUMAN_DIRECT, normalMsg.provenanceType)
        assertEquals(PrivacyLevel.AGENT_ACCESSIBLE, normalMsg.privacyLevel)

        // Send Human-Only sealed message
        presenter.toggleHumanOnlyMode()
        assertTrue(presenter.state.value.isHumanOnlyE2EEMode)

        presenter.onInputDraftChanged("Secret gate passcode: 1234#")
        presenter.sendMessage()

        assertEquals(2, presenter.state.value.items.size)
        val secretMsg = presenter.state.value.items.last() as TimelineItemUiModel.Message
        assertEquals("Secret gate passcode: 1234#", secretMsg.naturalText)
        assertEquals(MeshProvenanceType.HUMAN_ONLY_E2EE, secretMsg.provenanceType)
        assertEquals(PrivacyLevel.HUMAN_ONLY, secretMsg.privacyLevel)
        assertTrue(secretMsg.cueWords.isEmpty(), "Human-only messages must not generate cue tags for AI")
    }

    @Test
    fun testProgressiveDisclosureAndCalendarActions() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val req = TimelineItemUiModel.ProgressiveDisclosureRequest(
            id = "req_1",
            timestamp = 1000L,
            requestId = "req_eta",
            requester = EndpointIdentifier("did:aidos:jack", EndpointType.AGENT_DELEGATE, "key_jack_agent"),
            topicKey = "ESTIMATED_ARRIVAL_TIME",
            promptText = "Share your navigation ETA (~35 min)?",
            status = DisclosureStatus.PENDING
        )

        val cal = TimelineItemUiModel.CalendarProposal(
            id = "cal_1",
            timestamp = 2000L,
            eventTitle = "Sync with Jack",
            proposedTime = "5:00 PM",
            conflictSummary = "Dinner at 6:30 PM is free",
            isActionTaken = false
        )

        val presenter = ConversationPresenter(
            scope = scope,
            currentUserId = "did:aidos:john",
            currentDeviceKeyId = "key_john_phone",
            initialItems = listOf(req, cal)
        )

        // Approve disclosure
        presenter.approveDisclosure("req_eta")
        val updatedReq = presenter.state.value.items.first() as TimelineItemUiModel.ProgressiveDisclosureRequest
        assertEquals(DisclosureStatus.APPROVED, updatedReq.status)

        // Confirm calendar
        presenter.confirmCalendarAction("cal_1")
        val updatedCal = presenter.state.value.items.last() as TimelineItemUiModel.CalendarProposal
        assertTrue(updatedCal.isActionTaken)
    }

    @Test
    fun testGenerativeUiRegistryEnvelopeMapping() {
        // 1. Local Progressive Disclosure envelope
        val envelopeLocalInference = UniversalEnvelope(
            envelopeId = "env_local_1",
            conversationId = "conv_1",
            epoch = 1L,
            timestamp = 5000L,
            routing = RoutingHeader(
                sender = EndpointIdentifier("did:aidos:jack", EndpointType.AGENT_DELEGATE, "key_aidos"),
                recipients = emptyList()
            ),
            provenance = ProvenanceRecord(
                assertionLevel = AssertionLevel.AGENT_SYNTHESIZED,
                signerKeyId = "key_aidos",
                signatureHex = "00aa"
            ),
            policy = InformationBoundaryPolicy(privacyLevel = PrivacyLevel.AGENT_ACCESSIBLE),
            payload = EnvelopePayload(
                naturalLanguage = "His car broke down.",
                structuredAnnotations = listOf(
                    TypedAnnotation(type = "local_inference", confidence = 1.0),
                    TypedAnnotation(type = "cause", confidence = 0.95, attributes = mapOf("cue" to "Breakdown"))
                )
            )
        )

        val item1 = GenerativeUiRegistry.mapEnvelopeToTimelineItem(envelopeLocalInference)
        assertTrue(item1 is TimelineItemUiModel.Message)
        assertEquals(MeshProvenanceType.AGENT_RUNNER, item1.provenanceType)
        assertNotNull(item1.localInferenceNote)
        assertTrue(item1.cueWords.contains("Breakdown"))

        // 2. Escalation / Progressive Disclosure Request
        val envelopeEscalation = UniversalEnvelope(
            envelopeId = "env_esc_1",
            conversationId = "conv_1",
            epoch = 1L,
            timestamp = 6000L,
            routing = RoutingHeader(
                sender = EndpointIdentifier("did:aidos:john", EndpointType.AGENT_DELEGATE, "key_john_aidos"),
                recipients = emptyList()
            ),
            provenance = ProvenanceRecord(
                assertionLevel = AssertionLevel.AGENT_SYNTHESIZED,
                signerKeyId = "key_john_aidos",
                signatureHex = "00bb"
            ),
            policy = InformationBoundaryPolicy(privacyLevel = PrivacyLevel.AGENT_ACCESSIBLE),
            payload = EnvelopePayload(
                naturalLanguage = "John's assistant is requesting your Estimated Arrival Time.",
                structuredAnnotations = listOf(
                    TypedAnnotation(
                        type = "escalation_request",
                        confidence = 1.0,
                        attributes = mapOf(
                            "request_id" to "req_eta_99",
                            "topic" to "ESTIMATED_ARRIVAL_TIME",
                            "preview_value" to "~35 min"
                        )
                    )
                )
            )
        )

        val item2 = GenerativeUiRegistry.mapEnvelopeToTimelineItem(envelopeEscalation)
        assertTrue(item2 is TimelineItemUiModel.ProgressiveDisclosureRequest)
        assertEquals("req_eta_99", item2.requestId)
        assertEquals("ESTIMATED_ARRIVAL_TIME", item2.topicKey)
        assertEquals("~35 min", item2.proposedPayloadSnippet)

        // 3. Human-Only Sealed Envelope
        val envelopeHumanOnly = UniversalEnvelope(
            envelopeId = "env_ho_1",
            conversationId = "conv_1",
            epoch = 1L,
            timestamp = 7000L,
            routing = RoutingHeader(
                sender = EndpointIdentifier("did:aidos:jack", EndpointType.HUMAN_DIRECT, "key_jack_hw"),
                recipients = emptyList()
            ),
            provenance = ProvenanceRecord(
                assertionLevel = AssertionLevel.ORIGINAL_HUMAN,
                signerKeyId = "key_jack_hw",
                signatureHex = "00cc"
            ),
            policy = InformationBoundaryPolicy(privacyLevel = PrivacyLevel.HUMAN_ONLY),
            payload = EnvelopePayload(
                naturalLanguage = "Secret door code: 9912#"
            )
        )

        val item3 = GenerativeUiRegistry.mapEnvelopeToTimelineItem(envelopeHumanOnly)
        assertTrue(item3 is TimelineItemUiModel.Message)
        assertEquals(MeshProvenanceType.HUMAN_ONLY_E2EE, item3.provenanceType)
        assertEquals(PrivacyLevel.HUMAN_ONLY, item3.privacyLevel)
    }
}
