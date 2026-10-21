package com.vinilo.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vinilo.player.data.Store
import kotlin.math.abs
import kotlin.math.ln

/**
 * Efectos de sonido de Android sobre el reproductor: ecualizador de 5 bandas
 * (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz), refuerzo de graves, sonido envolvente
 * y volumen extra. Viven aqui para seguir activos con la app cerrada.
 * Si el telefono no soporta alguno, simplemente no se usa.
 */
object Fx {
    private var eq: Equalizer? = null
    private var bassFx: BassBoost? = null
    private var virtFx: Virtualizer? = null
    private var loudFx: LoudnessEnhancer? = null

    /** Cuales efectos existen en este telefono (la pantalla solo muestra los que si). */
    @Volatile
    var bassOk: Boolean = false
        private set

    @Volatile
    var virtOk: Boolean = false
        private set

    @Volatile
    var boostOk: Boolean = false
        private set

    /** Id de sesion de audio del reproductor. */
    @Volatile
    var sessionId: Int = 0
        private set

    private val freqs = intArrayOf(60, 230, 910, 3600, 14000)

    fun attach(session: Int, store: Store) {
        release()
        sessionId = session
        if (session == 0) return
        try {
            val e = Equalizer(0, session)
            e.setEnabled(store.eqOn)
            eq = e
        } catch (t: Throwable) {
            eq = null
        }
        try {
            val b = BassBoost(0, session)
            if (b.getStrengthSupported()) {
                bassFx = b
                bassOk = true
            } else {
                b.release()
            }
        } catch (t: Throwable) {
            bassFx = null
        }
        try {
            val v = Virtualizer(0, session)
            if (v.getStrengthSupported()) {
                virtFx = v
                virtOk = true
            } else {
                v.release()
            }
        } catch (t: Throwable) {
            virtFx = null
        }
        try {
            val l = LoudnessEnhancer(session)
            loudFx = l
            boostOk = true
        } catch (t: Throwable) {
            loudFx = null
        }
        apply(store.eq)
        applyBass(store.bass)
        applyVirt(store.virt)
        applyBoost(store.boost)
    }

    fun applyEnabled(on: Boolean) {
        try {
            eq?.setEnabled(on)
        } catch (t: Throwable) {
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

    /** pct de 0 a 100 */
    fun applyBass(pct: Int) {
        try {
            bassFx?.setStrength((pct * 10).coerceIn(0, 1000).toShort())
            bassFx?.setEnabled(pct > 0)
        } catch (t: Throwable) {
        }
    }

    fun applyVirt(pct: Int) {
        try {
            virtFx?.setStrength((pct * 10).coerceIn(0, 1000).toShort())
            virtFx?.setEnabled(pct > 0)
        } catch (t: Throwable) {
        }
    }

    /** Volumen extra: hasta +6 dB */
    fun applyBoost(pct: Int) {
        try {
            loudFx?.setTargetGain(pct * 6)
            loudFx?.setEnabled(pct > 0)
        } catch (t: Throwable) {
        }
    }

    fun release() {
        try {
            eq?.release()
        } catch (t: Throwable) {
        }
        try {
            bassFx?.release()
        } catch (t: Throwable) {
        }
        try {
            virtFx?.release()
        } catch (t: Throwable) {
        }
        try {
            loudFx?.release()
        } catch (t: Throwable) {
        }
        eq = null
        bassFx = null
        virtFx = null
        loudFx = null
        bassOk = false
        virtOk = false
        boostOk = false
    }
}

/**
 * Servicio de reproduccion: mantiene la musica sonando en segundo plano
 * y muestra la notificacion con caratula y controles.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        // Reproductor con un "analizador" de audio dentro (para las barras). No usa permisos.
        val ctx: Context = this
        val renderers = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(SpectrumTap()))
                    .build()
            }
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val store = Store.get(this)
        Fx.attach(player.audioSessionId, store)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                Fx.attach(audioSessionId, store)
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
