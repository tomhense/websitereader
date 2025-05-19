package com.example.websitereader

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player!!).setId("MyMediaSession").build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        player?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }
}