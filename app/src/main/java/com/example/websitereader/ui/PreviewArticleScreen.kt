package com.example.websitereader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PreviewArticleScreen(headline: String, content: String) {
    Column(
        modifier = Modifier
            .padding(vertical = 48.dp, horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(headline, style = MaterialTheme.typography.headlineSmall)
        Text(
            content,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 512.dp)
        )
    }
}