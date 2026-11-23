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

data class EqPresetSpec(
    val name: String,
    val curve: IntArray,
    val preampMb: Int
)

data class EqState(
    val available: Boolean = false,
    val engine: String = "",
    val bandMode: String = "AUTO",
    val enabled: Boolean = false,
    val preset: String = "Datar",
    val bands: List<EqBand> = emptyList(),
    val preampMb: Int = 0,
    val preampMinMb: Int = 0,
    val preampMaxMb: Int = 0
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
    private const val KEY_EQ_BANDS_10 = "eq_bands_10"
    private const val KEY_EQ_BANDS_5 = "eq_bands_5"
    private const val KEY_EQ_PREAMP = "eq_preamp"
    private const val KEY_EQ_MODE = "eq_mode"
    private const val KEY_MIX = "mix_mode"
    private const val KEY_VISUALIZER = "visualizer"

    const val ACTION_EQ_ENABLED = "com.za.sidqi.suara.EQ_ENABLED"
    const val ACTION_EQ_PRESET = "com.za.sidqi.suara.EQ_PRESET"
    const val ACTION_EQ_BAND = "com.za.sidqi.suara.EQ_BAND"
    const val ACTION_EQ_MODE = "com.za.sidqi.suara.EQ_MODE"
    const val ACTION_EQ_PREAMP = "com.za.sidqi.suara.EQ_PREAMP"
    const val ACTION_EQ_REFRESH = "com.za.sidqi.suara.EQ_REFRESH"
    const val ACTION_MIX_MODE = "com.za.sidqi.suara.MIX_MODE"

    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_PRESET = "preset"
    const val EXTRA_BAND = "band"
    const val EXTRA_LEVEL = "level"
    const val EXTRA_MODE = "mode"

    val bandModes = listOf("AUTO", "10", "5")

    val presetSpecs = listOf(
        EqPresetSpec("Datar", IntArray(10), 0),
        EqPresetSpec(
            "Sub Bass",
            intArrayOf(900, 800, 500, 100, -100, -200, -100, 0, 0, 0),
            -500
        ),
        EqPresetSpec(
            "Bass",
            intArrayOf(600, 500, 400, 200, 0, -100, -200, -300, -400, -500),
            -400
        ),
        EqPresetSpec(
            "V-Shape",
            intArrayOf(600, 500, 300, 0, -200, -300, -200, 100, 400, 500),
            -400
        ),
        EqPresetSpec(
            "Vokal",
            intArrayOf(-200, -100, 0, 200, 400, 500, 400, 200, 0, -100),
            -200
        ),
        EqPresetSpec(
            "Podcast",
            intArrayOf(-600, -500, -300, 0, 300, 500, 500, 300, 0, -200),
            -300
        ),
        EqPresetSpec(
            "Rock",
            intArrayOf(500, 400, 200, -100, -200, 0, 200, 400, 500, 400),
            -300
        ),
        EqPresetSpec(
            "Pop",
            intArrayOf(-100, 0, 200, 400, 500, 300, 0, -100, -200, -300),
            -200
        ),
        EqPresetSpec(
            "Jazz",
            intArrayOf(300, 200, 100, 200, -100, -200, 0, 200, 300, 200),
            -200
        ),
        EqPresetSpec(
            "Elektronik",
            intArrayOf(700, 600, 300, 0, -100, 200, 300, 200, 400, 500),
            -400
        ),
        EqPresetSpec(
            "Akustik",
            intArrayOf(200, 100, 0, 200, 300, 200, 100, 200, 300, 200),
            -200
        ),
        EqPresetSpec(
            "Air",
            intArrayOf(-300, -200, -100, 0, 100, 200, 300, 500, 700, 800),
            -300
        ),
        EqPresetSpec("Kustom", IntArray(10), 0)
    )

    val presets = presetSpecs.map { it.name }

    fun presetSpec(name: String): EqPresetSpec =
        presetSpecs.firstOrNull { it.name == name } ?: presetSpecs.first()

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

    fun eqPreamp(context: Context): Int =
        prefs(context).getInt(KEY_EQ_PREAMP, 0).coerceIn(-1200, 600)

    fun setEqPreamp(context: Context, valueMb: Int) {
        prefs(context).edit().putInt(KEY_EQ_PREAMP, valueMb.coerceIn(-1200, 600)).apply()
    }

    fun eqMode(context: Context): String =
        prefs(context).getString(KEY_EQ_MODE, "AUTO") ?: "AUTO"

    fun setEqMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_EQ_MODE, mode).apply()
    }

    fun eqBands(context: Context, count: Int = 10): IntArray? {
        val key = if (count >= 10) KEY_EQ_BANDS_10 else KEY_EQ_BANDS_5
        val raw = prefs(context).getString(key, null)
            ?: prefs(context).getString(KEY_EQ_BANDS, null)
        return raw
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toIntArray()
    }

    fun setEqBands(context: Context, levels: IntArray) {
        val key = if (levels.size >= 10) KEY_EQ_BANDS_10 else KEY_EQ_BANDS_5
        prefs(context).edit().putString(key, levels.joinToString(",")).apply()
    }

    fun visualizerEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VISUALIZER, true)

    fun setVisualizerEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VISUALIZER, enabled).apply()
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
