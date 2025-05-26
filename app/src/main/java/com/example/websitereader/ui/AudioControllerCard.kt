package com.example.websitereader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.websitereader.R
import kotlinx.coroutines.delay

// Helper to format ms to mm:ss
private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
fun AudioControllerCard(
    audioUrl: String, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // ExoPlayer setup
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(audioUrl))
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var userIsSeeking by remember { mutableStateOf(false) }

    // Periodically update the position and duration
    LaunchedEffect(isPlaying) {
        while (true) {
            if (!userIsSeeking) {
                position = exoPlayer.currentPosition
            }
            duration = exoPlayer.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    // Dispose ExoPlayer when the composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Card(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Audio Player", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // Play/Pause Button
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                    } else {
                        exoPlayer.play()
                        isPlaying = true
                    }
                }) {
                    if (isPlaying) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_pause_24),
                            contentDescription = "Pause"
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow, contentDescription = "Play"
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTime(position) + " / " + formatTime(duration),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Seek bar
            Slider(
                value = if (duration != 0L) position / duration.toFloat() else 0f,
                onValueChange = { value ->
                    userIsSeeking = true
                    position = (duration * value).toLong()
                },
                onValueChangeFinished = {
                    userIsSeeking = false
                    exoPlayer.seekTo(position)
                })
        }
    }
}

