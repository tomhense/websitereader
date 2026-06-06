package com.example.websitereader.tts

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.IOException

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
     * Concatenates multiple audio files into one using FFmpeg.
     * Uses stream copying (-c copy) to avoid re-encoding.
     */
    fun concatAudioFiles(inputFiles: List<File>, outputFile: File) {
        if (inputFiles.isEmpty()) return
        if (inputFiles.size == 1) {
            inputFiles[0].copyTo(outputFile, overwrite = true)
            return
        }

        // Create a temporary list file for ffmpeg concat demuxer
        val listFile = File.createTempFile("ffmpeg-concat", ".txt")
        try {
            listFile.writer().use { writer ->
                inputFiles.forEach { file ->
                    // FFmpeg concat demuxer requires escaping single quotes in file paths
                    val escapedPath = file.absolutePath.replace("'", "'\\''")
                    writer.write("file '$escapedPath'\n")
                }
            }

            val command = "-f concat -safe 0 -i \"${listFile.absolutePath}\" -c copy -y \"${outputFile.absolutePath}\""
            val session = FFmpegKit.execute(command)

            if (!ReturnCode.isSuccess(session.returnCode)) {
                throw IOException("FFmpeg failed with return code ${session.returnCode}. Command: $command. Log: ${session.allLogsAsString}")
            }
        } finally {
            listFile.delete()
        }
    }
}