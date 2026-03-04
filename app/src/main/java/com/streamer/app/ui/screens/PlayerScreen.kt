package com.streamer.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamer.app.player.StreamerPlayer
import com.streamer.app.ui.theme.StreamerMediumGray
import com.streamer.app.ui.theme.StreamerRed
import com.streamer.app.ui.theme.StreamerWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(streamUrl) {
        StreamerPlayer.create(context, streamUrl)
    }

    var playerError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                var httpCode: Int? = null
                var cause: Throwable? = error.cause
                while (cause != null) {
                    if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        httpCode = cause.responseCode
                        break
                    }
                    cause = cause.cause
                }

                val msg = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        "Unsupported codec. This file may use DTS/AC3 audio or H.265 video not supported on this device."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Network error. Check your connection."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "HTTP error${httpCode?.let { " $it" } ?: ""}. The server rejected the request."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                        "Unsupported container format. Try a different file."
                    else -> "Playback error: ${error.errorCodeName}"
                }
                playerError = msg
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Video player with built-in controls (audio/subtitle selection via gear icon + CC button)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 3000
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShowSubtitleButton(true)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .focusable()
        )

        // Error overlay
        playerError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Playback Failed",
                        style = MaterialTheme.typography.headlineSmall,
                        color = StreamerRed
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = StreamerWhite,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    val retryFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        try { retryFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                playerError = null
                                player.prepare()
                                player.playWhenReady = true
                            },
                            modifier = Modifier.focusRequester(retryFocusRequester),
                            colors = ButtonDefaults.colors(
                                containerColor = StreamerMediumGray,
                                contentColor = StreamerWhite
                            )
                        ) {
                            Text("Retry")
                        }
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.colors(
                                containerColor = StreamerMediumGray,
                                contentColor = StreamerWhite
                            )
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }
        }
    }
}
