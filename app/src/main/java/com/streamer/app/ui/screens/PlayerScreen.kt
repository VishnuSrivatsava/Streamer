package com.streamer.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamer.app.player.StreamerPlayer
import com.streamer.app.ui.platform.AdaptiveButton
import com.streamer.app.ui.platform.LocalIsTv
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
    val isTv = LocalIsTv.current
    val activity = context as? Activity
    val player = remember(streamUrl) {
        StreamerPlayer.create(context, streamUrl)
    }

    var playerError by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(!isTv) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var controlsVisible by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val boxFocusRequester = remember { FocusRequester() }
    val androidViewFocusRequester = remember { FocusRequester() }

    // Two-mode focus: when controls are hidden, Box gets focus (onPreviewKeyEvent
    // handles seek/play/show). When controls are visible, AndroidView gets focus
    // so key events reach PlayerView's controller for CC/gear/seek bar navigation.
    LaunchedEffect(controlsVisible) {
        if (isTv) {
            try {
                if (controlsVisible) {
                    androidViewFocusRequester.requestFocus()
                    // Also give Android View focus to PlayerView so its
                    // internal controller elements are in the focus chain
                    playerView?.requestFocus()
                } else {
                    boxFocusRequester.requestFocus()
                }
            } catch (_: Exception) {}
        }
    }

    // Apply/remove fullscreen mode on phone
    LaunchedEffect(isFullscreen) {
        if (isTv) return@LaunchedEffect
        activity?.let { act ->
            val window = act.window
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (isFullscreen) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            }
        }
    }

    // Restore system bars and orientation when leaving PlayerScreen
    DisposableEffect(Unit) {
        onDispose {
            if (!isTv) {
                activity?.let { act ->
                    val window = act.window
                    WindowCompat.setDecorFitsSystemWindows(window, true)
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                }
            }
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(boxFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!isTv) return@onPreviewKeyEvent false
                val pv = playerView ?: return@onPreviewKeyEvent false
                // Consume both ACTION_DOWN and ACTION_UP for handled keys
                val dominated = when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight,
                    Key.DirectionCenter, Key.Enter,
                    Key.DirectionUp, Key.DirectionDown -> true
                    else -> false
                }
                if (!dominated) return@onPreviewKeyEvent false
                // Only act on key-down, but consume key-up too
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                when (event.key) {
                    Key.DirectionLeft -> {
                        player.seekBack()
                        pv.showController()
                    }
                    Key.DirectionRight -> {
                        player.seekForward()
                        pv.showController()
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (player.isPlaying) player.pause() else player.play()
                        pv.showController()
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        pv.showController()
                    }
                    else -> {}
                }
                true
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 5000
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShowSubtitleButton(true)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                        }
                    )
                    if (!isTv) {
                        setFullscreenButtonClickListener { fullscreen ->
                            isFullscreen = fullscreen
                        }
                    }
                }.also { playerView = it }
            },
            update = { view ->
                view.resizeMode = resizeMode
            },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(androidViewFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    // When controls are visible and AndroidView has focus,
                    // manually handle D-pad focus navigation between controller
                    // elements (seek bar, CC, gear) since Compose's AndroidView
                    // doesn't support automatic View focus navigation.
                    if (!isTv || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val pv = playerView ?: return@onPreviewKeyEvent false
                    val focused = pv.findFocus() ?: pv

                    when (event.key) {
                        Key.DirectionDown, Key.DirectionUp -> {
                            val direction = if (event.key == Key.DirectionDown)
                                android.view.View.FOCUS_DOWN else android.view.View.FOCUS_UP
                            val next = focused.focusSearch(direction)
                            if (next != null && next != focused) {
                                next.requestFocus()
                                true
                            } else false
                        }
                        Key.DirectionLeft, Key.DirectionRight -> {
                            val direction = if (event.key == Key.DirectionLeft)
                                android.view.View.FOCUS_LEFT else android.view.View.FOCUS_RIGHT
                            val next = focused.focusSearch(direction)
                            if (next != null && next != focused) {
                                // Navigate between buttons (CC, gear, etc.)
                                next.requestFocus()
                                true
                            } else {
                                // No focus target (e.g. seek bar spans full width)
                                // → let the view handle it (seek bar scrubbing)
                                false
                            }
                        }
                        else -> false // Center/Enter pass through to activate buttons
                    }
                }
        )

        // Resize mode toggle (top-right, visible only when player controls are showing, phone only)
        if (controlsVisible && !isTv) {
            val resizeLabel = when (resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop"
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                else -> "Fit"
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .clickable {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = resizeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = StreamerWhite
                )
            }
        }

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
                        AdaptiveButton(
                            onClick = {
                                playerError = null
                                player.prepare()
                                player.playWhenReady = true
                            },
                            modifier = Modifier.focusRequester(retryFocusRequester),
                            containerColor = StreamerMediumGray,
                            contentColor = StreamerWhite
                        ) {
                            Text("Retry")
                        }
                        AdaptiveButton(
                            onClick = onBack,
                            containerColor = StreamerMediumGray,
                            contentColor = StreamerWhite
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }
        }
    }
}
