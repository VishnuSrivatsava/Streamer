package com.streamer.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamer.app.data.model.MediaItem
import com.streamer.app.data.repository.IndexRepository
import com.streamer.app.data.repository.TmdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowseViewModel(
    private val indexRepo: IndexRepository = IndexRepository(),
    private val tmdbRepo: TmdbRepository = TmdbRepository()
) : ViewModel() {

    sealed class BrowseUiState {
        object Loading : BrowseUiState()
        data class Success(
            val items: List<MediaItem>,
            val breadcrumbs: List<Pair<String, String>>,
            val currentPath: String
        ) : BrowseUiState()
        data class Error(val message: String) : BrowseUiState()
    }

    private val _uiState = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private val pathStack = mutableListOf<Pair<String, String>>()

    fun initIfNeeded(name: String, path: String) {
        if (pathStack.isEmpty()) {
            navigateTo(name, path)
        }
    }

    fun navigateTo(name: String, path: String) {
        pathStack.add(name to path)
        loadDirectory(path)
    }

    fun navigateBack(): Boolean {
        if (pathStack.size <= 1) return false
        pathStack.removeLast()
        val (_, path) = pathStack.last()
        loadDirectory(path)
        return true
    }

    private fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.value = BrowseUiState.Loading
            try {
                val items = indexRepo.listDirectoryFull(path)
                val isTv = path.startsWith("/1:/")
                val enriched = tmdbRepo.enrichItems(items, path, isTv)
                val sorted = enriched.sortedWith(
                    compareByDescending<MediaItem> { it.indexItem.isFolder }
                        .thenBy { it.indexItem.name.lowercase() }
                )
                _uiState.value = BrowseUiState.Success(
                    items = sorted,
                    breadcrumbs = pathStack.toList(),
                    currentPath = path
                )
            } catch (e: Exception) {
                _uiState.value = BrowseUiState.Error(e.message ?: "Failed to load directory")
            }
        }
    }

    fun retry() {
        if (pathStack.isNotEmpty()) {
            val (_, path) = pathStack.last()
            loadDirectory(path)
        }
    }
}
