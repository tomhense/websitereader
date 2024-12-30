package com.example.websitereader

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class TTSReader(context: Context) : TextToSpeech.OnInitListener {
    var readyStatus: Int = -1
    private val textToSpeech = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        readyStatus = status
    }

    private fun splitTextIntoChunks(text: String): List<String> {
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
    private suspend fun concatAudioFiles(
        context: Context,
        audioUris: List<Uri>,
        outputPath: String
    ): ExportResult = suspendCancellableCoroutine { continuation ->
        val mediaItemList = ImmutableList.Builder<EditedMediaItem>()
        for (uri in audioUris) {
            mediaItemList.add(EditedMediaItem.Builder(MediaItem.fromUri(uri)).build())
        }
        val audioSequence = EditedMediaItemSequence.Builder(mediaItemList.build()).build()
        val composition = Composition.Builder(ImmutableList.of(audioSequence)).build()
        val transformer = Transformer.Builder(context).build()

        val transformerListener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                // Resume coroutine with the result
                if (continuation.isActive) {
                    continuation.resume(exportResult)
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                // Resume coroutine with an exception if it fails
                if (continuation.isActive) {
                    continuation.resumeWithException(exportException)
                }
            }
        }

        transformer.addListener(transformerListener)

        continuation.invokeOnCancellation {
            // Clean up resources or cancel the operation if coroutine is cancelled
            transformer.removeListener(transformerListener)
        }

        try {
            //transformer.start(composition, outputPath)
            transformer.start(
                composition,
                "file:///data/user/0/com.example.websitereader/files/audio/test.mp4"
            )
        } catch (e: Exception) {
            // Handle the case where transformer.start immediately throws an exception
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    }

    suspend fun synthesizeTextToFile(
        context: Context,
        text: WebsiteFetcher.LocalizedString,
        fileName: String,
    ) = coroutineScope {
        val chunks = splitTextIntoChunks(text.string)
        val audioUris = ArrayList<Uri>()

        // Make sure dirs exist
        File(context.filesDir, "audio").mkdirs()

        val futures = chunks.indices.map { i ->
            //audioUris.add(File(context.filesDir, "audio/temp_$i.mp3").toUri())
            audioUris.add(Uri.fromFile(File(context.filesDir, "audio/temp_$i.mp3")))
            async {
                synthesizeTextChunkToFile(
                    context,
                    WebsiteFetcher.LocalizedString(chunks[i], text.langCode),
                    "audio/temp_$i.mp3",
                )
            }
        }

        futures.awaitAll()

        if (chunks.size == -1) {
            File(context.filesDir, "audio/temp_0.mp3").copyTo(
                File(context.filesDir, fileName),
                true
            )
        } else {
            concatAudioFiles(context, audioUris, fileName)
        }
    }


    private suspend fun synthesizeTextChunkToFile(
        context: Context,
        text: WebsiteFetcher.LocalizedString,
        fileName: String,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        if (readyStatus != TextToSpeech.SUCCESS) {
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

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceIdParam: String) {
                if (utteranceIdParam == utteranceId) {
                    continuation.resumeWithException(Exception("Synthesis error"))
                }
            }

            override fun onError(utteranceIdParam: String, errorCode: Int) {
                if (utteranceIdParam == utteranceId) {
                    continuation.resumeWithException(Exception("Synthesis error (error code $errorCode)"))
                }
            }
        }

        textToSpeech.setOnUtteranceProgressListener(utteranceProgressListener)
        textToSpeech.language = Locale(text.langCode)

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