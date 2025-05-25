package com.example.websitereader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onSettingsClicked: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Website Reader") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onSettingsClicked,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = "Welcome to the Website Reader App!",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}