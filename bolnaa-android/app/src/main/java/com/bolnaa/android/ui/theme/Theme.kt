package com.bolnaa.android.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = FlowPrimary,
    onPrimary = FlowTextPrimary,
    secondary = FlowSecondary,
    onSecondary = FlowTextPrimary,
    tertiary = FlowAccent,
    background = FlowBg,
    onBackground = FlowTextPrimary,
    surface = FlowSurface,
    onSurface = FlowTextPrimary,
    surfaceVariant = FlowSurfaceVariant,
    onSurfaceVariant = FlowTextSecondary,
    outline = FlowBorder,
    error = FlowError,
    onError = FlowTextPrimary
)

@Composable
fun BolnaaTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = FlowBg.toArgb()
            window.navigationBarColor = FlowBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
