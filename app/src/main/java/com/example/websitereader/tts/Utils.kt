package com.example.websitereader.tts

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Utils {
    // Concatenate audio files using remuxing, this works all major audio encodings
    suspend fun concatAudioFilesByRemuxing(
        context: Context,
        audioUris: List<Uri>,
        outputPath: String
    ): Boolean = suspendCancellableCoroutine { continuation ->

        try {
            // Create a MediaMuxer to write the output file
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Keep track of track index
            var audioTrackIndex = -1
            var isMuxerStarted = false

            audioUris.forEach { uri ->
                val extractor = MediaExtractor()
                extractor.setDataSource(context, uri, null)

                // Find the audio track
                val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    val format = extractor.getTrackFormat(index)
                    format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") ?: false
                } ?: return@forEach

                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)

                // Add track to muxer if not already added
                if (audioTrackIndex == -1) {
                    audioTrackIndex = muxer.addTrack(format)
                    muxer.start()
                    isMuxerStarted = true
                }

                // Extract and write the samples
                val buffer = ByteBuffer.allocate(1024 * 1024)
                val bufferInfo = MediaCodec.BufferInfo()

                extractor.selectTrack(trackIndex)

                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)

                    if (bufferInfo.size < 0) {
                        extractor.unselectTrack(trackIndex)
                        break
                    } else {
                        bufferInfo.presentationTimeUs = extractor.sampleTime

                        // Map extractor flags to codec flags
                        bufferInfo.flags = 0
                        val sampleFlags = extractor.sampleFlags
                        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            bufferInfo.flags =
                                bufferInfo.flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                        }
                        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
                            bufferInfo.flags =
                                bufferInfo.flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                        }
                        // Add more flag mappings if needed

                        muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                        extractor.advance()
                    }
                }

                extractor.release()
            }

            if (isMuxerStarted) {
                muxer.stop()
                muxer.release()
            }

            continuation.resume(true)

        } catch (e: Exception) {
            // Handle exceptions and resume with false
            continuation.resumeWithException(e)
        }
    }

    // Helper function to copy input stream to output stream
    private fun copyStream(input: InputStream, output: FileOutputStream) {
        val buffer = ByteArray(4096) // Use an adequate buffer size
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
    }

    // Does a simple byte for byte concat of audio files, this of course only works for wav & pcm
    suspend fun concatAudioFiles(
        context: Context,
        audioUris: List<Uri>,
        outputPath: String
    ): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            // Create output file
            val outputFile = File(outputPath)
            FileOutputStream(outputFile).use { outputStream ->

                // Iterate over each audio file URI
                audioUris.forEach { uri ->
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        copyStream(inputStream, outputStream)
                    } ?: throw IllegalArgumentException("Unable to open URI: $uri")
                }
            }

            // Signal success
            continuation.resume(true)

        } catch (e: Exception) {
            // Signal failure
            continuation.resumeWithException(e)
        }
    }

    fun splitTextIntoChunks(text: String, maxChunkLength: Int): List<String> {
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
}