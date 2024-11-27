package com.example.websitereader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import org.w3c.dom.Text
import java.io.File
import java.util.Locale

class TTSReader(private val context: Context): TextToSpeech.OnInitListener {
    var readyStatus: Int = -1
    private val textToSpeech = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        readyStatus = status
        if (status == TextToSpeech.SUCCESS) {
            // Set the language to US English
            val result = textToSpeech.setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Handle the error
                println("Language not supported or missing data")
            }
        } else {
            // Initialization failed
            println("Initialization Failed!")
        }
    }


    fun playAudio(context: Context, audioFilePath: String) {
        val exoPlayer = ExoPlayer.Builder(context).build()

        // Build a media item
        val mediaItem = MediaItem.fromUri(audioFilePath)

        // Set the media item to be played
        exoPlayer.setMediaItem(mediaItem)

        // Prepare the player
        exoPlayer.prepare()

        // Start the playback
        exoPlayer.play()

        // Add some logic to release the player when it's no longer needed
        exoPlayer.release()
    }


    fun synthesizeTextToFile(context: Context, text: WebsiteFetcher.LocalizedString, fileName: String, callback: (String?) -> Unit) {
        if(readyStatus != TextToSpeech.SUCCESS) {
            return
        }

        textToSpeech.language = Locale(text.langCode)

        val outputFile = File(context.filesDir, fileName)
        if(!outputFile.parentFile!!.exists()) {
            outputFile.parentFile!!.mkdirs()
        }

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utteranceId")

        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                // Do something when TTS starts
            }

            override fun onDone(utteranceId: String) {
                callback(outputFile.path)
                textToSpeech.shutdown()
            }

            override fun onError(utteranceId: String) {
                callback(null)
                textToSpeech.shutdown()
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                callback(null)
                textToSpeech.shutdown()
            }
        }

        textToSpeech.setOnUtteranceProgressListener(listener)

        @Suppress("DEPRECATION")
        textToSpeech.synthesizeToFile(text.string, params, outputFile, "utteranceId")
    }

    /*public fun speak(text: String) {
        //textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        textToSpeech.synthesizeToFile()
    }*/

    fun onDestroy() {
        // Shutdown TTS to release the resource
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}