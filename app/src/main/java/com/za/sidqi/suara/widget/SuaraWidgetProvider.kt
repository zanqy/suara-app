package com.za.sidqi.suara.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.za.sidqi.suara.ui.MainActivity
import com.za.sidqi.suara.R
import com.za.sidqi.suara.playback.PlaybackService

class SuaraWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = buildViews(context)
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetState.ACTION_STATE) {
            updateAll(context)
        }
    }

    private fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, SuaraWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return
        val views = buildViews(context)
        for (id in ids) manager.updateAppWidget(id, views)
    }

    private fun buildViews(context: Context): RemoteViews {
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

        return views
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
