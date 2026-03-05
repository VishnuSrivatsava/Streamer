package com.streamer.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.streamer.app.data.local.AppDatabase
import com.streamer.app.data.remote.NetworkModule
import com.streamer.app.ui.platform.DeviceUtils

class StreamerApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()

        val isTv = DeviceUtils.isTv(this)

        val imageLoader = ImageLoader.Builder(this)
            .crossfade(300)
            .respectCacheHeaders(false)
            .allowHardware(!isTv)
            .okHttpClient { NetworkModule.client }
            .logger(if (BuildConfig.DEBUG) DebugLogger() else null)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .build()

        Coil.setImageLoader(imageLoader)
    }
}
