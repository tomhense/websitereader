package com.example.websitereader.tts

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.websitereader.model.ExternalTTSProvider
import com.example.websitereader.tts.Utils.concatAudioFiles
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class OpenAI(
    private val context: Context, ttsProviderEntry: ExternalTTSProvider
) : Provider {
    override val isReady = CompletableDeferred<Unit>()
    private val baseUrl = ttsProviderEntry.apiBaseUrl
    private val apiKey = ttsProviderEntry.apiKey
    private val maxChunkLength = ttsProviderEntry.maxChunkLength
    private val voiceName = ttsProviderEntry.voiceName
    override val audioFormat = ttsProviderEntry.audioFormat
    private val modelName = ttsProviderEntry.modelName
    private val asyncSynthesization = ttsProviderEntry.asyncSynthesization

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

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
            File(context.cacheDir, "openai_chunk_$i.${audioFormat}")
        }
        try {
            if (asyncSynthesization) {
                // ASYNC: Generate all chunks in parallel and update progress as they complete
                val completedChunksCount = AtomicInteger(0)
                val jobs = chunks.mapIndexed { i, chunk ->
                    async {
                        val success = synthesizeTextChunkToFile(chunk, langCode, tempAudioFiles[i])
                        if (!success) throw IOException("OpenAI TTS failed for chunk $i")
                        
                        val completed = completedChunksCount.incrementAndGet()
                        progressCallback(completed.toDouble() / chunks.size, ProgressState.AUDIO_GENERATION)
                        true
                    }
                }
                jobs.awaitAll()
            } else {
                // SYNC: Generate chunks one by one
                for (i in chunks.indices) {
                    val success = synthesizeTextChunkToFile(chunks[i], langCode, tempAudioFiles[i])
                    if (!success) throw IOException("OpenAI TTS failed for chunk $i")
                    progressCallback((i + 1).toDouble() / chunks.size, ProgressState.AUDIO_GENERATION)
                }
            }
            // Concatenate
            progressCallback(1.0, ProgressState.CONCATENATION)
            Log.i("OpenAI", "Concatenating audio files")
            concatAudioFiles(context, tempAudioFiles, outputFile)
        } finally {
            tempAudioFiles.forEach { it.delete() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun synthesizeTextChunkToFile(
        text: String,
        langCode: String,
        file: File,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val ttsUrl = "$baseUrl/v1/audio/speech"

        val payload = JSONObject().apply {
            put("model", modelName)
            put("input", text)
            put("voice", voiceName)
            put("instructions", "Speak in a cheerful and positive tone.")
            put("response_format", audioFormat)
            put("lang_code", langCode)
        }

        val mediaType = "application/json".toMediaType()
        val request = Request.Builder()
            .url(ttsUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(mediaType))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("OpenAI", "TTS request failed", e)
                if (continuation.isActive) continuation.resume(false)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body
                        if (body != null) {
                            try {
                                FileOutputStream(file).use { fos ->
                                    fos.write(body.bytes())
                                }
                                if (continuation.isActive) continuation.resume(true)
                            } catch (e: Exception) {
                                Log.e("OpenAI", "Error writing chunk to file", e)
                                if (continuation.isActive) continuation.resume(false)
                            }
                        } else {
                            Log.e("OpenAI", "Response body is null")
                            if (continuation.isActive) continuation.resume(false)
                        }
                    } else {
                        Log.e("OpenAI", "TTS request failed: ${resp.code} ${resp.message}")
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            }
        })
    }
}
