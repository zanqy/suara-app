package com.za.sidqi.suara.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.media3.common.Player
import com.za.sidqi.suara.R
import com.za.sidqi.suara.playback.PlaybackService
import com.za.sidqi.suara.ui.MainActivity

object WidgetRenderer {
    private val barIds = intArrayOf(
        R.id.widget_bar_0, R.id.widget_bar_1, R.id.widget_bar_2, R.id.widget_bar_3,
        R.id.widget_bar_4, R.id.widget_bar_5, R.id.widget_bar_6, R.id.widget_bar_7,
        R.id.widget_bar_8, R.id.widget_bar_9, R.id.widget_bar_10, R.id.widget_bar_11,
        R.id.widget_bar_12, R.id.widget_bar_13, R.id.widget_bar_14, R.id.widget_bar_15,
        R.id.widget_bar_16, R.id.widget_bar_17, R.id.widget_bar_18, R.id.widget_bar_19,
        R.id.widget_bar_20, R.id.widget_bar_21, R.id.widget_bar_22, R.id.widget_bar_23,
        R.id.widget_bar_24, R.id.widget_bar_25, R.id.widget_bar_26, R.id.widget_bar_27
    )

    private val staticHeights = intArrayOf(
        8, 12, 20, 14, 25, 17, 29, 22, 12, 24, 16, 27, 23, 15,
        26, 18, 11, 24, 16, 9, 21, 13, 25, 17, 10, 20, 14, 28
    )

    fun updateAll(context: Context, animated: Boolean = false) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, SuaraWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return
        val views = build(context, animated)
        for (id in ids) manager.updateAppWidget(id, views)
    }

    fun build(context: Context, animated: Boolean = false): RemoteViews {
        val state = WidgetState.read(context)
        val views = RemoteViews(context.packageName, R.layout.widget_suara)

        views.setTextViewText(R.id.widget_kicker, "SPECTRUM · ${state.preset.uppercase()}")
        views.setTextViewText(R.id.widget_title, state.title)
        views.setTextViewText(R.id.widget_artist, state.artist)
        views.setTextViewText(
            R.id.widget_time,
            if (state.durationMs > 0) {
                "${WidgetState.formatDuration(state.positionMs)} / ${WidgetState.formatDuration(state.durationMs)}"
            } else {
                ""
            }
        )
        views.setImageViewResource(
            R.id.widget_play,
            if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        views.setImageViewResource(
            R.id.widget_repeat,
            when (state.repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.ic_widget_repeat_one
                Player.REPEAT_MODE_ALL -> R.drawable.ic_widget_repeat_all
                else -> R.drawable.ic_widget_repeat
            }
        )
        views.setImageViewResource(
            R.id.widget_favorite,
            if (state.favorite) R.drawable.ic_widget_favorite else R.drawable.ic_widget_favorite_border
        )

        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, openAudio = false))
        views.setOnClickPendingIntent(R.id.widget_title, openApp(context, openAudio = false))
        views.setOnClickPendingIntent(R.id.widget_artist, openApp(context, openAudio = false))
        views.setOnClickPendingIntent(R.id.widget_prev, service(context, PlaybackService.ACTION_WIDGET_PREV))
        views.setOnClickPendingIntent(R.id.widget_play, service(context, PlaybackService.ACTION_WIDGET_TOGGLE))
        views.setOnClickPendingIntent(R.id.widget_next, service(context, PlaybackService.ACTION_WIDGET_NEXT))
        views.setOnClickPendingIntent(R.id.widget_repeat, service(context, PlaybackService.ACTION_WIDGET_REPEAT))
        views.setOnClickPendingIntent(R.id.widget_favorite, service(context, PlaybackService.ACTION_WIDGET_FAVORITE))
        views.setOnClickPendingIntent(R.id.widget_eq, openApp(context, openAudio = true))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val heights = if (state.heights.any { it > 0 }) state.heights else staticHeights
            applyBarHeights(views, heights)
        }
        return views
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyBarHeights(views: RemoteViews, heights: IntArray) {
        barIds.forEachIndexed { index, id ->
            views.setViewLayoutHeight(
                id,
                heights.getOrElse(index) { 8 }.toFloat(),
                TypedValue.COMPLEX_UNIT_DIP
            )
        }
    }

    private fun service(context: Context, action: String): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openApp(context: Context, openAudio: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openAudio) putExtra(MainActivity.EXTRA_OPEN_AUDIO, true)
        }
        return PendingIntent.getActivity(
            context,
            if (openAudio) 2 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
