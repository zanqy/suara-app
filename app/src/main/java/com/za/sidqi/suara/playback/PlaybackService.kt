package com.za.sidqi.suara.playback

import android.annotation.SuppressLint
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.za.sidqi.suara.data.FavoriteEntity
import com.za.sidqi.suara.data.SuaraDatabase
import com.za.sidqi.suara.data.TrackEntity
import com.za.sidqi.suara.widget.WidgetRenderer
import com.za.sidqi.suara.widget.WidgetSnapshot
import com.za.sidqi.suara.widget.WidgetState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class PlaybackService : MediaSessionService() {
    companion object {
        const val ACTION_WIDGET_PREV = "com.za.sidqi.suara.WIDGET_PREV"
        const val ACTION_WIDGET_NEXT = "com.za.sidqi.suara.WIDGET_NEXT"
        const val ACTION_WIDGET_TOGGLE = "com.za.sidqi.suara.WIDGET_TOGGLE"
        const val ACTION_WIDGET_REPEAT = "com.za.sidqi.suara.WIDGET_REPEAT"
        const val ACTION_WIDGET_FAVORITE = "com.za.sidqi.suara.WIDGET_FAVORITE"
        const val ACTION_WIDGET_REFRESH = "com.za.sidqi.suara.WIDGET_REFRESH"
    }

    private enum class Engine { NONE, DYNAMICS, LEGACY }

    private var mediaSession: MediaSession? = null
    private var dynamics: DynamicsProcessing? = null
    private var legacy: Equalizer? = null
    private var engine = Engine.NONE
    private var eqSessionId = -1
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var widgetAnimationJob: Job? = null
    private var visualizer: EqVisualizer? = null
    private var visualizerSessionId = -1
    private var fftSilent = false
    private var restoreDeferred: CompletableDeferred<Unit>? = null
    private val syntheticHeights = FloatArray(28) { 8f }

    private val dynamicsCount = 10
    private val dynamicsCutoffs = floatArrayOf(
        31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
    )

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attachEqualizer(audioSessionId)
            attachVisualizer(audioSessionId)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                attachEqualizer(mediaSession?.player?.audioSessionId ?: -1)
                attachVisualizer(mediaSession?.player?.audioSessionId ?: -1)
            }
            publishWidgetState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                attachEqualizer(mediaSession?.player?.audioSessionId ?: -1)
                attachVisualizer(mediaSession?.player?.audioSessionId ?: -1)
                ensureEnvelope(mediaSession?.player?.currentMediaItem?.mediaId)
            }
            publishWidgetState()
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            ensureEnvelope(mediaItem?.mediaId)
            publishWidgetState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            publishWidgetState()
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
                ACTION_WIDGET_REFRESH -> {
                    publishWidgetState()
                    attachVisualizer(mediaSession?.player?.audioSessionId ?: -1)
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
        publishWidgetState()
        serviceScope.launch {
            runCatching { restoreQueueIfNeeded() }
            publishWidgetState()
        }
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
                addAction(ACTION_WIDGET_REFRESH)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WIDGET_TOGGLE -> {
                val player = mediaSession?.player
                if (player != null) {
                    if (player.mediaItemCount == 0) {
                        serviceScope.launch {
                            runCatching { restoreQueueIfNeeded() }
                            if (player.mediaItemCount > 0) {
                                if (player.isPlaying) player.pause() else player.play()
                                publishWidgetState()
                            }
                        }
                    } else {
                        if (player.isPlaying) player.pause() else player.play()
                        publishWidgetState()
                    }
                }
            }
            ACTION_WIDGET_PREV -> mediaSession?.player?.seekToPrevious()
            ACTION_WIDGET_NEXT -> mediaSession?.player?.seekToNext()
            ACTION_WIDGET_REPEAT -> {
                val player = mediaSession?.player ?: return super.onStartCommand(intent, flags, startId)
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                publishWidgetState()
            }
            ACTION_WIDGET_FAVORITE -> toggleFavorite()
            ACTION_WIDGET_REFRESH -> publishWidgetState()
        }
        return super.onStartCommand(intent, flags, startId)
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

    private fun publishWidgetState() {
        runCatching { publishWidgetStateInternal() }
    }

    private fun publishWidgetStateInternal() {
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val title = metadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: "Belum ada lagu"
        val artist = metadata?.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: "Tap untuk membuka Suara"
        val trackId = item?.mediaId?.toLongOrNull() ?: -1L
        val previous = WidgetState.read(this)
        val favorite = if (previous.trackId == trackId) previous.favorite else false
        WidgetState.write(
            this,
            WidgetSnapshot(
                title = title,
                artist = artist,
                durationMs = player.duration.coerceAtLeast(0),
                positionMs = player.currentPosition.coerceAtLeast(0),
                isPlaying = player.isPlaying,
                repeatMode = player.repeatMode,
                trackId = trackId,
                favorite = favorite,
                preset = AudioSettings.eqPreset(this),
                heights = previous.heights,
                live = previous.live
            )
        )
        sendWidgetBroadcast()
        updateWidgetAnimation()
        if (trackId > 0) {
            serviceScope.launch {
                val fav = runCatching {
                    SuaraDatabase.get(this@PlaybackService).dao().isFavorite(trackId)
                }.getOrDefault(false)
                WidgetState.setFavorite(this@PlaybackService, fav)
                sendWidgetBroadcast()
            }
        }
    }

    private suspend fun restoreQueueIfNeeded() {
        val player = mediaSession?.player ?: return
        if (player.mediaItemCount > 0) return
        val running = restoreDeferred
        if (running != null && !running.isCompleted) {
            running.await()
            return
        }
        val deferred = CompletableDeferred<Unit>()
        restoreDeferred = deferred
        try {
            val dao = SuaraDatabase.get(this).dao()
            val items = dao.observeQueue().first()
            if (items.isNotEmpty()) {
                val state = dao.playbackState()
                val index = items.indexOfFirst { it.id == state?.lastTrackId }.coerceAtLeast(0)
                player.setMediaItems(
                    items.map(::toMediaItem),
                    index,
                    state?.lastPositionMs ?: 0L
                )
                player.repeatMode = state?.repeatMode ?: Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = state?.shuffle ?: false
                player.prepare()
            }
        } finally {
            deferred.complete(Unit)
            if (restoreDeferred === deferred) restoreDeferred = null
        }
    }

    private fun toMediaItem(track: TrackEntity): MediaItem =
        MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist ?: "Artis tidak diketahui")
                    .setAlbumTitle(track.album ?: "")
                    .build()
            )
            .build()

    private fun ensureEnvelope(mediaId: String?) {
        // DIMATIKAN sementara: analisis MediaCodec menyebabkan crash saat play pada beberapa perangkat.
        // Diperbaiki lagi setelah logcat tersedia.
    }

    private fun attachVisualizer(sessionId: Int) {
        runCatching { attachVisualizerInternal(sessionId) }
    }

    private fun attachVisualizerInternal(sessionId: Int) {
        if (sessionId <= 0) {
            visualizer?.stop()
            visualizer = null
            visualizerSessionId = -1
            return
        }
        if (sessionId == visualizerSessionId && visualizer != null) return
        visualizerSessionId = sessionId
        visualizer?.stop()
        visualizer = null
        if (!AudioSettings.visualizerEnabled(this)) {
            WidgetState.setHeights(this, IntArray(28), false)
            visualizerSessionId = -1
            return
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            WidgetState.setHeights(this, IntArray(28), false)
            visualizerSessionId = -1
            return
        }
        val engine = EqVisualizer(this) { heights ->
            fftSilent = heights.all { it <= 6 }
            WidgetState.setHeights(this@PlaybackService, heights, !fftSilent)
            WidgetRenderer.updateAll(this@PlaybackService)
        }
        if (engine.start(sessionId)) {
            visualizer = engine
        } else {
            WidgetState.setHeights(this, IntArray(28), false)
            visualizerSessionId = -1
        }
    }

    private fun updateWidgetAnimation() {
        val playing = mediaSession?.player?.isPlaying == true
        if (playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (widgetAnimationJob?.isActive == true) return
            widgetAnimationJob = serviceScope.launch {
                while (isActive) {
                    delay(90)
                    if (visualizer?.isLive() == true && !fftSilent) continue
                    val heights = nextSyntheticHeights()
                    WidgetState.setHeights(this@PlaybackService, heights, false)
                    WidgetRenderer.updateAll(this@PlaybackService)
                }
            }
        } else {
            widgetAnimationJob?.cancel()
            widgetAnimationJob = null
            if (!playing) {
                fftSilent = false
                WidgetState.setHeights(this, IntArray(28), false)
                WidgetRenderer.updateAll(this)
            }
        }
    }

    private fun heightsFromEnvelope(envelope: IntArray, positionMs: Long): IntArray {
        val index = (positionMs / 100L).toInt().coerceIn(envelope.indices)
        val level = envelope[index] / 100f
        val t = System.currentTimeMillis() / 1000f
        return IntArray(28) { i ->
            val wave = abs(sin(i * 0.62f + t * 5.5f))
            val detail = abs(sin(i * 1.7f + t * 9f))
            (5f + level * (9f + wave * 14f + detail * 3f))
                .roundToInt()
                .coerceIn(5, 30)
        }
    }

    private fun nextSyntheticHeights(): IntArray {
        val t = System.currentTimeMillis() / 1000f
        val output = IntArray(28)
        for (i in 0 until 28) {
            val target = 7f +
                abs(sin(t * 3.1f + i * 0.47f)) * 18f +
                abs(sin(t * 1.3f + i * 0.19f)) * 5f
            syntheticHeights[i] = syntheticHeights[i] * 0.6f + target * 0.4f
            output[i] = syntheticHeights[i].roundToInt().coerceIn(5, 30)
        }
        return output
    }

    private fun toggleFavorite() {
        val trackId = mediaSession?.player?.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        serviceScope.launch {
            val dao = SuaraDatabase.get(this@PlaybackService).dao()
            val favorite = runCatching { dao.isFavorite(trackId) }.getOrDefault(false)
            if (favorite) {
                dao.removeFavorite(trackId)
            } else {
                dao.addFavorite(FavoriteEntity(trackId, System.currentTimeMillis()))
            }
            WidgetState.setFavorite(this@PlaybackService, !favorite)
            sendWidgetBroadcast()
        }
    }

    private fun sendWidgetBroadcast() {
        sendBroadcast(
            Intent(this, com.za.sidqi.suara.widget.SuaraWidgetProvider::class.java)
                .setAction(WidgetState.ACTION_STATE)
        )
    }

    private fun releaseEq() {
        runCatching { dynamics?.release() }
        runCatching { legacy?.release() }
        dynamics = null
        legacy = null
        engine = Engine.NONE
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(controlReceiver) }
        serviceScope.cancel()
        widgetAnimationJob = null
        visualizer?.stop()
        visualizer = null
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
