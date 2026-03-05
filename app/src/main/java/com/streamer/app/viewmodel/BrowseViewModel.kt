package com.streamer.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamer.app.StreamerApp
import com.streamer.app.data.model.MediaItem
import com.streamer.app.data.model.TmdbImageUtil
import com.streamer.app.data.remote.TmdbApiService
import com.streamer.app.data.remote.TvdbApiService
import com.streamer.app.data.remote.encodePathSegment
import com.streamer.app.data.repository.IndexRepository
import com.streamer.app.data.repository.TmdbRepository
import com.streamer.app.domain.FilenameParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val indexRepo = IndexRepository()
    private val tmdbRepo: TmdbRepository

    init {
        val dao = (application as StreamerApp).database.tmdbCacheDao()
        tmdbRepo = TmdbRepository(cacheDao = dao)
    }

    sealed class BrowseUiState {
        object Loading : BrowseUiState()
        data class Success(
            val items: List<MediaItem>,
            val breadcrumbs: List<Pair<String, String>>,
            val currentPath: String,
            val isEnriching: Boolean = false
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
                // Phase 1: Fetch index items and show immediately with parsed titles
                val items = indexRepo.listDirectoryFull(path)
                val isTv = path.startsWith("/1:/")

                val parsedItems = items
                    .filter { it.isVideo || it.isFolder }
                    .map { item ->
                        val parsed = FilenameParser.parse(item.name)
                        val encodedName = encodePathSegment(item.name)
                        val itemPath = path + encodedName + if (item.isFolder) "/" else ""
                        MediaItem(
                            indexItem = item,
                            path = itemPath,
                            title = parsed.title,
                            year = parsed.year,
                            resolution = parsed.resolution,
                            videoCodec = parsed.videoCodec,
                            audioCodec = parsed.audioCodec,
                            source = parsed.source
                        ) to parsed
                    }

                val sorted = parsedItems.map { it.first }
                    .sortedWith(
                        compareByDescending<MediaItem> { it.indexItem.isFolder }
                            .thenBy { it.indexItem.name.lowercase() }
                    )

                val hasMetadataApi = TmdbApiService.TMDB_API_KEY.isNotBlank() || TvdbApiService.TVDB_API_KEY.isNotBlank()

                // Emit immediately — user sees items right away
                _uiState.value = BrowseUiState.Success(
                    items = sorted,
                    breadcrumbs = pathStack.toList(),
                    currentPath = path,
                    isEnriching = hasMetadataApi
                )

                // Phase 2: Enrich with metadata in background, deduplicated and chunked
                if (hasMetadataApi) {
                    val enrichable = parsedItems.filter { !it.first.indexItem.isFolder }
                    val currentItems = sorted.toMutableList()
                    Log.d("BrowseVM", "Enrichment starting: ${enrichable.size} items, " +
                        "TMDB_KEY=${TmdbApiService.TMDB_API_KEY.length}ch, " +
                        "TVDB_KEY=${TvdbApiService.TVDB_API_KEY.length}ch")

                    // Deduplicate by cache key
                    val grouped = enrichable.groupBy { (_, parsed) ->
                        "${parsed.title}|${parsed.year}|${isTv || parsed.season != null}"
                    }

                    // Look up each unique title once
                    val metadataMap = mutableMapOf<String, com.streamer.app.data.model.TmdbSearchResult?>()
                    grouped.keys.toList().chunked(10).forEach { keyChunk ->
                        coroutineScope {
                            val results = keyChunk.map { key ->
                                async {
                                    val parsed = grouped[key]!!.first().second
                                    val result = tmdbRepo.findMetadata(parsed, isTv || parsed.season != null)
                                    key to result
                                }
                            }.awaitAll()
                            results.forEach { (key, result) ->
                                metadataMap[key] = result
                            }
                        }

                        // Apply results to all items sharing these keys and update UI
                        for (key in keyChunk) {
                            val tmdb = metadataMap[key] ?: continue
                            val itemsForKey = grouped[key] ?: continue
                            for ((mediaItem, _) in itemsForKey) {
                                val idx = currentItems.indexOfFirst {
                                    it.indexItem.name == mediaItem.indexItem.name
                                }
                                if (idx >= 0) {
                                    currentItems[idx] = currentItems[idx].copy(
                                        year = currentItems[idx].year
                                            ?: tmdb.releaseDate?.take(4)?.toIntOrNull()
                                            ?: tmdb.firstAirDate?.take(4)?.toIntOrNull(),
                                        posterUrl = TmdbImageUtil.posterUrl(tmdb.posterPath),
                                        backdropUrl = TmdbImageUtil.backdropUrl(tmdb.backdropPath),
                                        overview = tmdb.overview,
                                        rating = tmdb.voteAverage,
                                        tmdbId = tmdb.id
                                    )
                                }
                            }
                        }

                        // Re-emit updated list after each chunk
                        _uiState.value = BrowseUiState.Success(
                            items = currentItems.toList(),
                            breadcrumbs = pathStack.toList(),
                            currentPath = path,
                            isEnriching = true
                        )
                    }

                    // Final emit with enriching = false
                    val posterCount = currentItems.count { it.posterUrl != null }
                    Log.d("BrowseVM", "Enrichment done: $posterCount/${enrichable.size} items have posters")
                    _uiState.value = BrowseUiState.Success(
                        items = currentItems.toList(),
                        breadcrumbs = pathStack.toList(),
                        currentPath = path,
                        isEnriching = false
                    )
                }
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
