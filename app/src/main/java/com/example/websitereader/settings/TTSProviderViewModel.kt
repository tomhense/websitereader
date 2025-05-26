package com.example.websitereader.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.websitereader.model.ExternalTTSProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

open class TTSProviderViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TTSProviderRepository
    private val _providers = MutableStateFlow<List<ExternalTTSProvider>>(emptyList())
    val providers: StateFlow<List<ExternalTTSProvider>> = _providers

    init {
        viewModelScope.launch {
            repo.load(getApplication()).collect { entries: List<ExternalTTSProvider> ->
                _providers.value = entries
            }
        }
    }

    /** Save whole list if you want externally (optional) **/
    fun saveProviders(entries: List<ExternalTTSProvider>) {
        viewModelScope.launch {
            repo.save(getApplication(), entries)
        }
    }

    /** --- Recommended: Mutations via these helpers --- **/

    fun addProvider(provider: ExternalTTSProvider) {
        viewModelScope.launch {
            val updatedList = _providers.value.toMutableList().apply { add(provider) }
            repo.save(getApplication(), updatedList)
            _providers.value = updatedList
        }
    }

    fun updateProvider(index: Int, newProvider: ExternalTTSProvider) {
        viewModelScope.launch {
            val updatedList = _providers.value.toMutableList().apply {
                if (index in indices) set(index, newProvider)
            }
            repo.save(getApplication(), updatedList)
            _providers.value = updatedList
        }
    }

    fun removeProvider(index: Int) {
        viewModelScope.launch {
            val updatedList = _providers.value.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
            repo.save(getApplication(), updatedList)
            _providers.value = updatedList
        }
    }
}