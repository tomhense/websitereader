package com.example.websitereader.audioplayer

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

class AudioPlayer(context: Context) {
    private var controller: MediaController? = null

    init {
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                try {
                    controller = controllerFuture.get()
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
}