package com.example.websitereader.tts

import android.content.Context
import com.example.websitereader.tts.Utils.concatAudioFilesByRemuxing
import com.example.websitereader.tts.Utils.concatWaveFiles
import com.example.websitereader.tts.Utils.splitTextIntoLongChunks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class OpenAI(private val context: Context, private val apiKey: String) : Provider {
    override val isReady = CompletableDeferred<Unit>()
    private val client = OkHttpClient()
    private val maxChunkLength = 4096 // OpenAI TTS API limit
    private val openAIApiBaseUrl = "https://api.openai.com"
    private val audioFormat = "opus"

    // The price in dollar for one million tokens of input
    // For gpt-4o-mini-tts currently 0.6$ per 1M tokens.
    // Characters are converted to tokens with the thumb rule 4 chars per token
    private val pricePer1MCharacters = 0.6 / 4


    init {
        isReady.complete(Unit)
    }

    override suspend fun synthesizeTextToFile(
        text: String,
        langCode: String,
        outputFile: File,
        progressCallback: (Double, ProgressState) -> Unit
    ): Unit = coroutineScope {
        val chunks = splitTextIntoLongChunks(text, maxChunkLength)
        val tempAudioFiles = List(chunks.size) { i ->
            File(context.cacheDir, "openai-tts-chunk-${System.currentTimeMillis()}-$i.wav")
        }

        // Launch all requests in parallel (async)
        val jobs = chunks.mapIndexed { i, chunk ->
            async {
                progressCallback(i.toDouble() / chunks.size, ProgressState.AUDIO_GENERATION)
                val success = synthesizeTextChunkToFile(chunk, langCode, tempAudioFiles[i])
                if (!success) throw IOException("OpenAI TTS failed for chunk $i")
                true
            }
        }

        try {
            jobs.awaitAll()
        } catch (e: Exception) {
            tempAudioFiles.forEach { it.delete() }
            throw e
        }

        // Concatenate
        progressCallback(1.0, ProgressState.CONCATENATION)
        if (audioFormat == "wav") {
            concatWaveFiles(tempAudioFiles, outputFile)
        } else {
            concatAudioFilesByRemuxing(tempAudioFiles, outputFile)
        }

        // Clean temp
        tempAudioFiles.forEach { it.delete() }
    }

    // Calls OpenAI's TTS endpoint, saves as .wav
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun synthesizeTextChunkToFile(
        text: String,
        langCode: String,
        file: File,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val ttsUrl = "$openAIApiBaseUrl/v1/audio/speech"
        val model = "tts-1"
        val voice = mapLangCodeToVoice(langCode)

        // Use org.json to build request body
        val payload = JSONObject().apply {
            put("model", model)
            put("input", text)
            put("voice", voice)
            put("response_format", audioFormat)
        }

        val mediaType = "application/json".toMediaType()
        val request = Request.Builder().url(ttsUrl).addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(mediaType)).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (continuation.isActive) continuation.resume(false, null)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful && response.body != null) {
                    try {
                        val body: ResponseBody = response.body!!
                        FileOutputStream(file).use { fos ->
                            fos.write(body.bytes())
                        }
                        if (continuation.isActive) continuation.resume(true, null)
                    } catch (ex: Exception) {
                        if (continuation.isActive) continuation.resume(false, null)
                    }
                } else {
                    if (continuation.isActive) continuation.resume(false, null)
                }
                response.close()
            }
        })
    }

    // Example: map language to OpenAI voice (You should improve this mapping as appropriate)
    private fun mapLangCodeToVoice(langCode: String): String {
        return when (langCode) {
            "en", "en-US" -> "alloy"
            // Others: "echo", "fable", "onyx", "nova", "shimmer"
            // Add more as needed per OpenAI docs/users
            else -> "alloy"
        }
    }

    fun onDestroy() {
        // You can shutdown OkHttp dispatcher etc if you want
    }
}
