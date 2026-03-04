package com.streamer.app.viewmodel

import androidx.lifecycle.ViewModel

class PlayerViewModel : ViewModel() {
    var streamUrl: String = ""
        private set
    var title: String = ""
        private set
    var resumePosition: Long = 0L

    fun setMedia(url: String, mediaTitle: String) {
        streamUrl = url
        title = mediaTitle
    }
}
