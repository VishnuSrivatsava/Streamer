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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamer.app.data.model.MediaItem
import com.streamer.app.data.remote.IndexApiService
import com.streamer.app.ui.platform.AdaptiveButton
import com.streamer.app.ui.platform.AdaptiveDimens
import com.streamer.app.ui.platform.LocalIsTv
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
    val isTv = LocalIsTv.current

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

    if (isTv) {
        TvDetailLayout(mediaItem, uiState, focusRequester, onPlayClick)
    } else {
        PhoneDetailLayout(mediaItem, uiState, focusRequester, onPlayClick)
    }
}

// ── TV Layout: Horizontal Row (poster left, metadata right) ──

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TvDetailLayout(
    mediaItem: MediaItem,
    uiState: DetailViewModel.DetailUiState,
    focusRequester: FocusRequester,
    onPlayClick: (String, String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        DetailBackground(mediaItem)

        val contentPadding = AdaptiveDimens.horizontalPadding()
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            MetadataContent(
                mediaItem = mediaItem,
                uiState = uiState,
                focusRequester = focusRequester,
                onPlayClick = onPlayClick,
                titleStyle = MaterialTheme.typography.headlineLarge,
                overviewMaxLines = 5,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Phone Layout: Vertical scroll (backdrop top, metadata below) ──

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PhoneDetailLayout(
    mediaItem: MediaItem,
    uiState: DetailViewModel.DetailUiState,
    focusRequester: FocusRequester,
    onPlayClick: (String, String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StreamerBlack)
            .verticalScroll(scrollState)
    ) {
        // Backdrop at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
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
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    StreamerBlack.copy(alpha = 0.6f),
                                    StreamerBlack
                                ),
                                startY = 100f
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(StreamerMediumGray, StreamerBlack)
                            )
                        )
                )
            }
        }

        // Metadata below backdrop
        MetadataContent(
            mediaItem = mediaItem,
            uiState = uiState,
            focusRequester = focusRequester,
            onPlayClick = onPlayClick,
            titleStyle = MaterialTheme.typography.headlineMedium,
            overviewMaxLines = 10,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(32.dp))
    }
}

// ── Shared Metadata Content ──

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MetadataContent(
    mediaItem: MediaItem,
    uiState: DetailViewModel.DetailUiState,
    focusRequester: FocusRequester,
    onPlayClick: (String, String) -> Unit,
    titleStyle: TextStyle,
    overviewMaxLines: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title
        Text(
            text = mediaItem.title,
            style = titleStyle,
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
                maxLines = overviewMaxLines
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

        // Play button
        AdaptiveButton(
            onClick = {
                val url = IndexApiService.BASE_URL + mediaItem.path
                onPlayClick(url, mediaItem.title)
            },
            modifier = Modifier.focusRequester(focusRequester),
            containerColor = StreamerRed,
            contentColor = StreamerWhite
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

// ── TV Background (full-screen backdrop with gradients) ──

@Composable
private fun DetailBackground(mediaItem: MediaItem) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(StreamerMediumGray, StreamerBlack)
                    )
                )
        )
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
