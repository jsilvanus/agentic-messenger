package com.agenticmessenger.ui.molecular

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.agenticmessenger.ui.model.TimelineItemUiModel
import com.agenticmessenger.ui.model.toTcr
import dev.aidos.tcr.mesh.MessageBubble as TcrMessageBubble

/**
 * Message bubble (D46 amended): rendering lives in the TCR; this binds Amp's message model, which
 * carries amp-core envelope types, to the TCR's.
 */
@Composable
fun MessageBubble(
    message: TimelineItemUiModel.Message,
    isSelf: Boolean = false,
    modifier: Modifier = Modifier,
    onToggleAudio: () -> Unit = {},
    onToggleStt: (messageId: String) -> Unit = {},
    onUnshieldForAi: (messageId: String) -> Unit = {},
) {
    TcrMessageBubble(
        message = message.toTcr(),
        isSelf = isSelf,
        modifier = modifier,
        onToggleAudio = onToggleAudio,
        onToggleStt = onToggleStt,
        onUnshieldForAi = onUnshieldForAi,
    )
}
