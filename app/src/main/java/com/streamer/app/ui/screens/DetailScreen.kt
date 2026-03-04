package com.streamer.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamer.app.data.model.MediaItem
import com.streamer.app.data.remote.IndexApiService
import com.streamer.app.ui.theme.StreamerBlack
import com.streamer.app.ui.theme.StreamerDarkGray
import com.streamer.app.ui.theme.StreamerLightGray
import com.streamer.app.ui.theme.StreamerMediumGray
import com.streamer.app.ui.theme.StreamerRed
import com.streamer.app.ui.theme.StreamerWhite
import com.streamer.app.viewmodel.DetailViewModel

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    mediaItem: MediaItem,
    onPlayClick: (streamUrl: String, title: String) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(mediaItem) {
        viewModel.loadDetail(mediaItem)
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        val backdropUrl = mediaItem.backdropUrl ?: mediaItem.posterUrl
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                StreamerBlack,
                                StreamerBlack.copy(alpha = 0.85f),
                                StreamerBlack.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                StreamerBlack.copy(alpha = 0.5f),
                                StreamerBlack
                            ),
                            startY = 300f
                        )
                    )
            )
        } else {
            // No TMDb — use a subtle gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                StreamerMediumGray,
                                StreamerBlack
                            )
                        )
                    )
            )
        }

        // Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster (only if TMDb available)
            if (mediaItem.posterUrl != null) {
                AsyncImage(
                    model = mediaItem.posterUrl,
                    contentDescription = mediaItem.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(220.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // Metadata column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title
                Text(
                    text = mediaItem.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = StreamerWhite,
                    fontWeight = FontWeight.Bold
                )

                // Year + Rating + Runtime row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    mediaItem.year?.let {
                        Text(
                            text = "$it",
                            style = MaterialTheme.typography.bodyLarge,
                            color = StreamerWhite
                        )
                    }
                    mediaItem.rating?.let {
                        if (it > 0) {
                            Text(
                                text = String.format("%.1f", it),
                                style = MaterialTheme.typography.bodyLarge,
                                color = StreamerRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    uiState.detail?.runtime?.let {
                        Text(
                            text = "${it} min",
                            style = MaterialTheme.typography.bodyLarge,
                            color = StreamerLightGray
                        )
                    }
                }

                // Technical metadata badges
                val tags = buildList {
                    mediaItem.resolution?.let { add(it) }
                    mediaItem.videoCodec?.let { add(it) }
                    mediaItem.audioCodec?.let { add(it) }
                    mediaItem.source?.let { add(it) }
                }
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            Text(
                                text = tag,
                                modifier = Modifier
                                    .background(
                                        StreamerDarkGray.copy(alpha = 0.8f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = StreamerWhite,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Tagline (TMDb)
                uiState.detail?.tagline?.let { tagline ->
                    if (tagline.isNotBlank()) {
                        Text(
                            text = "\"$tagline\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StreamerLightGray
                        )
                    }
                }

                // Genres (TMDb)
                uiState.detail?.genres?.let { genres ->
                    if (genres.isNotEmpty()) {
                        Text(
                            text = genres.joinToString(" / ") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            color = StreamerLightGray
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Overview (TMDb)
                val overview = mediaItem.overview ?: uiState.detail?.overview
                if (overview != null) {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = StreamerWhite,
                        maxLines = 5
                    )
                }

                // File info
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    mediaItem.indexItem.sizeBytes?.let { bytes ->
                        Text(
                            text = "Size: ${formatFileSize(bytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StreamerLightGray
                        )
                    }
                    val ext = mediaItem.indexItem.name.substringAfterLast('.', "").uppercase()
                    if (ext.isNotBlank()) {
                        Text(
                            text = "Format: $ext",
                            style = MaterialTheme.typography.bodySmall,
                            color = StreamerLightGray
                        )
                    }
                    Text(
                        text = mediaItem.indexItem.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = StreamerLightGray.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Play button — explicitly styled for visibility
                Button(
                    onClick = {
                        val url = IndexApiService.BASE_URL + mediaItem.path
                        onPlayClick(url, mediaItem.title)
                    },
                    modifier = Modifier.focusRequester(focusRequester),
                    colors = ButtonDefaults.colors(
                        containerColor = StreamerRed,
                        contentColor = StreamerWhite,
                        focusedContainerColor = StreamerRed.copy(alpha = 0.8f),
                        focusedContentColor = StreamerWhite
                    )
                ) {
                    Text(
                        text = "Play",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format("%.1f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}
