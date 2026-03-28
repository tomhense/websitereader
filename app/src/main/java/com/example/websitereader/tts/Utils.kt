package com.example.websitereader.tts

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Utils {
    fun splitTextIntoShortChunks(text: String, maxChunkLength: Int): List<String> {
        val result = mutableListOf<String>()
        // Simple regex for splitting into sentences
        val sentenceRegex = Regex("(?<=[.!?])\\s+")
        val sentences = text.trim().split(sentenceRegex)

        for (sentence in sentences) {
            val trimmedSentence = sentence.trim()
            if (trimmedSentence.length <= maxChunkLength) {
                // Sentence fits into chunk
                result.add(trimmedSentence)
            } else {
                // Split long sentence by words if possible
                val words = trimmedSentence.split(" ")
                var chunk = StringBuilder()
                for (word in words) {
                    // Word itself is too long, must be split
                    if (word.length > maxChunkLength) {
                        // Add previous chunk, if any
                        if (chunk.isNotEmpty()) {
                            result.add(chunk.toString().trim())
                            chunk = StringBuilder()
                        }
                        // Split word
                        var start = 0
                        while (start < word.length) {
                            val end = minOf(start + maxChunkLength, word.length)
                            result.add(word.substring(start, end))
                            start = end
                        }
                    } else {
                        // Word fits, check if it fits in current chunk
                        if (chunk.length + word.length + 1 > maxChunkLength) {
                            if (chunk.isNotEmpty()) {
                                result.add(chunk.toString().trim())
                                chunk = StringBuilder()
                            }
                        }
                        if (chunk.isNotEmpty()) chunk.append(" ")
                        chunk.append(word)
                    }
                }
                if (chunk.isNotEmpty()) {
                    result.add(chunk.toString().trim())
                }
            }
        }
        return result
    }

    fun splitTextIntoLongChunks(text: String, maxChunkLength: Int): List<String> {
        val list = mutableListOf<String>()
        val words = text.split(" ")
        var chunk = ""
        for (word in words) {
            if ((chunk + word).length >= maxChunkLength) {
                list.add(chunk)
                chunk = ""
            }
            chunk += "$word "
        }
        list.add(chunk)
        return list
    }

    /**
     * Concatenates multiple audio files into one using Media3 Transformer.
     * This function ensures Media3 Transformer is accessed on the Main thread.
     */
    @OptIn(UnstableApi::class)
    suspend fun concatAudioFiles(context: Context, inputFiles: List<File>, outputFile: File) {
        if (inputFiles.isEmpty()) return
        if (inputFiles.size == 1) {
            inputFiles[0].copyTo(outputFile, overwrite = true)
            return
        }

        withContext(Dispatchers.Main) {
            val transformer = Transformer.Builder(context).build()
            val editedMediaItems = inputFiles.map { file ->
                EditedMediaItem.Builder(MediaItem.fromUri(file.absolutePath)).build()
            }

            val sequence = EditedMediaItemSequence(editedMediaItems)
            val composition = Composition.Builder(listOf(sequence)).build()

            val deferred = CompletableDeferred<Unit>()
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    deferred.complete(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    deferred.completeExceptionally(exportException)
                }
            }

            transformer.addListener(listener)
            transformer.start(composition, outputFile.absolutePath)

            try {
                deferred.await()
            } finally {
                transformer.removeListener(listener)
            }
        }
    }
}
