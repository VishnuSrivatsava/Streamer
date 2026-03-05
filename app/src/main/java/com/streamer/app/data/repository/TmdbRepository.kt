package com.streamer.app.data.repository

import android.util.Log
import com.streamer.app.data.local.TmdbCacheDao
import com.streamer.app.data.local.TmdbCacheEntity
import com.streamer.app.data.model.IndexItem
import com.streamer.app.data.model.MediaItem
import com.streamer.app.data.model.TmdbImageUtil
import com.streamer.app.data.model.TmdbSearchResult
import com.streamer.app.data.remote.TmdbApiService
import com.streamer.app.data.remote.TvdbApiService
import com.streamer.app.data.remote.TvdbSearchResult
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
    private val tmdbApi: TmdbApiService = TmdbApiService.create(),
    private val tvdbApi: TvdbApiService? = TvdbApiService.create(),
    private val cacheDao: TmdbCacheDao? = null
) {
    companion object {
        private const val TAG = "TmdbRepository"
        private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    }

    private val searchCache = mutableMapOf<String, TmdbSearchResult?>()
    private val rateLimiter = Semaphore(5)

    suspend fun findMetadata(
        parsed: FilenameParser.ParsedTitle,
        isTvShow: Boolean = false
    ): TmdbSearchResult? = withContext(Dispatchers.IO) {
        val cacheKey = "${parsed.title}|${parsed.year}|$isTvShow"

        // L1: in-memory cache
        if (searchCache.containsKey(cacheKey)) return@withContext searchCache[cacheKey]

        // L2: Room persistent cache
        try {
            val minTimestamp = System.currentTimeMillis() - CACHE_TTL_MS
            val cached = cacheDao?.get(cacheKey, minTimestamp)
            if (cached != null && cached.hasResult) {
                val result = cached.toSearchResult()
                searchCache[cacheKey] = result
                return@withContext result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Room cache read failed", e)
        }

        rateLimiter.withPermit {
            var lastError: Exception? = null
            for (attempt in 0..1) {
                try {
                    var best: TmdbSearchResult? = null

                    // Try TMDb first (if API key available)
                    if (TmdbApiService.TMDB_API_KEY.isNotBlank()) {
                        val response = if (isTvShow) {
                            tmdbApi.searchTvShows(query = parsed.title)
                        } else {
                            tmdbApi.searchMovies(query = parsed.title, year = parsed.year)
                        }

                        best = pickBestMatch(response.results, parsed.title, parsed.year, isTvShow, parsed.language)

                        // Short-circuit: if we have a year-matching result, skip fallbacks
                        if (best != null && parsed.year != null && matchesYear(best, parsed.year, isTvShow)) {
                            cacheResult(cacheKey, best)
                            delay(100)
                            return@withPermit best
                        }

                        // Fallback 1: retry without year filter if no results
                        if (best == null && !isTvShow && parsed.year != null && response.results.isEmpty()) {
                            val fallback = tmdbApi.searchMovies(query = parsed.title, year = null)
                            best = pickBestMatch(fallback.results, parsed.title, parsed.year, isTvShow, parsed.language)
                        }

                        // Fallback 2: if title has spaces, retry with spaces removed
                        val noSpaces = parsed.title.replace(" ", "")
                        if (best == null && noSpaces != parsed.title) {
                            val spacesFallback = if (isTvShow) {
                                tmdbApi.searchTvShows(query = noSpaces)
                            } else {
                                tmdbApi.searchMovies(query = noSpaces, year = parsed.year)
                            }
                            best = pickBestMatch(spacesFallback.results, noSpaces, parsed.year, isTvShow, parsed.language)
                            if (best == null && !isTvShow && parsed.year != null && spacesFallback.results.isEmpty()) {
                                val f2 = tmdbApi.searchMovies(query = noSpaces, year = null)
                                best = pickBestMatch(f2.results, noSpaces, parsed.year, isTvShow, parsed.language)
                            }
                        }
                    }

                    // Fallback 3: TVDB — when TMDb has no result or no poster
                    if ((best == null || best.posterPath == null) && tvdbApi != null) {
                        val tvdbResult = tryTvdbFallback(parsed.title, isTvShow)
                        if (tvdbResult != null) {
                            best = if (best != null) {
                                // TMDb had metadata but no poster — merge TVDB poster
                                best.copy(
                                    posterPath = tvdbResult.posterPath ?: best.posterPath,
                                    backdropPath = tvdbResult.backdropPath ?: best.backdropPath,
                                    overview = best.overview ?: tvdbResult.overview
                                )
                            } else {
                                tvdbResult
                            }
                        }
                    }

                    cacheResult(cacheKey, best)
                    delay(100)
                    return@withPermit best
                } catch (e: Exception) {
                    lastError = e
                    if (attempt == 0) {
                        Log.w(TAG, "TMDb search attempt 1 failed for: ${parsed.title}, retrying...", e)
                        delay(1000)
                    }
                }
            }
            Log.w(TAG, "TMDb search failed after retry for: ${parsed.title}", lastError)
            // Don't cache errors — allow future retries when network recovers
            null
        }
    }

    private suspend fun cacheResult(cacheKey: String, result: TmdbSearchResult?) {
        searchCache[cacheKey] = result
        try {
            cacheDao?.insert(result.toCacheEntity(cacheKey))
        } catch (e: Exception) {
            Log.w(TAG, "Room cache write failed", e)
        }
    }

    /**
     * Try TVDB as a fallback metadata source.
     * TVDB image URLs are full URLs, stored directly in posterPath/backdropPath.
     * TmdbImageUtil.posterUrl() handles both full URLs and TMDb relative paths.
     */
    private fun tryTvdbFallback(title: String, isTvShow: Boolean): TmdbSearchResult? {
        val api = tvdbApi ?: return null
        try {
            val type = if (isTvShow) "series" else "movie"
            val results = api.search(title, type)
            if (results.isEmpty()) {
                // Try without type filter
                val allResults = api.search(title)
                return pickBestTvdbMatch(allResults, title, isTvShow)
            }
            return pickBestTvdbMatch(results, title, isTvShow)
        } catch (e: Exception) {
            Log.w(TAG, "TVDB fallback failed for: $title", e)
            return null
        }
    }

    private fun pickBestTvdbMatch(
        results: List<TvdbSearchResult>,
        query: String,
        isTvShow: Boolean
    ): TmdbSearchResult? {
        if (results.isEmpty()) return null

        // Prefer exact name match with an image
        val exactWithImage = results.find {
            it.name?.equals(query, ignoreCase = true) == true &&
                (it.imageUrl != null || it.thumbnail != null)
        }
        val best = exactWithImage
            ?: results.find { it.imageUrl != null || it.thumbnail != null }
            ?: results.firstOrNull()
            ?: return null

        val imageUrl = best.imageUrl ?: best.thumbnail
        val year = best.year?.take(4)?.toIntOrNull()

        return TmdbSearchResult(
            id = best.tvdbId?.toIntOrNull() ?: 0,
            title = if (!isTvShow) best.name else null,
            name = if (isTvShow) best.name else null,
            overview = best.overview,
            posterPath = imageUrl, // Full URL — TmdbImageUtil handles this
            releaseDate = if (!isTvShow && year != null) "${year}-01-01" else null,
            firstAirDate = if (isTvShow && year != null) "${year}-01-01" else null
        )
    }

    private fun matchesYear(result: TmdbSearchResult, year: Int, isTvShow: Boolean): Boolean {
        val resultYear = if (isTvShow) {
            result.firstAirDate?.take(4)?.toIntOrNull()
        } else {
            result.releaseDate?.take(4)?.toIntOrNull()
        }
        return resultYear == year
    }

    /**
     * Pick the result whose title, year, and language best match.
     * Priority (language tiers only activate when language != null):
     *   0a. Exact title + exact year + matching language
     *   0b. Normalized title + exact year + matching language
     *   1.  Exact title + exact year
     *   2.  Normalized title + exact year
     *   3.  Starts-with title + exact year
     *   4.  Any result with exact year
     *   5.  Normalized title match (no year)
     *   6.  Exact title match
     *   7.  Starts-with title
     *   8.  Fallback to first result
     */
    private fun pickBestMatch(
        results: List<TmdbSearchResult>,
        query: String,
        year: Int?,
        isTvShow: Boolean,
        language: String? = null
    ): TmdbSearchResult? {
        if (results.isEmpty()) return null
        val q = query.lowercase().trim()
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

        fun TmdbSearchResult.matchesYearLocal(): Boolean {
            if (year == null) return false
            return matchesYear(this, year, isTvShow)
        }

        fun TmdbSearchResult.matchesLanguage(): Boolean {
            if (language == null) return false
            return originalLanguage == language
        }

        // 0a. Exact title + exact year + matching language
        if (year != null && language != null) {
            results.forEach { r ->
                if (r.matchesTitle() && r.matchesYearLocal() && r.matchesLanguage()) return r
            }
        }

        // 0b. Normalized title + exact year + matching language
        if (year != null && language != null) {
            results.forEach { r ->
                if (r.matchesTitleNormalized() && r.matchesYearLocal() && r.matchesLanguage()) return r
            }
        }

        // 1. Exact title + exact year
        if (year != null) {
            results.forEach { r ->
                if (r.matchesTitle() && r.matchesYearLocal()) return r
            }
        }

        // 2. Normalized title + exact year
        if (year != null) {
            results.forEach { r ->
                if (r.matchesTitleNormalized() && r.matchesYearLocal()) return r
            }
        }

        // 3. Starts-with title + exact year
        if (year != null) {
            results.forEach { r ->
                if (r.startsWithTitle() && r.matchesYearLocal()) return r
            }
        }

        // 4. Any result with exact year
        if (year != null) {
            results.forEach { r ->
                if (r.matchesYearLocal()) return r
            }
        }

        // 5. Normalized title match (no year)
        results.forEach { r ->
            if (r.matchesTitleNormalized()) return r
        }

        // 6. Exact title match
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
                    val tmdb = if (TmdbApiService.TMDB_API_KEY.isNotBlank() || tvdbApi != null) {
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

private fun TmdbCacheEntity.toSearchResult(): TmdbSearchResult? {
    if (!hasResult) return null
    return TmdbSearchResult(
        id = tmdbId ?: 0,
        title = title,
        name = name,
        originalTitle = originalTitle,
        originalName = originalName,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        firstAirDate = firstAirDate,
        voteAverage = voteAverage
    )
}

private fun TmdbSearchResult?.toCacheEntity(key: String): TmdbCacheEntity {
    return TmdbCacheEntity(
        cacheKey = key,
        tmdbId = this?.id,
        title = this?.title,
        name = this?.name,
        originalTitle = this?.originalTitle,
        originalName = this?.originalName,
        overview = this?.overview,
        posterPath = this?.posterPath,
        backdropPath = this?.backdropPath,
        releaseDate = this?.releaseDate,
        firstAirDate = this?.firstAirDate,
        voteAverage = this?.voteAverage,
        hasResult = this != null
    )
}
