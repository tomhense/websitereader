package com.example.websitereader.tts

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import com.example.websitereader.WebsiteFetcher
import com.example.websitereader.tts.Utils.concatAudioFiles
import com.example.websitereader.tts.Utils.splitTextIntoChunks
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class Android(private val context: Context, private val initCallback: () -> Unit) :
    TextToSpeech.OnInitListener,
    Provider {
    private var isReady: Boolean = false


    private val textToSpeech = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e("tts", "TTS engine initialization failed with status $status")
            Toast.makeText(
                context,
                "TTS engine initialization failed with status $status",
                Toast.LENGTH_SHORT
            ).show()
            throw IllegalStateException("ERROR INITIALIZING TTS")
        }
        initCallback()
        isReady = true
    }

    override suspend fun synthesizeTextToFile(
        text: WebsiteFetcher.LocalizedString,
        fileName: String,
        progressCallback: (Double, ProgressState) -> Unit
    ): Unit = coroutineScope {
        val chunks = splitTextIntoChunks(text.string, TextToSpeech.getMaxSpeechInputLength())
        val audioUris = ArrayList<Uri>()

        Log.i("tts", "${chunks.size} chunks")
        Log.i("tts", "Max tts chunk length: ${TextToSpeech.getMaxSpeechInputLength()}")
        for (i in chunks.indices) {
            Log.i("tts", "Chunk $i length: ${chunks[i].length}")
        }

        // Make sure dirs exist
        File(context.filesDir, "audio").mkdirs()

        // Processing the chunks in parallel seems not not work, so synchronous it is
        chunks.indices.map { i ->
            progressCallback(i.toDouble() / chunks.size, ProgressState.AUDIO_GENERATION)
            audioUris.add(Uri.fromFile(File(context.filesDir, "audio/temp_$i.mp3")))
            synthesizeTextChunkToFile(
                context,
                WebsiteFetcher.LocalizedString(chunks[i], text.langCode),
                "audio/temp_$i.mp3",
            )
        }

        Log.i("tts", "Starting to concat segments")
        progressCallback(1.0, ProgressState.CONCATENATION)
        concatAudioFiles(context, audioUris, fileName)
    }


    private suspend fun synthesizeTextChunkToFile(
        context: Context,
        text: WebsiteFetcher.LocalizedString,
        fileName: String,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        if (!isReady) {
            continuation.resumeWithException(IllegalStateException("TTS engine not initialized"))
            return@suspendCancellableCoroutine
        }

        val fileDescriptor = File(context.filesDir, fileName)

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utteranceId")

        val utteranceId = Random.nextLong().toString() // Generate a random utterance id
        val utteranceProgressListener = object : UtteranceProgressListener() {
            override fun onStart(utteranceIdParam: String) {
                // If needed, handle start of the synthesis
            }

            override fun onDone(utteranceIdParam: String) {
                if (utteranceIdParam == utteranceId) {
                    continuation.resume(true)
                }
            }

            @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceIdParam, errorCode)"))
            override fun onError(utteranceIdParam: String) {
                if (utteranceIdParam == utteranceId) {
                    continuation.resumeWithException(Exception("Synthesis error"))
                }
            }

            override fun onError(utteranceIdParam: String, errorCode: Int) {
                Log.i("tts", "Error code: $errorCode")
                if (utteranceIdParam == utteranceId) {
                    continuation.resumeWithException(Exception("Synthesis error (error code $errorCode)"))
                }
            }
        }

        textToSpeech.setOnUtteranceProgressListener(utteranceProgressListener)
        assert(text.langCode != null)
        textToSpeech.language = Locale(text.langCode!!)

        textToSpeech.synthesizeToFile(text.string, params, fileDescriptor, utteranceId)
            .also { result ->
                if (result != TextToSpeech.SUCCESS) {
                    continuation.resumeWithException(Exception("Error starting synthesis"))
                    return@suspendCancellableCoroutine
                }
            }
    }

    fun onDestroy() {
        // Shutdown TTS to release the resource
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}