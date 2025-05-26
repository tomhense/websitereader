package com.example.websitereader.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.websitereader.R
import com.example.websitereader.audioplayer.AudioPlayer

// Helper to format ms to mm:ss
private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}


@Composable
fun AudioPlayerCard(
    audioPlayer: AudioPlayer, audioUri: Uri, modifier: Modifier = Modifier
) {
    rememberCoroutineScope()

    // Remember state from AudioPlayer
    val isPlaying by audioPlayer.isPlaying.collectAsState()
    val position by audioPlayer.position.collectAsState()
    val duration by audioPlayer.duration.collectAsState()

    var userSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    // Set up initial play
    LaunchedEffect(audioUri) {
        audioPlayer.playAudio(audioUri)
    }

    Card(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                audioUri.lastPathSegment ?: "", style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { audioPlayer.playPause() }) {
                    if (isPlaying) {
                        Icon(
                            contentDescription = "Pause",
                            painter = painterResource(R.drawable.baseline_pause_24),
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Icon(
                            contentDescription = "Play",
                            imageVector = Icons.Default.PlayArrow,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(position) + " / " + formatTime(duration),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Progress Slider
        Slider(
            value = if (duration > 0) (if (userSeeking) sliderPosition else position / duration.toFloat()) else 0f,
            onValueChange = { value ->
                userSeeking = true
                sliderPosition = value
            },
            onValueChangeFinished = {
                // seek to new position
                audioPlayer.seekTo((duration * sliderPosition).toLong())
                userSeeking = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}