package com.streamer.app.data.remote

import android.util.Base64
import android.util.Log
import java.net.URLDecoder

object IndexDecoder {
    private const val TAG = "IndexDecoder"
    private const val PREFIX = "Y29kZWlzcHJvdGVjdGVk"
    private const val SUFFIX = "YmFzZTY0aXNleGNsdWRlZA=="

    fun decode(encryptedResponse: String): String {
        return try {
            val stripped = read(encryptedResponse)
            gdidecode(stripped)
        } catch (e: Exception) {
            Log.w(TAG, "Primary decode failed, trying fallback", e)
            try {
                val stripped = readFallback(encryptedResponse)
                gdidecode(stripped)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback decode also failed", e2)
                throw e2
            }
        }
    }

    private fun read(data: String): String {
        val withoutPrefix = if (data.startsWith(PREFIX)) {
            data.substring(PREFIX.length)
        } else {
            data
        }
        val withoutSuffix = if (withoutPrefix.endsWith(SUFFIX)) {
            withoutPrefix.substring(0, withoutPrefix.length - SUFFIX.length)
        } else {
            withoutPrefix
        }
        return withoutSuffix.reversed()
    }

    private fun readFallback(data: String): String {
        val reversed = data.reversed()
        val prefixLen = PREFIX.length
        val suffixLen = SUFFIX.length
        return reversed.substring(suffixLen, reversed.length - prefixLen)
    }

    private fun gdidecode(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val decoded = String(bytes, Charsets.UTF_8)
        return try {
            // The server uses encodeURIComponent() which does NOT encode '+'.
            // Java's URLDecoder.decode() treats '+' as space (x-www-form-urlencoded),
            // but we need literal '+'. Escape them before decoding.
            URLDecoder.decode(decoded.replace("+", "%2B"), "UTF-8")
        } catch (e: Exception) {
            decoded
        }
    }
}
