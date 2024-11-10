package com.example.websitereader

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.Collections

@UnstableApi
class TTSPlayer(looper: Looper, context: Context) : SimpleBasePlayer(looper), TextToSpeech.OnInitListener {

    private val textToSpeech: TextToSpeech = TextToSpeech(context, this)
    private var state: State = State.Builder()
        .setAvailableCommands(
            Player.Commands.Builder().addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA
            ).build()
        )
        .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .setAudioAttributes(PlaybackService.DEFAULT_AUDIO_ATTRIBUTES)
        .setPlaylist(Collections.singletonList(MediaItemData.Builder("test").build()))
        .setPlaylistMetadata(
            MediaMetadata.Builder().setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .setTitle("TTS test").build()
        )
        .setCurrentMediaItemIndex(0)
        .build()

    init {
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.i(TAG, "onStart")
                updatePlaybackState(STATE_READY, true)
            }

            override fun onDone(utteranceId: String?) {
                Log.i(TAG, "onDone")
                updatePlaybackState(STATE_ENDED, false)
            }

            override fun onError(utteranceId: String?) {
                Log.i(TAG, "onError")
                updatePlaybackState(STATE_ENDED, false)
            }
        })
    }

    override fun getState(): State {
        return state
    }

    private fun updatePlaybackState(playbackState: Int, playWhenReady: Boolean) {
        Handler(Looper.getMainLooper()).post {
            state = state.buildUpon()
                .setPlaybackState(playbackState)
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
            invalidateState()
        }
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        Log.i(TAG, "handleSetPlayWhenReady: $playWhenReady")
        if (playWhenReady) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(SAMPLE_TEXT, TextToSpeech.QUEUE_FLUSH, null, TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID)
            }
        } else {
            textToSpeech.stop()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        textToSpeech.stop()
        textToSpeech.shutdown()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        textToSpeech.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        return Futures.immediateVoidFuture()
    }

    override fun onInit(status: Int) {
        Log.i(TAG, "Tts init")
    }

    companion object {
        private const val TAG = "TtsPlayer"
        private const val SAMPLE_TEXT = "Hello World, this is a sample text for testing Media3's SimpleBasePlayer. I expect background playback to work and a notification to show up. However none of that is working."
    }
}