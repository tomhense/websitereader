package com.example.websitereader.tts

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.websitereader.settings.TTSProviderEntry
import com.example.websitereader.tts.Utils.concatAudioFilesToWav
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

class OpenAI(
    private val context: Context, ttsProviderEntry: TTSProviderEntry
) : Provider {
    override val isReady = CompletableDeferred<Unit>()
    private val baseUrl = ttsProviderEntry.apiBaseUrl
    private val apiKey = ttsProviderEntry.apiKey
    private val maxChunkLength = ttsProviderEntry.maxChunkLength
    private val voiceName = ttsProviderEntry.voiceName
    private val audioFormat = ttsProviderEntry.audioFormat
    private val modelName = ttsProviderEntry.modelName
    private val asyncSynthesization = ttsProviderEntry.asyncSynthesization

    private val client = OkHttpClient()

    init {
        isReady.complete(Unit)
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    override suspend fun synthesizeTextToFile(
        text: String,
        langCode: String,
        outputFile: File,
        progressCallback: (Double, ProgressState) -> Unit
    ): Unit = coroutineScope {
        val chunks = splitTextIntoLongChunks(text, maxChunkLength)
        val tempAudioFiles = List(chunks.size) { i ->
            File(context.cacheDir, "$i.${audioFormat}")
        }
        try {
            if (asyncSynthesization) {
                // ASYNC: Generate all chunks in parallel
                val jobs = chunks.mapIndexed { i, chunk ->
                    async {
                        progressCallback(i.toDouble() / chunks.size, ProgressState.AUDIO_GENERATION)
                        val success = synthesizeTextChunkToFile(chunk, langCode, tempAudioFiles[i])
                        if (!success) throw IOException("OpenAI TTS failed for chunk $i")
                        true
                    }
                }
                jobs.awaitAll()
            } else {
                // SYNC: Generate chunks one by one
                for (i in chunks.indices) {
                    progressCallback(i.toDouble() / chunks.size, ProgressState.AUDIO_GENERATION)
                    val success = synthesizeTextChunkToFile(chunks[i], langCode, tempAudioFiles[i])
                    if (!success) throw IOException("OpenAI TTS failed for chunk $i")
                }
            }
            // Concatenate
            progressCallback(1.0, ProgressState.CONCATENATION)
            Log.i("OpenAI", "Concatenating audio files")
            when (audioFormat) {
                "wav" -> {
                    concatWaveFiles(tempAudioFiles, outputFile)
                }

                "mp3", "ogg", "opus" -> {
                    concatAudioFilesToWav(tempAudioFiles, outputFile)
                }

                else -> {
                    Toast.makeText(
                        context,
                        "Unsupported audio format: $audioFormat",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } finally {
            tempAudioFiles.forEach { it.delete() }
        }
    }

    // Calls OpenAI's TTS endpoint, saves as .wav
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun synthesizeTextChunkToFile(
        text: String,
        langCode: String,
        file: File,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val ttsUrl = "$baseUrl/v1/audio/speech"

        // Use org.json to build request body
        val payload = JSONObject().apply {
            put("model", modelName)
            put("input", text)
            put("voice", voiceName)
            put("instructions", "Speak in a cheerful and positive tone.")
            put("response_format", audioFormat)
            put("lang_code", langCode)
        }

        val mediaType = "application/json".toMediaType()
        val request = Request.Builder().url(ttsUrl).addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(mediaType)).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("OpenAI", "TTS request failed", e)
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
                    Log.e("OpenAI", "TTS request failed : ${response.code} ${response.message}")
                    if (continuation.isActive) continuation.resume(false, null)
                }
                response.close()
            }
        })
    }
}
