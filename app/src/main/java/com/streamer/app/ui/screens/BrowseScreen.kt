package com.streamer.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.streamer.app.data.model.MediaItem
import com.streamer.app.ui.components.ErrorState
import com.streamer.app.ui.components.FolderCard
import com.streamer.app.ui.components.LoadingIndicator
import com.streamer.app.ui.components.MediaCard
import com.streamer.app.ui.components.TopBar
import com.streamer.app.viewmodel.BrowseViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowseScreen(
    categoryName: String,
    categoryPath: String,
    viewModel: BrowseViewModel = viewModel(),
    onFileClick: (MediaItem) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initIfNeeded(categoryName, categoryPath)
    }

    BackHandler {
        if (!viewModel.navigateBack()) {
            onBack()
        }
    }

    when (val state = uiState) {
        is BrowseViewModel.BrowseUiState.Loading -> LoadingIndicator()
        is BrowseViewModel.BrowseUiState.Error -> ErrorState(
            message = state.message,
            onRetry = { viewModel.retry() }
        )
        is BrowseViewModel.BrowseUiState.Success -> {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    title = categoryName,
                    breadcrumbs = state.breadcrumbs
                )

                val folders = state.items.filter { it.indexItem.isFolder }
                val files = state.items.filter { !it.indexItem.isFolder }

                TvLazyVerticalGrid(
                    columns = TvGridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(folders, key = { "folder_${it.indexItem.name}" }) { folder ->
                        FolderCard(
                            item = folder,
                            onClick = { viewModel.navigateTo(folder.title, folder.path) }
                        )
                    }

                    items(files, key = { "file_${it.indexItem.name}" }) { file ->
                        MediaCard(
                            item = file,
                            onClick = { onFileClick(file) }
                        )
                    }
                }
            }
        }
    }
}
