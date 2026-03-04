package com.streamer.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamer.app.data.model.MediaItem
import com.streamer.app.data.model.TmdbMovieDetail
import com.streamer.app.data.remote.TmdbApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    private val tmdbApi: TmdbApiService = TmdbApiService.create()
) : ViewModel() {

    data class DetailUiState(
        val mediaItem: MediaItem? = null,
        val detail: TmdbMovieDetail? = null,
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadDetail(mediaItem: MediaItem) {
        _uiState.value = DetailUiState(mediaItem = mediaItem, isLoading = true)
        viewModelScope.launch {
            try {
                val tmdbId = mediaItem.tmdbId
                if (tmdbId != null && TmdbApiService.TMDB_API_KEY.isNotBlank()) {
                    val detail = tmdbApi.getMovieDetails(tmdbId)
                    _uiState.value = _uiState.value.copy(detail = detail, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
