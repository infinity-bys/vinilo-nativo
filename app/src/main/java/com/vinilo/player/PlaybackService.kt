package com.vinilo.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Handler
import android.os.Looper
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
import kotlin.math.roundToInt

/**
 * Efectos de sonido de Android sobre el reproductor: refuerzo de graves y sonido
 * envolvente. (El ecualizador de 10 bandas es EqProcessor.)
 *
 * Importante: los efectos NUNCA se encienden ni se apagan con la musica sonando
 * (eso hace un chasquido). "Apagar" = llevar el valor a cero poco a poco.
 * Si el telefono no soporta alguno, simplemente no se usa.
 */
object Fx {
    private var bassFx: BassBoost? = null
    private var virtFx: Virtualizer? = null

    /** Cuales efectos existen en este telefono (la pantalla solo muestra los que si). */
    @Volatile
    var bassOk: Boolean = false
        private set

    @Volatile
    var virtOk: Boolean = false
        private set

    /** Id de sesion de audio del reproductor. */
    @Volatile
    var sessionId: Int = 0
        private set

    // 0 = graves (%), 1 = envolvente (%)
    private val handler = Handler(Looper.getMainLooper())
    private val cur = FloatArray(2)
    private val tgt = FloatArray(2)
    private val applied = IntArray(2) { Int.MIN_VALUE }
    private var running = false

    private val stepper = object : Runnable {
        override fun run() {
            var moving = false
            for (i in 0 until 2) {
                val d = tgt[i] - cur[i]
                if (abs(d) > 0.001f) {
                    cur[i] += d.coerceIn(-8f, 8f)
                    moving = true
                }
            }
            render()
            if (moving) {
                handler.postDelayed(this, 16L)
            } else {
                running = false
            }
        }
    }

    private fun loadTargets(store: Store) {
        tgt[0] = store.bass.toFloat()
        tgt[1] = store.virt.toFloat()
    }

    fun attach(session: Int, store: Store) {
        release()
        // el ecualizador de 10 bandas no depende de la sesion de audio
        EqState.load(store)
        sessionId = session
        if (session == 0) return
        try {
            val b = BassBoost(0, session)
            if (b.getStrengthSupported()) {
                b.setEnabled(true)
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
                v.setEnabled(true)
                virtFx = v
                virtOk = true
            } else {
                v.release()
            }
        } catch (t: Throwable) {
            virtFx = null
        }
        // estado inicial: se aplica de golpe (todavia no suena nada)
        loadTargets(store)
        for (i in 0 until 2) {
            cur[i] = tgt[i]
            applied[i] = Int.MIN_VALUE
        }
        render()
    }

    /** Lleva el ecualizador y los efectos a lo que dice el Store, con una transicion suave. */
    fun update(store: Store) {
        EqState.load(store)
        loadTargets(store)
        if (!running) {
            running = true
            handler.post(stepper)
        }
    }

    private fun render() {
        val bass = (cur[0] * 10f).roundToInt().coerceIn(0, 1000)
        if (bass != applied[0]) {
            applied[0] = bass
            try {
                bassFx?.setStrength(bass.toShort())
            } catch (t: Throwable) {
            }
        }
        val virt = (cur[1] * 10f).roundToInt().coerceIn(0, 1000)
        if (virt != applied[1]) {
            applied[1] = virt
            try {
                virtFx?.setStrength(virt.toShort())
            } catch (t: Throwable) {
            }
        }
    }

    fun release() {
        handler.removeCallbacks(stepper)
        running = false
        try {
            bassFx?.release()
        } catch (t: Throwable) {
        }
        try {
            virtFx?.release()
        } catch (t: Throwable) {
        }
        bassFx = null
        virtFx = null
        bassOk = false
        virtOk = false
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
                    .setAudioProcessors(arrayOf<AudioProcessor>(EqProcessor(), SpectrumTap()))
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
