package com.example.websitereader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.websitereader.model.ExternalTTSProvider
import com.example.websitereader.settings.TTSProviderStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(finishActivity: () -> Unit) {
    val context = LocalContext.current
    val entries by TTSProviderStore.providers.collectAsState()
    var editingEntryIndex by remember { mutableStateOf<Int?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var dialogEntry by remember { mutableStateOf<ExternalTTSProvider?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("TTS Providers") }) }, floatingActionButton = {
        FloatingActionButton(onClick = {
            showDialog = true
            dialogEntry = null
            editingEntryIndex = null
        }) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            itemsIndexed(entries) { idx, entry ->
                TTSProviderEntryItem(entry = entry, onEdit = {
                    showDialog = true
                    dialogEntry = entry
                    editingEntryIndex = idx
                }, onDelete = {
                    TTSProviderStore.removeProvider(context, idx)
                })
            }
        }
        if (showDialog) {
            EditTTSProviderDialog(
                entry = dialogEntry,
                onDismiss = { showDialog = false },
                onSave = { updated: ExternalTTSProvider ->
                    if (editingEntryIndex != null) {
                        TTSProviderStore.updateProvider(
                            context,
                            editingEntryIndex!!,
                            updated
                        )
                    } else {
                        TTSProviderStore.addProvider(context, updated)
                    }
                    showDialog = false
                })
        }
    }
}

@Composable
fun TTSProviderEntryItem(
    entry: ExternalTTSProvider, onEdit: () -> Unit, onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.labelMedium)
                Text(entry.apiBaseUrl, style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Entry")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Entry")
            }
        }
    }
}