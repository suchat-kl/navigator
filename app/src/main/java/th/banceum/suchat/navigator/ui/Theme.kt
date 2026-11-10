package th.banceum.suchat.navigator.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0F7B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEFE4),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF1A73E8),
    secondaryContainer = Color(0xFFD7E6FC),
    tertiary = Color(0xFFFFC83D),
    background = Color(0xFFF6FAF8),
    surface = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD8B8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513D),
    secondary = Color(0xFFA8C8FA),
    tertiary = Color(0xFFFFD873),
)

@Composable
fun NavigatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
