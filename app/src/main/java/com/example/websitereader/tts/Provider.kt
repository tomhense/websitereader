package com.example.websitereader.tts

import com.example.websitereader.WebsiteFetcher

enum class ProgressState(val value: Int) {
    AUDIO_GENERATION(1),
    CONCATENATION(2)
}

interface Provider {
    suspend fun synthesizeTextToFile(
        text: WebsiteFetcher.LocalizedString,
        fileName: String,
        progressCallback: (Double, ProgressState) -> Unit
    )
}