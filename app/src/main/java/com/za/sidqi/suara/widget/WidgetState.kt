package com.za.sidqi.suara.widget

import android.content.Context

data class WidgetSnapshot(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val positionMs: Long,
    val isPlaying: Boolean,
    val repeatMode: Int,
    val trackId: Long,
    val favorite: Boolean,
    val preset: String,
    val heights: IntArray = IntArray(28),
    val live: Boolean = false
)

object WidgetState {
    const val ACTION_STATE = "com.za.sidqi.suara.WIDGET_STATE"

    private const val PREFS = "suara_widget"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_DURATION = "duration"
    private const val KEY_POSITION = "position"
    private const val KEY_PLAYING = "playing"
    private const val KEY_REPEAT = "repeat"
    private const val KEY_TRACK_ID = "track_id"
    private const val KEY_FAVORITE = "favorite"
    private const val KEY_PRESET = "preset"
    private const val KEY_HEIGHTS = "heights"
    private const val KEY_LIVE = "live"

    fun read(context: Context): WidgetSnapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WidgetSnapshot(
            title = prefs.getString(KEY_TITLE, "Belum ada lagu") ?: "Belum ada lagu",
            artist = prefs.getString(KEY_ARTIST, "Tap untuk membuka Suara") ?: "Tap untuk membuka Suara",
            durationMs = prefs.getLong(KEY_DURATION, 0L),
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            isPlaying = prefs.getBoolean(KEY_PLAYING, false),
            repeatMode = prefs.getInt(KEY_REPEAT, 0),
            trackId = prefs.getLong(KEY_TRACK_ID, -1L),
            favorite = prefs.getBoolean(KEY_FAVORITE, false),
            preset = prefs.getString(KEY_PRESET, "Datar") ?: "Datar",
            heights = parseHeights(prefs.getString(KEY_HEIGHTS, null)),
            live = prefs.getBoolean(KEY_LIVE, false)
        )
    }

    fun write(context: Context, snapshot: WidgetSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_ARTIST, snapshot.artist)
            .putLong(KEY_DURATION, snapshot.durationMs)
            .putLong(KEY_POSITION, snapshot.positionMs)
            .putBoolean(KEY_PLAYING, snapshot.isPlaying)
            .putInt(KEY_REPEAT, snapshot.repeatMode)
            .putLong(KEY_TRACK_ID, snapshot.trackId)
            .putBoolean(KEY_FAVORITE, snapshot.favorite)
            .putString(KEY_PRESET, snapshot.preset)
            .putString(KEY_HEIGHTS, snapshot.heights.joinToString(","))
            .putBoolean(KEY_LIVE, snapshot.live)
            .apply()
    }

    fun setHeights(context: Context, heights: IntArray, live: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_HEIGHTS, heights.joinToString(","))
            .putBoolean(KEY_LIVE, live)
            .apply()
    }

    private fun parseHeights(raw: String?): IntArray {
        if (raw.isNullOrBlank()) return IntArray(28)
        val values = raw.split(',').mapNotNull { it.toIntOrNull() }
        return if (values.size == 28) values.toIntArray() else IntArray(28)
    }

    fun setFavorite(context: Context, favorite: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FAVORITE, favorite)
            .apply()
    }

    fun formatDuration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val minutes = total / 60
        val seconds = total % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
