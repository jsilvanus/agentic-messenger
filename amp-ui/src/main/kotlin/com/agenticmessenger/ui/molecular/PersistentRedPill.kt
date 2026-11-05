package com.agenticmessenger.ui.molecular

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.agent.AmpAgentService
import com.agenticmessenger.agent.ContactDescriptor
import com.agenticmessenger.ui.ai.OpenAiConfig
import com.agenticmessenger.ui.model.ContactUiModel
import dev.aidos.amp.AmpAgentTurnResult
import dev.aidos.amp.AmpConversationTurn
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Text display UI modes supported by the Red Pill.
 */
enum class RedPillTextMode {
    /** Basic compact pill at rest. */
    NONE,

    /** Mode a: Warm beige-ish thought bubble with dashed outline anchored to the Red Pill. */
    THOUGHT_BUBBLE,

    /** Mode b: Warm beige-ish question bubble (pill turns to question symbol '?') with interactive input box. */
    QUESTION_BUBBLE,

    /** Mode c: Full blown conversation history HUD and entry box. */
    CONVERSATION_HISTORY
}

/**
 * Symbol shapes for the small indicator dot (pip) in the Red Pill.
 */
enum class PipSymbolShape {
    CIRCLE,
    DIAMOND,
    STAR,
    QUESTION,
    CHECK,
    MIC,
    CLOCK
}

/**
 * 3 distinct animation styles for how the Orchestrator (Red Pill) projects a new pill into existence.
 */
enum class PillProjectionStyle {
    /** Radiant laser conduit that energizes and snaps target pill into existence. */
    BEAM_TELEPORT,

    /** Elastic budding from orchestrator body, slingshot ejection, and damped bounce. */
    MITOSIS_EJECT,

    /** Expanding quantum wireframe reticle and holographic scan-lines condensing into the pill. */
    HOLOGRAPHIC_MATERIALIZE
}

/**
 * Custom fast-go, fast-stop easing curve:
 * Covers ~85% of transit distance in first ~30% time with immense acceleration,
 * followed by velocity cruise and an immediate high-damping tactile snap stop.
 */
val FastGoFastStopEasing = CubicBezierEasing(0.05f, 0.95f, 0.10f, 1.0f)

/**
 * Active projected worker pill spawned by the Red Pill Orchestrator.
 */
data class ProjectedPillInstance(
    val id: String,
    val name: String,
    val roleColor: Color,
    val targetOffset: Offset,
    val style: PillProjectionStyle
)

/**
 * Target document object highlighted by the volumetric projection cone.
 */
data class TargetConeHighlight(
    val label: String,
    val targetOffset: Offset,
    val targetSize: Size,
    val color: Color = Color(0xFFF97316) // Warm orange-ish default
)

/**
 * Recorded multi-turn interaction entry for Mode c (Conversation History).
 */
data class RedPillHistoryEntry(
    val id: String,
    val speaker: String,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isFromUser: Boolean = false,
    val executedTools: List<String> = emptyList()
)

/**
 * Molecular component inspired by Dictator's Ember Red Pill (0xFFEF4444):
 * A persistent interactive capsule representing the User-Level AI Orchestrator.
 *
 * Enhanced Capabilities:
 * - Orchestrator Pill Projection: 3 animations to project new pills into existence
 *     1. Beam-Teleport (laser conduit & photon collapse)
 *     2. Mitosis / Fission Ejection (elastic budding & slingshot)
 *     3. Holographic Materialization (cybernetic reticle & scanlines)
 * - Autonomous Fast-Go / Fast-Stop relocation across the workspace
 * - Volumetric Orange-ish Projection Cone highlighting target document objects (words, paraphrases)
 * - 3 basic text display UI modes (Thought bubble, Question bubble, Conversation history)
 * - Symbol-information morphing indicator pip (idle, listening, thinking, question, done)
 * - Hover physics with dynamic elevation, dual-tone shadows, and ground cast shadow
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersistentRedPill(
    aiConfig: OpenAiConfig,
    contacts: List<ContactUiModel>,
    onSendMessageToContact: suspend (contact: ContactDescriptor, text: String, ghostwrite: Boolean) -> Boolean,
    onFetchRecentMessages: suspend (mailboxId: String, limit: Int) -> List<AmpConversationTurn>,
    modifier: Modifier = Modifier,
    initialMode: RedPillTextMode = RedPillTextMode.NONE,
    ambientThoughtText: String = "Orchestrator monitoring contacts & pending actions...",
    pendingQuestionPrompt: String = "Would you like me to draft a follow-up reply?",
) {
    val scope = rememberCoroutineScope()
    var currentTextMode by remember { mutableStateOf(initialMode) }
    var userPromptInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var latestResult by remember { mutableStateOf<AmpAgentTurnResult?>(null) }
    var executionError by remember { mutableStateOf<String?>(null) }

    // Active pill projection & target cone highlight states
    var activeProjection by remember { mutableStateOf<ProjectedPillInstance?>(null) }
    var activeTargetCone by remember { mutableStateOf<TargetConeHighlight?>(null) }
    val projectedPills = remember { mutableStateListOf<ProjectedPillInstance>() }

    // Autonomous fast-go, fast-stop relocation offset
    var isSnapMoved by remember { mutableStateOf(false) }
    val autonomousOffsetX = remember { Animatable(0f) }

    fun triggerAutonomousSnapRelocation() {
        scope.launch {
            val targetX = if (isSnapMoved) 0f else 60f
            isSnapMoved = !isSnapMoved
            autonomousOffsetX.animateTo(
                targetValue = targetX,
                animationSpec = tween(durationMillis = 320, easing = FastGoFastStopEasing)
            )
        }
    }

    fun projectNewPill(style: PillProjectionStyle) {
        val (name, color, offset) = when (style) {
            PillProjectionStyle.BEAM_TELEPORT -> Triple("Editor", Color(0xFF3B82F6), Offset(240f, 60f))
            PillProjectionStyle.MITOSIS_EJECT -> Triple("AI Gen", Color(0xFF10B981), Offset(180f, 75f))
            PillProjectionStyle.HOLOGRAPHIC_MATERIALIZE -> Triple("Analyst", Color(0xFF8B5CF6), Offset(300f, 70f))
        }
        val instance = ProjectedPillInstance(
            id = "proj_${System.currentTimeMillis()}",
            name = name,
            roleColor = color,
            targetOffset = offset,
            style = style
        )
        activeProjection = instance
    }

    // Multi-turn conversation history
    val historyEntries = remember {
        mutableStateListOf(
            RedPillHistoryEntry(
                id = "h0",
                speaker = "Aidos",
                text = "Co-pilot online. Ready for cross-chat instructions.",
                isFromUser = false
            )
        )
    }

    // Hover Float Oscillation
    val infiniteTransition = rememberInfiniteTransition(label = "redPillHover")
    val hoverOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hoverOffset"
    )

    // Pulsing animation for the indicator pip
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Symbol-Information UI state resolution
    val (pipColor, pipShape) = when {
        isExecuting -> Color(0xFFA855F7) to PipSymbolShape.STAR // Thinking / reasoning violet
        currentTextMode == RedPillTextMode.QUESTION_BUBBLE -> Color(0xFFFBBF24) to PipSymbolShape.QUESTION // Question gold
        userPromptInput.isNotBlank() -> Color(0xFFEF4444) to PipSymbolShape.MIC // Listening / user typing red
        latestResult != null && !isExecuting -> Color(0xFF10B981) to PipSymbolShape.CHECK // Done emerald
        else -> Color(0xFFFF8A80) to PipSymbolShape.CIRCLE // Ambient resting rose
    }

    // Dynamic elevation that rises as the pill hovers
    val dynamicElevation = (6.dp + (-hoverOffset * 1.2f).dp)

    // Wire orchestrator service with cross-chat callbacks
    val orchestratorService = remember(contacts) {
        AmpAgentService(
            contactLookup = { query ->
                contacts.firstOrNull {
                    it.displayName.contains(query, ignoreCase = true) ||
                            it.handle.contains(query, ignoreCase = true) ||
                            it.principalId.contains(query, ignoreCase = true)
                }?.let {
                    ContactDescriptor(
                        id = it.contactId,
                        displayName = it.displayName,
                        mailboxId = it.mailboxId,
                        principalId = it.principalId,
                        x25519PublicKeyHex = it.x25519PublicKeyHex
                    )
                }
            },
            messageFetcher = onFetchRecentMessages,
            messageSender = onSendMessageToContact,
        )
    }

    fun executeOrchestrator(prompt: String) {
        val q = prompt.trim()
        if (q.isBlank() || isExecuting) return

        // Record user turn in conversation history
        historyEntries.add(
            RedPillHistoryEntry(
                id = "u_${System.currentTimeMillis()}",
                speaker = "You",
                text = q,
                isFromUser = true
            )
        )

        scope.launch {
            isExecuting = true
            executionError = null
            latestResult = null
            userPromptInput = ""

            val res = orchestratorService.processTurn(
                incomingMessage = q,
                baseUrl = aiConfig.baseUrl,
                model = aiConfig.model,
                apiKey = aiConfig.apiKey
            )

            res.fold(
                onSuccess = { turnResult ->
                    latestResult = turnResult
                    historyEntries.add(
                        RedPillHistoryEntry(
                            id = "a_${System.currentTimeMillis()}",
                            speaker = "Aidos",
                            text = turnResult.replyText,
                            isFromUser = false,
                            executedTools = turnResult.executedToolCalls.map { it.toolName }
                        )
                    )
                },
                onFailure = { err ->
                    val errMsg = err.message ?: "Orchestrator error"
                    executionError = errMsg
                    historyEntries.add(
                        RedPillHistoryEntry(
                            id = "err_${System.currentTimeMillis()}",
                            speaker = "System",
                            text = "Error: $errMsg",
                            isFromUser = false
                        )
                    )
                }
            )
            isExecuting = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Volumetric Orange-ish Target Projection Cone
        activeTargetCone?.let { cone ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val srcX = size.width / 2f
                    val srcY = 0f
                    val tgtLeft = cone.targetOffset.x
                    val tgtRight = tgtLeft + cone.targetSize.width
                    val tgtTop = cone.targetOffset.y
                    val tgtHeight = cone.targetSize.height

                    val conePath = Path().apply {
                        moveTo(srcX, srcY)
                        lineTo(tgtLeft, tgtTop)
                        lineTo(tgtRight, tgtTop)
                        close()
                    }

                    // Volumetric gradient
                    drawPath(
                        path = conePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                cone.color.copy(alpha = 0.55f),
                                cone.color.copy(alpha = 0.22f),
                                cone.color.copy(alpha = 0.08f)
                            ),
                            startY = srcY,
                            endY = tgtTop
                        )
                    )

                    // Target Highlight Box
                    drawRoundRect(
                        color = cone.color.copy(alpha = 0.22f),
                        topLeft = Offset(tgtLeft, tgtTop),
                        size = Size(cone.targetSize.width, tgtHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawRoundRect(
                        color = cone.color,
                        topLeft = Offset(tgtLeft, tgtTop),
                        size = Size(cone.targetSize.width, tgtHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = 1.25.dp.toPx())
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = cone.color,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = cone.targetOffset.x.roundToInt(),
                                y = (cone.targetOffset.y - 18.dp.toPx()).roundToInt().coerceAtLeast(0)
                            )
                        }
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Target: ${cone.label}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Active Spawning Animation Overlay (Beam, Mitosis, or Holographic)
        activeProjection?.let { proj ->
            val animProgress = remember(proj.id) { Animatable(0f) }
            LaunchedEffect(proj.id) {
                animProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 650, easing = FastGoFastStopEasing)
                )
                projectedPills.add(proj)
                activeProjection = null
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                val p = animProgress.value
                val dst = proj.targetOffset

                when (proj.style) {
                    PillProjectionStyle.BEAM_TELEPORT -> {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val src = Offset(size.width / 2f, 0f)
                            val beamLen = (p / 0.85f).coerceIn(0f, 1f)
                            val end = Offset(src.x + (dst.x - src.x) * beamLen, src.y + (dst.y - src.y) * beamLen)

                            // Radiant Conduit
                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFEF4444), proj.roleColor),
                                    start = src,
                                    end = end
                                ),
                                start = src,
                                end = end,
                                strokeWidth = 5.dp.toPx() * (1f - p * 0.4f),
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color.White,
                                start = src,
                                end = end,
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Terminal Photon Condensation Ring
                            if (p > 0.4f) {
                                val ringP = (p - 0.4f) / 0.6f
                                val r = 24.dp.toPx() * (1f - ringP)
                                drawCircle(
                                    color = proj.roleColor.copy(alpha = 1f - ringP),
                                    radius = r,
                                    center = dst,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                    PillProjectionStyle.MITOSIS_EJECT -> {
                        val srcX = 140f
                        val srcY = 0f
                        val curX = srcX + (dst.x - srcX) * p
                        val curY = srcY + (dst.y - srcY) * p
                        val stretch = 1f + sin(p * Math.PI.toFloat()) * 0.35f

                        if (p < 0.4f) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawLine(
                                    color = Color(0xFFEF4444).copy(alpha = 1f - p / 0.4f),
                                    start = Offset(srcX, srcY),
                                    end = Offset(curX, curY),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(curX.roundToInt(), curY.roundToInt()) }
                                .scale(scaleX = stretch, scaleY = 1f / stretch)
                                .size(24.dp, 14.dp)
                                .background(proj.roleColor, shape = CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.6f), shape = CircleShape)
                        )
                    }
                    PillProjectionStyle.HOLOGRAPHIC_MATERIALIZE -> {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val src = Offset(size.width / 2f, 0f)
                            // Hologram laser sweep
                            drawLine(
                                color = proj.roleColor.copy(alpha = 0.35f),
                                start = src,
                                end = dst,
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            )
                            // Reticle
                            drawCircle(
                                color = proj.roleColor.copy(alpha = p * 0.7f),
                                radius = 18.dp.toPx() * (1f - p * 0.2f),
                                center = dst,
                                style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                            )
                            drawRoundRect(
                                color = proj.roleColor.copy(alpha = p),
                                topLeft = Offset(dst.x - 12.dp.toPx(), dst.y - 7.dp.toPx()),
                                size = Size(24.dp.toPx(), 14.dp.toPx()),
                                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                            )
                        }
                    }
                }
            }
        }

        // Mode a) Beige-ish Thought Bubble (Dashed outline)
        AnimatedVisibility(
            visible = currentTextMode == RedPillTextMode.THOUGHT_BUBBLE,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFBF8F2),
                    modifier = Modifier
                        .shadow(elevation = 3.dp, shape = RoundedCornerShape(14.dp))
                        .drawBehind {
                            val stroke = Stroke(
                                width = 1.25.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
                            )
                            drawRoundRect(
                                color = Color(0xFFCBBFAF),
                                size = size,
                                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                                style = stroke
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clickable { currentTextMode = RedPillTextMode.NONE }
                ) {
                    Text(
                        text = "💭 $ambientThoughtText",
                        color = Color(0xFF3E3832),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(Color(0xFFFBF8F2), shape = CircleShape)
                        .border(0.75.dp, Color(0xFFCBBFAF), shape = CircleShape)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .background(Color(0xFFFBF8F2), shape = CircleShape)
                        .border(0.75.dp, Color(0xFFCBBFAF), shape = CircleShape)
                )
            }
        }

        // Mode b) Beige-ish Question Bubble (Interactive input box)
        AnimatedVisibility(
            visible = currentTextMode == RedPillTextMode.QUESTION_BUBBLE,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            var questionResponse by remember { mutableStateOf("") }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFBF8F2),
                    modifier = Modifier
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFFE0C9A6), shape = RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column(modifier = Modifier.widthIn(min = 220.dp, max = 340.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "?",
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pendingQuestionPrompt,
                                color = Color(0xFF292524),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicTextField(
                                value = questionResponse,
                                onValueChange = { questionResponse = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color.White, shape = RoundedCornerShape(6.dp))
                                    .border(0.5.dp, Color(0xFFD6C8B4), shape = RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                textStyle = TextStyle(fontSize = 11.sp, color = Color(0xFF1C1917))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEF4444))
                                    .clickable {
                                        if (questionResponse.isNotBlank()) {
                                            executeOrchestrator(questionResponse)
                                            questionResponse = ""
                                            currentTextMode = RedPillTextMode.CONVERSATION_HISTORY
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Reply", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(Color(0xFFFBF8F2), shape = CircleShape)
                        .border(0.75.dp, Color(0xFFE0C9A6), shape = CircleShape)
                )
            }
        }

        // 1. The Floating Red Pill Capsule (Hover physics, body gradient, and fast-go snap motion)
        Box(
            modifier = Modifier
                .offset { IntOffset(x = autonomousOffsetX.value.roundToInt(), y = hoverOffset.roundToInt()) }
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                shadowElevation = dynamicElevation,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xFFFF6253), // Top Coral highlight
                                Color(0xFFEF4444), // Vibrant Vermilion body
                                Color(0xFFB91C1C)  // Deep Crimson base
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable {
                        // Toggle between resting and conversation history drawer
                        currentTextMode = if (currentTextMode == RedPillTextMode.CONVERSATION_HISTORY) {
                            RedPillTextMode.NONE
                        } else {
                            RedPillTextMode.CONVERSATION_HISTORY
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Morphing Indicator Pip (Symbol-Information UI states)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(12.dp)
                                .alpha(pulseAlpha)
                        ) {
                            when (pipShape) {
                                PipSymbolShape.CIRCLE -> {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(pipColor, shape = CircleShape)
                                    )
                                }
                                PipSymbolShape.DIAMOND -> {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .rotate(45f)
                                            .background(pipColor, shape = RoundedCornerShape(1.dp))
                                    )
                                }
                                PipSymbolShape.STAR -> {
                                    Text(
                                        text = "✦",
                                        fontSize = 10.sp,
                                        color = pipColor,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 10.sp
                                    )
                                }
                                PipSymbolShape.QUESTION -> {
                                    Text(
                                        text = "?",
                                        fontSize = 10.sp,
                                        color = pipColor,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 10.sp
                                    )
                                }
                                PipSymbolShape.CHECK -> {
                                    Text(
                                        text = "✓",
                                        fontSize = 10.sp,
                                        color = pipColor,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 10.sp
                                    )
                                }
                                PipSymbolShape.MIC -> {
                                    Text(
                                        text = "🎙",
                                        fontSize = 9.sp,
                                        lineHeight = 9.sp
                                    )
                                }
                                PipSymbolShape.CLOCK -> {
                                    Text(
                                        text = "⏱",
                                        fontSize = 9.sp,
                                        lineHeight = 9.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentTextMode == RedPillTextMode.QUESTION_BUBBLE) "❓ Aidos Orchestrator" else "🔴 Aidos Orchestrator",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• Cross-Chat AI",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.88f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Quick Mode Switcher Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = if (currentTextMode == RedPillTextMode.THOUGHT_BUBBLE) 0.35f else 0.15f))
                                    .clickable {
                                        currentTextMode = if (currentTextMode == RedPillTextMode.THOUGHT_BUBBLE) RedPillTextMode.NONE else RedPillTextMode.THOUGHT_BUBBLE
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("💭 Thought", fontSize = 10.sp, color = Color.White)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = if (currentTextMode == RedPillTextMode.QUESTION_BUBBLE) 0.35f else 0.15f))
                                    .clickable {
                                        currentTextMode = if (currentTextMode == RedPillTextMode.QUESTION_BUBBLE) RedPillTextMode.NONE else RedPillTextMode.QUESTION_BUBBLE
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("❓ Ask", fontSize = 10.sp, color = Color.White)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = if (currentTextMode == RedPillTextMode.CONVERSATION_HISTORY) 0.35f else 0.15f))
                                    .clickable {
                                        currentTextMode = if (currentTextMode == RedPillTextMode.CONVERSATION_HISTORY) RedPillTextMode.NONE else RedPillTextMode.CONVERSATION_HISTORY
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(if (currentTextMode == RedPillTextMode.CONVERSATION_HISTORY) "▲ Close" else "▼ History", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Ground cast shadow underneath the hover capsule
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .height(3.dp)
                .width((72.dp - (-hoverOffset * 4f).dp).coerceAtLeast(36.dp))
                .alpha((0.35f - (-hoverOffset * 0.05f)).coerceIn(0.1f, 0.4f))
                .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape)
        )

        // Render Projected Child Pills spawned by Orchestrator
        if (projectedPills.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (proj in projectedPills) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = proj.roleColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, proj.roleColor),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(proj.roleColor, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "✨ ${proj.name}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = proj.roleColor
                            )
                        }
                    }
                }
            }
        }

        // Orchestrator Action Toolbar: Pill Projections, Cone Highlight & Snap Move
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.05f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Orchestrator Actions:",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                // 3 Pill Projections
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF3B82F6).copy(alpha = 0.18f))
                        .clickable { projectNewPill(PillProjectionStyle.BEAM_TELEPORT) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("⚡ Beam Pill", fontSize = 9.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.18f))
                        .clickable { projectNewPill(PillProjectionStyle.MITOSIS_EJECT) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("🧬 Mitosis Pill", fontSize = 9.sp, color = Color(0xFF047857), fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF8B5CF6).copy(alpha = 0.18f))
                        .clickable { projectNewPill(PillProjectionStyle.HOLOGRAPHIC_MATERIALIZE) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("📡 Holo Pill", fontSize = 9.sp, color = Color(0xFF6D28D9), fontWeight = FontWeight.Medium)
                }

                // Orange Target Projection Cone
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF97316).copy(alpha = if (activeTargetCone != null) 0.35f else 0.18f))
                        .clickable {
                            activeTargetCone = if (activeTargetCone != null) null else TargetConeHighlight(
                                label = "Key Action",
                                targetOffset = Offset(80f, 60f),
                                targetSize = Size(140f, 24f)
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(if (activeTargetCone != null) "🔦 Cone Active" else "🔦 Cone", fontSize = 9.sp, color = Color(0xFFC2410C), fontWeight = FontWeight.Medium)
                }

                // Autonomous Fast-Go / Fast-Stop Relocation
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.18f))
                        .clickable { triggerAutonomousSnapRelocation() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("⚡ Snap Move", fontSize = 9.sp, color = Color(0xFFB91C1C), fontWeight = FontWeight.Medium)
                }
            }
        }

        // Mode c) Full-blown Conversation History HUD and entry box
        AnimatedVisibility(
            visible = currentTextMode == RedPillTextMode.CONVERSATION_HISTORY,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 2.dp, end = 2.dp)
                    .border(
                        width = 1.dp,
                        color = Color(0xFFEF4444).copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "💬 Multi-Turn Conversation History (Aidos core):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // History message feed
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color.Black.copy(alpha = 0.04f), shape = RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (turn in historyEntries.takeLast(6)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (turn.isFromUser) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (turn.isFromUser) Color(0xFFEF4444) else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .shadow(1.dp, shape = RoundedCornerShape(8.dp))
                                        .widthIn(max = 280.dp)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = turn.text,
                                            fontSize = 11.sp,
                                            color = if (turn.isFromUser) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (turn.executedTools.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Tools: ${turn.executedTools.joinToString()}",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (turn.isFromUser) Color.White.copy(alpha = 0.8f) else Color(0xFF166534)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Input & Run Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = userPromptInput,
                            onValueChange = { userPromptInput = it },
                            placeholder = {
                                Text(
                                    "e.g. 'Tell Bob I\'ll be 10 min late', 'What did Bob say?'",
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { executeOrchestrator(userPromptInput) },
                            enabled = !isExecuting && userPromptInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Run", fontSize = 12.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Action Chips
                    Text(
                        text = "Suggested actions:",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val targetContact = contacts.firstOrNull()?.displayName ?: "Bob"
                        val chips = listOf(
                            "💬 Tell $targetContact I'll be 10 minutes late",
                            "🔍 What did $targetContact say in our chat?",
                            "📅 Check if I am free at 5 PM"
                        )
                        for (chipText in chips) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                    .clickable {
                                        val prompt = chipText.substringAfter(" ")
                                        userPromptInput = prompt
                                        executeOrchestrator(prompt)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(chipText, fontSize = 10.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Execution Errors
                    if (executionError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEE2E2), shape = RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text("⚠️ $executionError", fontSize = 11.sp, color = Color(0xFF991B1B))
                        }
                    }
                }
            }
        }
    }
}
