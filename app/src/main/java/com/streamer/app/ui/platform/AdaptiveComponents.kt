package com.streamer.app.ui.platform

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.streamer.app.ui.theme.StreamerDarkGray
import com.streamer.app.ui.theme.StreamerRed
import com.streamer.app.ui.theme.StreamerWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AdaptiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (LocalIsTv.current) {
        androidx.tv.material3.Card(
            onClick = onClick,
            modifier = modifier
        ) {
            content()
        }
    } else {
        androidx.compose.material3.Card(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = StreamerDarkGray
            )
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AdaptiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = StreamerRed,
    contentColor: Color = StreamerWhite,
    content: @Composable () -> Unit
) {
    if (LocalIsTv.current) {
        androidx.tv.material3.Button(
            onClick = onClick,
            modifier = modifier,
            colors = androidx.tv.material3.ButtonDefaults.colors(
                containerColor = containerColor,
                contentColor = contentColor,
                focusedContainerColor = containerColor.copy(alpha = 0.8f),
                focusedContentColor = contentColor
            )
        ) {
            content()
        }
    } else {
        androidx.compose.material3.Button(
            onClick = onClick,
            modifier = modifier,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        ) {
            content()
        }
    }
}
