package com.streamer.app.data.repository

import android.util.Log
import com.streamer.app.data.model.IndexItem
import com.streamer.app.data.model.MediaItem
import com.streamer.app.data.model.TmdbImageUtil
import com.streamer.app.data.model.TmdbSearchResult
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

                var best = pickBestMatch(response.results, parsed.title, parsed.year, isTvShow)

                // Fallback 1: retry without year filter if no results
                if (best == null && !isTvShow && parsed.year != null && response.results.isEmpty()) {
                    val fallback = tmdbApi.searchMovies(query = parsed.title, year = null)
                    best = pickBestMatch(fallback.results, parsed.title, parsed.year, isTvShow)
                }

                // Fallback 2: if title has spaces, retry with spaces removed
                // Handles cases like "3 BHK" (from "3-BHK") → "3BHK"
                val noSpaces = parsed.title.replace(" ", "")
                if (best == null && noSpaces != parsed.title) {
                    val spacesFallback = if (isTvShow) {
                        tmdbApi.searchTvShows(query = noSpaces)
                    } else {
                        tmdbApi.searchMovies(query = noSpaces, year = parsed.year)
                    }
                    best = pickBestMatch(spacesFallback.results, noSpaces, parsed.year, isTvShow)
                    // Also try without year
                    if (best == null && !isTvShow && parsed.year != null && spacesFallback.results.isEmpty()) {
                        val f2 = tmdbApi.searchMovies(query = noSpaces, year = null)
                        best = pickBestMatch(f2.results, noSpaces, parsed.year, isTvShow)
                    }
                }

                searchCache[cacheKey] = best
                delay(100)
                best
            } catch (e: Exception) {
                Log.w(TAG, "TMDb search failed for: ${parsed.title}", e)
                searchCache[cacheKey] = null
                null
            }
        }
    }

    /**
     * Pick the result whose title and year best match.
     * Priority: exact title + exact year > exact title > starts-with title > first result.
     */
    private fun pickBestMatch(
        results: List<TmdbSearchResult>,
        query: String,
        year: Int?,
        isTvShow: Boolean
    ): TmdbSearchResult? {
        if (results.isEmpty()) return null
        val q = query.lowercase().trim()
        // Normalized version: only keep letters and digits for fuzzy comparison
        // e.g. "Pushpa 2 - The Rule" and "Pushpa 2 The Rule" both become "pushpa2therule"
        val qNorm = q.replace(Regex("[^a-z0-9]"), "")

        fun TmdbSearchResult.matchesTitle(): Boolean {
            val titles = listOfNotNull(title, name, originalTitle, originalName)
            return titles.any { it.equals(query, ignoreCase = true) }
        }

        fun TmdbSearchResult.matchesTitleNormalized(): Boolean {
            val titles = listOfNotNull(title, name, originalTitle, originalName)
            return titles.any {
                it.lowercase().replace(Regex("[^a-z0-9]"), "") == qNorm
            }
        }

        fun TmdbSearchResult.startsWithTitle(): Boolean {
            val titles = listOfNotNull(title, name, originalTitle, originalName)
            return titles.any { it.lowercase().startsWith(q) }
        }

        fun TmdbSearchResult.matchesYear(): Boolean {
            if (year == null) return false
            val resultYear = if (isTvShow) {
                firstAirDate?.take(4)?.toIntOrNull()
            } else {
                releaseDate?.take(4)?.toIntOrNull()
            }
            return resultYear == year
        }

        // 1. Exact title + exact year
        if (year != null) {
            results.forEach { r ->
                if (r.matchesTitle() && r.matchesYear()) return r
            }
        }

        // 2. Normalized title + exact year (handles "Pushpa 2 The Rule" vs "Pushpa 2 - The Rule")
        if (year != null) {
            results.forEach { r ->
                if (r.matchesTitleNormalized() && r.matchesYear()) return r
            }
        }

        // 3. Starts-with title + exact year
        if (year != null) {
            results.forEach { r ->
                if (r.startsWithTitle() && r.matchesYear()) return r
            }
        }

        // 4. Any result with exact year (for generic titles like "Single")
        if (year != null) {
            results.forEach { r ->
                if (r.matchesYear()) return r
            }
        }

        // 5. Normalized title match (no year)
        results.forEach { r ->
            if (r.matchesTitleNormalized()) return r
        }

        // 6. Exact title match (no year available)
        results.forEach { r ->
            if (r.matchesTitle()) return r
        }

        // 7. Starts-with title
        results.forEach { r ->
            if (r.startsWithTitle()) return r
        }

        // 8. Fallback to first result
        return results.firstOrNull()
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
                    val tmdb = if (TmdbApiService.TMDB_API_KEY.isNotBlank()) {
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
