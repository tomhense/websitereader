package com.example.websitereader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.websitereader.model.ExternalTTSProvider


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSelector(
    value: String,
    onValueChange: (String) -> Unit,
    defaultVoices: List<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Voice Name") },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose voice")
                }
            },
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            defaultVoices.forEach { voice ->
                DropdownMenuItem(text = { Text(voice) }, onClick = {
                    onValueChange(voice)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun EditTTSProviderDialog(
    entry: ExternalTTSProvider?, onDismiss: () -> Unit, onSave: (ExternalTTSProvider) -> Unit
) {
    // Default values
    val defaultName = "OpenAI"
    val defaultApiBaseUrl = "https://api.openai.com"
    val defaultPricePer1M = 3
    val defaultMaxChunk = 4096
    val defaultVoices = listOf(
        "alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer"
    )
    val defaultModelName = "gpt-4o-mini-tts"
    val defaultAudioFormats = listOf("mp3", "opus", "wav")
    val defaultAsyncSynthesization = false

    // State
    var name by remember { mutableStateOf(entry?.name ?: defaultName) }
    var apiBaseUrl by remember { mutableStateOf(entry?.apiBaseUrl ?: defaultApiBaseUrl) }
    var voiceName by remember { mutableStateOf(entry?.voiceName ?: defaultVoices.first()) }
    var pricePer1M by remember {
        mutableStateOf(
            entry?.pricePer1MCharacters?.toString() ?: defaultPricePer1M.toString()
        )
    }
    var maxChunkLength by remember {
        mutableStateOf(
            entry?.maxChunkLength?.toString() ?: defaultMaxChunk.toString()
        )
    }
    var apiKey by remember { mutableStateOf(entry?.apiKey ?: "") }
    var modelName by remember { mutableStateOf(entry?.modelName ?: defaultModelName) }
    var audioFormat by remember {
        mutableStateOf(
            entry?.audioFormat ?: defaultAudioFormats.first()
        )
    }
    var asyncSynthesization by remember {
        mutableStateOf(
            entry?.asyncSynthesization ?: defaultAsyncSynthesization
        )
    }

    var formatMenuExpanded by remember { mutableStateOf(false) }
    var voicesMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "Add TTS Provider" else "Edit TTS Provider") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiBaseUrl,
                    onValueChange = { apiBaseUrl = it },
                    label = { Text("API Base URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                VoiceSelector(
                    defaultVoices = defaultVoices,
                    value = voiceName,
                    onValueChange = { voiceName = it },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pricePer1M,
                    onValueChange = { pricePer1M = it },
                    label = { Text("Price per 1M Characters") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxChunkLength,
                    onValueChange = { maxChunkLength = it },
                    label = { Text("Max Chunk Length") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        readOnly = true,
                        value = audioFormat,
                        onValueChange = {},
                        label = { Text("Audio Format") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { formatMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Choose format"
                                )
                            }
                        })
                    DropdownMenu(
                        expanded = formatMenuExpanded,
                        onDismissRequest = { formatMenuExpanded = false }) {
                        for (fmt in defaultAudioFormats) {
                            DropdownMenuItem(onClick = {
                                audioFormat = fmt
                                formatMenuExpanded = false
                            }, text = { Text(fmt) })
                        }
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = asyncSynthesization,
                        onCheckedChange = { asyncSynthesization = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Async Synthesization")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    ExternalTTSProvider(
                        name = name,
                        apiBaseUrl = apiBaseUrl,
                        voiceName = voiceName,
                        pricePer1MCharacters = pricePer1M.toDoubleOrNull() ?: 0.0,
                        maxChunkLength = maxChunkLength.toIntOrNull() ?: 0,
                        apiKey = apiKey,
                        modelName = modelName,
                        audioFormat = audioFormat,
                        asyncSynthesization = asyncSynthesization
                    )
                )
            }, content = { Text("Save") })
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        })
}