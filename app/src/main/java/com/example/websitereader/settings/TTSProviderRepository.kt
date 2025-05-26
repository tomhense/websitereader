package com.example.websitereader.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.websitereader.model.ExternalTTSProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore("tts_provider_entries")

object TTSProviderRepository {
    private val PREFS_KEY = stringPreferencesKey("my_entries")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(context: Context, entries: List<ExternalTTSProvider>) {
        val serialized =
            json.encodeToString(ListSerializer(ExternalTTSProvider.serializer()), entries)
        withContext(Dispatchers.IO) {
            // Save to DataStore, not SharedPreferences!
            context.applicationContext.dataStore.edit { prefs ->
                prefs[PREFS_KEY] = serialized
            }
        }
    }

    fun load(context: Context): Flow<List<ExternalTTSProvider>> =
        context.applicationContext.dataStore.data.map { prefs: Preferences ->
            val serialized = prefs[PREFS_KEY]
            if (serialized != null) {
                try {
                    json.decodeFromString<List<ExternalTTSProvider>>(serialized)
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
}