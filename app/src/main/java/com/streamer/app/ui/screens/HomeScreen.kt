package com.streamer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamer.app.ui.components.ErrorState
import com.streamer.app.ui.components.LoadingIndicator
import com.streamer.app.ui.platform.AdaptiveCard
import com.streamer.app.ui.platform.AdaptiveDimens
import com.streamer.app.ui.platform.LocalIsTv
import com.streamer.app.ui.theme.StreamerDarkGray
import com.streamer.app.ui.theme.StreamerRed
import com.streamer.app.viewmodel.HomeViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onCategoryClick: (name: String, path: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is HomeViewModel.HomeUiState.Loading -> LoadingIndicator()
        is HomeViewModel.HomeUiState.Error -> ErrorState(
            message = state.message,
            onRetry = { viewModel.loadHome() }
        )
        is HomeViewModel.HomeUiState.Success -> {
            val horizontalPadding = AdaptiveDimens.horizontalPadding()
            val gridMinSize = AdaptiveDimens.homeGridMinSize()
            val isTv = LocalIsTv.current
            val firstItemFocus = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                if (isTv) {
                    try { firstItemFocus.requestFocus() } catch (_: Exception) {}
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Streamer",
                    style = MaterialTheme.typography.headlineLarge,
                    color = StreamerRed,
                    modifier = Modifier.padding(start = horizontalPadding, top = 32.dp, bottom = 24.dp)
                )

                if (LocalIsTv.current) {
                    TvLazyVerticalGrid(
                        columns = TvGridCells.Adaptive(minSize = gridMinSize),
                        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(state.drives.size, key = { state.drives[it].path }) { index ->
                            val drive = state.drives[index]
                            DriveCard(
                                name = drive.name,
                                onClick = { onCategoryClick(drive.name, drive.path) },
                                modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize),
                        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(state.drives.size, key = { state.drives[it].path }) { index ->
                            val drive = state.drives[index]
                            DriveCard(name = drive.name, onClick = { onCategoryClick(drive.name, drive.path) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DriveCard(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AdaptiveCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    StreamerDarkGray,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
