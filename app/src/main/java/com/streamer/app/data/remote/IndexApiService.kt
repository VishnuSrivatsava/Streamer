package com.streamer.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.streamer.app.data.model.IndexItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

import java.util.concurrent.TimeUnit

class IndexApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val gson: Gson = Gson()
) {
    companion object {
        const val TAG = "IndexApiService"
        const val BASE_URL = "https://myindex.21h51a05l9.workers.dev"
    }

    suspend fun listDirectory(
        path: String,
        pageToken: String? = null,
        pageIndex: Int = 0
    ): IndexListResult = withContext(Dispatchers.IO) {
        val url = "$BASE_URL$path"
        Log.d(TAG, "Listing directory: $url (page=$pageIndex)")

        val formBody = FormBody.Builder()
            .add("password", "")
            .add("page_token", pageToken ?: "")
            .add("page_index", pageIndex.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw IOException("Empty response from $url")

        val json = IndexDecoder.decode(body)
        Log.d(TAG, "Decoded JSON (first 200 chars): ${json.take(200)}")

        val parsed = gson.fromJson(json, IndexApiResponse::class.java)
        IndexListResult(
            items = parsed.data?.files ?: emptyList(),
            nextPageToken = parsed.data?.nextPageToken
        )
    }

    suspend fun discoverDrives(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(BASE_URL).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            val regex = Regex("""window\.drive_names\s*=\s*JSON\.parse\('(.+?)'\)""")
            val match = regex.find(html)
            if (match != null) {
                val jsonArray = match.groupValues[1]
                val names: List<String> = gson.fromJson(jsonArray, Array<String>::class.java).toList()
                names.mapIndexed { index, name -> name to "/$index:/" }
            } else {
                defaultDrives()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Drive discovery failed, using defaults", e)
            defaultDrives()
        }
    }

    private fun defaultDrives(): List<Pair<String, String>> = listOf(
        "Movies" to "/0:/",
        "TV Shows" to "/1:/",
        "Courses" to "/2:/",
        "Randoms" to "/3:/",
        "MyDrive" to "/4:/"
    )

    fun buildStreamUrl(basePath: String, fileName: String): String {
        val encodedName = encodePathSegment(fileName)
        return "$BASE_URL$basePath$encodedName"
    }
}

data class IndexApiResponse(
    @SerializedName("data") val data: IndexDataWrapper? = null,
    @SerializedName("nextPageToken") val nextPageToken: String? = null,
    @SerializedName("curPageIndex") val curPageIndex: Int? = null
)

data class IndexDataWrapper(
    @SerializedName("files") val files: List<IndexItem>? = null,
    @SerializedName("nextPageToken") val nextPageToken: String? = null
)

data class IndexListResult(
    val items: List<IndexItem>,
    val nextPageToken: String?
)

/**
 * Encode a filename for use in a URL path segment.
 * Only encodes characters with special meaning in URLs (space, #, ?).
 * Leaves everything else (parentheses, brackets, +, &, etc.) raw —
 * matching the Bhadoo Drive Index web UI URL format.
 */
fun encodePathSegment(name: String): String {
    return name
        .replace("%", "%25")
        .replace(" ", "%20")
        .replace("#", "%23")
        .replace("?", "%3F")
}
