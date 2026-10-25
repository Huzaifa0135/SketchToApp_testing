package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SketchToAppColorScheme = darkColorScheme(
    primary = TealAccent,
    onPrimary = OnTealAccent,
    primaryContainer = TealAccentDim,
    onPrimaryContainer = PrimaryText,
    secondary = VioletHighlight,
    onSecondary = PrimaryText,
    secondaryContainer = VioletContainer,
    onSecondaryContainer = OnVioletContainer,
    tertiary = StatusSuccess,
    onTertiary = OnTealAccent,
    background = NavyBackground,
    onBackground = PrimaryText,
    surface = PanelBackground,
    onSurface = PrimaryText,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = SecondaryText,
    surfaceContainerLowest = NavyBackground,
    surfaceContainerLow = PanelBackground,
    surfaceContainer = ElevatedSurface,
    surfaceContainerHigh = ElevatedSurfaceHigh,
    outline = SubtleBorder,
    outlineVariant = SubtleBorder,
    error = StatusError,
    onError = NavyBackground,
    errorContainer = ErrorContainerDark,
    onErrorContainer = PrimaryText
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SketchToAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SketchToAppColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
