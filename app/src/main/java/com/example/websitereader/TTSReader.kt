package com.example.websitereader

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class TTSReader(private val context: Context) : TextToSpeech.OnInitListener {
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

    fun splitTextIntoChunks(text: String): List<String> {
        val list = mutableListOf<String>()
        val words = text.split(" ")
        var chunk = ""
        val maxInputLength = TextToSpeech.getMaxSpeechInputLength()
        for (word in words) {
            if ((chunk + word).length > maxInputLength) {
                list.add(chunk)
                chunk = ""
            }
            chunk += "$word "
        }
        list.add(chunk)
        return list
    }


    @OptIn(UnstableApi::class)
    fun concatAudioFiles(audioUris: List<Uri>, outputPath: String) {
        val mediaItemList = ImmutableList.Builder<EditedMediaItem>()
        for (uri in audioUris) {
            mediaItemList.add(EditedMediaItem.Builder(MediaItem.fromUri(uri)).build())
        }
        val audioSequence = EditedMediaItemSequence.Builder(mediaItemList.build()).build()
        val composition = Composition.Builder(ImmutableList.of(audioSequence)).build()

        val transformer = Transformer.Builder(context).build()
        transformer.start(composition, outputPath)
    }

    suspend fun synthesizeTextToFile(
        context: Context,
        text: WebsiteFetcher.LocalizedString,
        fileName: String,
        callback: (String?) -> Unit
    ) {
        val chunks = splitTextIntoChunks(text.string)

        for (i in chunks.indices) {
            synthesizeTextToFileShort(
                context,
                WebsiteFetcher.LocalizedString(chunks[i], text.langCode),
                "audio/temp_$i.mp3",
                callback
            )
        }
    }

    suspend fun synthesizeTextToFileShort(
        context: Context,
        text: WebsiteFetcher.LocalizedString,
        fileName: String,
        callback: (String?) -> Unit
    ) {
        if (readyStatus != TextToSpeech.SUCCESS) {
            return
        }

        CoroutineScope(Dispatchers.Default).launch {
            textToSpeech.language = Locale(text.langCode)

            val outputFile = File(context.filesDir, fileName)
            if (!outputFile.parentFile!!.exists()) {
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
                    Log.e("TTS error", "onError: $utteranceId")
                    callback(null)
                    textToSpeech.shutdown()
                }

                override fun onError(utteranceId: String, errorCode: Int) {
                    Log.e("TTS error", "onError: $utteranceId, errorCode: $errorCode")
                    Log.e(
                        "test",
                        "${text.string.length.toString()} | ${TextToSpeech.getMaxSpeechInputLength()}"
                    )
                    callback(null)
                    textToSpeech.shutdown()
                }
            }

            textToSpeech.setOnUtteranceProgressListener(listener)

            @Suppress("DEPRECATION")
            textToSpeech.synthesizeToFile(text.string, params, outputFile, "utteranceId")
        }

    }

    fun onDestroy() {
        // Shutdown TTS to release the resource
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}