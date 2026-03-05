package com.streamer.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.streamer.app.data.model.MediaItem
import com.streamer.app.ui.components.ErrorState
import com.streamer.app.ui.components.FolderCard
import com.streamer.app.ui.components.LoadingIndicator
import com.streamer.app.ui.components.MediaCard
import com.streamer.app.ui.components.TopBar
import com.streamer.app.ui.platform.AdaptiveDimens
import com.streamer.app.ui.platform.LocalIsTv
import com.streamer.app.ui.theme.StreamerDarkGray
import com.streamer.app.ui.theme.StreamerLightGray
import com.streamer.app.ui.theme.StreamerMediumGray
import com.streamer.app.ui.theme.StreamerRed
import com.streamer.app.ui.theme.StreamerWhite
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
            val horizontalPadding = AdaptiveDimens.horizontalPadding()
            val gridMinSize = AdaptiveDimens.gridMinSize()

            // Reset search when navigating to a new directory
            var searchQuery by remember(state.currentPath) { mutableStateOf("") }

            val folders = state.items.filter { it.indexItem.isFolder }
            val files = state.items.filter { !it.indexItem.isFolder }

            val filteredFolders = remember(folders, searchQuery) {
                if (searchQuery.isBlank()) folders
                else folders.filter { it.title.contains(searchQuery, ignoreCase = true) }
            }
            val filteredFiles = remember(files, searchQuery) {
                if (searchQuery.isBlank()) files
                else files.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                        it.indexItem.name.contains(searchQuery, ignoreCase = true)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    title = categoryName,
                    breadcrumbs = state.breadcrumbs
                )

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        androidx.compose.material3.Text(
                            "Search...",
                            color = StreamerLightGray
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            androidx.compose.material3.Text(
                                text = "\u2715",
                                color = StreamerLightGray,
                                modifier = Modifier
                                    .clickable { searchQuery = "" }
                                    .padding(8.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = 4.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StreamerWhite,
                        unfocusedTextColor = StreamerWhite,
                        focusedContainerColor = StreamerDarkGray,
                        unfocusedContainerColor = StreamerDarkGray,
                        focusedBorderColor = StreamerRed,
                        unfocusedBorderColor = StreamerMediumGray,
                        cursorColor = StreamerRed
                    )
                )

                if (state.isEnriching) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = StreamerRed
                    )
                }

                if (LocalIsTv.current) {
                    TvLazyVerticalGrid(
                        columns = TvGridCells.Adaptive(minSize = gridMinSize),
                        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredFolders.size, key = { "folder_${filteredFolders[it].indexItem.name}" }) { index ->
                            val folder = filteredFolders[index]
                            FolderCard(
                                item = folder,
                                onClick = { viewModel.navigateTo(folder.title, folder.path) }
                            )
                        }
                        items(filteredFiles.size, key = { "file_${filteredFiles[it].indexItem.name}" }) { index ->
                            val file = filteredFiles[index]
                            MediaCard(item = file, onClick = { onFileClick(file) })
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize),
                        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredFolders.size, key = { "folder_${filteredFolders[it].indexItem.name}" }) { index ->
                            val folder = filteredFolders[index]
                            FolderCard(
                                item = folder,
                                onClick = { viewModel.navigateTo(folder.title, folder.path) }
                            )
                        }
                        items(filteredFiles.size, key = { "file_${filteredFiles[it].indexItem.name}" }) { index ->
                            val file = filteredFiles[index]
                            MediaCard(item = file, onClick = { onFileClick(file) })
                        }
                    }
                }
            }
        }
    }
}
