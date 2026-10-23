package com.agenticmessenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.model.MeshProvenanceType

/**
 * Custom color palette for the 4-Party Conversation Mesh & Agentic Provenance.
 */
@Immutable
data class ProvenanceColors(
    val humanDirectBackground: Color,
    val humanDirectContent: Color,
    val agentRunnerBackground: Color,
    val agentRunnerContent: Color,
    val humanOnlyBackground: Color,
    val humanOnlyContent: Color,
    val systemServiceBackground: Color,
    val systemServiceContent: Color,
    val cueChipBackground: Color,
    val cueChipContent: Color,
    val waveformBar: Color,
    val waveformProgress: Color,
    val pulseActive: Color
)

val LocalProvenanceColors = staticCompositionLocalOf {
    ProvenanceColors(
        humanDirectBackground = Color(0xFF6750A4),
        humanDirectContent = Color(0xFFFFFFFF),
        agentRunnerBackground = Color(0xFF006A60),
        agentRunnerContent = Color(0xFFFFFFFF),
        humanOnlyBackground = Color(0xFFB3261E),
        humanOnlyContent = Color(0xFFFFFFFF),
        systemServiceBackground = Color(0xFF49454F),
        systemServiceContent = Color(0xFFFFFFFF),
        cueChipBackground = Color(0xFFE8DEF8),
        cueChipContent = Color(0xFF1D192B),
        waveformBar = Color(0xFF9ECAFF),
        waveformProgress = Color(0xFF0061A4),
        pulseActive = Color(0xFF10B981)
    )
}

private val DarkProvenanceColors = ProvenanceColors(
    humanDirectBackground = Color(0xFF352B4E),
    humanDirectContent = Color(0xFFE8DDFF),
    agentRunnerBackground = Color(0xFF163E37),
    agentRunnerContent = Color(0xFF8CF3DD),
    humanOnlyBackground = Color(0xFF491C1C),
    humanOnlyContent = Color(0xFFFFB4AB),
    systemServiceBackground = Color(0xFF28282E),
    systemServiceContent = Color(0xFFE2E2EC),
    cueChipBackground = Color(0xFF2B2930),
    cueChipContent = Color(0xFFE6E1E5),
    waveformBar = Color(0xFF41474D),
    waveformProgress = Color(0xFF82B1FF),
    pulseActive = Color(0xFF34D399)
)

private val LightProvenanceColors = ProvenanceColors(
    humanDirectBackground = Color(0xFFECE6F0),
    humanDirectContent = Color(0xFF21005D),
    agentRunnerBackground = Color(0xFFE6F5F2),
    agentRunnerContent = Color(0xFF003731),
    humanOnlyBackground = Color(0xFFFFEDEB),
    humanOnlyContent = Color(0xFF410002),
    systemServiceBackground = Color(0xFFF4F3F7),
    systemServiceContent = Color(0xFF1D1B20),
    cueChipBackground = Color(0xFFF3EDF7),
    cueChipContent = Color(0xFF49454F),
    waveformBar = Color(0xFFD0E4FF),
    waveformProgress = Color(0xFF0061A4),
    pulseActive = Color(0xFF059669)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1D1B20),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

val AmpShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

val AmpTypography = Typography(
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun AmpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val provenanceColors = if (darkTheme) DarkProvenanceColors else LightProvenanceColors

    CompositionLocalProvider(
        LocalProvenanceColors provides provenanceColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AmpTypography,
            shapes = AmpShapes,
            content = content
        )
    }
}
