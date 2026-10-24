package com.bithead.shelter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ShelterColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = GoldOnPrimary,
    primaryContainer = GoldSubtleContainer,
    onPrimaryContainer = GoldPrimary,
    secondary = ShelterBlueSoft,
    onSecondary = SlateMeshSubtle,
    secondaryContainer = SlateMeshSubtle,
    onSecondaryContainer = ShelterBlueSoft,
    tertiary = SlateMesh,
    onTertiary = TextBone,
    error = CrimsonText,
    onError = CrimsonContainer,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonText,
    background = CanvasDark,
    onBackground = TextBone,
    surface = SurfacePanel,
    surfaceDim = CanvasDark,
    surfaceBright = SurfaceActive,
    surfaceContainerLowest = CanvasDark,
    surfaceContainerLow = SurfaceLowest,
    surfaceContainer = SurfacePanel,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = SurfaceActive,
    onSurface = TextBone,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextMidGray,
    outline = BorderMedium,
    outlineVariant = BorderSubtle,
    surfaceTint = SurfacePanel,
    inverseSurface = TextBone,
    inverseOnSurface = CanvasDark,
    inversePrimary = GoldOnPrimary,
)

private val ShelterShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

@Composable
fun ShelterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ShelterColorScheme, typography = ShelterTypography,
        shapes = ShelterShapes, content = content)
}
