package app.srvther.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Fallback scheme: a stunning navy dark theme for devices below Android 12.
private val SrvtherDarkColorScheme = darkColorScheme(
    primary = SrvtherBlue,
    onPrimary = Color.White,
    secondary = SrvtherCyan,
    onSecondary = Color(0xFF04211C),
    tertiary = SrvtherCyan,
    background = Navy900,
    onBackground = OnDark,
    surface = Navy800,
    onSurface = OnDark,
    surfaceVariant = Navy700,
    onSurfaceVariant = OnDarkMuted,
    error = SrvtherError,
    onError = Color.White,
    outline = Navy600,
)

/**
 * Material You: uses the wallpaper-derived dynamic dark palette on Android 12+,
 * and falls back to the navy scheme otherwise. Always dark by design.
 */
@Composable
fun SrvtherTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        SrvtherDarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SrvtherTypography,
        content = content,
    )
}
