package com.za.sidqi.suara.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.za.sidqi.suara.R
import com.za.sidqi.suara.data.PlaylistSummary
import com.za.sidqi.suara.playback.AudioSettings
import com.za.sidqi.suara.playback.AudioState
import com.za.sidqi.suara.playback.EqBand
import com.za.sidqi.suara.playback.EqState
import com.za.sidqi.suara.data.TrackEntity
import com.za.sidqi.suara.playback.PlayerHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

private val Bg = Color(0xFF0B0D0B)
private val Surface = Color(0xFF171B17)
private val Ink = Color(0xFFF3F0E7)
private val Muted = Color(0xFFB7BFB4)
private val Coral = Color(0xFFFF7654)
private val Amber = Color(0xFFF3C45C)
private val Mint = Color(0xFF7CE0B4)
private val Line = Color(0xFF333B33)

private fun suaraIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.White)
    ).build()

private object SuaraIcons {
    val Play = suaraIcon("Play", "M8,5v14l11,-7z")
    val Pause = suaraIcon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")
    val Previous = suaraIcon("Previous", "M6,6h2v12H6zM9.5,12l8.5,6V6z")
    val Next = suaraIcon("Next", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    val Shuffle = suaraIcon(
        "Shuffle",
        "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5V4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04 -3.13,-3.13z"
    )
    val Repeat = suaraIcon(
        "Repeat",
        "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v5z"
    )
    val RepeatOne = suaraIcon(
        "RepeatOne",
        "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v5zM13,15V9h-2v1h-1v2h1v3h2z"
    )
    val Favorite = suaraIcon(
        "Favorite",
        "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"
    )
    val FavoriteBorder = suaraIcon(
        "FavoriteBorder",
        "M16.5,3c-1.74,0 -3.41,0.81 -4.5,2.09C10.91,3.81 9.24,3 7.5,3 4.42,3 2,5.42 2,8.5c0,3.78 3.4,6.86 8.55,11.54L12,21.35l1.45,-1.32C18.6,15.36 22,12.28 22,8.5 22,5.42 19.58,3 16.5,3zM12.1,18.55l-0.1,0.1 -0.1,-0.1C7.14,14.24 4,11.39 4,8.5 4,6.5 5.5,5 7.5,5c1.54,0 3.04,0.99 3.57,2.36h1.87C13.46,5.99 14.96,5 16.5,5c2,0 3.5,1.5 3.5,3.5 0,2.89 -3.14,5.74 -7.9,10.05z"
    )
    val Queue = suaraIcon(
        "Queue",
        "M15,6H3v2h12V6zM15,10H3v2h12v-2zM3,16h8v-2H3v2zM17,6v8.18c-0.31,-0.11 -0.65,-0.18 -1,-0.18 -1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3V8h3V6h-5z"
    )
    val More = suaraIcon(
        "More",
        "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"
    )
    val Add = suaraIcon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val Delete = suaraIcon(
        "Delete",
        "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"
    )
    val Close = suaraIcon(
        "Close",
        "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"
    )
    val VolumeUp = suaraIcon(
        "VolumeUp",
        "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z"
    )
    val VolumeOff = suaraIcon(
        "VolumeOff",
        "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z"
    )
    val Tune = suaraIcon(
        "Tune",
        "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z"
    )
    val Back = suaraIcon("Back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
}

private val Fraunces = FontFamily(Font(R.font.fraunces))
private val Plex = FontFamily(
    Font(R.font.plex_regular),
    Font(R.font.plex_semibold, FontWeight.SemiBold)
)
private val DmSans = FontFamily(Font(R.font.dm_sans))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Coral,
                    background = Bg,
                    surface = Surface,
                    onPrimary = Color(0xFF14180F),
                    onBackground = Ink,
                    onSurface = Ink
                )
            ) {
                SuaraRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuaraRoot() {
    val context = LocalContext.current
    val vm: LibraryViewModel = viewModel()
    val tracks by vm.visibleTracks.collectAsState()
    val allTracks by vm.tracks.collectAsState()
    val favoriteIds by vm.favoriteIds.collectAsState()
    val queue by vm.queue.collectAsState()
    val playlists by vm.playlists.collectAsState()

    var hasPermission by remember { mutableStateOf(hasAudioPermission(context)) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showPlaylists by rememberSaveable { mutableStateOf(false) }
    var showAudio by rememberSaveable { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<TrackEntity?>(null) }
    var appVolume by remember { mutableStateOf(AudioSettings.appVolume(context)) }
    var mixMode by remember { mutableStateOf(AudioSettings.mixMode(context)) }
    val eqState by AudioState.eq.collectAsState()
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var currentTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var restored by remember { mutableStateOf(false) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasAudioPermission(context)
        if (hasPermission) vm.scan()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(requiredPermissions()) else vm.scan()
    }

    DisposableEffect(Unit) {
        PlayerHolder.connect(context) { connected ->
            controller = connected
            connected.volume = AudioSettings.appVolume(context)
        }
        onDispose { }
    }

    LaunchedEffect(controller) {
        val player = controller ?: return@LaunchedEffect
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentTrack = allTracks.firstOrNull { it.id.toString() == mediaItem?.mediaId }
                durationMs = player.duration.coerceAtLeast(0)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                durationMs = player.duration.coerceAtLeast(0)
            }

            override fun onVolumeChanged(volume: Float) {
                appVolume = volume.coerceIn(0f, 1f)
            }
        }
        player.addListener(listener)
        try {
            while (true) {
                positionMs = player.currentPosition.coerceAtLeast(0)
                durationMs = player.duration.coerceAtLeast(0)
                if (currentTrack == null && player.currentMediaItem != null) {
                    currentTrack = allTracks.firstOrNull {
                        it.id.toString() == player.currentMediaItem?.mediaId
                    }
                }
                delay(500)
            }
        } finally {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(controller, queue, restored) {
        val player = controller ?: return@LaunchedEffect
        if (restored || queue.isEmpty() || player.mediaItemCount > 0) return@LaunchedEffect
        val state = vm.playbackState()
        val index = queue.indexOfFirst { it.id == state?.lastTrackId }.coerceAtLeast(0)
        player.setMediaItems(queue.map(::toMediaItem), index, state?.lastPositionMs ?: 0L)
        player.repeatMode = state?.repeatMode ?: Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = state?.shuffle ?: false
        player.prepare()
        restored = true
        currentTrack = queue.getOrNull(index)
    }

    fun playTrack(track: TrackEntity, list: List<TrackEntity>) {
        val player = controller ?: return
        val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.setMediaItems(list.map(::toMediaItem), index, 0L)
        player.prepare()
        player.play()
        currentTrack = track
        vm.saveQueue(list)
    }

    fun addToQueue(track: TrackEntity) {
        val player = controller ?: return
        player.addMediaItem(toMediaItem(track))
        vm.saveQueue(queue + track)
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (currentTrack != null) {
                MiniPlayer(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    onOpen = { showNowPlaying = true },
                    onToggle = {
                        if (isPlaying) controller?.pause() else controller?.play()
                    },
                    onNext = { controller?.seekToNext() }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Suara.", fontFamily = Fraunces, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("MUSIK LOKAL", fontFamily = Plex, fontSize = 10.sp, color = Muted, letterSpacing = 2.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        showAudio = true
                        AudioSettings.send(context, AudioSettings.ACTION_EQ_REFRESH)
                    }) {
                        Icon(SuaraIcons.Tune, contentDescription = "Audio", tint = Muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AUDIO", fontFamily = Plex, fontSize = 10.sp, color = Muted)
                    }
                    TextButton(onClick = { showPlaylists = true }) {
                        Text("PLAYLIST", fontFamily = Plex, fontSize = 10.sp, color = Coral)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            SearchField(value = vm.query.collectAsState().value, onValueChange = { vm.query.value = it })
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceFilter.entries.forEach { item ->
                    FilterChip(
                        selected = vm.filter.collectAsState().value == item,
                        onClick = { vm.filter.value = item },
                        label = { Text(item.label, fontFamily = Plex, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Coral,
                            selectedLabelColor = Color(0xFF14180F),
                            labelColor = Muted,
                            containerColor = Surface
                        )
                    )
                }
            }

            val sortMode by vm.sort.collectAsState()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${tracks.size} lagu",
                    fontFamily = Plex,
                    fontSize = 10.sp,
                    color = Muted
                )
                TextButton(onClick = {
                    vm.sort.value = when (sortMode) {
                        SortMode.TITLE -> SortMode.RECENT
                        SortMode.RECENT -> SortMode.DURATION
                        SortMode.DURATION -> SortMode.TITLE
                    }
                }) {
                    Text("Urut: ${sortMode.label}", fontFamily = Plex, fontSize = 10.sp, color = Coral)
                }
            }

            if (vm.scanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Amber,
                    trackColor = Line
                )
            }

            if (!hasPermission) {
                PermissionCard(
                    onOpenSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    },
                    onRequest = { launcher.launch(requiredPermissions()) }
                )
            } else if (tracks.isEmpty() && !vm.scanning) {
                EmptyState()
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            isFavorite = favoriteIds.contains(track.id),
                            onClick = { playTrack(track, tracks) },
                            onPlay = { playTrack(track, tracks) },
                            onQueue = { addToQueue(track) },
                            onAddToPlaylist = { playlistTarget = track },
                            onFavorite = { vm.toggleFavorite(track) },
                            onDelete = { vm.removeMissing(track) }
                        )
                    }
                }
            }
        }
    }

    if (showNowPlaying && currentTrack != null) {
        ModalBottomSheet(
            onDismissRequest = { showNowPlaying = false },
            containerColor = Surface
        ) {
            NowPlaying(
                track = currentTrack!!,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                isFavorite = favoriteIds.contains(currentTrack!!.id),
                shuffle = controller?.shuffleModeEnabled == true,
                repeatMode = controller?.repeatMode ?: Player.REPEAT_MODE_OFF,
                onSeek = { controller?.seekTo(it) },
                onToggle = { if (isPlaying) controller?.pause() else controller?.play() },
                onPrev = { controller?.seekToPrevious() },
                onNext = { controller?.seekToNext() },
                onShuffle = {
                    controller?.shuffleModeEnabled = !(controller?.shuffleModeEnabled ?: false)
                },
                onRepeat = {
                    controller?.repeatMode = when (controller?.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                },
                onFavorite = { vm.toggleFavorite(currentTrack!!) },
                onQueue = { showQueue = true },
                appVolume = appVolume,
                onVolume = { value ->
                    appVolume = value
                    controller?.volume = value
                    AudioSettings.setAppVolume(context, value)
                }
            )
        }
    }

    if (showAudio) {
        Dialog(
            onDismissRequest = { showAudio = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(Modifier.fillMaxSize(), color = Bg) {
                AudioScreen(
                    appVolume = appVolume,
                    onVolume = { value ->
                        appVolume = value
                        controller?.volume = value
                        AudioSettings.setAppVolume(context, value)
                    },
                    eqState = eqState,
                    onEqEnabled = { enabled ->
                        AudioSettings.setEqEnabled(context, enabled)
                        AudioSettings.send(context, AudioSettings.ACTION_EQ_ENABLED) {
                            putExtra(AudioSettings.EXTRA_ENABLED, enabled)
                        }
                    },
                    onPreset = { preset ->
                        AudioSettings.setEqPreset(context, preset)
                        AudioSettings.send(context, AudioSettings.ACTION_EQ_PRESET) {
                            putExtra(AudioSettings.EXTRA_PRESET, preset)
                        }
                    },
                    onBand = { band, level ->
                        AudioSettings.send(context, AudioSettings.ACTION_EQ_BAND) {
                            putExtra(AudioSettings.EXTRA_BAND, band)
                            putExtra(AudioSettings.EXTRA_LEVEL, level)
                        }
                    },
                    onPreamp = { level ->
                        AudioSettings.setEqPreamp(context, level)
                        AudioSettings.send(context, AudioSettings.ACTION_EQ_PREAMP) {
                            putExtra(AudioSettings.EXTRA_LEVEL, level)
                        }
                    },
                    mixMode = mixMode,
                    onMixMode = { enabled ->
                        mixMode = enabled
                        AudioSettings.setMixMode(context, enabled)
                        AudioSettings.send(context, AudioSettings.ACTION_MIX_MODE) {
                            putExtra(AudioSettings.EXTRA_ENABLED, enabled)
                        }
                    },
                    onBack = { showAudio = false }
                )
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            containerColor = Surface
        ) {
            QueueSheet(
                queue = queue,
                currentId = currentTrack?.id,
                onPlay = { playTrack(it, queue) },
                onRemove = { index ->
                    controller?.removeMediaItem(index)
                    vm.saveQueue(queue.filterIndexed { i, _ -> i != index })
                }
            )
        }
    }

    if (showPlaylists) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylists = false },
            containerColor = Surface
        ) {
            PlaylistSheet(
                playlists = playlists,
                tracksFor = { id -> vm.playlistTracks(id) },
                onPlayAll = { list -> list.firstOrNull()?.let { playTrack(it, list) } },
                onPlayTrack = { track, list -> playTrack(track, list) },
                onRemoveTrack = { id, track -> vm.removeFromPlaylist(id, track) },
                onCreate = { name -> vm.createPlaylist(name) },
                onDelete = { id -> vm.deletePlaylist(id) }
            )
        }
    }

    playlistTarget?.let { track ->
        AddToPlaylistDialog(
            track = track,
            playlists = playlists,
            onDismiss = { playlistTarget = null },
            onAdd = { id -> vm.addToPlaylist(id, track); playlistTarget = null },
            onCreate = { name -> vm.createPlaylist(name, track); playlistTarget = null }
        )
    }

    LaunchedEffect(controller, currentTrack, isPlaying) {
        val player = controller ?: return@LaunchedEffect
        while (true) {
            delay(5000)
            vm.savePlaybackState(
                trackId = currentTrack?.id,
                positionMs = player.currentPosition,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .background(Color(0xFF101410))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 13.sp, fontFamily = Plex),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isBlank()) {
            Text("Cari judul atau artis…", color = Color(0xFF737C72), fontSize = 12.sp, fontFamily = Plex)
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Izin musik diperlukan", fontFamily = Fraunces, fontSize = 17.sp, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "Suara perlu membaca file audio di folder Music dan Download. Tidak ada data yang dikirim keluar.",
                fontFamily = DmSans, fontSize = 12.sp, color = Muted
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color(0xFF14180F))
                ) { Text("Izinkan", fontFamily = Plex, fontSize = 11.sp) }
                TextButton(onClick = onOpenSettings) {
                    Text("Buka Pengaturan", fontFamily = Plex, fontSize = 11.sp, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        Modifier.fillMaxWidth().padding(top = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Belum ada musik", fontFamily = Fraunces, fontSize = 20.sp, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "Simpan lagu dari Telegram ke Music, atau taruh file di Download/Simpan.",
                fontFamily = DmSans, fontSize = 12.sp, color = Muted
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: TrackEntity,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtBlock(track, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                fontFamily = DmSans,
                fontSize = 14.sp,
                color = if (track.isMissing) Muted else Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(track.artist ?: "Artis tidak diketahui")
                    if (track.isMissing) append(" · Tidak ditemukan")
                },
                fontFamily = Plex,
                fontSize = 10.sp,
                color = if (track.isMissing) Coral else Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            formatDuration(track.durationMs),
            fontFamily = Plex,
            fontSize = 10.sp,
            color = Muted
        )
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(SuaraIcons.More, contentDescription = "Menu lagu", tint = Muted, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Putar") }, onClick = { menu = false; onPlay() })
                DropdownMenuItem(text = { Text("Tambahkan ke antrean") }, onClick = { menu = false; onQueue() })
                DropdownMenuItem(text = { Text("Tambahkan ke playlist") }, onClick = { menu = false; onAddToPlaylist() })
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "Hapus favorit" else "Favorit") },
                    onClick = { menu = false; onFavorite() }
                )
                if (track.isMissing) {
                    DropdownMenuItem(text = { Text("Hapus dari daftar") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun ArtBlock(track: TrackEntity, size: androidx.compose.ui.unit.Dp) {
    val color = when (track.source) {
        "MUSIC" -> Coral
        "DOWNLOAD" -> Amber
        else -> Mint
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = if (track.isMissing) 0.25f else 1f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials(track.title),
            color = Color(0xFF14180F),
            fontFamily = Plex,
            fontWeight = FontWeight.Bold,
            fontSize = if (size > 100.dp) 30.sp else 13.sp
        )
    }
}

@Composable
private fun MiniPlayer(
    track: TrackEntity,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtBlock(track, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontFamily = DmSans, fontSize = 12.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist ?: "Suara", fontFamily = Plex, fontSize = 9.sp, color = Muted, maxLines = 1)
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (isPlaying) SuaraIcons.Pause else SuaraIcons.Play,
                contentDescription = if (isPlaying) "Jeda" else "Putar",
                tint = Ink,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                SuaraIcons.Next,
                contentDescription = "Lagu berikutnya",
                tint = Ink,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NowPlaying(
    track: TrackEntity,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    isFavorite: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit,
    onQueue: () -> Unit,
    appVolume: Float,
    onVolume: (Float) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArtBlock(track, 190.dp)
        Spacer(Modifier.height(18.dp))
        Text(track.title, fontFamily = Fraunces, fontSize = 22.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(track.artist ?: "Artis tidak diketahui", fontFamily = Plex, fontSize = 11.sp, color = Muted)
        Spacer(Modifier.height(18.dp))
        Slider(
            value = positionMs.coerceAtMost(durationMs.coerceAtLeast(1L)).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Coral,
                activeTrackColor = Coral,
                inactiveTrackColor = Line
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(positionMs), fontFamily = Plex, fontSize = 10.sp, color = Muted)
            Text(formatDuration(durationMs), fontFamily = Plex, fontSize = 10.sp, color = Muted)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (appVolume <= 0.01f) SuaraIcons.VolumeOff else SuaraIcons.VolumeUp,
                contentDescription = "Volume aplikasi",
                tint = Muted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Slider(
                value = appVolume,
                onValueChange = onVolume,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Coral,
                    activeTrackColor = Coral,
                    inactiveTrackColor = Line
                )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${(appVolume * 100).roundToInt()}%",
                fontFamily = Plex,
                fontSize = 10.sp,
                color = Muted,
                modifier = Modifier.width(36.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffle) {
                Icon(
                    SuaraIcons.Shuffle,
                    contentDescription = "Acak",
                    tint = if (shuffle) Coral else Muted,
                    modifier = Modifier.size(21.dp)
                )
            }
            IconButton(onClick = onPrev) {
                Icon(SuaraIcons.Previous, contentDescription = "Sebelumnya", tint = Ink, modifier = Modifier.size(26.dp))
            }
            Button(
                onClick = onToggle,
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color(0xFF14180F))
            ) {
                Icon(
                    if (isPlaying) SuaraIcons.Pause else SuaraIcons.Play,
                    contentDescription = if (isPlaying) "Jeda" else "Putar",
                    tint = Color(0xFF14180F),
                    modifier = Modifier.size(30.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(SuaraIcons.Next, contentDescription = "Berikutnya", tint = Ink, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onRepeat) {
                Icon(
                    if (repeatMode == Player.REPEAT_MODE_ONE) SuaraIcons.RepeatOne else SuaraIcons.Repeat,
                    contentDescription = "Ulangi",
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) Coral else Muted,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onFavorite) {
                Icon(
                    if (isFavorite) SuaraIcons.Favorite else SuaraIcons.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = if (isFavorite) Coral else Muted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isFavorite) "TERFAVORIT" else "FAVORIT", fontFamily = Plex, fontSize = 10.sp, color = if (isFavorite) Coral else Muted)
            }
            TextButton(onClick = onQueue) {
                Icon(SuaraIcons.Queue, contentDescription = "Antrean", tint = Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ANTREAN", fontFamily = Plex, fontSize = 10.sp, color = Muted)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AudioScreen(
    appVolume: Float,
    onVolume: (Float) -> Unit,
    eqState: EqState,
    onEqEnabled: (Boolean) -> Unit,
    onPreset: (String) -> Unit,
    onBand: (Int, Int) -> Unit,
    onPreamp: (Int) -> Unit,
    mixMode: Boolean,
    onMixMode: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(SuaraIcons.Back, contentDescription = "Kembali", tint = Ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("AUDIO", fontFamily = Plex, fontSize = 9.sp, color = Muted, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) {
                Text("SELESAI", fontFamily = Plex, fontSize = 9.sp, color = Coral)
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Equalizer.",
                fontFamily = Fraunces,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                when (eqState.engine) {
                    "DYNAMICS" -> "10 BAND · DYNAMICS PROCESSING"
                    "EQUALIZER" -> "${eqState.bands.size} BAND · FALLBACK EQUALIZER"
                    else -> "MENUNGGU AUDIO"
                },
                fontFamily = Plex,
                fontSize = 9.sp,
                color = Muted,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(18.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("VOLUME APLIKASI", fontFamily = Plex, fontSize = 8.sp, color = Muted, letterSpacing = 1.2.sp)
                        Text("${(appVolume * 100).roundToInt()}%", fontFamily = Plex, fontSize = 10.sp, color = Ink)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (appVolume <= 0.01f) SuaraIcons.VolumeOff else SuaraIcons.VolumeUp,
                            contentDescription = "Volume aplikasi",
                            tint = Muted,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = appVolume,
                            onValueChange = onVolume,
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = Coral,
                                activeTrackColor = Coral,
                                inactiveTrackColor = Line
                            )
                        )
                        Text(
                            formatDb((appVolume - 1f).times(20f).roundToInt() * 100),
                            fontFamily = Plex,
                            fontSize = 9.sp,
                            color = Muted,
                            modifier = Modifier.width(58.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioSettings.presets.forEach { preset ->
                    FilterChip(
                        selected = eqState.preset == preset,
                        onClick = { onPreset(preset) },
                        label = { Text(preset, fontFamily = Plex, fontSize = 9.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Coral,
                            selectedLabelColor = Color(0xFF14180F),
                            labelColor = Muted,
                            containerColor = Surface
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            if (!eqState.available || eqState.bands.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Equalizer belum aktif.", fontFamily = Fraunces, fontSize = 18.sp, color = Ink)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Putar satu lagu dulu supaya audio session ExoPlayer aktif. Kalau tetap kosong, perangkat ini tidak mendukung equalizer.",
                            fontFamily = DmSans, fontSize = 12.sp, color = Muted
                        )
                    }
                }
            } else {
                EqCurveCard(eqState = eqState, onBand = onBand)
                Spacer(Modifier.height(12.dp))
                EqFadersCard(eqState = eqState, onBand = onBand)
                if (eqState.preampMaxMb > eqState.preampMinMb) {
                    Spacer(Modifier.height(12.dp))
                    PreampCard(eqState = eqState, onPreamp = onPreamp)
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("MODE CAMPUR AUDIO", fontFamily = Plex, fontSize = 8.sp, color = Ink, letterSpacing = 1.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Musik tetap berbunyi saat aplikasi lain memutar audio. Notifikasi bisa tumpang tindih.",
                            fontFamily = DmSans, fontSize = 11.sp, color = Muted
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = mixMode, onCheckedChange = onMixMode)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    onPreset("Datar")
                    if (eqState.preampMaxMb > eqState.preampMinMb) onPreamp(0)
                }) {
                    Text("RESET KE DATAR", fontFamily = Plex, fontSize = 9.sp, color = Muted)
                }
                Text("AUTO-SIMPAN", fontFamily = Plex, fontSize = 8.sp, color = Mint, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EqCurveCard(eqState: EqState, onBand: (Int, Int) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("RESPONS FREKUENSI", fontFamily = Plex, fontSize = 8.sp, color = Muted, letterSpacing = 1.sp)
                Text("${eqState.bands.size} BAND", fontFamily = Plex, fontSize = 8.sp, color = Mint, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(6.dp))
            EqCurve(eqState = eqState, onBand = onBand)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                eqState.bands.forEach { band ->
                    Text(formatHz(band.freqHz), fontFamily = Plex, fontSize = 7.sp, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun EqCurve(eqState: EqState, onBand: (Int, Int) -> Unit) {
    val bands = eqState.bands
    if (bands.isEmpty()) return
    val minMb = bands.minOf { it.minMb }.toFloat()
    val maxMb = bands.maxOf { it.maxMb }.toFloat()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(bands) {
                fun update(position: Offset) {
                    val n = bands.size
                    if (n == 0) return
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val top = 8.dp.toPx()
                    val bottom = height - 8.dp.toPx()
                    if (width <= 0f || bottom <= top) return
                    val index = ((position.x / width) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                    val ratio = ((position.y - top) / (bottom - top)).coerceIn(0f, 1f)
                    val level = (maxMb - ratio * (maxMb - minMb)).roundToInt()
                    onBand(index, level.coerceIn(minMb.toInt(), maxMb.toInt()))
                }
                detectDragGestures(
                    onDragStart = { update(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        update(change.position)
                    }
                )
            }
    ) {
        val n = bands.size
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val width = size.width
        fun x(index: Int): Float =
            if (n <= 1) width / 2f else index * width / (n - 1f)
        fun y(level: Int): Float =
            bottom - ((level - minMb) / (maxMb - minMb)) * (bottom - top)

        for (g in 0..4) {
            val gy = top + (bottom - top) * g / 4f
            drawLine(
                color = Color(0xFF2A312A),
                start = Offset(0f, gy),
                end = Offset(width, gy),
                strokeWidth = 1.dp.toPx()
            )
        }
        bands.forEachIndexed { index, _ ->
            drawLine(
                color = Color(0xFF252C25),
                start = Offset(x(index), top),
                end = Offset(x(index), bottom),
                strokeWidth = 1.dp.toPx()
            )
        }
        drawLine(
            color = Color(0xFF4B554A),
            start = Offset(0f, y(0)),
            end = Offset(width, y(0)),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f))
        )

        val fill = Path()
        val line = Path()
        fill.moveTo(x(0), y(bands[0].levelMb))
        line.moveTo(x(0), y(bands[0].levelMb))
        for (i in 1 until n) {
            val prevX = x(i - 1)
            val prevY = y(bands[i - 1].levelMb)
            val nextX = x(i)
            val nextY = y(bands[i].levelMb)
            val mid = (prevX + nextX) / 2f
            fill.cubicTo(mid, prevY, mid, nextY, nextX, nextY)
            line.cubicTo(mid, prevY, mid, nextY, nextX, nextY)
        }
        fill.lineTo(x(n - 1), y(0))
        fill.lineTo(x(0), y(0))
        fill.close()
        drawPath(fill, color = Coral.copy(alpha = 0.16f))
        drawPath(
            line,
            color = Coral,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        )
        bands.forEachIndexed { index, band ->
            val center = Offset(x(index), y(band.levelMb))
            drawCircle(Color(0xFF0B0D0B), radius = 5.2.dp.toPx(), center = center)
            drawCircle(Amber, radius = 3.3.dp.toPx(), center = center)
        }
    }
}

@Composable
private fun EqFadersCard(eqState: EqState, onBand: (Int, Int) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("KONTROL BAND", fontFamily = Plex, fontSize = 8.sp, color = Muted, letterSpacing = 1.sp)
                Text("SKALA ±12 dB", fontFamily = Plex, fontSize = 8.sp, color = Amber, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                eqState.bands.forEach { band ->
                    EqFader(band = band, onBand = onBand)
                }
            }
        }
    }
}

@Composable
private fun EqFader(band: EqBand, onBand: (Int, Int) -> Unit) {
    val minMb = band.minMb.toFloat()
    val maxMb = band.maxMb.toFloat()
    Column(
        Modifier.width(46.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            formatDb(band.levelMb),
            fontFamily = Plex,
            fontSize = 8.sp,
            color = Amber,
            maxLines = 1
        )
        Box(
            Modifier
                .width(30.dp)
                .height(140.dp)
                .pointerInput(band.index, minMb, maxMb) {
                    fun update(y: Float) {
                        val height = size.height.toFloat()
                        if (height <= 0f) return
                        val ratio = (y / height).coerceIn(0f, 1f)
                        val level = (maxMb - ratio * (maxMb - minMb)).roundToInt()
                        onBand(band.index, level.coerceIn(minMb.toInt(), maxMb.toInt()))
                    }
                    detectDragGestures(
                        onDragStart = { update(it.y) },
                        onDrag = { change, _ ->
                            change.consume()
                            update(change.position.y)
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                drawLine(
                    color = Color(0xFF2A312A),
                    start = Offset(cx, 4.dp.toPx()),
                    end = Offset(cx, size.height - 4.dp.toPx()),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF4B554A),
                    start = Offset(cx - 7.dp.toPx(), size.height / 2f),
                    end = Offset(cx + 7.dp.toPx(), size.height / 2f),
                    strokeWidth = 1.dp.toPx()
                )
                val ratio = if (maxMb > minMb) (maxMb - band.levelMb) / (maxMb - minMb) else 0.5f
                val thumbY = (ratio * (size.height - 8.dp.toPx())) + 4.dp.toPx()
                drawLine(
                    color = Coral,
                    start = Offset(cx - 10.dp.toPx(), thumbY),
                    end = Offset(cx + 10.dp.toPx(), thumbY),
                    strokeWidth = 7.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Text(formatHz(band.freqHz), fontFamily = Plex, fontSize = 7.sp, color = Muted)
    }
}

@Composable
private fun PreampCard(eqState: EqState, onPreamp: (Int) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PREAMP", fontFamily = Plex, fontSize = 8.sp, color = Muted, letterSpacing = 1.sp)
                Text(formatDb(eqState.preampMb), fontFamily = Plex, fontSize = 10.sp, color = Amber)
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = eqState.preampMb.toFloat(),
                onValueChange = { onPreamp(it.roundToInt()) },
                valueRange = eqState.preampMinMb.toFloat()..eqState.preampMaxMb.toFloat(),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Amber,
                    activeTrackColor = Amber,
                    inactiveTrackColor = Line
                )
            )
            Text(
                "Turunkan preamp kalau boost bikin suara pecah.",
                fontFamily = DmSans,
                fontSize = 11.sp,
                color = Muted
            )
        }
    }
}

@Composable
private fun QueueSheet(
    queue: List<TrackEntity>,
    currentId: Long?,
    onPlay: (TrackEntity) -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text("Antrean", fontFamily = Fraunces, fontSize = 20.sp, color = Ink)
        Spacer(Modifier.height(10.dp))
        if (queue.isEmpty()) {
            Text("Antrean kosong.", fontFamily = DmSans, fontSize = 12.sp, color = Muted)
        } else {
            LazyColumn {
                itemsIndexed(queue) { index, track ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPlay(track) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                fontFamily = DmSans,
                                fontSize = 13.sp,
                                color = if (track.id == currentId) Coral else Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(track.artist ?: "", fontFamily = Plex, fontSize = 9.sp, color = Muted)
                        }
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(SuaraIcons.Close, contentDescription = "Hapus dari antrean", tint = Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSheet(
    playlists: List<PlaylistSummary>,
    tracksFor: (Long) -> Flow<List<TrackEntity>>,
    onPlayAll: (List<TrackEntity>) -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onRemoveTrack: (Long, TrackEntity) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var selected by remember { mutableStateOf<PlaylistSummary?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        val current = selected
        if (current == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Playlist", fontFamily = Fraunces, fontSize = 20.sp, color = Ink)
                TextButton(onClick = { showCreate = true }) {
                    Text("BUAT", fontFamily = Plex, fontSize = 10.sp, color = Coral)
                }
            }
            if (playlists.isEmpty()) {
                Text(
                    "Belum ada playlist. Buat satu dari menu lagu atau tombol BUAT.",
                    fontFamily = DmSans, fontSize = 12.sp, color = Muted,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { selected = playlist }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(playlist.name, fontFamily = DmSans, fontSize = 14.sp, color = Ink)
                                Text("${playlist.trackCount} lagu", fontFamily = Plex, fontSize = 10.sp, color = Muted)
                            }
                            Text("BUKA", fontFamily = Plex, fontSize = 10.sp, color = Coral)
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { selected = null }) {
                    Text("KEMBALI", fontFamily = Plex, fontSize = 10.sp, color = Muted)
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("HAPUS", fontFamily = Plex, fontSize = 10.sp, color = Coral)
                }
            }
            Text(current.name, fontFamily = Fraunces, fontSize = 22.sp, color = Ink)
            Spacer(Modifier.height(10.dp))
            val detail by tracksFor(current.id).collectAsState(initial = emptyList())
            Button(
                onClick = { onPlayAll(detail) },
                enabled = detail.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color(0xFF14180F))
            ) { Text("PUTAR SEMUA", fontFamily = Plex, fontSize = 11.sp) }
            Spacer(Modifier.height(10.dp))
            if (detail.isEmpty()) {
                Text("Playlist masih kosong.", fontFamily = DmSans, fontSize = 12.sp, color = Muted)
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(detail) { _, track ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onPlayTrack(track, detail) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    fontFamily = DmSans, fontSize = 13.sp, color = Ink,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(track.artist ?: "", fontFamily = Plex, fontSize = 9.sp, color = Muted)
                            }
                            TextButton(onClick = { onRemoveTrack(current.id, track) }) {
                                Text("HAPUS", fontFamily = Plex, fontSize = 9.sp, color = Muted)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name -> onCreate(name); showCreate = false }
        )
    }
    if (confirmDelete && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus playlist?") },
            text = { Text("Lagu tidak dihapus, hanya playlist \"${selected!!.name}\" yang dibuang.") },
            confirmButton = {
                TextButton(onClick = { onDelete(selected!!.id); confirmDelete = false; selected = null }) {
                    Text("HAPUS", color = Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("BATAL") }
            }
        )
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlist baru", fontFamily = Fraunces) },
        text = {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 14.sp, fontFamily = DmSans),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth()
                            .border(1.dp, Line, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        if (name.isBlank()) Text("Nama playlist", color = Muted, fontSize = 13.sp)
                        inner()
                    }
                }
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) }
            ) { Text("BUAT") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("BATAL") } }
    )
}

@Composable
private fun AddToPlaylistDialog(
    track: TrackEntity,
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onAdd: (Long) -> Unit,
    onCreate: (String) -> Unit
) {
    var create by remember { mutableStateOf(false) }
    if (create) {
        CreatePlaylistDialog(
            onDismiss = onDismiss,
            onConfirm = { name -> onCreate(name); onDismiss() }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambahkan ke playlist", fontFamily = Fraunces) },
        text = {
            Column {
                if (playlists.isEmpty()) {
                    Text("Belum ada playlist. Buat baru dulu.", fontFamily = DmSans, fontSize = 12.sp, color = Muted)
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            TextButton(
                                onClick = { onAdd(playlist.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name, fontFamily = DmSans, fontSize = 13.sp, color = Ink)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { create = true }) { Text("BUAT BARU") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("BATAL") } }
    )
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

private fun hasAudioPermission(context: android.content.Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun requiredPermissions(): Array<String> = buildList {
    add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatHz(hz: Int): String =
    if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "$hz Hz"

private fun formatDb(mb: Int): String {
    val db = mb / 100f
    val prefix = if (db > 0f) "+" else ""
    return "$prefix%.1f dB".format(db)
}

private fun initials(title: String): String =
    title.split(' ', '-', '_')
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "S" }
