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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamer.app.player.StreamerPlayer
import com.streamer.app.ui.platform.AdaptiveButton
import com.streamer.app.ui.platform.LocalIsTv
import com.streamer.app.ui.theme.StreamerBlack
import com.streamer.app.ui.theme.StreamerDarkGray
import com.streamer.app.ui.theme.StreamerLightGray
import com.streamer.app.ui.theme.StreamerMediumGray
import com.streamer.app.ui.theme.StreamerRed
import com.streamer.app.ui.theme.StreamerWhite
import com.streamer.app.ui.theme.StreamerYellow
import kotlinx.coroutines.delay
import java.util.Locale

// --- Track selection data & helpers ---

private data class TrackInfo(
    val group: Tracks.Group,
    val trackIndex: Int,
    val displayName: String,
    val isSelected: Boolean
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun extractTracks(player: Player, trackType: Int): List<TrackInfo> {
    val result = mutableListOf<TrackInfo>()
    for (group in player.currentTracks.groups) {
        if (group.type != trackType) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            result.add(
                TrackInfo(
                    group = group,
                    trackIndex = i,
                    displayName = formatTrackLabel(format, trackType, result.size + 1),
                    isSelected = group.isTrackSelected(i)
                )
            )
        }
    }
    return result
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun formatTrackLabel(format: Format, trackType: Int, fallbackIndex: Int): String {
    val parts = mutableListOf<String>()

    // Language
    val langName = format.language
        ?.takeIf { it != "und" && it.isNotBlank() }
        ?.let { code ->
            try {
                val locale = Locale.forLanguageTag(code)
                locale.getDisplayLanguage(Locale.ENGLISH)
                    .takeIf { it.isNotBlank() && it != code }
            } catch (_: Exception) { null }
        }

    // Label from container metadata
    val label = format.label?.takeIf { it.isNotBlank() }

    when {
        langName != null && label != null && !label.equals(langName, ignoreCase = true) -> {
            parts.add(langName)
            parts.add("($label)")
        }
        langName != null -> parts.add(langName)
        label != null -> parts.add(label)
        else -> parts.add("Track $fallbackIndex")
    }

    // Channel count for audio
    if (trackType == C.TRACK_TYPE_AUDIO && format.channelCount > 0) {
        parts.add(
            when (format.channelCount) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> "${format.channelCount}ch"
            }
        )
    }

    // Codec
    format.sampleMimeType?.let { mime ->
        val codec = when {
            "ac3" in mime -> "AC3"
            "eac3" in mime || "e-ac3" in mime -> "EAC3"
            "mp4a" in mime || "aac" in mime -> "AAC"
            "dts" in mime -> "DTS"
            "opus" in mime -> "Opus"
            "flac" in mime -> "FLAC"
            "vorbis" in mime -> "Vorbis"
            "truehd" in mime -> "TrueHD"
            "subrip" in mime -> "SRT"
            "ssa" in mime || "ass" in mime -> "ASS"
            "vtt" in mime || "webvtt" in mime -> "VTT"
            "pgs" in mime -> "PGS"
            else -> null
        }
        codec?.let { parts.add(it) }
    }

    return parts.joinToString(" \u00B7 ")
}

private fun formatTime(ms: Long): String {
    if (ms < 0 || ms == C.TIME_UNSET) return "--:--"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

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
    var seekRepeatCount by remember { mutableIntStateOf(0) }
    var lastSeekTimeNanos by remember { mutableStateOf(0L) }
    var autoHideKey by remember { mutableIntStateOf(0) }

    // Track selection state
    var showTrackSelector by remember { mutableStateOf<String?>(null) } // null | "audio" | "subtitle"
    var audioTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }

    // Auto-hide TV controls after 5 seconds of inactivity
    LaunchedEffect(controlsVisible, autoHideKey) {
        if (controlsVisible && isTv) {
            delay(5000)
            if (player.isPlaying) controlsVisible = false
        }
    }

    // Box always holds Compose focus. Track selector overlay manages its own focus.
    LaunchedEffect(controlsVisible, showTrackSelector) {
        if (isTv) {
            try {
                if (showTrackSelector != null) {
                    // Track selector overlay manages its own focus
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

    // Restore system bars and orientation when leaving PlayerScreen.
    // Force focus back to the Activity root so Compose can cleanly
    // assign focus on the destination screen (Home/Browse).
    DisposableEffect(Unit) {
        onDispose {
            playerView?.clearFocus()
            activity?.window?.decorView?.requestFocus()
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

            override fun onTracksChanged(tracks: Tracks) {
                audioTracks = extractTracks(player, C.TRACK_TYPE_AUDIO)
                subtitleTracks = extractTracks(player, C.TRACK_TYPE_TEXT)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    BackHandler {
        if (showTrackSelector != null) {
            showTrackSelector = null
        } else if (isTv && controlsVisible) {
            controlsVisible = false
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(boxFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!isTv) return@onPreviewKeyEvent false
                // When track selector overlay is open, let it handle D-pad
                if (showTrackSelector != null) return@onPreviewKeyEvent false

                if (controlsVisible) {
                    if (event.type == KeyEventType.KeyDown) autoHideKey++
                    // Center/Enter always toggles play/pause, even with controls showing
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                    ) {
                        if (player.isPlaying) player.pause() else player.play()
                        return@onPreviewKeyEvent true
                    }
                    // Let Compose handle D-pad natively for TvPlayerControls
                    return@onPreviewKeyEvent false
                }

                // Controls hidden: handle seeking, play/pause, show controls
                val dominated = when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight,
                    Key.DirectionCenter, Key.Enter,
                    Key.DirectionUp, Key.DirectionDown -> true
                    else -> false
                }
                if (!dominated) return@onPreviewKeyEvent false

                // On key-up: reset seek repeat counter for seek keys, consume all
                if (event.type != KeyEventType.KeyDown) {
                    if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                        seekRepeatCount = 0
                        lastSeekTimeNanos = 0L
                    }
                    return@onPreviewKeyEvent true
                }

                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        // Throttle: Android TV sends ~20 key repeats/sec when holding.
                        // Only process one seek per 250ms to prevent jumping minutes.
                        val now = System.nanoTime()
                        val elapsedMs = (now - lastSeekTimeNanos) / 1_000_000
                        if (seekRepeatCount > 0 && elapsedMs < 250) {
                            // Consume but don't seek — too fast
                        } else {
                            seekRepeatCount++
                            lastSeekTimeNanos = now
                            val seekMs = minOf(
                                10_000L + (seekRepeatCount - 1) * 2_000L,
                                30_000L
                            )
                            val current = player.currentPosition
                            val target = if (event.key == Key.DirectionLeft) {
                                maxOf(0L, current - seekMs)
                            } else {
                                val dur = player.duration
                                if (dur != androidx.media3.common.C.TIME_UNSET) {
                                    minOf(dur, current + seekMs)
                                } else {
                                    current + seekMs
                                }
                            }
                            player.seekTo(target)
                        }
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (player.isPlaying) player.pause() else player.play()
                        controlsVisible = true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        controlsVisible = true
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
                    useController = !isTv
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    if (isTv) {
                        isFocusable = false
                        isFocusableInTouchMode = false
                    } else {
                        controllerAutoShow = true
                        controllerShowTimeoutMs = 5000
                        setShowSubtitleButton(true)
                        isFocusable = true
                        isFocusableInTouchMode = true
                        descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                controlsVisible = visibility == android.view.View.VISIBLE
                            }
                        )
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
        )

        // TV Compose controller overlay
        if (isTv && controlsVisible && showTrackSelector == null) {
            TvPlayerControls(
                player = player,
                subtitleTracks = subtitleTracks,
                onShowTrackSelector = { showTrackSelector = it },
                onInteraction = { autoHideKey++ },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Track selector trigger buttons (top-left, phone only, visible when controls show and tracks available)
        if (controlsVisible && !isTv && showTrackSelector == null &&
            (audioTracks.size > 1 || subtitleTracks.isNotEmpty())
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (subtitleTracks.isNotEmpty()) {
                    AdaptiveButton(
                        onClick = { showTrackSelector = "subtitle" },
                        containerColor = StreamerMediumGray,
                        contentColor = StreamerWhite
                    ) {
                        Text("Subs")
                    }
                }
                if (audioTracks.size > 1) {
                    AdaptiveButton(
                        onClick = { showTrackSelector = "audio" },
                        containerColor = StreamerMediumGray,
                        contentColor = StreamerWhite
                    ) {
                        Text("Audio")
                    }
                }
            }
        }

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

        // Track selection overlay
        showTrackSelector?.let { selectorType ->
            val isAudio = selectorType == "audio"
            val tracks = if (isAudio) audioTracks else subtitleTracks
            val dialogTitle = if (isAudio) "Audio Track" else "Subtitle Track"
            val firstItemFocus = remember { FocusRequester() }

            LaunchedEffect(selectorType) {
                if (isTv) {
                    try { firstItemFocus.requestFocus() } catch (_: Exception) {}
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { showTrackSelector = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .background(StreamerDarkGray, RoundedCornerShape(12.dp))
                        .padding(20.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {},
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = dialogTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = StreamerWhite,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val itemCount = tracks.size + if (!isAudio) 1 else 0
                    LazyColumn(
                        modifier = Modifier.height(
                            minOf(itemCount * 52, 340).dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // "Off" option for subtitles
                        if (!isAudio) {
                            item {
                                val isOff = subtitleTracks.none { it.isSelected }
                                TrackOptionButton(
                                    label = "Off",
                                    isSelected = isOff,
                                    modifier = if (isOff || tracks.isEmpty())
                                        Modifier.focusRequester(firstItemFocus) else Modifier,
                                    onClick = {
                                        player.trackSelectionParameters =
                                            player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .build()
                                        showTrackSelector = null
                                    }
                                )
                            }
                        }

                        itemsIndexed(tracks) { index, track ->
                            val shouldFocus = if (isAudio) {
                                track.isSelected || (index == 0 && tracks.none { it.isSelected })
                            } else {
                                track.isSelected
                            }
                            TrackOptionButton(
                                label = track.displayName,
                                isSelected = track.isSelected,
                                modifier = if (shouldFocus)
                                    Modifier.focusRequester(firstItemFocus) else Modifier,
                                onClick = {
                                    val trackType = if (isAudio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
                                    val override = TrackSelectionOverride(
                                        track.group.mediaTrackGroup,
                                        listOf(track.trackIndex)
                                    )
                                    player.trackSelectionParameters =
                                        player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(trackType, false)
                                            .setOverrideForType(override)
                                            .build()
                                    showTrackSelector = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPlayerControls(
    player: Player,
    subtitleTracks: List<TrackInfo>,
    onShowTrackSelector: (String) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playPauseFocus = remember { FocusRequester() }
    var position by remember { mutableStateOf(player.currentPosition) }
    var duration by remember { mutableStateOf(player.duration) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }

    // Poll player state
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            duration = player.duration
            isPlaying = player.isPlaying
            delay(500)
        }
    }

    // Focus play/pause by default (delay ensures layout is ready)
    LaunchedEffect(Unit) {
        delay(100)
        try { playPauseFocus.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Time display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(position),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StreamerWhite
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StreamerLightGray
                )
            }

            // Seek bar
            TvSeekBar(
                position = position,
                duration = duration,
                onSeek = { newPos -> player.seekTo(newPos) },
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                    isPlaying = !isPlaying
                },
                onInteraction = onInteraction
            )

            // Button row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (subtitleTracks.isNotEmpty()) {
                    AdaptiveButton(
                        onClick = {
                            onShowTrackSelector("subtitle")
                            onInteraction()
                        },
                        containerColor = StreamerMediumGray,
                        contentColor = StreamerWhite
                    ) {
                        Text("Subs")
                    }
                }

                AdaptiveButton(
                    onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                        isPlaying = !isPlaying
                        onInteraction()
                    },
                    modifier = Modifier.focusRequester(playPauseFocus),
                    containerColor = StreamerMediumGray,
                    contentColor = StreamerWhite
                ) {
                    Text(if (isPlaying) "Pause" else "Play")
                }

                AdaptiveButton(
                    onClick = {
                        onShowTrackSelector("audio")
                        onInteraction()
                    },
                    containerColor = StreamerMediumGray,
                    contentColor = StreamerWhite
                ) {
                    Text("Audio")
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var seekRepeatCount by remember { mutableIntStateOf(0) }
    var lastSeekTimeNanos by remember { mutableStateOf(0L) }
    val fraction = if (duration > 0 && duration != C.TIME_UNSET) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else 0f
    val barHeight = if (isFocused) 8.dp else 4.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        // Reset counters on key up
                        if (event.type != KeyEventType.KeyDown) {
                            seekRepeatCount = 0
                            lastSeekTimeNanos = 0L
                            return@onPreviewKeyEvent true
                        }
                        // Throttle: one seek per 250ms
                        val now = System.nanoTime()
                        val elapsedMs = (now - lastSeekTimeNanos) / 1_000_000
                        if (seekRepeatCount > 0 && elapsedMs < 250) {
                            return@onPreviewKeyEvent true
                        }
                        seekRepeatCount++
                        lastSeekTimeNanos = now
                        // Progressive: 10s, 12s, 14s, ... up to 30s
                        val seekMs = minOf(
                            10_000L + (seekRepeatCount - 1) * 2_000L,
                            30_000L
                        )
                        val newPos = if (event.key == Key.DirectionLeft) {
                            maxOf(0L, position - seekMs)
                        } else {
                            val dur = if (duration != C.TIME_UNSET) duration else Long.MAX_VALUE
                            minOf(dur, position + seekMs)
                        }
                        onSeek(newPos)
                        onInteraction()
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (event.type == KeyEventType.KeyDown) {
                            onPlayPause()
                            onInteraction()
                        }
                        true
                    }
                    else -> false // Up/Down pass through for focus navigation
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(StreamerMediumGray, RoundedCornerShape(2.dp))
        )
        // Filled portion
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(barHeight)
                .background(StreamerRed, RoundedCornerShape(2.dp))
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackOptionButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AdaptiveButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        containerColor = if (isSelected) StreamerMediumGray else StreamerBlack,
        contentColor = if (isSelected) StreamerYellow else StreamerLightGray
    ) {
        Text(
            text = if (isSelected) "\u2713  $label" else label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
