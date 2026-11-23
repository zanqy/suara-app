package com.za.sidqi.suara.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.za.sidqi.suara.playback.PlaybackService

class SuaraWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetRenderer.updateAll(context)
        context.sendBroadcast(
            Intent(PlaybackService.ACTION_WIDGET_REFRESH).setPackage(context.packageName)
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetState.ACTION_STATE) {
            WidgetRenderer.updateAll(context)
        }
    }
}
