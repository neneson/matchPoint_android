package cl.matchpoint.marcador.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val MatchpointColorScheme = ColorScheme(
    primary = MatchpointColors.primary,
    onPrimary = MatchpointColors.foreground,
    secondary = MatchpointColors.secondary,
    onSecondary = MatchpointColors.foreground,
    background = MatchpointColors.background,
    onBackground = MatchpointColors.foreground,
    onSurface = MatchpointColors.foreground,
    onSurfaceVariant = MatchpointColors.mutedForeground,
    error = MatchpointColors.destructive,
    onError = MatchpointColors.foreground,
)

@Composable
fun MatchpointTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MatchpointColorScheme, content = content)
}
