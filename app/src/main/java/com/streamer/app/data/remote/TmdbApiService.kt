package com.streamer.app.data.remote

import com.streamer.app.data.model.TmdbMovieDetail
import com.streamer.app.data.model.TmdbSearchResponse
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TmdbApiService {

    @GET("3/search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String = TMDB_API_KEY,
        @Query("query") query: String,
        @Query("year") year: Int? = null,
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("3/search/tv")
    suspend fun searchTvShows(
        @Query("api_key") apiKey: String = TMDB_API_KEY,
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("3/movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String = TMDB_API_KEY
    ): TmdbMovieDetail

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/"

        // TODO: Replace with your actual TMDb API key
        // Get one free at https://www.themoviedb.org/settings/api
        const val TMDB_API_KEY = "YOUR_TMDB_API_KEY_HERE"

        fun create(): TmdbApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(
                    OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()
                )
                .build()
                .create(TmdbApiService::class.java)
        }
    }
}
