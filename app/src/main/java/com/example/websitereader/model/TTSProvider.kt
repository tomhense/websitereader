package com.example.websitereader.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface TTSProvider

@Serializable
@SerialName("system")
data object SystemTTSProvider : TTSProvider

@Serializable
@SerialName("external")
data class ExternalTTSProvider(
    var name: String,
    var apiBaseUrl: String,
    var apiKey: String,
    var voiceName: String,
    var pricePer1MCharacters: Double,
    var maxChunkLength: Int,
    var modelName: String,
    var asyncSynthesization: Boolean,
    var audioFormat: String
) : TTSProvider