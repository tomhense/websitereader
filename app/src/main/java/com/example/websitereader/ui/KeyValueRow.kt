package com.example.websitereader.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

@Composable
fun KeyValueRow(
    boldText: String, normalText: String, style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Row {
        Text(
            text = "$boldText: ", style = style.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = normalText,
            style = style,
        )
    }
}