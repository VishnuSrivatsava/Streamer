package com.streamer.app.data.model

import com.google.gson.annotations.SerializedName

data class TmdbSearchResponse(
    @SerializedName("page") val page: Int,
    @SerializedName("results") val results: List<TmdbSearchResult>,
    @SerializedName("total_results") val totalResults: Int,
    @SerializedName("total_pages") val totalPages: Int
)

data class TmdbSearchResult(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("original_name") val originalName: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("genre_ids") val genreIds: List<Int>? = null,
    @SerializedName("original_language") val originalLanguage: String? = null
) {
    val displayTitle: String
        get() = title ?: name ?: ""
}

data class TmdbMovieDetail(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("runtime") val runtime: Int? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("tagline") val tagline: String? = null,
    @SerializedName("genres") val genres: List<TmdbGenre>? = null
)

data class TmdbGenre(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)

object TmdbImageUtil {
    private const val BASE = "https://image.tmdb.org/t/p/"

    fun posterUrl(path: String?, size: String = "w342"): String? =
        path?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http")) it else "$BASE$size$it"
        }

    fun backdropUrl(path: String?, size: String = "w780"): String? =
        path?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http")) it else "$BASE$size$it"
        }
}
