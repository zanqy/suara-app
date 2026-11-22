package com.za.sidqi.suara.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.Equalizer
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlin.math.abs

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var equalizer: Equalizer? = null
    private var equalizerSessionId = -1

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attachEqualizer(audioSessionId)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
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
                AudioSettings.ACTION_EQ_REFRESH -> {
                    if (equalizer == null) {
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

    private fun attachEqualizer(sessionId: Int) {
        if (sessionId <= 0 || sessionId == equalizerSessionId) return
        equalizer?.release()
        equalizer = null
        equalizerSessionId = sessionId
        val created = runCatching { Equalizer(0, sessionId) }.getOrNull()
        if (created == null || runCatching { created.numberOfBands.toInt() }.getOrDefault(0) <= 0) {
            runCatching { created?.release() }
            AudioState.publish(EqState())
            return
        }
        equalizer = created
        applyEqSettings()
    }

    private fun applyEqSettings() {
        val eq = equalizer ?: run {
            AudioState.publish(EqState())
            return
        }
        runCatching { eq.setEnabled(AudioSettings.eqEnabled(this)) }
        val preset = AudioSettings.eqPreset(this)
        if (preset == "Kustom") {
            val saved = AudioSettings.eqBands(this)
            if (saved != null) {
                val count = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
                val range = runCatching { eq.bandLevelRange }.getOrNull()
                if (range != null) {
                    for (i in 0 until minOf(saved.size, count)) {
                        val level = saved[i].coerceIn(range[0].toInt(), range[1].toInt())
                        runCatching { eq.setBandLevel(i.toShort(), level.toShort()) }
                    }
                }
            }
        } else {
            applyPreset(eq, preset)
        }
        publishEqState()
    }

    private fun applyPreset(eq: Equalizer, preset: String) {
        val count = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
        val range = runCatching { eq.bandLevelRange }.getOrNull() ?: return
        if (count <= 0) return
        val min = range[0].toInt()
        val max = range[1].toInt()
        val levels = IntArray(count)
        for (i in 0 until count) {
            val pos = if (count == 1) 0.5f else i / (count - 1f)
            val factor = when (preset) {
                "Bass" -> 1f - pos * 1.6f
                "Vokal" -> 0.85f - abs(pos - 0.5f) * 2.2f
                "Treble" -> -0.35f + pos * 1.35f
                else -> 0f
            }
            val level = (max * factor.coerceIn(-1f, 1f)).toInt().coerceIn(min, max)
            levels[i] = level
            runCatching { eq.setBandLevel(i.toShort(), level.toShort()) }
        }
        AudioSettings.setEqBands(this, levels)
    }

    private fun setEqEnabled(enabled: Boolean) {
        AudioSettings.setEqEnabled(this, enabled)
        runCatching { equalizer?.setEnabled(enabled) }
        publishEqState()
    }

    private fun setEqPreset(preset: String) {
        AudioSettings.setEqPreset(this, preset)
        applyEqSettings()
    }

    private fun setEqBand(index: Int, levelMb: Int) {
        val eq = equalizer ?: return
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
        publishEqState()
    }

    private fun publishEqState() {
        val eq = equalizer ?: run {
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
                enabled = runCatching { eq.enabled }.getOrDefault(false),
                preset = AudioSettings.eqPreset(this),
                bands = bands
            )
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(controlReceiver) }
        equalizer?.release()
        equalizer = null
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
