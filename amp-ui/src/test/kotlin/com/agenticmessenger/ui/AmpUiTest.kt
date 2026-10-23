package com.agenticmessenger.ui

import com.agenticmessenger.client.AmpSession
import com.agenticmessenger.client.identity.ClientIdentity
import com.agenticmessenger.client.network.AmpConnectionState
import com.agenticmessenger.client.storage.SqliteStorage
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
import com.agenticmessenger.relay.configureRelayServer
import com.agenticmessenger.relay.queue.SqliteStoreAndForwardQueue
import com.agenticmessenger.relay.registry.KeyPackageRegistry
import com.agenticmessenger.relay.router.MailboxRouter
import com.agenticmessenger.relay.service.RelayService
import com.agenticmessenger.relay.storage.EncryptedBlobStorage
import com.agenticmessenger.ui.ai.OpenAiClient
import com.agenticmessenger.ui.ai.OpenAiConfig
import com.agenticmessenger.ui.ai.OpenAiMessage
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
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import java.nio.file.Files
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

    @Test
    fun testEndToEndConversationPresenterDualWindowMessaging() = runBlocking {
        val tempDir = Files.createTempDirectory("amp_ui_test")
        val port = ServerSocket(0).use { it.localPort }
        val dbFile = tempDir.resolve("test_ui_relay.db").toFile()
        val blobDir = tempDir.resolve("blobs").toFile()
        blobDir.mkdirs()

        val q = SqliteStoreAndForwardQueue("jdbc:sqlite:${dbFile.absolutePath}")
        val blobStorage = EncryptedBlobStorage(blobDir)
        val kr = KeyPackageRegistry("jdbc:sqlite:${dbFile.absolutePath}")
        val router = MailboxRouter()
        val relayService = RelayService(router, q)

        val server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            configureRelayServer(
                router = router,
                queue = q,
                blobStorage = blobStorage,
                keyRegistry = kr,
                relayService = relayService
            )
        }.start(wait = false)

        Thread.sleep(800)

        val wsUrl = "ws://127.0.0.1:$port/ws/relay"
        val testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        val aliceIdentity = ClientIdentity.generate("alice_test_dev", "did:aidos:alice")
        val bobIdentity = ClientIdentity.generate("bob_test_dev", "did:aidos:bob")

        val aliceStorage = SqliteStorage.inMemory()
        val bobStorage = SqliteStorage.inMemory()

        val aliceSession = AmpSession(identity = aliceIdentity, storage = aliceStorage, relayWsUrl = wsUrl)
        val bobSession = AmpSession(identity = bobIdentity, storage = bobStorage, relayWsUrl = wsUrl)

        aliceSession.start()
        bobSession.start()

        try {
            withTimeout(5000) {
                aliceSession.connectionState.first { it == AmpConnectionState.CONNECTED }
                bobSession.connectionState.first { it == AmpConnectionState.CONNECTED }
            }

            val bobContact = ContactUiModel(
                contactId = "bob",
                displayName = "Bob",
                handle = "did:aidos:bob",
                mailboxId = bobIdentity.mailboxToken,
                principalId = "did:aidos:bob",
                x25519PublicKeyHex = bobIdentity.x25519PublicKeyHex
            )

            val aliceContact = ContactUiModel(
                contactId = "alice",
                displayName = "Alice",
                handle = "did:aidos:alice",
                mailboxId = aliceIdentity.mailboxToken,
                principalId = "did:aidos:alice",
                x25519PublicKeyHex = aliceIdentity.x25519PublicKeyHex
            )

            val alicePresenter = ConversationPresenter(
                scope = testScope,
                currentUserId = aliceIdentity.principalId,
                currentDeviceKeyId = aliceIdentity.deviceId,
                initialContact = bobContact,
                session = aliceSession
            )

            val bobPresenter = ConversationPresenter(
                scope = testScope,
                currentUserId = bobIdentity.principalId,
                currentDeviceKeyId = bobIdentity.deviceId,
                initialContact = aliceContact,
                session = bobSession
            )

            // Alice sends message to Bob
            alicePresenter.onInputDraftChanged("Hi Bob, this is Alice via UI presenter!")
            alicePresenter.sendMessage()

            // Wait for Bob's presenter state to receive message
            withTimeout(5000) {
                while (bobPresenter.state.value.items.none { it is TimelineItemUiModel.Message && it.naturalText == "Hi Bob, this is Alice via UI presenter!" }) {
                    delay(50)
                }
            }

            val bobReceivedMsg = bobPresenter.state.value.items.first {
                it is TimelineItemUiModel.Message && it.naturalText == "Hi Bob, this is Alice via UI presenter!"
            } as TimelineItemUiModel.Message
            assertEquals(aliceIdentity.mailboxToken, bobReceivedMsg.sender.principalId)
            assertTrue(bobReceivedMsg.isDelivered)

            // Bob replies to Alice
            bobPresenter.onInputDraftChanged("Hello Alice, message verified!")
            bobPresenter.sendMessage()

            // Wait for Alice's presenter state to receive Bob's reply
            withTimeout(5000) {
                while (alicePresenter.state.value.items.none { it is TimelineItemUiModel.Message && it.naturalText == "Hello Alice, message verified!" }) {
                    delay(50)
                }
            }

            val aliceReceivedMsg = alicePresenter.state.value.items.first {
                it is TimelineItemUiModel.Message && it.naturalText == "Hello Alice, message verified!"
            } as TimelineItemUiModel.Message
            assertEquals(bobIdentity.mailboxToken, aliceReceivedMsg.sender.principalId)
        } finally {
            testScope.cancel()
            aliceSession.close()
            bobSession.close()
            server.stop(500, 1000)
            q.close()
            kr.close()
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun testConversationPresenterAiCopilotIntegration() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)

        val fakeAiClient = object : OpenAiClient() {
            override suspend fun chatCompletion(
                config: OpenAiConfig,
                messages: List<OpenAiMessage>
            ): Result<String> {
                return Result.success("Aidos response: 3:00 PM looks great!")
            }
        }

        val presenter = ConversationPresenter(
            scope = scope,
            currentUserId = "did:aidos:john",
            currentDeviceKeyId = "key_john_phone",
            openAiClient = fakeAiClient
        )

        // Directly ask AI copilot
        presenter.askAi("Summarize unread messages")
        testScheduler.advanceUntilIdle()

        val items = presenter.state.value.items
        assertTrue(items.isNotEmpty())
        val aiTurn = items.last() as TimelineItemUiModel.Message
        assertEquals(MeshProvenanceType.AGENT_RUNNER, aiTurn.provenanceType)
        assertEquals("did:aidos:john:aidos", aiTurn.sender.principalId)
        assertNotNull(aiTurn.localInferenceNote)
        assertEquals("Aidos response: 3:00 PM looks great!", aiTurn.naturalText)

        // Test @aidos trigger in sendMessage
        presenter.onInputDraftChanged("@aidos suggest a meeting slot")
        presenter.sendMessage()
        testScheduler.advanceUntilIdle()

        val updatedItems = presenter.state.value.items
        assertEquals(3, updatedItems.size) // 1 initial AI turn + 1 human @aidos turn + 1 AI response turn
        val humanTurn = updatedItems[1] as TimelineItemUiModel.Message
        assertEquals("@aidos suggest a meeting slot", humanTurn.naturalText)
        assertEquals(MeshProvenanceType.HUMAN_DIRECT, humanTurn.provenanceType)

        val triggeredAiTurn = updatedItems[2] as TimelineItemUiModel.Message
        assertEquals(MeshProvenanceType.AGENT_RUNNER, triggeredAiTurn.provenanceType)
        assertEquals("Aidos response: 3:00 PM looks great!", triggeredAiTurn.naturalText)
    }
}
