package com.za.sidqi.suara.playback

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(
    val index: Int,
    val freqHz: Int,
    val levelMb: Int,
    val minMb: Int,
    val maxMb: Int
)

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val preset: String = "Datar",
    val bands: List<EqBand> = emptyList()
)

object AudioState {
    private val _eq = MutableStateFlow(EqState())
    val eq: StateFlow<EqState> = _eq.asStateFlow()

    fun publish(state: EqState) {
        _eq.value = state
    }
}

object AudioSettings {
    private const val PREFS = "suara_audio"
    private const val KEY_VOLUME = "app_volume"
    private const val KEY_EQ_ENABLED = "eq_enabled"
    private const val KEY_EQ_PRESET = "eq_preset"
    private const val KEY_EQ_BANDS = "eq_bands"
    private const val KEY_MIX = "mix_mode"

    const val ACTION_EQ_ENABLED = "com.za.sidqi.suara.EQ_ENABLED"
    const val ACTION_EQ_PRESET = "com.za.sidqi.suara.EQ_PRESET"
    const val ACTION_EQ_BAND = "com.za.sidqi.suara.EQ_BAND"
    const val ACTION_EQ_REFRESH = "com.za.sidqi.suara.EQ_REFRESH"
    const val ACTION_MIX_MODE = "com.za.sidqi.suara.MIX_MODE"

    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_PRESET = "preset"
    const val EXTRA_BAND = "band"
    const val EXTRA_LEVEL = "level"

    val presets = listOf("Datar", "Bass", "Vokal", "Treble", "Kustom")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun appVolume(context: Context): Float =
        prefs(context).getFloat(KEY_VOLUME, 1f).coerceIn(0f, 1f)

    fun setAppVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_VOLUME, value.coerceIn(0f, 1f)).apply()
    }

    fun eqEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_EQ_ENABLED, false)

    fun setEqEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
    }

    fun eqPreset(context: Context): String = prefs(context).getString(KEY_EQ_PRESET, "Datar") ?: "Datar"

    fun setEqPreset(context: Context, preset: String) {
        prefs(context).edit().putString(KEY_EQ_PRESET, preset).apply()
    }

    fun eqBands(context: Context): IntArray? =
        prefs(context).getString(KEY_EQ_BANDS, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toIntArray()

    fun setEqBands(context: Context, levels: IntArray) {
        prefs(context).edit().putString(KEY_EQ_BANDS, levels.joinToString(",")).apply()
    }

    fun mixMode(context: Context): Boolean = prefs(context).getBoolean(KEY_MIX, false)

    fun setMixMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MIX, enabled).apply()
    }

    fun send(context: Context, action: String, configure: Intent.() -> Unit = {}) {
        val intent = Intent(action).setPackage(context.packageName).apply(configure)
        context.sendBroadcast(intent)
    }
}
