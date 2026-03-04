package com.streamer.app.data.model

data class MediaItem(
    val indexItem: IndexItem,
    val path: String,
    val title: String,
    val year: Int? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val overview: String? = null,
    val rating: Double? = null,
    val tmdbId: Int? = null,
    val resolution: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val source: String? = null
)
