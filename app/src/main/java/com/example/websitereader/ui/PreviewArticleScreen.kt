package com.example.websitereader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PreviewArticleScreen(headline: String, content: String) {
    Column(modifier = Modifier.padding(PaddingValues(16.dp))) {
        Text(headline, style = MaterialTheme.typography.headlineLarge)
        Text(
            content,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}