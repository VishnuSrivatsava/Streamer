package com.streamer.app.data.model

import com.google.gson.annotations.SerializedName

data class IndexItem(
    @SerializedName("name") val name: String,
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("size") val size: String? = null,
    @SerializedName("modifiedTime") val modifiedTime: String? = null,
    @SerializedName("id") val id: String = ""
) {
    val isFolder: Boolean
        get() = mimeType == "application/vnd.google-apps.folder"

    val isVideo: Boolean
        get() = mimeType.startsWith("video/") ||
                name.endsWith(".mkv", true) ||
                name.endsWith(".mp4", true) ||
                name.endsWith(".avi", true) ||
                name.endsWith(".webm", true) ||
                name.endsWith(".mov", true) ||
                name.endsWith(".flv", true) ||
                name.endsWith(".m4v", true) ||
                name.endsWith(".ts", true)

    val sizeBytes: Long?
        get() = size?.toLongOrNull()
}
