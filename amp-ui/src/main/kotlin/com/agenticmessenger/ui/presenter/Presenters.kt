package com.agenticmessenger.ui.presenter

import com.agenticmessenger.core.crypto.BlindedMailbox
import com.agenticmessenger.core.model.AssertionLevel
import com.agenticmessenger.core.model.EndpointIdentifier
import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.core.model.ProvenanceRecord
import com.agenticmessenger.ui.model.ContactFilter
import com.agenticmessenger.ui.model.ContactUiModel
import com.agenticmessenger.ui.model.ContactsUiState
import com.agenticmessenger.ui.model.ConversationUiState
import com.agenticmessenger.ui.model.ConversationViewMode
import com.agenticmessenger.ui.model.DisclosureStatus
import com.agenticmessenger.ui.model.LoginUiState
import com.agenticmessenger.ui.model.MeshProvenanceType
import com.agenticmessenger.ui.model.PresenceState
import com.agenticmessenger.ui.model.TimelineItemUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presenter for Login & Mailbox initialization.
 */
class LoginPresenter(
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        computeMailbox()
    }

    fun onPrincipalIdChanged(principalId: String) {
        _state.update { it.copy(principalId = principalId) }
    }

    fun onDeviceKeyIdChanged(deviceKeyId: String) {
        _state.update { it.copy(deviceKeyId = deviceKeyId) }
        computeMailbox()
    }

    fun onEpochKeyHexChanged(epochKeyHex: String) {
        _state.update { it.copy(epochKeyHex = epochKeyHex) }
        computeMailbox()
    }

    fun onRelayEndpointChanged(endpoint: String) {
        _state.update { it.copy(relayEndpoint = endpoint) }
    }

    fun connectToRelay() {
        scope.launch {
            _state.update { it.copy(isConnecting = true, errorMessage = null) }
            try {
                // Simulate relay handshake
                _state.update { it.copy(isConnecting = false, isConnected = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isConnecting = false, errorMessage = e.message) }
            }
        }
    }

    private fun computeMailbox() {
        val s = _state.value
        try {
            val mailbox = BlindedMailbox.compute(s.epochKeyHex, s.deviceKeyId)
            _state.update { it.copy(calculatedMailboxId = mailbox) }
        } catch (_: Exception) {
            // Ignore temporary malformed hex while user types
        }
    }
}

/**
 * Presenter for Contacts Directory list.
 */
class ContactsPresenter(
    private val scope: CoroutineScope,
    initialContacts: List<ContactUiModel> = emptyList()
) {
    private val _state = MutableStateFlow(
        ContactsUiState(contacts = initialContacts)
    )
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun onFilterChanged(filter: ContactFilter) {
        _state.update { it.copy(activeFilter = filter) }
    }

    fun selectContact(contactId: String?) {
        _state.update { it.copy(selectedContactId = contactId) }
    }

    fun addContact(contact: ContactUiModel) {
        _state.update { it.copy(contacts = it.contacts + contact) }
    }

    fun filteredContacts(): List<ContactUiModel> {
        val s = _state.value
        return s.contacts.filter { contact ->
            val matchesQuery = s.searchQuery.isBlank() ||
                    contact.displayName.contains(s.searchQuery, ignoreCase = true) ||
                    contact.handle.contains(s.searchQuery, ignoreCase = true)

            val matchesFilter = when (s.activeFilter) {
                ContactFilter.ALL -> true
                ContactFilter.AIDOS_ACTIVE -> contact.isAidosActive
                ContactFilter.ACTION_REQUIRED -> contact.pendingDisclosureCount > 0
            }

            matchesQuery && matchesFilter
        }
    }
}

/**
 * Presenter for active Conversation thread.
 */
class ConversationPresenter(
    private val scope: CoroutineScope,
    private val currentUserId: String,
    private val currentDeviceKeyId: String,
    initialContact: ContactUiModel? = null,
    initialItems: List<TimelineItemUiModel> = emptyList()
) {
    private val _state = MutableStateFlow(
        ConversationUiState(
            contact = initialContact,
            items = initialItems
        )
    )
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    fun onViewModeChanged(mode: ConversationViewMode) {
        _state.update { it.copy(viewMode = mode) }
    }

    fun onInputDraftChanged(draft: String) {
        _state.update { it.copy(inputDraft = draft) }
    }

    fun toggleHumanOnlyMode() {
        _state.update { it.copy(isHumanOnlyE2EEMode = !it.isHumanOnlyE2EEMode) }
    }

    fun sendMessage() {
        val text = _state.value.inputDraft.trim()
        if (text.isEmpty()) return

        val isHumanOnly = _state.value.isHumanOnlyE2EEMode
        val now = System.currentTimeMillis()

        val newMessage = TimelineItemUiModel.Message(
            id = "msg_$now",
            timestamp = now,
            sender = EndpointIdentifier(
                principalId = currentUserId,
                endpointType = EndpointType.HUMAN_DIRECT,
                deviceKeyId = currentDeviceKeyId
            ),
            provenanceType = if (isHumanOnly) MeshProvenanceType.HUMAN_ONLY_E2EE else MeshProvenanceType.HUMAN_DIRECT,
            provenanceRecord = ProvenanceRecord(
                assertionLevel = AssertionLevel.ORIGINAL_HUMAN,
                signerKeyId = currentDeviceKeyId,
                signatureHex = "mock_sig_hex"
            ),
            privacyLevel = if (isHumanOnly) PrivacyLevel.HUMAN_ONLY else PrivacyLevel.AGENT_ACCESSIBLE,
            naturalText = text,
            cueWords = if (isHumanOnly) emptyList() else listOf(text.take(15))
        )

        _state.update {
            it.copy(
                items = it.items + newMessage,
                inputDraft = ""
            )
        }
    }

    fun approveDisclosure(requestId: String) {
        _state.update { s ->
            val updated = s.items.map { item ->
                if (item is TimelineItemUiModel.ProgressiveDisclosureRequest && item.requestId == requestId) {
                    item.copy(status = DisclosureStatus.APPROVED)
                } else item
            }
            s.copy(items = updated)
        }
    }

    fun declineDisclosure(requestId: String) {
        _state.update { s ->
            val updated = s.items.map { item ->
                if (item is TimelineItemUiModel.ProgressiveDisclosureRequest && item.requestId == requestId) {
                    item.copy(status = DisclosureStatus.DECLINED)
                } else item
            }
            s.copy(items = updated)
        }
    }

    fun confirmCalendarAction(proposalId: String) {
        _state.update { s ->
            val updated = s.items.map { item ->
                if (item is TimelineItemUiModel.CalendarProposal && item.id == proposalId) {
                    item.copy(isActionTaken = true)
                } else item
            }
            s.copy(items = updated)
        }
    }
}
