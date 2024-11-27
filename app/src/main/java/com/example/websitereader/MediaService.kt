package com.example.websitereader;

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
class MediaService : MediaSessionService() {

    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        val context = this
        val exoPlayer = ExoPlayer.Builder(context).build()
        mediaSession = MediaSession.Builder(context, exoPlayer)
            .setCallback(object : MediaSession.Callback {
                // Implement playback controls like play, pause, and seek
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
    }
}