package com.streamer.app.data.repository

import android.util.Log
import com.streamer.app.data.model.IndexItem
import com.streamer.app.data.model.MediaItem
import com.streamer.app.data.model.TmdbImageUtil
import com.streamer.app.data.model.TmdbSearchResult
import com.streamer.app.data.remote.IndexApiService
import com.streamer.app.data.remote.TmdbApiService
import com.streamer.app.data.remote.encodePathSegment
import com.streamer.app.domain.FilenameParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext


class TmdbRepository(
    private val tmdbApi: TmdbApiService = TmdbApiService.create()
) {
    companion object {
        private const val TAG = "TmdbRepository"
    }

    private val searchCache = mutableMapOf<String, TmdbSearchResult?>()
    private val rateLimiter = Semaphore(5)

    suspend fun findMetadata(
        parsed: FilenameParser.ParsedTitle,
        isTvShow: Boolean = false
    ): TmdbSearchResult? = withContext(Dispatchers.IO) {
        val cacheKey = "${parsed.title}|${parsed.year}|$isTvShow"
        if (searchCache.containsKey(cacheKey)) return@withContext searchCache[cacheKey]

        rateLimiter.withPermit {
            try {
                val response = if (isTvShow) {
                    tmdbApi.searchTvShows(query = parsed.title)
                } else {
                    tmdbApi.searchMovies(query = parsed.title, year = parsed.year)
                }
                val best = response.results.firstOrNull()
                searchCache[cacheKey] = best
                delay(100) // Small delay to avoid rate limiting
                best
            } catch (e: Exception) {
                Log.w(TAG, "TMDb search failed for: ${parsed.title}", e)
                searchCache[cacheKey] = null
                null
            }
        }
    }

    suspend fun enrichItems(
        items: List<IndexItem>,
        basePath: String,
        isTvCategory: Boolean = false
    ): List<MediaItem> = coroutineScope {
        items
            .filter { it.isVideo || it.isFolder }
            .map { item ->
                async {
                    val parsed = FilenameParser.parse(item.name)
                    val tmdb = if (TmdbApiService.TMDB_API_KEY != "YOUR_TMDB_API_KEY_HERE") {
                        findMetadata(parsed, isTvCategory || parsed.season != null)
                    } else {
                        null
                    }

                    val encodedName = encodePathSegment(item.name)
                    val itemPath = basePath + encodedName + if (item.isFolder) "/" else ""

                    MediaItem(
                        indexItem = item,
                        path = itemPath,
                        title = parsed.title,
                        year = parsed.year ?: tmdb?.releaseDate?.take(4)?.toIntOrNull()
                            ?: tmdb?.firstAirDate?.take(4)?.toIntOrNull(),
                        posterUrl = TmdbImageUtil.posterUrl(tmdb?.posterPath),
                        backdropUrl = TmdbImageUtil.backdropUrl(tmdb?.backdropPath),
                        overview = tmdb?.overview,
                        rating = tmdb?.voteAverage,
                        tmdbId = tmdb?.id,
                        resolution = parsed.resolution,
                        videoCodec = parsed.videoCodec,
                        audioCodec = parsed.audioCodec,
                        source = parsed.source
                    )
                }
            }
            .awaitAll()
    }
}
