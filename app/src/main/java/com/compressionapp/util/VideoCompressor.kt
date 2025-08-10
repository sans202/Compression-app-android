package com.compressionapp.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodecInfo
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VideoCompressor {

    private const val TIMEOUT_USEC = 2500L

    // This is the main public function. It's a suspend function to ensure it runs off the main thread.
    suspend fun compressVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        quality: Int // A value from 0-100
    ) = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            // Set up the extractor
            extractor.setDataSource(context, inputUri, null)

            // Find the video track and get its format
            val videoTrackIndex = findTrackIndex(extractor, "video/")
            if (videoTrackIndex == -1) {
                throw IllegalStateException("No video track found in the input file.")
            }
            val originalVideoFormat = extractor.getTrackFormat(videoTrackIndex)

            // Calculate the new bitrate based on the quality slider
            val originalBitrate = originalVideoFormat.getInteger(MediaFormat.KEY_BIT_RATE, 9000000) // Default to 9Mbps
            val newBitrate = (originalBitrate * (quality / 100.0)).toInt()

            // Create a new video format for the encoder
            val outputVideoFormat = MediaFormat.createVideoFormat(
                originalVideoFormat.getString(MediaFormat.KEY_MIME)!!,
                originalVideoFormat.getInteger(MediaFormat.KEY_WIDTH),
                originalVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            ).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, newBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, originalVideoFormat.getInteger(MediaFormat.KEY_FRAME_RATE))
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, originalVideoFormat.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL))
            }

            // Set up the encoder
            val encoder = MediaCodec.createEncoderByType(outputVideoFormat.getString(MediaFormat.KEY_MIME)!!)
            encoder.configure(outputVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            // Set up the muxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrackIndex = muxer.addTrack(outputVideoFormat)

            // Find the audio track and pass it through without re-encoding
            val audioTrackIndex = findTrackIndex(extractor, "audio/")
            var muxerAudioTrackIndex = -1
            if(audioTrackIndex != -1) {
                val audioFormat = extractor.getTrackFormat(audioTrackIndex)
                muxerAudioTrackIndex = muxer.addTrack(audioFormat)
            }

            muxer.start()

            // Select the video track for processing
            extractor.selectTrack(videoTrackIndex)

            // The main transcoding loop
            val bufferInfo = MediaCodec.BufferInfo()
            val buffer = ByteBuffer.allocate(1024 * 1024) // 1MB buffer
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    break // End of stream
                }

                val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC)
                if(inputBufferIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                    inputBuffer?.put(buffer)
                    encoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, extractor.sampleFlags)
                }

                var outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                while(outputBufferIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    muxer.writeSampleData(muxerVideoTrackIndex, outputBuffer!!, bufferInfo)
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                }

                extractor.advance()
            }

            // Also copy the audio track
            if (audioTrackIndex != -1) {
                extractor.selectTrack(audioTrackIndex)
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        break
                    }
                    bufferInfo.set(0, sampleSize, extractor.sampleTime, extractor.sampleFlags)
                    muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
                    extractor.advance()
                }
            }

        } finally {
            extractor.release()
            muxer?.stop()
            muxer?.release()
        }
    }

    private fun findTrackIndex(extractor: MediaExtractor, mimeTypePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(mimeTypePrefix) == true) {
                return i
            }
        }
        return -1
    }
}

// Note: This is a simplified implementation. A production-ready version would need
// more robust error handling, support for different video formats, and potentially
// hardware-accelerated decoding/encoding via Surfaces. It also needs MediaCodecInfo
// which I can't import directly here. I'll add it in the next step.
