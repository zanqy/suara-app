package com.za.sidqi.suara.playback

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlin.math.roundToInt

class PlaybackService : MediaSessionService() {
    private enum class Engine { NONE, DYNAMICS, LEGACY }

    private var mediaSession: MediaSession? = null
    private var dynamics: DynamicsProcessing? = null
    private var legacy: Equalizer? = null
    private var engine = Engine.NONE
    private var eqSessionId = -1

    private val dynamicsCount = 10
    private val dynamicsCutoffs = floatArrayOf(
        31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
    )

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attachEqualizer(audioSessionId)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                attachEqualizer(mediaSession?.player?.audioSessionId ?: -1)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                attachEqualizer(mediaSession?.player?.audioSessionId ?: -1)
            }
        }
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioSettings.ACTION_EQ_ENABLED -> {
                    setEqEnabled(intent.getBooleanExtra(AudioSettings.EXTRA_ENABLED, false))
                }
                AudioSettings.ACTION_EQ_PRESET -> {
                    setEqPreset(intent.getStringExtra(AudioSettings.EXTRA_PRESET) ?: "Datar")
                }
                AudioSettings.ACTION_EQ_BAND -> {
                    setEqBand(
                        intent.getIntExtra(AudioSettings.EXTRA_BAND, 0),
                        intent.getIntExtra(AudioSettings.EXTRA_LEVEL, 0)
                    )
                }
                AudioSettings.ACTION_EQ_PREAMP -> {
                    setEqPreamp(intent.getIntExtra(AudioSettings.EXTRA_LEVEL, 0))
                }
                AudioSettings.ACTION_EQ_MODE -> {
                    setEqMode(intent.getStringExtra(AudioSettings.EXTRA_MODE) ?: "AUTO")
                }
                AudioSettings.ACTION_EQ_REFRESH -> {
                    if (engine == Engine.NONE) {
                        attachEqualizer(mediaSession?.player?.audioSessionId ?: -1)
                    } else {
                        publishEqState()
                    }
                }
                AudioSettings.ACTION_MIX_MODE -> {
                    applyMixMode(intent.getBooleanExtra(AudioSettings.EXTRA_ENABLED, false))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .build()
            .apply {
                setAudioAttributes(mediaAttributes(), !AudioSettings.mixMode(this@PlaybackService))
                setHandleAudioBecomingNoisy(true)
                volume = AudioSettings.appVolume(this@PlaybackService)
                addListener(playerListener)
            }
        mediaSession = MediaSession.Builder(this, player).build()
        ContextCompat.registerReceiver(
            this,
            controlReceiver,
            IntentFilter().apply {
                addAction(AudioSettings.ACTION_EQ_ENABLED)
                addAction(AudioSettings.ACTION_EQ_PRESET)
                addAction(AudioSettings.ACTION_EQ_BAND)
                addAction(AudioSettings.ACTION_EQ_PREAMP)
                addAction(AudioSettings.ACTION_EQ_MODE)
                addAction(AudioSettings.ACTION_EQ_REFRESH)
                addAction(AudioSettings.ACTION_MIX_MODE)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    private fun mediaAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

    private fun applyMixMode(enabled: Boolean) {
        AudioSettings.setMixMode(this, enabled)
        mediaSession?.player?.setAudioAttributes(mediaAttributes(), !enabled)
    }

    @SuppressLint("NewApi")
    private fun attachEqualizer(sessionId: Int, force: Boolean = false) {
        if (sessionId <= 0) return
        if (!force && sessionId == eqSessionId) return
        releaseEq()
        eqSessionId = sessionId

        val requestedMode = AudioSettings.eqMode(this)
        if (requestedMode != "5" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val created = runCatching { buildDynamics(sessionId) }.getOrNull()
            if (created != null) {
                dynamics = created
                engine = Engine.DYNAMICS
                if (runCatching { applyDynamicsSettings() }.isSuccess) return
                releaseEq()
            }
        }

        val fallback = runCatching { Equalizer(0, sessionId) }.getOrNull()
        if (fallback != null && runCatching { fallback.numberOfBands.toInt() }.getOrDefault(0) > 0) {
            legacy = fallback
            engine = Engine.LEGACY
            if (runCatching { applyLegacySettings() }.isSuccess) return
            releaseEq()
        }

        eqSessionId = -1
        AudioState.publish(EqState(bandMode = AudioSettings.eqMode(this)))
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun buildDynamics(sessionId: Int): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            true,
            dynamicsCount,
            false,
            0,
            false,
            0,
            true
        ).build()
        val processor = DynamicsProcessing(0, sessionId, config)
        val preset = AudioSettings.eqPreset(this)
        val saved = AudioSettings.eqBands(this, dynamicsCount)
        val levels = if (preset == "Kustom" && saved != null) {
            saved.copyOf(dynamicsCount)
        } else {
            presetLevels(preset, dynamicsCount)
        }
        if (preset != "Kustom") AudioSettings.setEqPreamp(this, presetLevelsPreamp(preset))
        processor.setEnabled(AudioSettings.eqEnabled(this))
        processor.setInputGainAllChannelsTo(AudioSettings.eqPreamp(this) / 100f)
        for (i in 0 until dynamicsCount) {
            processor.setPreEqBandAllChannelsTo(
                i,
                DynamicsProcessing.EqBand(
                    true,
                    dynamicsCutoffs[i],
                    (levels.getOrNull(i) ?: 0) / 100f
                )
            )
        }
        return processor
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun applyDynamicsSettings() {
        val processor = dynamics ?: run {
            AudioState.publish(EqState())
            return
        }
        val preset = AudioSettings.eqPreset(this)
        val saved = AudioSettings.eqBands(this, dynamicsCount)
        val levels = if (preset == "Kustom" && saved != null) {
            saved.copyOf(dynamicsCount)
        } else {
            presetLevels(preset, dynamicsCount)
        }
        if (preset != "Kustom") AudioSettings.setEqPreamp(this, presetLevelsPreamp(preset))
        processor.setEnabled(AudioSettings.eqEnabled(this))
        processor.setInputGainAllChannelsTo(AudioSettings.eqPreamp(this) / 100f)
        for (i in 0 until dynamicsCount) {
            processor.setPreEqBandAllChannelsTo(
                i,
                DynamicsProcessing.EqBand(
                    true,
                    dynamicsCutoffs[i],
                    (levels.getOrNull(i) ?: 0) / 100f
                )
            )
        }
        if (preset != "Kustom") {
            AudioSettings.setEqBands(this, levels)
        }
        publishDynamics()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun publishDynamics() {
        val processor = dynamics ?: run {
            AudioState.publish(EqState())
            return
        }
        val bands = (0 until dynamicsCount).map { i ->
            val band = runCatching { processor.getPreEqBandByChannelIndex(0, i) }.getOrNull()
            EqBand(
                index = i,
                freqHz = dynamicsCutoffs[i].toInt(),
                levelMb = ((band?.gain ?: 0f) * 100).roundToInt(),
                minMb = -1200,
                maxMb = 1200
            )
        }
        AudioState.publish(
            EqState(
                available = true,
                engine = "DYNAMICS",
                bandMode = AudioSettings.eqMode(this),
                enabled = runCatching { processor.enabled }.getOrDefault(false),
                preset = AudioSettings.eqPreset(this),
                bands = bands,
                preampMb = runCatching {
                    (processor.getInputGainByChannelIndex(0) * 100).roundToInt()
                }.getOrDefault(AudioSettings.eqPreamp(this)),
                preampMinMb = -1200,
                preampMaxMb = 600
            )
        )
    }

    private fun applyLegacySettings() {
        val eq = legacy ?: run {
            AudioState.publish(EqState())
            return
        }
        runCatching { eq.setEnabled(AudioSettings.eqEnabled(this)) }
        val count = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
        val range = runCatching { eq.bandLevelRange }.getOrNull()
        if (count <= 0 || range == null) {
            AudioState.publish(EqState())
            return
        }
        val preset = AudioSettings.eqPreset(this)
        val saved = AudioSettings.eqBands(this, count)
        val levels = if (preset == "Kustom" && saved != null && saved.size >= count) {
            saved
        } else {
            presetLevels(preset, count)
        }
        for (i in 0 until count) {
            val level = levels.getOrNull(i)?.coerceIn(range[0].toInt(), range[1].toInt()) ?: 0
            runCatching { eq.setBandLevel(i.toShort(), level.toShort()) }
        }
        if (preset != "Kustom") {
            AudioSettings.setEqBands(this, IntArray(count) { i -> levels[i] })
        }
        publishLegacy()
    }

    private fun publishLegacy() {
        val eq = legacy ?: run {
            AudioState.publish(EqState())
            return
        }
        val count = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
        val range = runCatching { eq.bandLevelRange }.getOrNull()
        if (count <= 0 || range == null) {
            AudioState.publish(EqState())
            return
        }
        val bands = (0 until count).map { i ->
            EqBand(
                index = i,
                freqHz = runCatching { eq.getCenterFreq(i.toShort()) / 1000 }.getOrDefault(0),
                levelMb = runCatching { eq.getBandLevel(i.toShort()).toInt() }.getOrDefault(0),
                minMb = range[0].toInt(),
                maxMb = range[1].toInt()
            )
        }
        AudioState.publish(
            EqState(
                available = true,
                engine = "EQUALIZER",
                bandMode = AudioSettings.eqMode(this),
                enabled = runCatching { eq.enabled }.getOrDefault(false),
                preset = AudioSettings.eqPreset(this),
                bands = bands,
                preampMb = 0,
                preampMinMb = 0,
                preampMaxMb = 0
            )
        )
    }

    private fun setEqEnabled(enabled: Boolean) {
        AudioSettings.setEqEnabled(this, enabled)
        when (engine) {
            Engine.DYNAMICS -> runCatching { dynamics?.setEnabled(enabled) }
            Engine.LEGACY -> runCatching { legacy?.setEnabled(enabled) }
            Engine.NONE -> Unit
        }
        publishEqState()
    }

    private fun setEqPreset(preset: String) {
        AudioSettings.setEqPreset(this, preset)
        if (preset != "Kustom") {
            AudioSettings.setEqPreamp(this, presetLevelsPreamp(preset))
        }
        when (engine) {
            Engine.DYNAMICS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { applyDynamicsSettings() }
            }
            Engine.LEGACY -> runCatching { applyLegacySettings() }
            Engine.NONE -> Unit
        }
    }

    private fun setEqBand(index: Int, levelMb: Int) {
        when (engine) {
            Engine.DYNAMICS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setDynamicsBand(index, levelMb)
            }
            Engine.LEGACY -> setLegacyBand(index, levelMb)
            Engine.NONE -> Unit
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setDynamicsBand(index: Int, levelMb: Int) {
        val processor = dynamics ?: return
        if (index !in 0 until dynamicsCount) return
        val level = levelMb.coerceIn(-1200, 1200)
        runCatching {
            processor.setPreEqBandAllChannelsTo(
                index,
                DynamicsProcessing.EqBand(true, dynamicsCutoffs[index], level / 100f)
            )
        }
        val saved = AudioSettings.eqBands(this, dynamicsCount)?.copyOf(dynamicsCount) ?: IntArray(dynamicsCount)
        saved[index] = level
        AudioSettings.setEqBands(this, saved)
        AudioSettings.setEqPreset(this, "Kustom")
        publishDynamics()
    }

    private fun setLegacyBand(index: Int, levelMb: Int) {
        val eq = legacy ?: return
        val count = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
        if (index !in 0 until count) return
        val range = runCatching { eq.bandLevelRange }.getOrNull() ?: return
        val level = levelMb.coerceIn(range[0].toInt(), range[1].toInt())
        runCatching { eq.setBandLevel(index.toShort(), level.toShort()) }
        val levels = IntArray(count) {
            runCatching { eq.getBandLevel(it.toShort()).toInt() }.getOrDefault(0)
        }
        AudioSettings.setEqBands(this, levels)
        AudioSettings.setEqPreset(this, "Kustom")
        runCatching { applyLegacySettings() }
    }

    private fun setEqPreamp(levelMb: Int) {
        AudioSettings.setEqPreamp(this, levelMb)
        if (engine == Engine.DYNAMICS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { dynamics?.setInputGainAllChannelsTo(AudioSettings.eqPreamp(this) / 100f) }
            publishEqState()
        }
    }

    private fun setEqMode(mode: String) {
        AudioSettings.setEqMode(this, mode)
        eqSessionId = -1
        attachEqualizer(mediaSession?.player?.audioSessionId ?: -1, force = true)
    }

    private fun publishEqState() {
        when (engine) {
            Engine.DYNAMICS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                publishDynamics()
            }
            Engine.LEGACY -> publishLegacy()
            Engine.NONE -> AudioState.publish(EqState())
        }
    }

    private fun presetLevels(preset: String, count: Int): IntArray {
        val ten = AudioSettings.presetSpec(preset).curve
        if (count == 10) return ten.copyOf()
        return IntArray(count) { i ->
            ten[(i * 9 / (count - 1).coerceAtLeast(1)).coerceIn(0, 9)]
        }
    }

    private fun presetLevelsPreamp(preset: String): Int =
        AudioSettings.presetSpec(preset).preampMb

    private fun releaseEq() {
        runCatching { dynamics?.release() }
        runCatching { legacy?.release() }
        dynamics = null
        legacy = null
        engine = Engine.NONE
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(controlReceiver) }
        releaseEq()
        eqSessionId = -1
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
