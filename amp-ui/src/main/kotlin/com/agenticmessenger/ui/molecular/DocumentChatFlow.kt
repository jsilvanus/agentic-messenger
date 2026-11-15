package com.agenticmessenger.ui.molecular

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.agenticmessenger.ui.model.ConversationViewMode
import com.agenticmessenger.ui.model.TimelineItemUiModel
import com.agenticmessenger.ui.model.toTcr
import com.agenticmessenger.ui.presenter.ConversationPresenter
import dev.aidos.tcr.document.DocumentChatFlow as TcrDocumentChatFlow

/**
 * Document-style conversation flow (D46 amended): rendering, the caret and the Red Pill drawer live
 * in the TCR. This binds Amp's timeline (which carries amp-core envelope types) and its presenter
 * to the TCR component.
 */
@Composable
fun DocumentChatFlow(
    items: List<TimelineItemUiModel>,
    currentUserId: String,
    inputDraft: String,
    onInputDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    viewMode: ConversationViewMode,
    listState: LazyListState,
    presenter: ConversationPresenter,
    modifier: Modifier = Modifier,
    onOrchestratorCommand: (command: String, targetMessageId: String?) -> Unit = { _, _ -> },
) {
    val tcrItems = remember(items) { items.map { it.toTcr() } }
    TcrDocumentChatFlow(
        items = tcrItems,
        currentUserId = currentUserId,
        inputDraft = inputDraft,
        onInputDraftChanged = onInputDraftChanged,
        onSend = onSend,
        viewMode = viewMode,
        listState = listState,
        modifier = modifier,
        onOrchestratorCommand = onOrchestratorCommand,
        onApproveDisclosure = { presenter.approveDisclosure(it) },
        onDeclineDisclosure = { presenter.declineDisclosure(it) },
        onConfirmCalendarAction = { presenter.confirmCalendarAction(it) },
        onToggleSttVisibility = { presenter.toggleSttVisibility(it) },
        onUnshieldMessageForAi = { presenter.unshieldMessageForAi(it) },
    )
}
