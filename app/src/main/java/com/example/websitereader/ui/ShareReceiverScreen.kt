package com.example.websitereader.ui

import android.util.Patterns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.websitereader.settings.TTSProviderEntry
import com.example.websitereader.settings.TTSProviderEntryStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareReceiverScreen(
    sharedUrl: String, finishActivity: () -> Unit
) {
    val context = LocalContext.current
    var urlToProcess by remember { mutableStateOf(sharedUrl) }
    var urlError by remember { mutableStateOf<String?>(null) }

    var ttsProviders by remember { mutableStateOf<TTSProviderEntry?>(null) }
    var ttsProviderList by remember { mutableStateOf(listOf<TTSProviderEntry>()) }
    var selectedTTSProviderIndex by remember { mutableStateOf<Int?>(null) }

    var isGenerating by remember { mutableStateOf(false) }
    var generationResult by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Load TTS Providers from persistence
    LaunchedEffect(Unit) {
        val providers = TTSProviderEntryStorage.load(context).toList()
        ttsProviderList = providers
        selectedTTSProviderIndex = if (providers.isNotEmpty()) 0 else null
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Share to Website Reader") })
    }, floatingActionButton = {
        if (!isGenerating) {
            FloatingActionButton(
                onClick = {
                    // Validation
                    if (!Patterns.WEB_URL.matcher(urlToProcess).matches()) {
                        urlError = "Invalid URL"
                        return@FloatingActionButton
                    }
                    if (selectedTTSProviderIndex == null) {
                        errorMsg = "Please select a TTS provider"
                        return@FloatingActionButton
                    }
                    urlError = null
                    errorMsg = null
                    isGenerating = true

                    // Example: launch your backend audio generation logic
                    val provider = ttsProviderList[selectedTTSProviderIndex!!]
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            // TODO: Call your service/UI logic for generating audio below
                            // This is a placeholder, you should use your actual connection/service:
                            // audioGenerationServiceConnector.generate(...)
                            // For demonstration, simulate a result:
                            kotlinx.coroutines.delay(2000)
                            generationResult = "Audio generation complete!"
                        } catch (e: Exception) {
                            errorMsg = e.message
                        }
                        isGenerating = false
                    }
                }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Generate")
            }
        }
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = urlToProcess,
                onValueChange = { urlToProcess = it },
                label = { Text("Article URL") },
                isError = urlError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (urlError != null) {
                Text(urlError!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("TTS Provider", style = MaterialTheme.typography.bodySmall)
            if (ttsProviderList.isEmpty()) {
                Text(
                    "No TTS Providers found. Add one in Settings.",
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                LazyColumn {
                    itemsIndexed(ttsProviderList) { idx, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTTSProviderIndex = idx }
                                .padding(vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(
                                selected = idx == selectedTTSProviderIndex,
                                onClick = { selectedTTSProviderIndex = idx })
                            Text(text = entry.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isGenerating) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text(
                        " Generating audio... Please wait.",
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            if (generationResult != null) {
                Text(
                    generationResult!!,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
            if (errorMsg != null) {
                Text(
                    errorMsg!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
    }
}