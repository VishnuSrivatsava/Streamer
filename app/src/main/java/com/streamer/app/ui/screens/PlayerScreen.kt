package com.streamer.app.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamer.app.player.StreamerPlayer
import com.streamer.app.ui.theme.StreamerBlack
import com.streamer.app.ui.theme.StreamerDarkGray
import com.streamer.app.ui.theme.StreamerLightGray
import com.streamer.app.ui.theme.StreamerMediumGray
import com.streamer.app.ui.theme.StreamerRed
import com.streamer.app.ui.theme.StreamerWhite
import kotlinx.coroutines.delay
import java.util.Locale

private data class TrackInfo(
    val name: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

private enum class TrackSelectorType { AUDIO, SUBTITLE }

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

    var audioTracks by remember { mutableStateOf(emptyList<TrackInfo>()) }
    var subtitleTracks by remember { mutableStateOf(emptyList<TrackInfo>()) }
    var showTrackSelector by remember { mutableStateOf<TrackSelectorType?>(null) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // Auto-hide the hint after 4 seconds
    LaunchedEffect(showHint) {
        if (showHint) {
            delay(4000)
            showHint = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                audioTracks = extractTracks(tracks, C.TRACK_TYPE_AUDIO)
                subtitleTracks = extractTracks(tracks, C.TRACK_TYPE_TEXT)
            }
            override fun onPlayerError(error: PlaybackException) {
                // Extract HTTP status code if available
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

    BackHandler {
        when {
            showTrackSelector != null -> showTrackSelector = null
            showSettingsMenu -> showSettingsMenu = false
            else -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                when (keyEvent.nativeKeyEvent.keyCode) {
                    // Menu button on TV remote → show settings menu
                    KeyEvent.KEYCODE_MENU,
                    KeyEvent.KEYCODE_INFO,
                    KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> {
                        if (showTrackSelector == null && !showSettingsMenu) {
                            if (audioTracks.size > 1 || subtitleTracks.isNotEmpty()) {
                                showSettingsMenu = true
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }
                    // Captions/CC button on TV remote → directly open subtitles
                    KeyEvent.KEYCODE_CAPTIONS -> {
                        if (subtitleTracks.isNotEmpty() && showTrackSelector == null) {
                            showTrackSelector = TrackSelectorType.SUBTITLE
                            showSettingsMenu = false
                            return@onPreviewKeyEvent true
                        }
                        false
                    }
                    else -> false
                }
            }
    ) {
        // Video player
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 3000
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .focusable()
        )

        // Hint text — shows briefly on start
        val hasTrackOptions = audioTracks.size > 1 || subtitleTracks.isNotEmpty()
        AnimatedVisibility(
            visible = showHint && hasTrackOptions,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text(
                text = "Press MENU for audio/subtitle options",
                modifier = Modifier
                    .background(StreamerBlack.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = StreamerLightGray
            )
        }

        // Settings menu (Audio / Subtitle picker)
        if (showSettingsMenu) {
            SettingsMenuOverlay(
                hasAudioTracks = audioTracks.size > 1,
                hasSubtitleTracks = subtitleTracks.isNotEmpty(),
                onAudioClick = {
                    showSettingsMenu = false
                    showTrackSelector = TrackSelectorType.AUDIO
                },
                onSubtitleClick = {
                    showSettingsMenu = false
                    showTrackSelector = TrackSelectorType.SUBTITLE
                },
                onDismiss = { showSettingsMenu = false }
            )
        }

        // Track selection overlay
        if (showTrackSelector != null) {
            val type = showTrackSelector!!
            TrackSelectionOverlay(
                type = type,
                tracks = when (type) {
                    TrackSelectorType.AUDIO -> audioTracks
                    TrackSelectorType.SUBTITLE -> subtitleTracks
                },
                onTrackSelected = { groupIndex, trackIndex ->
                    val trackType = when (type) {
                        TrackSelectorType.AUDIO -> C.TRACK_TYPE_AUDIO
                        TrackSelectorType.SUBTITLE -> C.TRACK_TYPE_TEXT
                    }
                    selectTrack(player, trackType, groupIndex, trackIndex)
                    showTrackSelector = null
                },
                onSubtitlesOff = if (type == TrackSelectorType.SUBTITLE) {
                    {
                        disableTrackType(player, C.TRACK_TYPE_TEXT)
                        showTrackSelector = null
                    }
                } else null,
                onDismiss = { showTrackSelector = null }
            )
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsMenuOverlay(
    hasAudioTracks: Boolean,
    hasSubtitleTracks: Boolean,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(StreamerDarkGray, RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = StreamerWhite,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (hasAudioTracks) {
                Button(
                    onClick = onAudioClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (hasAudioTracks) Modifier.focusRequester(focusRequester)
                            else Modifier
                        ),
                    colors = ButtonDefaults.colors(
                        containerColor = StreamerMediumGray,
                        contentColor = StreamerWhite
                    )
                ) {
                    Text("Audio Track", style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (hasSubtitleTracks) {
                Button(
                    onClick = onSubtitleClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!hasAudioTracks) Modifier.focusRequester(focusRequester)
                            else Modifier
                        ),
                    colors = ButtonDefaults.colors(
                        containerColor = StreamerMediumGray,
                        contentColor = StreamerWhite
                    )
                ) {
                    Text("Subtitles", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackSelectionOverlay(
    type: TrackSelectorType,
    tracks: List<TrackInfo>,
    onTrackSelected: (groupIndex: Int, trackIndex: Int) -> Unit,
    onSubtitlesOff: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val subtitlesOff = type == TrackSelectorType.SUBTITLE && tracks.none { it.isSelected }

    val focusTargetIndex = if (subtitlesOff) {
        -1 // Focus the "Off" button
    } else {
        val sel = tracks.indexOfFirst { it.isSelected }
        if (sel >= 0) sel else 0
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .heightIn(max = 500.dp)
                .background(StreamerDarkGray, RoundedCornerShape(12.dp))
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (type) {
                    TrackSelectorType.AUDIO -> "Select Audio Track"
                    TrackSelectorType.SUBTITLE -> "Select Subtitle Track"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = StreamerWhite,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // "Off" option for subtitles
            if (onSubtitlesOff != null) {
                TrackOptionButton(
                    label = "Off",
                    isSelected = subtitlesOff,
                    onClick = onSubtitlesOff,
                    modifier = if (focusTargetIndex == -1)
                        Modifier.focusRequester(focusRequester)
                    else Modifier
                )
            }

            // Track items
            tracks.forEachIndexed { index, track ->
                val isActive = track.isSelected && !subtitlesOff
                TrackOptionButton(
                    label = track.name,
                    isSelected = isActive,
                    onClick = { onTrackSelected(track.groupIndex, track.trackIndex) },
                    modifier = if (index == focusTargetIndex)
                        Modifier.focusRequester(focusRequester)
                    else Modifier
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackOptionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.colors(
            containerColor = if (isSelected) StreamerRed.copy(alpha = 0.3f)
            else StreamerMediumGray,
            contentColor = StreamerWhite
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isSelected) ">" else "  ",
                color = if (isSelected) StreamerRed else Color.Transparent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = StreamerWhite
            )
        }
    }
}

// --- Track extraction and selection helpers ---

private fun extractTracks(tracks: Tracks, trackType: Int): List<TrackInfo> {
    val result = mutableListOf<TrackInfo>()
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != trackType) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            val format = group.getTrackFormat(trackIndex)
            val name = buildTrackName(format, trackType, result.size + 1)
            result.add(
                TrackInfo(
                    name = name,
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    isSelected = group.isTrackSelected(trackIndex)
                )
            )
        }
    }
    return result
}

private fun buildTrackName(format: Format, trackType: Int, index: Int): String {
    val parts = mutableListOf<String>()

    // Language display name
    val langName = format.language
        ?.takeIf { it != "und" && it.isNotBlank() }
        ?.let { code ->
            try {
                val locale = Locale.forLanguageTag(code)
                locale.getDisplayLanguage(Locale.ENGLISH)
                    .takeIf { it.isNotBlank() && it != code }
            } catch (_: Exception) { null }
        }

    // Label
    val label = format.label?.takeIf { it.isNotBlank() }

    when {
        langName != null && label != null && !label.equals(langName, ignoreCase = true) -> {
            parts.add(langName)
            parts.add("($label)")
        }
        langName != null -> parts.add(langName)
        label != null -> parts.add(label)
        else -> parts.add("Track $index")
    }

    // Audio-specific details
    if (trackType == C.TRACK_TYPE_AUDIO) {
        if (format.channelCount > 0) {
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
        getCodecName(format)?.let { parts.add(it) }
    }

    return parts.joinToString(" - ")
}

private fun getCodecName(format: Format): String? {
    val source = format.codecs ?: format.sampleMimeType ?: return null
    val s = source.lowercase()
    return when {
        "ac-3" in s || "ac3" in s -> "AC3"
        "ec-3" in s || "eac3" in s -> "EAC3"
        "mp4a" in s || "aac" in s -> "AAC"
        "dtsc" in s || "dts" in s -> "DTS"
        "opus" in s -> "Opus"
        "flac" in s -> "FLAC"
        "vorbis" in s -> "Vorbis"
        "truehd" in s -> "TrueHD"
        "pcm" in s -> "PCM"
        else -> null
    }
}

private fun selectTrack(player: ExoPlayer, trackType: Int, groupIndex: Int, trackIndex: Int) {
    val group = player.currentTracks.groups[groupIndex]
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(trackType, false)
        .setOverrideForType(
            TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
        )
        .build()
}

private fun disableTrackType(player: ExoPlayer, trackType: Int) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(trackType, true)
        .build()
}
