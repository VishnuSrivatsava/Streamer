# Keep data models for Gson serialization
-keep class com.streamer.app.data.model.** { *; }

# Keep Retrofit interfaces
-keep,allowobfuscation interface com.streamer.app.data.remote.TmdbApiService

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
