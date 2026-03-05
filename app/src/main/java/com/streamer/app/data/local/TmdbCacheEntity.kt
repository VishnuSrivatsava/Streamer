package com.streamer.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tmdb_cache")
data class TmdbCacheEntity(
    @PrimaryKey val cacheKey: String,
    val tmdbId: Int?,
    val title: String?,
    val name: String?,
    val originalTitle: String?,
    val originalName: String?,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val firstAirDate: String?,
    val voteAverage: Double?,
    val hasResult: Boolean,
    val cachedAt: Long = System.currentTimeMillis()
)
