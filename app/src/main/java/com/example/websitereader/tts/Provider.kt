package com.example.websitereader.tts

import kotlinx.coroutines.CompletableDeferred
import java.io.File

enum class ProgressState(val value: Int) {
    AUDIO_GENERATION(1),
    CONCATENATION(2)
}

interface Provider {
    val isReady: CompletableDeferred<Unit>

    suspend fun synthesizeTextToFile(
        text: String,
        langCode: String,
        outputFile: File,
        progressCallback: (Double, ProgressState) -> Unit
    )
}