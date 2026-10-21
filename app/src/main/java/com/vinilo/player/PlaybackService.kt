package com.vinilo.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
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
 * Efectos de sonido de Android sobre el reproductor: ecualizador de 5 bandas
 * (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz), refuerzo de graves, sonido envolvente
 * y volumen extra. Viven aqui para seguir activos con la app cerrada.
 *
 * Importante: los efectos NUNCA se encienden ni se apagan con la musica sonando
 * (eso hace un chasquido). "Apagar" = llevar todo a cero poco a poco.
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
    private var bandMap = IntArray(0)
    private var bandLo = -1500
    private var bandHi = 1500

    // Valores 0..4 = bandas (dB), 5 = graves (%), 6 = envolvente (%), 7 = volumen extra (%)
    private val handler = Handler(Looper.getMainLooper())
    private val cur = FloatArray(8)
    private val tgt = FloatArray(8)
    private val applied = IntArray(8) { Int.MIN_VALUE }
    private var running = false

    private val stepper = object : Runnable {
        override fun run() {
            var moving = false
            for (i in 0 until 8) {
                val d = tgt[i] - cur[i]
                if (abs(d) > 0.001f) {
                    val maxStep = if (i < 5) 1.5f else 8f
                    cur[i] += d.coerceIn(-maxStep, maxStep)
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
        for (i in 0 until 5) {
            tgt[i] = if (store.eqOn) store.eq.getOrElse(i) { 0 }.toFloat() else 0f
        }
        tgt[5] = store.bass.toFloat()
        tgt[6] = store.virt.toFloat()
        tgt[7] = store.boost.toFloat()
    }

    fun attach(session: Int, store: Store) {
        release()
        sessionId = session
        if (session == 0) return
        try {
            val e = Equalizer(0, session)
            e.setEnabled(true)
            eq = e
            val range = e.bandLevelRange
            bandLo = range[0].toInt()
            bandHi = range[1].toInt()
            val n = e.numberOfBands.toInt()
            bandMap = IntArray(n) { b ->
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
                best
            }
        } catch (t: Throwable) {
            eq = null
        }
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
        try {
            val l = LoudnessEnhancer(session)
            l.setEnabled(true)
            loudFx = l
            boostOk = true
        } catch (t: Throwable) {
            loudFx = null
        }
        // estado inicial: se aplica de golpe (todavia no suena nada)
        loadTargets(store)
        for (i in 0 until 8) {
            cur[i] = tgt[i]
            applied[i] = Int.MIN_VALUE
        }
        render()
    }

    /** Lleva los efectos a lo que dice el Store, con una transicion suave. */
    fun update(store: Store) {
        loadTargets(store)
        if (!running) {
            running = true
            handler.post(stepper)
        }
    }

    private fun render() {
        val e = eq
        if (e != null) {
            for (ui in 0 until 5) {
                val mb = (cur[ui] * 100f).roundToInt()
                if (mb != applied[ui]) {
                    applied[ui] = mb
                    for (b in bandMap.indices) {
                        if (bandMap[b] == ui) {
                            try {
                                e.setBandLevel(b.toShort(), mb.coerceIn(bandLo, bandHi).toShort())
                            } catch (t: Throwable) {
                            }
                        }
                    }
                }
            }
        }
        val bass = (cur[5] * 10f).roundToInt().coerceIn(0, 1000)
        if (bass != applied[5]) {
            applied[5] = bass
            try {
                bassFx?.setStrength(bass.toShort())
            } catch (t: Throwable) {
            }
        }
        val virt = (cur[6] * 10f).roundToInt().coerceIn(0, 1000)
        if (virt != applied[6]) {
            applied[6] = virt
            try {
                virtFx?.setStrength(virt.toShort())
            } catch (t: Throwable) {
            }
        }
        // volumen extra: hasta +6 dB (600 milibelios)
        val boost = (cur[7] * 6f).roundToInt().coerceIn(0, 600)
        if (boost != applied[7]) {
            applied[7] = boost
            try {
                loudFx?.setTargetGain(boost)
            } catch (t: Throwable) {
            }
        }
    }

    fun release() {
        handler.removeCallbacks(stepper)
        running = false
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
        bandMap = IntArray(0)
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
