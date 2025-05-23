package com.example.websitereader.settings

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class TTSProviderEntry(
    var name: String,
    var apiBaseUrl: String,
    var apiKey: String,
    var voiceName: String,
    var pricePer1MCharacters: Double,
    var maxChunkLength: Int,
    var modelName: String,
    var asyncSynthesization: Boolean,
    var audioFormat: String
)

object TTSProviderEntryStorage {
    private const val PREFS_KEY = "my_entries"
    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, entries: List<TTSProviderEntry>) {
        val serialized = json.encodeToString(
            ListSerializer(TTSProviderEntry.serializer()),
            entries
        )
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit { putString(PREFS_KEY, serialized) }
    }

    fun toJson(entry: TTSProviderEntry): String {
        val serialized = json.encodeToString(
            TTSProviderEntry.serializer(),
            entry
        )
        return serialized
    }

    fun fromJson(src: String): TTSProviderEntry {
        val entry = json.decodeFromString(
            TTSProviderEntry.serializer(),
            src
        )
        return entry
    }

    fun load(context: Context): List<TTSProviderEntry> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val serialized = prefs.getString(PREFS_KEY, null)
        return if (serialized != null) {
            try {
                json.decodeFromString<List<TTSProviderEntry>>(serialized)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
}