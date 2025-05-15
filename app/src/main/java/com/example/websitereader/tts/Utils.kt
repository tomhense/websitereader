package com.example.websitereader.tts

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Utils {
    fun splitTextIntoChunks(text: String, maxChunkLength: Int): List<String> {
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

    // Concatenate audio files using remuxing, this works all major audio encodings except wave and pcm
    suspend fun concatAudioFilesByRemuxing(
        audioFiles: List<File>, output: File
    ): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            // Create a MediaMuxer to write the output file
            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            // Keep track of track index
            var audioTrackIndex = -1
            var isMuxerStarted = false
            audioFiles.forEach { file ->
                val extractor = MediaExtractor()
                extractor.setDataSource(file.absolutePath)
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
                            bufferInfo.flags = bufferInfo.flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
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

    // Simple WAV header reader (for PCM)
    @Suppress("ArrayInDataClass")
    data class WavInfo(val headerBytes: ByteArray, val dataStart: Int, val dataLen: Int)

    // Extend ByteArray with a function to find a slice
    fun ByteArray.indexOfSlice(slice: ByteArray): Int {
        outer@ for (i in 0..this.size - slice.size) {
            for (j in slice.indices) {
                if (this[i + j] != slice[j]) continue@outer
            }
            return i
        }
        return -1
    }

    // Reads WAV header, returns info
    fun readWavHeader(file: File): WavInfo {
        val bytes = file.readBytes()
        require(bytes.copyOfRange(0, 4).decodeToString() == "RIFF")
        val dataIdx = bytes.indexOfSlice("data".toByteArray())
        require(dataIdx >= 0)
        val dataLen = ByteBuffer.wrap(bytes, dataIdx + 4, 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        return WavInfo(bytes.copyOfRange(0, dataIdx + 8), dataIdx + 8, dataLen)
    }

    // Writes a standard PCM WAV header
    fun makeWavHeader(totalDataLen: Int, templateHeader: ByteArray): ByteArray {
        val newHeader = templateHeader.copyOf()
        // Chunk size at offset 4 (4 bytes, little-endian)
        val chunkSize = totalDataLen + newHeader.size - 8
        ByteBuffer.wrap(newHeader, 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(chunkSize)
        // Subchunk2 size (data len) at offset newHeader.size-4 (typically)
        ByteBuffer.wrap(newHeader, newHeader.size - 4, 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(totalDataLen)
        return newHeader
    }

    // Concatenate WAV files
    fun concatWaveFiles(wavFiles: List<File>, outputFile: File) {
        require(wavFiles.isNotEmpty())
        val wavInfos = wavFiles.map(::readWavHeader)
        val totalPcmLen = wavInfos.sumOf { it.dataLen }
        val templateHeader = wavInfos[0].headerBytes
        val outputHeader = makeWavHeader(totalPcmLen, templateHeader)

        FileOutputStream(outputFile).use { out ->
            out.write(outputHeader)
            for (info in wavInfos) {
                FileInputStream(wavFiles[wavInfos.indexOf(info)]).use { `in` ->
                    `in`.skip(info.dataStart.toLong())
                    val buf = ByteArray(4096)
                    var remaining = info.dataLen
                    while (remaining > 0) {
                        val read = `in`.read(buf, 0, minOf(buf.size, remaining))
                        if (read == -1) break
                        out.write(buf, 0, read)
                        remaining -= read
                    }
                }
            }
        }
    }
}