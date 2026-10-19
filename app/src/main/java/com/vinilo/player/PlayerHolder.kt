package com.vinilo.player

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vinilo.player.data.Song
import com.vinilo.player.data.toMediaItem

/**
 * Puente entre la pantalla y el servicio de reproduccion.
 * Guarda el estado que la interfaz necesita (cancion actual, si suena o no).
 */
class PlayerHolder(private val context: Context) {

    private var controller: MediaController? = null
    private var releaser: (() -> Unit)? = null

    var resumeTick by mutableIntStateOf(0)
    var currentId by mutableLongStateOf(-1L)
    var isPlaying by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")

    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        releaser = { MediaController.releaseFuture(future) }
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        this@PlayerHolder.isPlaying = isPlaying
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        sync(mediaItem)
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        sync(c.currentMediaItem)
                    }
                })
                sync(c.currentMediaItem)
                isPlaying = c.isPlaying
            } catch (e: Exception) {
                controller = null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun sync(item: MediaItem?) {
        currentId = item?.mediaId?.toLongOrNull() ?: -1L
        title = item?.mediaMetadata?.title?.toString() ?: ""
        artist = item?.mediaMetadata?.artist?.toString() ?: ""
    }

    fun playList(songs: List<Song>, index: Int) {
        val c = controller ?: return
        c.setMediaItems(songs.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun prev() {
        controller?.seekToPreviousMediaItem()
    }

    fun release() {
        releaser?.invoke()
        releaser = null
        controller = null
    }
}
