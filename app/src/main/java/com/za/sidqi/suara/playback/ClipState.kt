package com.za.sidqi.suara.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ClipRuntime(
    val active: Boolean = false,
    val trackId: Long = -1L,
    val clipId: String = "",
    val name: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val index: Int = -1,
    val total: Int = 0,
    val repeatLimit: Int = 0,
    val loopCount: Int = 0,
    val positionMs: Long = 0L
) {
    val repeatLabel: String get() = if (repeatLimit <= 0) "∞" else "$repeatLimit×"

    val progressLabel: String get() {
        val elapsed = (positionMs - startMs).coerceAtLeast(0)
        val duration = (endMs - startMs).coerceAtLeast(0)
        return "${formatClipTime(elapsed)} / ${formatClipTime(duration)}"
    }
}

fun formatClipTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000L).toInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

object ClipState {
    private val _state = MutableStateFlow(ClipRuntime())
    val state: StateFlow<ClipRuntime> = _state.asStateFlow()

    fun publish(state: ClipRuntime) {
        _state.value = state
    }

    fun clear() {
        _state.value = ClipRuntime()
    }
}

object ClipActions {
    const val ACTION_START = "com.za.sidqi.suara.CLIP_START"
    const val ACTION_NEXT = "com.za.sidqi.suara.CLIP_NEXT"
    const val ACTION_PREV = "com.za.sidqi.suara.CLIP_PREV"
    const val ACTION_STOP = "com.za.sidqi.suara.CLIP_STOP"
    const val ACTION_RELOAD = "com.za.sidqi.suara.CLIP_RELOAD"

    const val EXTRA_TRACK_ID = "clip_track_id"
    const val EXTRA_CLIP_ID = "clip_id"
}
