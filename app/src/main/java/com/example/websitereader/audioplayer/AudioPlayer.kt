package com.example.websitereader.audioplayer

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AudioPlayer(context: Context) {
    private var controller: MediaController? = null

    // Expose these to UI
    val isPlaying = MutableStateFlow(false)
    val position = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)

    private var positionJob: Job? = null

    init {
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                try {
                    controller = controllerFuture.get()
                    // Observe playback state changes
                    controller?.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlayingVal: Boolean) {
                            isPlaying.value = isPlayingVal
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            duration.value = controller?.duration ?: 0L
                        }
                    })
                    startPositionUpdates() // Start periodic updates for position
                } catch (e: Exception) {
                    Log.e("AudioPlayer", "MediaController init failed: ${e.message}")
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun playAudio(audioFile: Uri) {
        controller?.apply {
            setMediaItem(MediaItem.fromUri(audioFile))
            prepare()
            play()
        }
    }

    fun playPause() {
        controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(pos: Long) {
        controller?.seekTo(pos)
    }

    private fun startPositionUpdates() {
        if (positionJob == null) {
            positionJob = CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    controller?.let {
                        position.value = it.currentPosition
                        duration.value = it.duration
                    }
                    delay(300)
                }
            }
        }
    }
}