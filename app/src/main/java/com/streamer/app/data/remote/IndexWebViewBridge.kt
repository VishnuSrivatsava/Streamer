package com.streamer.app.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.streamer.app.data.model.IndexItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class IndexWebViewBridge(private val context: Context) {
    companion object {
        private const val TAG = "IndexWebViewBridge"
        private const val EXTRACTION_SCRIPT = """
            (function() {
                var items = [];
                var links = document.querySelectorAll('a.list-group-item, .list-group a');
                if (links.length === 0) {
                    links = document.querySelectorAll('a[href]');
                }
                links.forEach(function(el) {
                    var name = (el.querySelector('.list-title') || el.querySelector('.file-name') || el).textContent.trim();
                    if (!name || name === '' || name === '..') return;
                    var href = el.getAttribute('href') || '';
                    var isFolder = href.endsWith('/');
                    var badges = el.querySelectorAll('.badge, .file-size');
                    var size = badges.length > 0 ? badges[0].textContent.trim() : '';
                    items.push({
                        name: name,
                        mimeType: isFolder ? 'application/vnd.google-apps.folder' : 'unknown',
                        size: size,
                        modifiedTime: '',
                        id: ''
                    });
                });
                AndroidBridge.onItemsExtracted(JSON.stringify(items));
            })();
        """
    }

    private var webView: WebView? = null
    private var currentDeferred: CompletableDeferred<List<IndexItem>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize() {
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            addJavascriptInterface(JsBridge(), "AndroidBridge")
        }
    }

    suspend fun listDirectory(path: String): List<IndexItem> {
        val deferred = CompletableDeferred<List<IndexItem>>()
        currentDeferred = deferred

        withContext(Dispatchers.Main) {
            val wv = webView ?: run {
                initialize()
                webView!!
            }
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(TAG, "Page finished loading: $url")
                    view?.postDelayed({
                        view.evaluateJavascript(EXTRACTION_SCRIPT, null)
                    }, 2000)
                }
            }
            wv.loadUrl("${IndexApiService.BASE_URL}$path")
        }

        return withTimeout(20_000) { deferred.await() }
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onItemsExtracted(jsonArray: String) {
            Log.d(TAG, "Extracted items: ${jsonArray.take(200)}")
            try {
                val items = Gson().fromJson(jsonArray, Array<IndexItem>::class.java).toList()
                currentDeferred?.complete(items)
            } catch (e: Exception) {
                currentDeferred?.completeExceptionally(e)
            }
        }
    }

    fun destroy() {
        webView?.destroy()
        webView = null
    }
}
