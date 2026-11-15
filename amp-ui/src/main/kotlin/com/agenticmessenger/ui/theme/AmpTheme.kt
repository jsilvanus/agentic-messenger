package com.agenticmessenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import dev.aidos.tcr.theme.TcrTheme

// D46 (amended): the palette, shapes and typography are the TCR's. Kept as aliases so Amp call
// sites do not change.
typealias ProvenanceColors = dev.aidos.tcr.theme.ProvenanceColors

val LocalProvenanceColors = dev.aidos.tcr.theme.LocalProvenanceColors
val AmpShapes = dev.aidos.tcr.theme.TcrShapes
val AmpTypography = dev.aidos.tcr.theme.TcrMaterialTypography

@Composable
fun AmpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    TcrTheme(darkTheme = darkTheme, content = content)
}
