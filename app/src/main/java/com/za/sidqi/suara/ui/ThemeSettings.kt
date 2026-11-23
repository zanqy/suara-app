package com.za.sidqi.suara.ui

import android.content.Context

object ThemeSettings {
    private const val PREFS = "suara_theme"
    private const val KEY_LIGHT = "light"

    fun isLight(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LIGHT, false)

    fun set(context: Context, light: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LIGHT, light)
            .apply()
    }
}
