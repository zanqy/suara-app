package com.za.sidqi.suara.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["uri"], unique = true), Index("source")]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val folder: String,
    val source: String,
    val artworkUri: String?,
    val lastSeenAt: Long,
    val isMissing: Boolean = false
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: Long,
    val createdAt: Long
)

@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey val position: Int,
    val trackId: Long,
    val addedAt: Long
)

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 1,
    val lastTrackId: Long? = null,
    val lastPositionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0
)

@Dao
interface SuaraDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun allTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun trackById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun trackByUri(uri: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET isMissing = 1 WHERE lastSeenAt < :before")
    suspend fun markMissingBefore(before: Long)

    @Query("UPDATE tracks SET isMissing = :missing WHERE id = :id")
    suspend fun setMissing(id: Long, missing: Boolean)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: Long)

    @Query("SELECT trackId FROM favorites")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: Long)

    @Query("SELECT t.* FROM tracks t INNER JOIN favorites f ON t.id = f.trackId ORDER BY f.createdAt DESC")
    fun observeFavorites(): Flow<List<TrackEntity>>

    @Query("SELECT t.* FROM tracks t INNER JOIN queue q ON t.id = q.trackId ORDER BY q.position ASC")
    fun observeQueue(): Flow<List<TrackEntity>>

    @Query("DELETE FROM queue")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(items: List<QueueEntity>)

    @Query("SELECT * FROM playback_state WHERE id = 1")
    suspend fun playbackState(): PlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackState(state: PlaybackStateEntity)
}

@Database(
    entities = [TrackEntity::class, FavoriteEntity::class, QueueEntity::class, PlaybackStateEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SuaraDatabase : RoomDatabase() {
    abstract fun dao(): SuaraDao

    companion object {
        @Volatile
        private var instance: SuaraDatabase? = null

        fun get(context: Context): SuaraDatabase = instance ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, SuaraDatabase::class.java, "suara.db")
                .build()
                .also { instance = it }
        }
    }
}
