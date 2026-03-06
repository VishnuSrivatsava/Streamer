package com.streamer.app.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.streamer.app.data.remote.NetworkModule
import java.net.URLDecoder

@OptIn(UnstableApi::class)
object StreamerPlayer {

    private const val TAG = "StreamerPlayer"

    // Browser-like User-Agent to avoid Cloudflare bot filtering
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun create(context: Context, streamUrl: String): ExoPlayer {
        Log.d(TAG, "Creating player for: $streamUrl")

        // Use the shared OkHttpClient (has OCSP-lenient TrustManager for TV devices)
        val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.client)
            .setUserAgent(USER_AGENT)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // Enable decoder fallback: if a hardware decoder fails, try software fallback.
        // This is critical for codecs like AC3/EAC3/DTS that some devices don't support.
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        // Track selector: allow unsupported audio/video tracks to be selected
        // so they can attempt playback rather than being filtered out
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setExceedAudioConstraintsIfNecessary(true)
                .setExceedVideoConstraintsIfNecessary(true)
                .build()
        }

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        // Error logging
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error [${error.errorCodeName}]: ${error.message}", error)
                // Walk the cause chain for HTTP status details
                var cause: Throwable? = error.cause
                while (cause != null) {
                    Log.e(TAG, "Cause: ${cause::class.java.simpleName}: ${cause.message}")
                    if (cause is HttpDataSource.InvalidResponseCodeException) {
                        Log.e(TAG, "HTTP Status Code: ${cause.responseCode}")
                        Log.e(TAG, "Request URL: ${cause.dataSpec.uri}")
                    }
                    cause = cause.cause
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                Log.d(TAG, "Playback state: $stateName")
            }
        })

        // Build media item with MIME type hint for proper extractor selection
        val mediaItemBuilder = MediaItem.Builder().setUri(streamUrl)
        guessMimeType(streamUrl)?.let { mimeType ->
            mediaItemBuilder.setMimeType(mimeType)
            Log.d(TAG, "Set MIME type hint: $mimeType")
        }

        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        player.playWhenReady = true

        return player
    }

    private fun guessMimeType(url: String): String? {
        val decodedUrl = try {
            URLDecoder.decode(url, "UTF-8").lowercase()
        } catch (_: Exception) {
            url.lowercase()
        }
        return when {
            decodedUrl.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            decodedUrl.endsWith(".mp4") || decodedUrl.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
            decodedUrl.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
            decodedUrl.endsWith(".avi") -> MimeTypes.VIDEO_MATROSKA
            decodedUrl.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
            decodedUrl.endsWith(".flv") -> MimeTypes.VIDEO_FLV
            decodedUrl.endsWith(".mov") -> MimeTypes.VIDEO_MP4
            else -> null
        }
    }
}
