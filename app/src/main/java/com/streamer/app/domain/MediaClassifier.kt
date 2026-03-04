package com.streamer.app.domain

import com.streamer.app.data.model.IndexItem

object MediaClassifier {
    enum class MediaType { VIDEO, AUDIO, IMAGE, SUBTITLE, FOLDER, OTHER }

    private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "webm", "mov", "flv", "wmv", "m4v", "ts", "mpg", "mpeg")
    private val AUDIO_EXT = setOf("mp3", "flac", "aac", "ogg", "wav", "m4a", "wma", "opus")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val SUBTITLE_EXT = setOf("srt", "sub", "ass", "ssa", "vtt")

    fun classify(item: IndexItem): MediaType {
        if (item.isFolder) return MediaType.FOLDER
        val ext = item.name.substringAfterLast('.', "").lowercase()
        return when {
            ext in VIDEO_EXT -> MediaType.VIDEO
            ext in AUDIO_EXT -> MediaType.AUDIO
            ext in IMAGE_EXT -> MediaType.IMAGE
            ext in SUBTITLE_EXT -> MediaType.SUBTITLE
            else -> MediaType.OTHER
        }
    }
}
