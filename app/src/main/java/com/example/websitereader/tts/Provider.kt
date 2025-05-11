package com.example.websitereader.tts

import android.content.Context
import com.example.websitereader.WebsiteFetcher

interface Provider {
    suspend fun synthesizeTextToFile(
        context: Context,
        text: WebsiteFetcher.LocalizedString,
        fileName: String
    )
}