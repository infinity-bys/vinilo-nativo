package com.vinilo.player

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vinilo.player.data.Song
import com.vinilo.player.data.Store
import com.vinilo.player.data.toMediaItem

/**
 * Puente entre la pantalla y el servicio de reproduccion.
 * Guarda lo que la interfaz necesita: cancion actual, progreso, modo,
 * velocidad, temporizador de apagado, reproducciones, ultima cancion...
 */
class PlayerHolder(private val context: Context) {

    val store: Store = Store.get(context)

    private var controller: MediaController? = null
    private var releaser: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())

    var ready by mutableStateOf(false)
        private set
    var resumeTick by mutableIntStateOf(0)
    var currentId by mutableLongStateOf(-1L)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    // Temporizador de apagado
    var sleepEndAt by mutableLongStateOf(0L)
        private set
    var sleepLeftMs by mutableLongStateOf(0L)
        private set
    var sleepEot by mutableStateOf(false)
        private set

    private var countedId = -1L
    private var lastSaveAt = 0L
    private var restored = false

    private val tick = object : Runnable {
        override fun run() {
            val c = controller
            if (c != null) {
                positionMs = c.currentPosition.coerceAtLeast(0L)
                val d = c.duration
                durationMs = if (d == C.TIME_UNSET || d < 0L) 0L else d
                if (c.isPlaying) {
                    val id = currentId
                    // Cuenta una reproduccion cuando suena 20 segundos
                    if (id != -1L && id != countedId && positionMs >= 20_000L) {
                        countedId = id
                        store.addPlay(id)
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastSaveAt > 4000L) {
                        lastSaveAt = now
                        saveLast()
                    }
                }
                if (sleepEndAt > 0L) {
                    val rem = sleepEndAt - SystemClock.elapsedRealtime()
                    if (rem <= 0L) {
                        c.pause()
                        clearSleep()
                    } else {
                        sleepLeftMs = rem
                        // Ultimos 10 segundos: el volumen baja poco a poco
                        if (rem < 10_000L) c.volume = (rem / 10_000f).coerceIn(0f, 1f)
                    }
                }
            }
            handler.postDelayed(this, 300L)
        }
    }

    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        releaser = { MediaController.releaseFuture(future) }
        future.addListener({
            try {
                val c = future.get()
                controller = c
                applyMode(store.mode)
                c.setPlaybackSpeed(store.speed)
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        this@PlayerHolder.isPlaying = isPlaying
                        if (!isPlaying) saveLast()
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        countedId = -1L
                        sync(mediaItem)
                        // "Al terminar esta cancion": se detiene cuando acaba la actual
                        if (sleepEot && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
                        ) {
                            c.pause()
                            clearSleep()
                        }
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        sync(c.currentMediaItem)
                    }
                })
                sync(c.currentMediaItem)
                isPlaying = c.isPlaying
                ready = true
                handler.removeCallbacks(tick)
                handler.post(tick)
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

    /** Reproduce la lista desde la cancion indicada. */
    fun playList(songs: List<Song>, index: Int) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        c.setMediaItems(songs.map { it.toMediaItem() }, index.coerceIn(0, songs.size - 1), 0L)
        c.prepare()
        c.play()
    }

    /** Al abrir la app, deja lista (sin sonar) la ultima cancion en el punto donde quedo. */
    fun restore(list: List<Song>) {
        val c = controller ?: return
        if (restored) return
        restored = true
        if (c.mediaItemCount > 0) return
        val id = store.lastId()
        if (id == -1L) return
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val pos = store.lastPos()
        c.setMediaItems(list.map { it.toMediaItem() }, idx, if (pos > 0L) pos else 0L)
        c.prepare()
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

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        positionMs = ms
    }

    fun stopAndClear() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
    }

    /** 0 = repetir todas, 1 = repetir una, 2 = aleatorio */
    fun applyMode(m: Int) {
        store.setMode(m)
        val c = controller ?: return
        c.shuffleModeEnabled = (m == 2)
        c.repeatMode = if (m == 1) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
    }

    fun applySpeed(v: Float) {
        store.setSpeed(v)
        controller?.setPlaybackSpeed(v)
    }

    // ---------- Temporizador de apagado ----------
    fun setSleepMinutes(m: Int) {
        clearSleep()
        sleepEndAt = SystemClock.elapsedRealtime() + m * 60_000L
        sleepLeftMs = m * 60_000L
    }

    fun setSleepEot() {
        clearSleep()
        sleepEot = true
    }

    fun clearSleep() {
        sleepEndAt = 0L
        sleepLeftMs = 0L
        sleepEot = false
        controller?.volume = 1f
    }

    fun saveLast() {
        if (currentId != -1L) store.saveLast(currentId, positionMs)
    }

    fun release() {
        handler.removeCallbacks(tick)
        releaser?.invoke()
        releaser = null
        controller = null
        ready = false
    }
}
