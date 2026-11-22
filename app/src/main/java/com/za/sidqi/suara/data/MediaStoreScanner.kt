package com.za.sidqi.suara.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreScanner(
    private val context: Context,
    private val dao: SuaraDao
) {
    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Audio.Media.RELATIVE_PATH)
        } else {
            projection.add(MediaStore.Audio.Media.DATA)
        }

        val scanStart = System.currentTimeMillis()
        val existingByUri = dao.allTracks().associateBy { it.uri }
        val byKey = LinkedHashMap<String, TrackEntity>()

        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection.toTypedArray(),
            null,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val pathCol = cursor.getColumnIndex(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Audio.Media.RELATIVE_PATH
                else MediaStore.Audio.Media.DATA
            )

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idCol)
                val duration = cursor.getLong(durationCol)
                val size = cursor.getLong(sizeCol)
                if (duration < 15_000L || size <= 0L) continue

                val path = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                if (path.contains("Ringtones", true) ||
                    path.contains("Notifications", true) ||
                    path.contains("Alarms", true)
                ) continue

                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                    ?: path.substringAfterLast('/').substringBeforeLast('.')
                val artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                val album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                val albumId = cursor.getLong(albumIdCol)

                val source = when {
                    path.startsWith("Music/") -> "MUSIC"
                    path.startsWith("Download/") -> "DOWNLOAD"
                    else -> "OTHER"
                }
                val key = "${title.lowercase()}|${duration / 1000}|$size"
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaId
                ).toString()
                val artwork = if (albumId > 0) {
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()
                } else null

                val item = TrackEntity(
                    id = existingByUri[uri]?.id ?: 0,
                    mediaId = mediaId,
                    uri = uri,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = duration,
                    sizeBytes = size,
                    folder = path.trimEnd('/'),
                    source = source,
                    artworkUri = artwork,
                    lastSeenAt = scanStart,
                    isMissing = false
                )
                val current = byKey[key]
                if (current == null || (current.source != "MUSIC" && source == "MUSIC")) {
                    byKey[key] = item
                }
            }
        }

        val tracks = byKey.values.toList()
        dao.upsertTracks(tracks)
        dao.markMissingBefore(scanStart)
        tracks.size
    }
}
