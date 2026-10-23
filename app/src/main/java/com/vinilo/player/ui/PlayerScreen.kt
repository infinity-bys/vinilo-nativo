package com.vinilo.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.vinilo.player.PlayerHolder
import com.vinilo.player.R
import com.vinilo.player.Spectrum
import com.vinilo.player.data.Song
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Disco de vinilo que gira mientras suena
// ---------------------------------------------------------------------------
@Composable
fun Disc(diameter: Dp, playing: Boolean, cover: ImageBitmap?, look: Look) {
    val rot = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                rot.animateTo(rot.value + 360f, tween(6000, easing = LinearEasing))
                rot.snapTo(rot.value % 360f)
            }
        }
    }
    Box(
        Modifier
            .size(diameter)
            .shadow(16.dp, CircleShape)
            .clip(CircleShape)
            .graphicsLayer { rotationZ = rot.value },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF1D1528))
            val step = 4.dp.toPx()
            val sw = 2.dp.toPx()
            var rad = sw
            while (rad < r) {
                drawCircle(Color(0xFF120D1A), radius = rad, style = Stroke(width = sw))
                rad += step
            }
            val shine = Color(0x14FFFFFF)
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, shine, Color.Transparent, Color.Transparent, shine, Color.Transparent, Color.Transparent)
                ),
                radius = r
            )
        }
        // etiqueta central: caratula o degradado
        Box(
            Modifier
                .size(diameter * 0.46f)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(look.acc, look.acc2))),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Box(
            Modifier
                .size(diameter * 0.08f)
                .clip(CircleShape)
                .background(C.Bg)
        )
    }
}

// ---------------------------------------------------------------------------
// Barras al ritmo de la musica (audio real; si no hay permiso, animacion de respaldo)
// ---------------------------------------------------------------------------
private fun simBar(i: Int, t: Float): Float {
    val a = sin(t * (2.1f + (i % 7) * 0.45f) + i * 1.3f) * 0.5f + 0.5f
    val b = sin(t * 0.9f + i * 0.35f) * 0.5f + 0.5f
    val env = 1f - (i.toFloat() / Spectrum.N) * 0.55f
    return (0.2f + 0.8f * a * (0.4f + 0.6f * b)) * env
}

@Composable
fun Bars(
    playing: Boolean,
    top: Color,
    bottom: Color,
    position: () -> Long,
    modifier: Modifier = Modifier
) {
    val n = Spectrum.N
    val cur = remember { FloatArray(n) }
    val lv = remember { FloatArray(n) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                withFrameNanos { ns ->
                    val t = ns / 1_000_000_000f
                    val live = Spectrum.fill(position(), lv)
                    for (i in 0 until n) {
                        val target = if (live) lv[i] else simBar(i, t)
                        // sube rapido y baja suave, como un ecualizador real
                        cur[i] = if (target > cur[i]) cur[i] + (target - cur[i]) * 0.6f else cur[i] * 0.88f
                    }
                    frame++
                }
            }
        } else {
            while (cur.any { it > 0.01f }) {
                withFrameNanos {
                    for (i in 0 until n) cur[i] = cur[i] * 0.86f
                    frame++
                }
            }
            for (i in 0 until n) cur[i] = 0f
            frame++
        }
    }
    val brush = remember(top, bottom) { Brush.verticalGradient(listOf(top, bottom)) }
    Canvas(modifier) {
        if (frame < 0) return@Canvas
        val g = size.width / n
        val bw = g * 0.62f
        for (i in 0 until n) {
            val h = max(3.dp.toPx(), cur[i] * size.height)
            drawRoundRect(
                brush = brush,
                topLeft = Offset(i * g + (g - bw) / 2f, size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Barritas chicas de "sonando ahora" (para la cancion actual en la lista)
// ---------------------------------------------------------------------------
@Composable
fun NowBars(playing: Boolean, color: Color, position: () -> Long, modifier: Modifier = Modifier) {
    val cur = remember { floatArrayOf(0.45f, 0.8f, 0.55f, 0.7f) }
    val lv = remember { FloatArray(Spectrum.N) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing) {
        if (playing) {
            val starts = intArrayOf(1, 6, 13, 21)
            while (true) {
                withFrameNanos { ns ->
                    val t = ns / 1_000_000_000f
                    val live = Spectrum.fill(position(), lv)
                    for (i in 0 until 4) {
                        val target = if (live) {
                            var m = 0f
                            for (k in starts[i] until min(starts[i] + 4, Spectrum.N)) m = max(m, lv[k])
                            m
                        } else {
                            0.25f + 0.75f * (sin(t * (3.1f + i * 1.1f) + i * 1.7f) * 0.5f + 0.5f)
                        }
                        cur[i] = if (target > cur[i]) cur[i] + (target - cur[i]) * 0.6f else max(0.12f, cur[i] * 0.88f)
                    }
                    frame++
                }
            }
        }
    }
    Canvas(modifier.size(24.dp, 22.dp)) {
        if (frame < 0) return@Canvas
        val bw = 4.dp.toPx()
        val gap = 2.5.dp.toPx()
        for (i in 0 until 4) {
            val h = max(3.dp.toPx(), cur[i] * size.height)
            drawRoundRect(
                color,
                Offset(i * (bw + gap), size.height - h),
                Size(bw, h),
                CornerRadius(bw / 2f, bw / 2f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Barra de progreso: linea fina con un puntico que se desliza
// ---------------------------------------------------------------------------
@Composable
private fun SeekBar(
    frac: Float,
    dragging: Boolean,
    look: Look,
    onDrag: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var w by remember { mutableFloatStateOf(1f) }
    val dragState = rememberUpdatedState(onDrag)
    val doneState = rememberUpdatedState(onDone)
    Box(
        modifier
            .height(36.dp)
            .onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    dragState.value((o.x / w).coerceIn(0f, 1f))
                    doneState.value()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragState.value((o.x / w).coerceIn(0f, 1f)) },
                    onDragEnd = { doneState.value() },
                    onDragCancel = { doneState.value() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragState.value((change.position.x / w).coerceIn(0f, 1f))
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val th = 4.dp.toPx()
            val cy = size.height / 2f
            val x = size.width * frac
            drawRoundRect(
                color = C.Line,
                topLeft = Offset(0f, cy - th / 2f),
                size = Size(size.width, th),
                cornerRadius = CornerRadius(th / 2f, th / 2f)
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(look.acc, look.acc2), startX = 0f, endX = max(size.width, 1f)),
                topLeft = Offset(0f, cy - th / 2f),
                size = Size(x, th),
                cornerRadius = CornerRadius(th / 2f, th / 2f)
            )
            if (dragging) {
                drawCircle(look.acc.copy(alpha = 0.25f), radius = 14.dp.toPx(), center = Offset(x, cy))
            }
            drawCircle(look.soft, radius = (if (dragging) 8.dp else 6.dp).toPx(), center = Offset(x, cy))
        }
    }
}

@Composable
private fun SeekRow(holder: PlayerHolder, look: Look) {
    var dragging by remember { mutableStateOf(false) }
    var dragV by remember { mutableFloatStateOf(0f) }
    var live by remember { mutableLongStateOf(holder.positionMs) }
    // mientras suena, la posicion se lee en cada fotograma: el puntico se mueve fluido
    LaunchedEffect(holder.isPlaying) {
        while (holder.isPlaying) {
            withFrameNanos { }
            live = holder.livePosition()
        }
    }
    val dur = holder.durationMs
    val pos = if (holder.isPlaying) live else holder.positionMs
    val frac = if (dur > 0L) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val shown = if (dragging) dragV else frac
    val shownMs = if (dragging) (dragV * dur).toLong() else pos
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(fmtTime(shownMs), color = C.Mute, fontSize = 12.sp, modifier = Modifier.width(40.dp))
        SeekBar(
            frac = shown,
            dragging = dragging,
            look = look,
            onDrag = { v ->
                dragging = true
                dragV = v
            },
            onDone = {
                holder.seekTo((dragV * dur).toLong())
                dragging = false
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        Text(
            fmtTime(dur),
            color = C.Mute,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp)
        )
    }
}

private val modeNames = listOf("Repetir todas", "Repetir una", "Aleatorio")
private val modeIcons = listOf(R.drawable.ic_repeat, R.drawable.ic_repeat_one, R.drawable.ic_shuffle)

/** Boton redondo solo con icono (ecualizador y repetir). */
@Composable
private fun RoundBtn(icon: Int, desc: String, on: Boolean, acc: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (on) acc.copy(alpha = 0.14f) else C.Bg)
            .border(1.dp, if (on) acc else C.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), desc, tint = if (on) acc else C.Mute, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun Pill(icon: Int, text: String, on: Boolean, acc: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .height(52.dp)
            .clip(shape)
            .background(if (on) acc.copy(alpha = 0.14f) else C.Bg)
            .border(1.dp, if (on) acc else C.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(painterResource(icon), null, tint = if (on) acc else C.Mute, modifier = Modifier.size(22.dp))
        Text(text, color = if (on) acc else C.Mute, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Pantalla completa del reproductor
// ---------------------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    holder: PlayerHolder,
    song: Song?,
    cover: ImageBitmap?,
    look: Look,
    isFav: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onMenu: () -> Unit,
    onEq: () -> Unit,
    onFav: () -> Unit,
    onMode: () -> Unit,
    lyricsOpen: Boolean,
    onLyricsToggle: () -> Unit,
    lyrics: LyricsUi
) {
    val store = holder.store
    val bgTop by animateColorAsState(look.top, tween(600), label = "bgTop")
    val bgBottom by animateColorAsState(look.bottom, tween(600), label = "bgBottom")
    val eqOn = store.eqOn && (store.eq.any { it != 0 } || store.bass > 0 || store.virt > 0)
    val sleepOn = holder.sleepEndAt > 0L || holder.sleepEot

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // el disco se achica en telefonos bajitos para que quepan todos los botones
    val discSize = min(min(maxWidth.value * 0.72f, 290f), (maxHeight.value - 440f).coerceAtLeast(110f)).dp
    Column(
        Modifier
            .fillMaxSize()
            // sin esto, los toques en zonas vacias atravesaban hasta la lista de abajo
            .pointerInput(Unit) { detectTapGestures { } }
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Barra superior: volver, titulo y ajustes
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(R.drawable.ic_chevron_down, "Volver a la lista", onBack, bg = C.Bg, size = 42.dp)
            Text(
                "Reproduciendo",
                color = C.Mute,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconBtn(
                R.drawable.ic_tune, "Ajustes", onSettings,
                tint = if (sleepOn) look.acc else C.Mute
            )
        }

        if (lyricsOpen) {
            LyricsPanel(
                holder, lyrics, song, look,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            Spacer(Modifier.weight(1f))

            Disc(discSize, holder.isPlaying, cover, look)

            Spacer(Modifier.height(14.dp))
            Bars(
                holder.isPlaying, look.acc, look.acc2,
                { holder.livePosition() },
                Modifier.fillMaxWidth().height(42.dp)
            )
            Spacer(Modifier.height(10.dp))

            val title = if (holder.title.isNotEmpty()) holder.title else song?.title ?: "Elige una canción"
            val sub = when {
                holder.artist.isNotEmpty() -> holder.artist
                song != null -> fmtSize(song.size)
                else -> "Tu música, sin conexión"
            }
            Text(
                title,
                color = C.Text,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )
            Text(
                sub,
                color = C.Mute,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(Modifier.height(6.dp))
        SeekRow(holder, look)
        Spacer(Modifier.height(18.dp))

        // Controles principales
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(
                if (isFav) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                "Favorita", onFav,
                tint = if (isFav) look.acc2 else C.Mute, size = 46.dp, iconSize = 24.dp
            )
            IconBtn(R.drawable.ic_prev, "Anterior", { holder.prev() }, tint = look.soft, size = 52.dp, iconSize = 28.dp)
            Box(
                Modifier
                    .size(64.dp)
                    .shadow(10.dp, CircleShape, ambientColor = look.acc2, spotColor = look.acc2)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(look.acc, look.acc2)))
                    .clickable { holder.toggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(if (holder.isPlaying) R.drawable.ic_pause_wide else R.drawable.ic_play),
                    if (holder.isPlaying) "Pausar" else "Reproducir",
                    tint = C.Bg,
                    modifier = Modifier.size(34.dp)
                )
            }
            IconBtn(R.drawable.ic_next, "Siguiente", { holder.next() }, tint = look.soft, size = 52.dp, iconSize = 28.dp)
            Box(contentAlignment = Alignment.TopEnd) {
                IconBtn(
                    R.drawable.ic_more, "Más opciones", onMenu,
                    tint = if (store.speed != 1f) look.acc else C.Mute, size = 46.dp
                )
                if (store.speed != 1f) {
                    Text(
                        speedText(store.speed),
                        color = C.Bg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(look.acc)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (lyricsOpen) Spacer(Modifier.height(14.dp)) else Spacer(Modifier.weight(0.6f))

        // Ecualizador y modo de reproduccion
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundBtn(R.drawable.ic_equalizer, "Ecualizador", eqOn, look.acc, onEq)
            Pill(R.drawable.ic_lyrics, "Letras", lyricsOpen, look.acc, onLyricsToggle)
            RoundBtn(modeIcons[store.mode], modeNames[store.mode], store.mode > 0, look.acc, onMode)
        }
    }
    }
}

fun speedText(v: Float): String = if (v == 1f) "Normal" else (v.toString().removeSuffix(".0") + "x")

// ---------------------------------------------------------------------------
// Mini reproductor (abajo de la lista)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(holder: PlayerHolder, song: Song?, look: Look, onOpen: () -> Unit) {
    val rot = remember { Animatable(0f) }
    val playing = holder.isPlaying
    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                rot.animateTo(rot.value + 360f, tween(6000, easing = LinearEasing))
                rot.snapTo(rot.value % 360f)
            }
        }
    }
    val dur = holder.durationMs
    val frac = if (dur > 0L) (holder.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(22.dp)

    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(C.Panel)
            .border(1.dp, C.Line, shape)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(C.Line)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(look.acc, look.acc2)))
            )
        }
        Row(
            Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (song != null) {
                    Cover(
                        song, 44.dp,
                        modifier = Modifier
                            .border(4.dp, Color(0xFF120D1A), CircleShape)
                            .graphicsLayer { rotationZ = rot.value },
                        shape = CircleShape
                    )
                } else {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(look.acc, look.acc2)))
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // si el nombre no cabe, se desplaza solo para leerlo completo
                    Text(
                        if (holder.title.isNotEmpty()) holder.title else song?.title ?: "",
                        color = C.Text,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    Text("Toca para abrir", color = C.Mute, fontSize = 12.sp)
                }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(look.acc, look.acc2)))
                    .clickable { holder.toggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(if (playing) R.drawable.ic_pause_wide else R.drawable.ic_play),
                    null, tint = C.Bg, modifier = Modifier.size(22.dp)
                )
            }
            IconBtn(R.drawable.ic_next, "Siguiente", { holder.next() }, tint = look.soft, size = 44.dp, iconSize = 26.dp)
        }
    }
}
