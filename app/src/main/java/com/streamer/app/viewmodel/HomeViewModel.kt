package com.streamer.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamer.app.data.remote.IndexApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val apiService: IndexApiService = IndexApiService()
) : ViewModel() {

    data class DriveInfo(val name: String, val path: String)

    sealed class HomeUiState {
        object Loading : HomeUiState()
        data class Success(val drives: List<DriveInfo>) : HomeUiState()
        data class Error(val message: String) : HomeUiState()
    }

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHome()
    }

    fun loadHome() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val drives = apiService.discoverDrives()
                if (drives.isNotEmpty()) {
                    _uiState.value = HomeUiState.Success(
                        drives.map { (name, path) -> DriveInfo(name, path) }
                    )
                } else {
                    _uiState.value = HomeUiState.Error("No drives found.")
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load drives")
            }
        }
    }
}
