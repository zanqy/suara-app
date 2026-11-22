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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.za.sidqi.suara.R
import com.za.sidqi.suara.data.PlaylistSummary
import com.za.sidqi.suara.data.TrackEntity
import com.za.sidqi.suara.playback.PlayerHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private val Bg = Color(0xFF0B0D0B)
private val Surface = Color(0xFF171B17)
private val Ink = Color(0xFFF3F0E7)
private val Muted = Color(0xFFB7BFB4)
private val Coral = Color(0xFFFF7654)
private val Amber = Color(0xFFF3C45C)
private val Mint = Color(0xFF7CE0B4)
private val Line = Color(0xFF333B33)

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
    var playlistTarget by remember { mutableStateOf<TrackEntity?>(null) }
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
        PlayerHolder.connect(context) { controller = it }
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
                TextButton(onClick = { showPlaylists = true }) {
                    Text("PLAYLIST", fontFamily = Plex, fontSize = 10.sp, color = Coral)
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
                onQueue = { showQueue = true }
            )
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
                Text("···", color = Muted, fontSize = 16.sp)
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
            Text(if (isPlaying) "JEDA" else "PUTAR", color = Ink, fontFamily = Plex, fontSize = 9.sp)
        }
        IconButton(onClick = onNext) {
            Text("LANJUT", color = Ink, fontFamily = Plex, fontSize = 9.sp)
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
    onQueue: () -> Unit
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
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onShuffle) {
                Text("ACAK", fontFamily = Plex, fontSize = 10.sp, color = if (shuffle) Coral else Muted)
            }
            TextButton(onClick = onPrev) { Text("SEBELUM", fontFamily = Plex, fontSize = 10.sp, color = Ink) }
            Button(
                onClick = onToggle,
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color(0xFF14180F))
            ) { Text(if (isPlaying) "JEDA" else "PUTAR", fontFamily = Plex, fontSize = 11.sp) }
            TextButton(onClick = onNext) { Text("LANJUT", fontFamily = Plex, fontSize = 10.sp, color = Ink) }
            TextButton(onClick = onRepeat) {
                Text(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "ULANG 1"
                        else -> "ULANG"
                    },
                    fontFamily = Plex,
                    fontSize = 10.sp,
                    color = if (repeatMode != Player.REPEAT_MODE_OFF) Coral else Muted
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onFavorite) {
                Text(if (isFavorite) "TERFAVORIT" else "FAVORIT", fontFamily = Plex, fontSize = 10.sp, color = if (isFavorite) Coral else Muted)
            }
            TextButton(onClick = onQueue) {
                Text("ANTREAN", fontFamily = Plex, fontSize = 10.sp, color = Muted)
            }
        }
        Spacer(Modifier.height(20.dp))
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
                            Text("×", color = Muted, fontSize = 18.sp)
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

private fun initials(title: String): String =
    title.split(' ', '-', '_')
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "S" }
