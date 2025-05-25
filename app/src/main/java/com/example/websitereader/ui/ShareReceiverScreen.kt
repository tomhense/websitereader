package com.example.websitereader.ui

import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.websitereader.processSharedUrlLogic
import com.example.websitereader.settings.TTSProviderEntry
import com.example.websitereader.settings.TTSProviderEntryStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareReceiverScreen(
    sharedUrl: String,
    finishActivity: () -> Unit
) {
    val context = LocalContext.current
    var urlToProcess by remember { mutableStateOf(sharedUrl) }
    var urlError by remember { mutableStateOf<String?>(null) }

    var ttsProviderList by remember { mutableStateOf(listOf<TTSProviderEntry>()) }
    var selectedTTSProviderIndex by remember { mutableStateOf<Int?>(null) }

    var isGenerating by remember { mutableStateOf(false) }
    var generationResult by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Load TTS Providers (migrated from Activity onCreate-style logic)
    LaunchedEffect(Unit) {
        val providers = TTSProviderEntryStorage.load(context).toList()
        ttsProviderList = providers
        selectedTTSProviderIndex = if (providers.isNotEmpty()) 0 else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share URL to Audio") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Input validation
                    if (!Patterns.WEB_URL.matcher(urlToProcess).matches()) {
                        urlError = "Invalid URL"
                        return@FloatingActionButton
                    }
                    val ttsProvider = selectedTTSProviderIndex?.let { idx -> ttsProviderList[idx] }
                    if (ttsProvider == null) {
                        errorMsg = "No TTS provider selected"
                        return@FloatingActionButton
                    }
                    isGenerating = true
                    errorMsg = null
                    generationResult = null

                    // kick off coroutine for non-UI work (migrated logic)
                    CoroutineScope(Dispatchers.Main).launch {
                        processSharedUrlLogic(
                            context = context,
                            url = urlToProcess,
                            ttsProvider = ttsProvider,
                            onAudioFileReady = { uri: Uri ->
                                isGenerating = false
                                generationResult = "Audio file ready: $uri"
                                // Optionally: auto-launch playback, or show snackbar, etc
                                finishActivity()
                            },
                            onError = { e ->
                                isGenerating = false
                                errorMsg = e.message ?: "Unknown error"
                            }
                        )
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send/Generate")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = urlToProcess,
                onValueChange = {
                    urlToProcess = it
                    urlError = null
                },
                isError = urlError != null,
                label = { Text("Article URL") },
                modifier = Modifier.fillMaxWidth()
            )
            if (urlError != null) {
                Text(
                    text = urlError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Choose TTS Provider:")
            LazyColumn {
                itemsIndexed(ttsProviderList) { idx, provider ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedTTSProviderIndex = idx }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = selectedTTSProviderIndex == idx,
                            onClick = { selectedTTSProviderIndex = idx }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(provider.name)
                    }
                }
            }

            if (isGenerating) {
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator()
            }

            errorMsg?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            generationResult?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}