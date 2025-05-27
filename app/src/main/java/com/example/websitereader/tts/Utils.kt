package com.example.websitereader.tts

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

    data class WavSpec(
        val sampleRate: Int,
        val channelCount: Int,
        val pcmEncoding: Int   // e.g. AudioFormat.ENCODING_PCM_16BIT
    )


    private fun decodeToPcmBytes(audioFile: File): Pair<ByteArray, WavSpec> {
        val extractor = MediaExtractor()
        extractor.setDataSource(audioFile.absolutePath)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        require(trackIndex != -1) { "No audio track found" }
        extractor.selectTrack(trackIndex)
        val mime = format!!.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        // Get audio info
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        // We'll output 16-bit PCM
        val pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        val output = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputBufIdx = codec.dequeueInputBuffer(10000)
                if (inputBufIdx >= 0) {
                    val inputBuf = codec.getInputBuffer(inputBufIdx)!!
                    val sampleSize = extractor.readSampleData(inputBuf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputBufIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        sawInputEOS = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inputBufIdx, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            val outputBufIdx = codec.dequeueOutputBuffer(bufferInfo, 10000)
            when {
                outputBufIdx >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outputBufIdx)!!
                    outBuf.position(bufferInfo.offset)
                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                    val chunk = ByteArray(bufferInfo.size)
                    outBuf.get(chunk)
                    output.write(chunk)
                    codec.releaseOutputBuffer(outputBufIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                }

                outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // handle format change if needed
                }

                outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // no available buffer, just skip for now
                }
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        val wavSpec = WavSpec(sampleRate, channelCount, pcmEncoding)
        return output.toByteArray() to wavSpec
    }

    private fun makePcmWavHeader(
        totalDataLen: Int, numChannels: Int, sampleRate: Int, bitsPerSample: Int
    ): ByteArray {
        val header = ByteArray(44)
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val chunkSize = 36 + totalDataLen
        // RIFF header
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] =
            'F'.code.toByte(); header[3] = 'F'.code.toByte()
        ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] =
            'V'.code.toByte(); header[11] = 'E'.code.toByte()
        // fmt subchunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] =
            't'.code.toByte(); header[15] = ' '.code.toByte()
        ByteBuffer.wrap(header, 16, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(16) // PCM
        ByteBuffer.wrap(header, 20, 2).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1) // Audio format = PCM
        ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(numChannels.toShort())
        ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate)
        ByteBuffer.wrap(header, 28, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate)
        ByteBuffer.wrap(header, 32, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(blockAlign.toShort())
        ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(bitsPerSample.toShort())
        // data subchunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] =
            't'.code.toByte(); header[39] = 'a'.code.toByte()
        ByteBuffer.wrap(header, 40, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalDataLen)
        return header
    }

    fun concatAudioFilesToWav(
        inputFiles: List<File>, outputFile: File
    ) {
        require(inputFiles.isNotEmpty()) { "No input files" }
        val pcmStreams = mutableListOf<ByteArray>()
        var wavSpec: WavSpec? = null
        for (file in inputFiles) {
            val (pcm, spec) = decodeToPcmBytes(file)
            if (wavSpec == null) {
                wavSpec = spec
            } else {
                require(
                    spec.sampleRate == wavSpec.sampleRate && spec.channelCount == wavSpec.channelCount && spec.pcmEncoding == wavSpec.pcmEncoding
                ) { "All inputs must have same sample rate/channels/encoding" }
            }
            pcmStreams.add(pcm)
        }
        val totalPcmLen = pcmStreams.sumOf { it.size }

        // Prepare header
        // Create a template header modeled after PCM WAV
        // If you have an existing WAV you want to match, pass its first 44 bytes as templateWavHeader
        val header = makePcmWavHeader(
            totalDataLen = totalPcmLen,
            numChannels = wavSpec!!.channelCount,
            sampleRate = wavSpec.sampleRate,
            bitsPerSample = 16 // Because we always use PCM 16-bit output
        )

        FileOutputStream(outputFile).use { out ->
            out.write(header)
            for (pcm in pcmStreams) out.write(pcm)
        }
    }

    // Simple WAV header reader (for PCM)
    @Suppress("ArrayInDataClass")
    data class WavInfo(val headerBytes: ByteArray, val dataStart: Int, val dataLen: Int)

    // Extend ByteArray with a function to find a slice
    private fun ByteArray.indexOfSlice(slice: ByteArray): Int {
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
        val reportedDataLen =
            ByteBuffer.wrap(bytes, dataIdx + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val dataStart = dataIdx + 8

        // If dataLen is 0xFFFFFFFF or exceeds file size, fallback
        val maxPossibleDataLen = bytes.size - dataStart
        val dataLen =
            if (reportedDataLen == -1 || reportedDataLen > maxPossibleDataLen) maxPossibleDataLen
            else reportedDataLen
        // Make header up to and including the dataLen field (since we want to replace it)
        return WavInfo(bytes.copyOfRange(0, dataStart), dataStart, dataLen)
    }

    // Writes a standard PCM WAV header
    private fun makeWavHeader(totalDataLen: Int, templateHeader: ByteArray): ByteArray {
        val newHeader = templateHeader.copyOf()
        // Chunk size at offset 4 (4 bytes, little-endian)
        val chunkSize = totalDataLen + newHeader.size - 8
        ByteBuffer.wrap(newHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize)
        // Subchunk2 size (data len) at offset newHeader.size-4 (typically)
        ByteBuffer.wrap(newHeader, newHeader.size - 4, 4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(totalDataLen)
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
            for ((file, info) in wavFiles.zip(wavInfos)) {
                FileInputStream(file).use { input ->
                    input.skip(info.dataStart.toLong())
                    val buf = ByteArray(4096)
                    var remaining = info.dataLen
                    while (remaining > 0) {
                        val read = input.read(buf, 0, minOf(buf.size, remaining))
                        if (read == -1) break
                        out.write(buf, 0, read)
                        remaining -= read
                    }
                }
            }
        }
    }
}