package com.agenticmessenger.ui.atomic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.model.MeshProvenanceType
import com.agenticmessenger.ui.theme.LocalProvenanceColors

/**
 * Atomic component: ProvenanceBadge
 *
 * Distinct visual provenance badges for the 4-party conversation mesh:
 * - 👤 Human Direct
 * - 🤖 Delegated Runner / Aidos Assistant
 * - 🔒 Human-Only E2EE
 * - ⚙️ System Service
 *
 * Can be rendered standalone in chat streams or embedded inside Aidos Desktop
 * Generative UI cards to display execution origin and trust level.
 */
@Composable
fun ProvenanceBadge(
    provenanceType: MeshProvenanceType,
    modifier: Modifier = Modifier,
    label: String? = null,
    signerKeySnippet: String? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalProvenanceColors.current

    val (bg, fg, defaultIcon, defaultText) = when (provenanceType) {
        MeshProvenanceType.HUMAN_DIRECT -> Quadruple(
            colors.humanDirectBackground,
            colors.humanDirectContent,
            "👤",
            "Human Direct"
        )
        MeshProvenanceType.AGENT_RUNNER -> Quadruple(
            colors.agentRunnerBackground,
            colors.agentRunnerContent,
            "🤖",
            "Delegated Runner"
        )
        MeshProvenanceType.HUMAN_ONLY_E2EE -> Quadruple(
            colors.humanOnlyBackground,
            colors.humanOnlyContent,
            "🔒",
            "Human-Only E2EE"
        )
        MeshProvenanceType.SYSTEM_SERVICE -> Quadruple(
            colors.systemServiceBackground,
            colors.systemServiceContent,
            "⚙️",
            "System Service"
        )
    }

    val displayText = label ?: defaultText

    val clickableModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Box(
        modifier = clickableModifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = fg.copy(alpha = 0.35f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = defaultIcon,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = displayText,
                color = fg,
                style = MaterialTheme.typography.labelSmall
            )
            if (signerKeySnippet != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "($signerKeySnippet)",
                    color = fg.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
