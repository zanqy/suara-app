package com.za.sidqi.suara.playback

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class EqVisualizer(
    private val context: Context,
    private val onHeights: (IntArray) -> Unit
) {
    private var visualizer: Visualizer? = null
    private var previous = FloatArray(BAND_COUNT)
    private var lastEmit = 0L

    fun start(sessionId: Int): Boolean {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            val effect = Visualizer(sessionId)
            val range = Visualizer.getCaptureSizeRange()
            effect.captureSize = range[1]
            effect.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) = Unit

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null) return
                        val now = System.currentTimeMillis()
                        if (now - lastEmit < EMIT_INTERVAL_MS) return
                        lastEmit = now
                        onHeights(computeHeights(fft))
                    }
                },
                min(Visualizer.getMaxCaptureRate(), CAPTURE_RATE),
                false,
                true
            )
            effect.enabled = true
            visualizer = effect
            previous = FloatArray(BAND_COUNT)
            true
        } catch (t: Throwable) {
            runCatching { visualizer?.release() }
            visualizer = null
            false
        }
    }

    private fun computeHeights(fft: ByteArray): IntArray {
        val usable = (fft.size / 2).coerceAtLeast(2)
        val output = IntArray(BAND_COUNT)
        for (index in 0 until BAND_COUNT) {
            val startRatio = (index.toDouble() / BAND_COUNT).pow(2.0)
            val endRatio = ((index + 1).toDouble() / BAND_COUNT).pow(2.0)
            val start = (startRatio * usable).toInt().coerceIn(1, usable - 1)
            val end = (endRatio * usable).toInt().coerceIn(start + 1, usable)
            var sum = 0
            var count = 0
            for (bin in start until end) {
                sum += fft[bin].toInt() and 0xFF
                count++
            }
            val average = if (count > 0) sum.toFloat() / count else 0f
            val target = MIN_HEIGHT + (average / 255f) * (MAX_HEIGHT - MIN_HEIGHT)
            previous[index] = previous[index] * SMOOTHING + target * (1f - SMOOTHING)
            output[index] = previous[index].roundToInt().coerceIn(MIN_HEIGHT.toInt(), MAX_HEIGHT.toInt())
        }
        return output
    }

    fun stop() {
        runCatching { visualizer?.setEnabled(false) }
        runCatching { visualizer?.release() }
        visualizer = null
    }

    companion object {
        private const val BAND_COUNT = 28
        private const val EMIT_INTERVAL_MS = 85L
        private const val CAPTURE_RATE = 20000
        private const val MIN_HEIGHT = 5f
        private const val MAX_HEIGHT = 30f
        private const val SMOOTHING = 0.55f
    }
}
