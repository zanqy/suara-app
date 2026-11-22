package com.za.sidqi.suara.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

object PlayerHolder {
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null

    fun connect(context: Context, onReady: (MediaController) -> Unit) {
        controller?.let {
            onReady(it)
            return
        }
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val pending = MediaController.Builder(context, token).buildAsync()
        future = pending
        pending.addListener(
            {
                runCatching {
                    controller = pending.get()
                    controller?.let(onReady)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }
}
