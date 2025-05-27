package com.example.websitereader.settings

import android.content.Context
import androidx.core.content.edit
import com.example.websitereader.model.ExternalTTSProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object TTSProviderStore {
    private const val PREFS_NAME = "tts_providers"
    private const val KEY = "providers"
    private val json = Json { ignoreUnknownKeys = true }

    // All your screens can observe this.
    private val _providers = MutableStateFlow<List<ExternalTTSProvider>>(emptyList())
    val providers: StateFlow<List<ExternalTTSProvider>> get() = _providers

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        val list = if (raw != null) {
            try {
                json.decodeFromString<List<ExternalTTSProvider>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()
        _providers.value = list
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = json.encodeToString(_providers.value)
        prefs.edit { putString(KEY, raw) }
    }

    fun addProvider(context: Context, provider: ExternalTTSProvider) {
        _providers.value += provider
        save(context)
    }

    fun updateProvider(context: Context, index: Int, provider: ExternalTTSProvider) {
        val list = _providers.value.toMutableList()
        if (index in list.indices) {
            list[index] = provider
            _providers.value = list
            save(context)
        }
    }

    fun removeProvider(context: Context, index: Int) {
        val list = _providers.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _providers.value = list
            save(context)
        }
    }

    fun setProviders(context: Context, newList: List<ExternalTTSProvider>) {
        _providers.value = newList
        save(context)
    }
}