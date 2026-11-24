package com.za.sidqi.suara.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max

object WaveformLoader {
    fun load(context: Context, uriString: String, bars: Int): FloatArray? = runCatching {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(uriString), null)
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    break
                }
            }
            if (trackIndex < 0) return@runCatching null

            val format = extractor.getTrackFormat(trackIndex)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            if (durationUs <= 0L) return@runCatching null

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@runCatching null
            extractor.selectTrack(trackIndex)
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val peaks = FloatArray(bars)
            val info = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            var guard = 0

            while (!outputEos && guard < 20000) {
                guard++
                if (!inputEos) {
                    val inputIndex = decoder.dequeueInputBuffer(10000L)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex) ?: break
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEos = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = decoder.dequeueOutputBuffer(info, 10000L)
                if (outputIndex >= 0) {
                    val buffer = decoder.getOutputBuffer(outputIndex)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val samples = buffer.asShortBuffer()
                        val bin = ((info.presentationTimeUs.toFloat() / durationUs) * bars)
                            .toInt().coerceIn(0, bars - 1)
                        var peak = peaks[bin]
                        val count = samples.remaining()
                        val step = max(1, count / 256)
                        var i = 0
                        while (i < count) {
                            val value = abs(samples.get(i).toInt())
                            if (value > peak) peak = value.toFloat()
                            i += step
                        }
                        peaks[bin] = peak
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEos = true
                }
            }

            val maximum = peaks.maxOrNull() ?: return@runCatching null
            if (maximum <= 0f) return@runCatching null
            FloatArray(bars) { i -> 0.14f + 0.86f * (peaks[i] / maximum) }
        } finally {
            codec?.let { runCatching { it.stop(); it.release() } }
            extractor.release()
        }
    }.getOrNull()
}
