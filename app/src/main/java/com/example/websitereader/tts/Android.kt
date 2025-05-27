package com.example.websitereader.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import com.example.websitereader.tts.Utils.concatWaveFiles
import com.example.websitereader.tts.Utils.splitTextIntoShortChunks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class Android(private val context: Context) : TextToSpeech.OnInitListener, Provider {
    override val isReady = CompletableDeferred<Unit>()
    override val audioFormat = "wav"

    private val textToSpeech = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e("tts", "TTS engine initialization failed with status $status")
            Toast.makeText(
                context, "TTS engine initialization failed with status $status", Toast.LENGTH_SHORT
            ).show()
            throw IllegalStateException("ERROR INITIALIZING TTS")
        }
        isReady.complete(Unit)
    }

    override suspend fun synthesizeTextToFile(
        text: String,
        langCode: String,
        outputFile: File,
        progressCallback: (Double, ProgressState) -> Unit
    ): Unit = coroutineScope {
        // Wait for the TTS engine to be initialized
        if (!isReady.isCompleted) {
            isReady.await()
            Log.i("tts", "TTS engine initialized")
        }

        // Create chunks from the long given text which the TTS can ingest
        Log.i("tts", "Max tts chunk length: ${TextToSpeech.getMaxSpeechInputLength()}")
        val chunks = splitTextIntoShortChunks(text, TextToSpeech.getMaxSpeechInputLength())
        for (i in chunks.indices) {
            Log.i("tts", "Chunk ${i + 1} length: ${chunks[i].length}")
        }

        // Processing the chunks in parallel seems not not work, so synchronous it is
        val tempAudioFiles = ArrayList<File>()
        chunks.indices.map { i ->
            progressCallback(i.toDouble() / chunks.size, ProgressState.AUDIO_GENERATION)
            val tempFile = File(context.cacheDir, "$i.wav")
            tempAudioFiles.add(tempFile)
            synthesizeTextChunkToFile(
                chunks[i], langCode, tempFile
            )
        }

        Log.i("tts", "Starting to concat segments")
        progressCallback(1.0, ProgressState.CONCATENATION)
        concatWaveFiles(tempAudioFiles, outputFile)

        // Clean up temp files
        Log.i("tts", "Cleaning up temp files")
        tempAudioFiles.forEach { it.delete() }
    }


    private suspend fun synthesizeTextChunkToFile(
        text: String,
        langCode: String,
        file: File,
    ): Boolean = suspendCancellableCoroutine { continuation ->
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
        textToSpeech.language = Locale(langCode)

        textToSpeech.synthesizeToFile(text, params, file, utteranceId).also { result ->
            if (result != TextToSpeech.SUCCESS) {
                continuation.resumeWithException(Exception("Error starting synthesis"))
                return@suspendCancellableCoroutine
            }
        }
    }
}