package com.streamer.app.data.remote

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.streamer.app.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson

data class TvdbLoginResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("data") val data: TvdbTokenData?
)

data class TvdbTokenData(
    @SerializedName("token") val token: String?
)

data class TvdbSearchResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("data") val data: List<TvdbSearchResult>?
)

data class TvdbSearchResult(
    @SerializedName("objectID") val objectId: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("thumbnail") val thumbnail: String?,
    @SerializedName("year") val year: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("tvdb_id") val tvdbId: String?,
    @SerializedName("primary_type") val primaryType: String?
)

class TvdbApiService private constructor(
    private val apiKey: String
) {
    companion object {
        private const val TAG = "TvdbApiService"
        private const val BASE_URL = "https://api4.thetvdb.com/v4"
        val TVDB_API_KEY: String = BuildConfig.TVDB_API_KEY

        fun create(): TvdbApiService? {
            return if (TVDB_API_KEY.isNotBlank()) TvdbApiService(TVDB_API_KEY) else null
        }
    }

    private val client = NetworkModule.client

    private val gson = Gson()

    @Volatile
    private var token: String? = null

    private fun login(): String? {
        if (token != null) return token

        try {
            val body = """{"apikey":"$apiKey"}"""
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/login")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: return null
            val loginResponse = gson.fromJson(json, TvdbLoginResponse::class.java)
            token = loginResponse.data?.token
            return token
        } catch (e: Exception) {
            Log.w(TAG, "TVDB login failed", e)
            return null
        }
    }

    fun search(query: String, type: String? = null): List<TvdbSearchResult> {
        val jwt = login() ?: return emptyList()

        try {
            val urlBuilder = StringBuilder("$BASE_URL/search?query=${java.net.URLEncoder.encode(query, "UTF-8")}")
            if (type != null) {
                urlBuilder.append("&type=$type")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .addHeader("Authorization", "Bearer $jwt")
                .build()
            val response = client.newCall(request).execute()

            if (response.code == 401) {
                // Token expired, retry with fresh login
                token = null
                val newJwt = login() ?: return emptyList()
                val retryRequest = Request.Builder()
                    .url(urlBuilder.toString())
                    .get()
                    .addHeader("Authorization", "Bearer $newJwt")
                    .build()
                val retryResponse = client.newCall(retryRequest).execute()
                val json = retryResponse.body?.string() ?: return emptyList()
                val searchResponse = gson.fromJson(json, TvdbSearchResponse::class.java)
                return searchResponse.data ?: emptyList()
            }

            val json = response.body?.string() ?: return emptyList()
            val searchResponse = gson.fromJson(json, TvdbSearchResponse::class.java)
            return searchResponse.data ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "TVDB search failed for: $query", e)
            return emptyList()
        }
    }
}
