package com.za.sidqi.suara.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.za.sidqi.suara.data.ClipEntity
import com.za.sidqi.suara.data.FavoriteEntity
import com.za.sidqi.suara.data.MediaStoreScanner
import com.za.sidqi.suara.data.PlaybackStateEntity
import com.za.sidqi.suara.data.PlaylistEntity
import com.za.sidqi.suara.data.PlaylistSummary
import com.za.sidqi.suara.data.PlaylistTrackEntity
import com.za.sidqi.suara.data.QueueEntity
import com.za.sidqi.suara.data.SuaraDatabase
import com.za.sidqi.suara.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SourceFilter(val label: String) {
    ALL("Semua"),
    MUSIC("Music"),
    DOWNLOAD("Downloads")
}

enum class SortMode(val label: String) {
    TITLE("Judul"),
    RECENT("Terbaru"),
    DURATION("Durasi")
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = SuaraDatabase.get(app).dao()
    private val scanner = MediaStoreScanner(app, dao)

    val tracks: StateFlow<List<TrackEntity>> = dao.observeTracks().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val favoriteIds: StateFlow<List<Long>> = dao.observeFavoriteIds().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val queue: StateFlow<List<TrackEntity>> = dao.observeQueue().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val favoriteTracks: StateFlow<List<TrackEntity>> = dao.observeFavorites().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val playlists: StateFlow<List<PlaylistSummary>> = dao.observePlaylists().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(SourceFilter.ALL)
    val sort = MutableStateFlow(SortMode.TITLE)

    val visibleTracks: StateFlow<List<TrackEntity>> = combine(
        tracks, query, filter, sort
    ) { list, q, f, s ->
        val needle = q.trim().lowercase()
        list.asSequence()
            .filter { track ->
                (f == SourceFilter.ALL || track.source == f.name) &&
                    (needle.isBlank() ||
                        track.title.lowercase().contains(needle) ||
                        (track.artist ?: "").lowercase().contains(needle))
            }
            .sortedWith(
                when (s) {
                    SortMode.TITLE -> compareBy { it.title.lowercase() }
                    SortMode.RECENT -> compareByDescending { it.lastSeenAt }
                    SortMode.DURATION -> compareByDescending { it.durationMs }
                }
            )
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var scanning by mutableStateOf(false)
        private set
    var lastScanCount by mutableStateOf<Int?>(null)
        private set

    fun scan() {
        if (scanning) return
        viewModelScope.launch {
            scanning = true
            runCatching {
                withContextIo { scanner.scan() }
            }.onSuccess { lastScanCount = it }
            scanning = false
        }
    }

    fun toggleFavorite(track: TrackEntity) {
        viewModelScope.launch {
            if (favoriteIds.value.contains(track.id)) {
                dao.removeFavorite(track.id)
            } else {
                dao.addFavorite(FavoriteEntity(track.id, System.currentTimeMillis()))
            }
        }
    }

    fun createPlaylist(name: String, track: TrackEntity? = null) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val id = dao.insertPlaylist(
                PlaylistEntity(name = clean, createdAt = System.currentTimeMillis())
            )
            if (track != null) {
                dao.addPlaylistTrack(
                    PlaylistTrackEntity(
                        playlistId = id,
                        trackId = track.id,
                        position = 0,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            dao.deletePlaylistTracks(id)
            dao.deletePlaylist(id)
        }
    }

    fun addToPlaylist(playlistId: Long, track: TrackEntity) {
        viewModelScope.launch {
            dao.addPlaylistTrack(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = dao.nextPlaylistPosition(playlistId),
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeFromPlaylist(playlistId: Long, track: TrackEntity) {
        viewModelScope.launch { dao.removePlaylistTrack(playlistId, track.id) }
    }

    fun playlistTracks(id: Long): Flow<List<TrackEntity>> = dao.observePlaylistTracks(id)

    val clips: StateFlow<List<ClipEntity>> = dao.observeAllClips().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun observeClips(trackId: Long): Flow<List<ClipEntity>> = dao.observeClips(trackId)

    fun saveClip(clip: ClipEntity) {
        viewModelScope.launch { dao.upsertClip(clip) }
    }

    fun deleteClip(id: String, trackId: Long) {
        viewModelScope.launch {
            dao.deleteClip(id, trackId)
            dao.clipsForTrack(trackId).forEachIndexed { index, clip ->
                dao.setClipPosition(clip.id, trackId, index)
            }
        }
    }

    fun moveClip(clips: List<ClipEntity>, index: Int, delta: Int, trackId: Long) {
        val target = index + delta
        if (index !in clips.indices || target !in clips.indices) return
        viewModelScope.launch {
            val swapped = clips.toMutableList().also {
                val a = it[index]
                it[index] = it[target]
                it[target] = a
            }
            swapped.forEachIndexed { i, clip -> dao.setClipPosition(clip.id, trackId, i) }
        }
    }

    fun saveQueue(items: List<TrackEntity>) {
        viewModelScope.launch {
            dao.clearQueue()
            val now = System.currentTimeMillis()
            dao.insertQueue(items.mapIndexed { index, track ->
                QueueEntity(index, track.id, now + index)
            })
        }
    }

    fun removeMissing(track: TrackEntity) {
        viewModelScope.launch { dao.deleteTrack(track.id) }
    }

    suspend fun playbackState(): PlaybackStateEntity? = dao.playbackState()

    fun savePlaybackState(trackId: Long?, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        viewModelScope.launch {
            dao.savePlaybackState(
                PlaybackStateEntity(
                    id = 1,
                    lastTrackId = trackId,
                    lastPositionMs = positionMs,
                    shuffle = shuffle,
                    repeatMode = repeatMode
                )
            )
        }
    }

    private suspend fun <T> withContextIo(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }
}
