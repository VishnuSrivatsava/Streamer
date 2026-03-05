package com.streamer.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalIsTv = staticCompositionLocalOf { true }

object AdaptiveDimens {
    @Composable
    fun horizontalPadding(): Dp = if (LocalIsTv.current) 48.dp else 16.dp

    @Composable
    fun gridMinSize(): Dp = if (LocalIsTv.current) 150.dp else 120.dp

    @Composable
    fun homeGridMinSize(): Dp = if (LocalIsTv.current) 250.dp else 160.dp

    @Composable
    fun cardWidth(): Dp = if (LocalIsTv.current) 150.dp else 110.dp

    @Composable
    fun folderCardWidth(): Dp = if (LocalIsTv.current) 200.dp else 160.dp
}
