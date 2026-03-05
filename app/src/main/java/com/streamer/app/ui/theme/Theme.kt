package com.streamer.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.streamer.app.ui.platform.LocalIsTv

@OptIn(ExperimentalTvMaterial3Api::class)
private val StreamerColorScheme = darkColorScheme(
    primary = StreamerRed,
    onPrimary = StreamerWhite,
    primaryContainer = StreamerDarkRed,
    onPrimaryContainer = StreamerWhite,
    surface = StreamerBlack,
    onSurface = StreamerWhite,
    surfaceVariant = StreamerDarkGray,
    onSurfaceVariant = StreamerLightGray,
    background = StreamerBlack,
    onBackground = StreamerWhite,
    border = StreamerRed
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreamerTheme(isTv: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsTv provides isTv) {
        MaterialTheme(
            colorScheme = StreamerColorScheme,
            typography = StreamerTypography,
            content = content
        )
    }
}
