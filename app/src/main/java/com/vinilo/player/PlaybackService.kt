package com.vinilo.player

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vinilo.player.data.Store
import kotlin.math.abs
import kotlin.math.ln

/**
 * Ecualizador de 5 bandas (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz) sobre el
 * ecualizador del sistema. Vive aqui para que siga activo con la app cerrada.
 */
object Fx {
    private var eq: Equalizer? = null

    /** Id de sesion de audio del reproductor (lo usa el visualizador). */
    @Volatile
    var sessionId: Int = 0
        private set

    private val freqs = intArrayOf(60, 230, 910, 3600, 14000)

    fun attach(session: Int, gains: List<Int>) {
        release()
        sessionId = session
        if (session == 0) return
        try {
            val e = Equalizer(0, session)
            e.setEnabled(true)
            eq = e
            apply(gains)
        } catch (t: Throwable) {
            eq = null
        }
    }

    /** gains: 5 valores en dB (-12 a 12). Se reparten en las bandas reales del telefono. */
    fun apply(gains: List<Int>) {
        val e = eq ?: return
        try {
            val range = e.bandLevelRange
            val lo = range[0].toInt()
            val hi = range[1].toInt()
            val n = e.numberOfBands.toInt()
            for (b in 0 until n) {
                val hz = e.getCenterFreq(b.toShort()) / 1000.0
                var best = 0
                var bestD = Double.MAX_VALUE
                for (i in freqs.indices) {
                    val d = abs(ln(hz / freqs[i]))
                    if (d < bestD) {
                        bestD = d
                        best = i
                    }
                }
                val g = gains.getOrElse(best) { 0 }
                val level = (g * 100).coerceIn(lo, hi)
                e.setBandLevel(b.toShort(), level.toShort())
            }
        } catch (t: Throwable) {
            // algunos telefonos no dejan cambiar las bandas: se ignora
        }
    }

    fun release() {
        try {
            eq?.release()
        } catch (t: Throwable) {
        }
        eq = null
    }
}

/**
 * Servicio de reproduccion: mantiene la musica sonando en segundo plano
 * y muestra la notificacion con caratula y controles.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val store = Store.get(this)
        Fx.attach(player.audioSessionId, store.eq)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                Fx.attach(audioSessionId, store.eq)
            }
        })

        val builder = MediaSession.Builder(this, player)
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            builder.setSessionActivity(
                PendingIntent.getActivity(
                    this, 0, launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si esta sonando, la musica sigue aunque cierres la app desde recientes.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Fx.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
