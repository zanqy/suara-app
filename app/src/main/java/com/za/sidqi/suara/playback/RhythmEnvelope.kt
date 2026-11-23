package com.za.sidqi.suara.playback

import android.content.Context
import android.net.Uri
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.za.sidqi.suara.data.TrackEntity
import java.nio.ByteOrder
import kotlin.math.sqrt

object RhythmEnvelope {
    private const val BUCKET_MS = 100L
    private const val MAX_BUCKETS = 6000
    private val cache = mutableMapOf<Long, IntArray>()

    fun get(trackId: Long): IntArray? = cache[trackId]

    fun analyze(context: Context, track: TrackEntity): IntArray? {
        val cached = cache[track.id]
        if (cached != null) return cached
        val pfd = runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(track.uri), "r")
        }.getOrNull() ?: return null

        return pfd.use { descriptor ->
            runCatching {
                val extractor = MediaExtractor()
                if (descriptor.statSize > 0) {
                    extractor.setDataSource(descriptor.fileDescriptor, 0L, descriptor.statSize)
                } else {
                    extractor.setDataSource(descriptor.fileDescriptor)
                }
                val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index)
                        .getString(MediaFormat.KEY_MIME)
                        ?.startsWith("audio/") == true
                } ?: return@use null

                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return@use null
                extractor.selectTrack(trackIndex)
                val codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val buckets = IntArray(
                    ((track.durationMs / BUCKET_MS).toInt() + 1).coerceIn(1, MAX_BUCKETS)
                )
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var guard = 0

                try {
                    while ((!inputDone || !outputDone) && guard++ < 200000) {
                        if (!inputDone) {
                            val inputIndex = codec.dequeueInputBuffer(5000)
                            if (inputIndex >= 0) {
                                val buffer = codec.getInputBuffer(inputIndex) ?: continue
                                val size = extractor.readSampleData(buffer, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(
                                        inputIndex, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(
                                        inputIndex, 0, size, extractor.sampleTime, 0
                                    )
                                    extractor.advance()
                                }
                            }
                        }

                        val outputIndex = codec.dequeueOutputBuffer(info, 5000)
                        if (outputIndex >= 0) {
                            if (info.size > 0 &&
                                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                            ) {
                                val buffer = codec.getOutputBuffer(outputIndex)
                                if (buffer != null) {
                                    buffer.order(ByteOrder.LITTLE_ENDIAN)
                                    var sum = 0.0
                                    var count = 0
                                    while (buffer.remaining() >= 2) {
                                        val sample = buffer.short.toInt()
                                        sum += sample.toDouble() * sample
                                        count++
                                    }
                                    if (count > 0) {
                                        val rms = sqrt(sum / count).toInt()
                                        val bucket = (info.presentationTimeUs / (BUCKET_MS * 1000))
                                            .toInt()
                                            .coerceIn(0, buckets.lastIndex)
                                        if (rms > buckets[bucket]) buckets[bucket] = rms
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                    }

                    val peak = buckets.maxOrNull() ?: 0
                    if (peak > 0) {
                        for (i in buckets.indices) {
                            buckets[i] = (buckets[i] * 100 / peak).coerceIn(0, 100)
                        }
                    }
                    cache[track.id] = buckets
                    buckets
                } finally {
                    runCatching { codec.stop() }
                    runCatching { codec.release() }
                    runCatching { extractor.release() }
                }
            }.getOrNull()
        }
    }
}
