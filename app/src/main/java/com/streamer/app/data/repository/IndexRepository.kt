package com.streamer.app.data.repository

import android.util.Log
import com.streamer.app.data.model.IndexItem
import com.streamer.app.data.remote.IndexApiService
import com.streamer.app.data.remote.IndexWebViewBridge

class IndexRepository(
    private val apiService: IndexApiService = IndexApiService(),
    private var webViewBridge: IndexWebViewBridge? = null
) {
    companion object {
        private const val TAG = "IndexRepository"
    }

    private val cache = mutableMapOf<String, List<IndexItem>>()

    suspend fun listDirectory(path: String, forceRefresh: Boolean = false): List<IndexItem> {
        if (!forceRefresh && cache.containsKey(path)) {
            return cache[path]!!
        }

        return try {
            val result = apiService.listDirectory(path)
            cache[path] = result.items
            result.items
        } catch (e: Exception) {
            Log.w(TAG, "Native decode failed for $path", e)
            try {
                val items = webViewBridge?.listDirectory(path) ?: throw e
                cache[path] = items
                items
            } catch (e2: Exception) {
                Log.e(TAG, "WebView fallback also failed for $path", e2)
                throw e2
            }
        }
    }

    suspend fun listDirectoryFull(path: String, forceRefresh: Boolean = false): List<IndexItem> {
        if (!forceRefresh && cache.containsKey(path)) {
            return cache[path]!!
        }

        val allItems = mutableListOf<IndexItem>()
        var pageToken: String? = null
        var pageIndex = 0

        try {
            do {
                val result = apiService.listDirectory(path, pageToken, pageIndex)
                allItems.addAll(result.items)
                pageToken = result.nextPageToken
                pageIndex++
            } while (pageToken != null && pageToken.isNotEmpty())
        } catch (e: Exception) {
            if (allItems.isEmpty()) {
                Log.w(TAG, "Native list failed, trying WebView for $path", e)
                val items = webViewBridge?.listDirectory(path) ?: throw e
                cache[path] = items
                return items
            }
        }

        cache[path] = allItems
        return allItems
    }

    fun buildStreamUrl(basePath: String, fileName: String): String {
        return apiService.buildStreamUrl(basePath, fileName)
    }

    fun clearCache() = cache.clear()

    fun setWebViewBridge(bridge: IndexWebViewBridge) {
        webViewBridge = bridge
    }
}
