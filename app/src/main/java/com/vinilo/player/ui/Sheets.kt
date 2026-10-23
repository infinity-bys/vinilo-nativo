package com.vinilo.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.vinilo.player.Fx
import kotlin.math.max
import kotlin.math.min
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinilo.player.PlayerHolder
import com.vinilo.player.R
import com.vinilo.player.data.LrcResult
import com.vinilo.player.data.Playlist
import com.vinilo.player.data.Song
import com.vinilo.player.data.Sorts
import com.vinilo.player.data.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Contenedor comun de todas las hojas inferiores
// ---------------------------------------------------------------------------
/**
 * Hoja inferior que vive DENTRO de la pantalla (no abre una ventana nueva del sistema),
 * asi que aparece al instante y el disco no se entrecorta. Se cierra tocando fuera,
 * arrastrando hacia abajo o con el boton atras.
 */
@Composable
fun VSheet(onClose: () -> Unit, content: @Composable (close: () -> Unit) -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(200))
    }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, tween(160))
                onClose()
            }
        }
    }
    BackHandler { close() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxH = maxHeight * 0.9f
        // fondo oscuro: tocarlo cierra la hoja
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(Color(0xAA000000))
                .pointerInput(Unit) { detectTapGestures { close() } }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = (1f - progress.value) * size.height + dragY }
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(C.Panel)
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            // asa: se arrastra hacia abajo para cerrar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragY > 120f) close() else dragY = 0f
                            },
                            onDragCancel = { dragY = 0f }
                        ) { change, amount ->
                            change.consume()
                            dragY = (dragY + amount).coerceAtLeast(0f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(C.Mute.copy(alpha = 0.5f))
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxH - 28.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            ) {
                content(close)
            }
        }
    }
}

@Composable
fun SheetTitle(text: String) {
    Text(
        text,
        color = C.Text,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBtn(R.drawable.ic_chevron_left, "Atrás", onBack, size = 40.dp)
        Text(
            title,
            color = C.Text,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )
    }
}

/** Miniatura de una lista: la caratula de su primera cancion, o un degradado. */
@Composable
fun PlaylistThumb(p: Playlist, byId: Map<Long, Song>, size: Dp = 52.dp) {
    val first = p.ids.firstNotNullOfOrNull { byId[it] }
    if (first != null) {
        Cover(first, size)
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(C.Acc, C.Acc2))),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_note), null, tint = C.Bg, modifier = Modifier.size(size / 2))
        }
    }
}

// ---------------------------------------------------------------------------
// Ajustes (con subpaginas: temporizador y canciones ocultas)
// ---------------------------------------------------------------------------
@Composable
fun SettingsSheet(
    store: Store,
    holder: PlayerHolder,
    hidden: List<Song>,
    onRescan: () -> Unit,
    onUnhide: (List<Song>) -> Unit,
    toast: (String) -> Unit
) {
    var page by remember { mutableStateOf("main") }
    when (page) {
        "sleep" -> {
            SubHeader("Temporizador de apagado") { page = "main" }
            val opts = listOf(15 to "15 minutos", 30 to "30 minutos", 45 to "45 minutos", 60 to "1 hora")
            for ((m, label) in opts) {
                SheetRow(label, {
                    holder.setSleepMinutes(m)
                    toast("Se apagará en $label")
                    page = "main"
                })
            }
            SheetRow("Al terminar esta canción", {
                holder.setSleepEot()
                toast("Se apagará al terminar la canción")
                page = "main"
            }, selected = holder.sleepEot)
            SheetRow("Desactivar", {
                holder.clearSleep()
                page = "main"
            }, danger = true)
        }

        "hidden" -> {
            SubHeader("Canciones ocultas") { page = "main" }
            if (hidden.isEmpty()) {
                EmptyCard(
                    "No hay canciones ocultas",
                    "Las que ocultes, y los audios que no son música, aparecerán aquí."
                )
            } else {
                SheetRow("Mostrar todas", { onUnhide(hidden) }, icon = R.drawable.ic_eye)
                for (s in hidden.take(150)) {
                    val why = if (s.id in store.hidden) "Oculta por ti" else s.junk
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onUnhide(listOf(s)) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(s, 46.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(why, color = C.Mute, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            "Mostrar",
                            color = C.Acc,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, C.Acc, RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
                if (hidden.size > 150) {
                    Text(
                        "y ${hidden.size - 150} más…",
                        color = C.Mute,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        else -> {
            SheetTitle("Ajustes")
            val sleepText = when {
                holder.sleepEndAt > 0L -> maxOf(1, ceil(holder.sleepLeftMs / 60000.0).toInt()).toString() + " min"
                holder.sleepEot -> "Al terminar la canción"
                else -> "Desactivado"
            }
            SheetRow("Temporizador de apagado", { page = "sleep" }, icon = R.drawable.ic_timer, value = sleepText)
            SheetRow(
                "Botones con colores de la carátula",
                { store.changeAccentOn(!store.accentOn) },
                icon = R.drawable.ic_palette,
                trailing = { Sw(store.accentOn) }
            )
            SheetRow(
                "Ocultar audios que no son música",
                {
                    store.changeAutoHide(!store.autoHide)
                    toast(if (store.autoHide) "Audios que no son música: ocultos" else "Mostrando todos los audios")
                },
                icon = R.drawable.ic_filter,
                trailing = { Sw(store.autoHide) }
            )
            SheetRow(
                "Canciones ocultas",
                { page = "hidden" },
                icon = R.drawable.ic_eye_off,
                value = if (hidden.isEmpty()) "Ninguna" else hidden.size.toString()
            )
            SheetRow("Volver a buscar música", onRescan, icon = R.drawable.ic_refresh)
        }
    }
}

// ---------------------------------------------------------------------------
// Ordenar
// ---------------------------------------------------------------------------
@Composable
fun SortSheet(store: Store, close: () -> Unit) {
    SheetTitle("Ordenar canciones por")
    for (o in Sorts.all) {
        val on = store.sortKey == o.key
        SheetRow(
            o.name,
            {
                store.sortBy(o.key)
                close()
            },
            selected = on,
            showCheck = false,
            trailing = {
                if (on) {
                    Icon(
                        painterResource(if (store.sortDir > 0) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down),
                        null, tint = C.Acc, modifier = Modifier.size(22.dp)
                    )
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Ecualizador: 5 bandas verticales, curva en vivo, presets y efectos
// ---------------------------------------------------------------------------
private val eqNames5 = listOf("31-63", "125-250", "500-1K", "2K-4K", "8K-16K")
private val eqNames = listOf("31", "63", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
private val eqPresets = listOf(
    "Normal" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    "Graves" to listOf(7, 6, 5, 3, 1, 0, 0, 0, 0, 0),
    "Voces" to listOf(-2, -2, -1, 1, 3, 4, 4, 2, 0, -1),
    "Agudos" to listOf(0, 0, 0, 0, 0, 1, 3, 5, 6, 7),
    "Rock" to listOf(5, 4, 3, 1, -1, -1, 0, 3, 4, 5),
    "Pop" to listOf(-1, -1, 0, 2, 4, 4, 2, 0, -1, -2),
    "Hip-Hop" to listOf(6, 5, 3, 2, -1, -1, 1, 0, 2, 3),
    "Electrónica" to listOf(5, 4, 1, 0, -2, 2, 1, 1, 4, 5),
    "Jazz" to listOf(4, 3, 1, 2, -2, -2, 0, 1, 3, 4),
    "Clásica" to listOf(4, 3, 3, 2, -1, -1, 0, 2, 3, 4),
    "Acústica" to listOf(4, 4, 3, 1, 1, 1, 2, 3, 3, 2)
)

/** Selector "5 Bandas | 10 Bandas". */
@Composable
private fun BandsSwitch(bands: Int, onPick: (Int) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Bg)
            .border(1.dp, C.Line, shape)
            .padding(4.dp)
    ) {
        for (n in listOf(5, 10)) {
            val on = bands == n
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) C.Acc else Color.Transparent)
                    .clickable { onPick(n) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$n Bandas",
                    color = if (on) C.Bg else C.Mute,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/** Dibujo suave de como queda el sonido con los ajustes actuales. */
@Composable
private fun EqCurve(gains: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val amp = h / 2f * 0.8f
        drawLine(C.Line, Offset(0f, cy), Offset(w, cy), strokeWidth = 1.dp.toPx())
        val pts = ArrayList<Offset>()
        val cnt = gains.size
        pts.add(Offset(0f, cy - gains[0] / 12f * amp))
        for (i in 0 until cnt) {
            pts.add(Offset(w * (i + 0.5f) / cnt, cy - gains[i] / 12f * amp))
        }
        pts.add(Offset(w, cy - gains[cnt - 1] / 12f * amp))

        fun curve(p: Path) {
            p.moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val mx = (pts[i].x + pts[i + 1].x) / 2f
                p.cubicTo(mx, pts[i].y, mx, pts[i + 1].y, pts[i + 1].x, pts[i + 1].y)
            }
        }

        val fill = Path()
        curve(fill)
        fill.lineTo(w, cy)
        fill.lineTo(0f, cy)
        fill.close()
        drawPath(fill, C.Acc.copy(alpha = 0.14f))

        val line = Path()
        curve(line)
        drawPath(
            line,
            Brush.horizontalGradient(listOf(C.Acc, C.Acc2)),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/** Una banda: slider vertical fino con un puntico, de -12 a +12 dB. */
@Composable
private fun EqBand(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var h by remember { mutableFloatStateOf(1f) }
    val change = rememberUpdatedState(onChange)
    val pad = with(LocalDensity.current) { 10.dp.toPx() }

    fun valueAt(y: Float): Int {
        val f = (1f - (y - pad) / (h - 2f * pad)).coerceIn(0f, 1f)
        return (-12f + f * 24f).roundToInt()
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            (if (value > 0) "+" else "") + value,
            color = if (value != 0) C.Acc else C.Mute,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .onSizeChanged { h = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTapGestures { o -> change.value(valueAt(o.y)) }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { o -> change.value(valueAt(o.y)) }
                    ) { c, _ ->
                        c.consume()
                        change.value(valueAt(c.position.y))
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val tw = 4.dp.toPx()
                val top = pad
                val bot = size.height - pad
                val mid = (top + bot) / 2f
                val ty = bot - (value + 12f) / 24f * (bot - top)
                drawRoundRect(
                    C.Line,
                    Offset(cx - tw / 2f, top),
                    Size(tw, bot - top),
                    CornerRadius(tw / 2f, tw / 2f)
                )
                drawLine(
                    C.Mute,
                    Offset(cx - 11.dp.toPx(), mid),
                    Offset(cx + 11.dp.toPx(), mid),
                    strokeWidth = 1.5.dp.toPx()
                )
                val y0 = min(mid, ty)
                val y1 = max(mid, ty)
                if (y1 - y0 > 0.5f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(C.Acc, C.Acc2), startY = top, endY = bot),
                        topLeft = Offset(cx - tw / 2f, y0),
                        size = Size(tw, y1 - y0),
                        cornerRadius = CornerRadius(tw / 2f, tw / 2f)
                    )
                }
                drawCircle(C.Text, radius = 9.dp.toPx(), center = Offset(cx, ty))
                drawCircle(C.Acc, radius = 4.dp.toPx(), center = Offset(cx, ty))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = C.Mute, fontSize = 10.sp)
    }
}

/** Slider horizontal fino (linea + puntico) para los efectos. */
@Composable
private fun ThinSlider(frac: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var w by remember { mutableFloatStateOf(1f) }
    val change = rememberUpdatedState(onChange)
    Box(
        modifier
            .height(32.dp)
            .onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { o -> change.value((o.x / w).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> change.value((o.x / w).coerceIn(0f, 1f)) }
                ) { c, _ ->
                    c.consume()
                    change.value((c.position.x / w).coerceIn(0f, 1f))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val th = 4.dp.toPx()
            val cy = size.height / 2f
            val x = size.width * frac
            drawRoundRect(
                C.Line,
                Offset(0f, cy - th / 2f),
                Size(size.width, th),
                CornerRadius(th / 2f, th / 2f)
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(C.Acc, C.Acc2), startX = 0f, endX = max(size.width, 1f)),
                topLeft = Offset(0f, cy - th / 2f),
                size = Size(x, th),
                cornerRadius = CornerRadius(th / 2f, th / 2f)
            )
            drawCircle(C.Text, radius = 8.dp.toPx(), center = Offset(x, cy))
            drawCircle(C.Acc, radius = 3.5.dp.toPx(), center = Offset(x, cy))
        }
    }
}

@Composable
private fun FxRow(label: String, pct: Int, hint: String? = null, onPct: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(
                "$pct%",
                color = if (pct > 0) C.Acc else C.Mute,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        ThinSlider(
            pct / 100f,
            { f -> onPct((f * 100f).roundToInt()) },
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
        if (hint != null) {
            Text(hint, color = C.Mute, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
fun EqSheet(store: Store) {
    fun setGains(g: List<Int>) {
        // mover cualquier cosa enciende el ecualizador
        if (!store.eqOn) store.changeEqOn(true)
        store.changeEq(g)
        Fx.update(store)
    }

    // Titulo + interruptor
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Ecualizador",
            color = C.Text,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        Text(if (store.eqOn) "Activado" else "Apagado", color = C.Mute, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    store.changeEqOn(!store.eqOn)
                    Fx.update(store)
                }
        ) {
            Sw(store.eqOn)
        }
    }

    Column(Modifier.alpha(if (store.eqOn) 1f else 0.45f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((name, v) in eqPresets) {
                Chip(name, store.eq == v, { setGains(v) })
            }
        }
        Spacer(Modifier.height(14.dp))
        EqCurve(
            store.eq,
            Modifier
                .fillMaxWidth()
                .height(64.dp)
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (store.eqBands == 10) {
                for (i in 0 until 10) {
                    EqBand(
                        eqNames[i],
                        store.eq[i],
                        { v ->
                            val g = store.eq.toMutableList()
                            g[i] = v
                            setGains(g)
                        },
                        Modifier.weight(1f)
                    )
                }
            } else {
                // 5 bandas: cada slider mueve dos bandas vecinas a la vez
                for (j in 0 until 5) {
                    EqBand(
                        eqNames5[j],
                        ((store.eq[2 * j] + store.eq[2 * j + 1]) / 2f).roundToInt(),
                        { v ->
                            val g = store.eq.toMutableList()
                            g[2 * j] = v
                            g[2 * j + 1] = v
                            setGains(g)
                        },
                        Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        BandsSwitch(store.eqBands) { store.changeEqBands(it) }
    }

    // Efectos extra (solo los que tu telefono soporta)
    Spacer(Modifier.height(18.dp))
    Text(
        "Efectos de sonido",
        color = C.Mute,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp)
    )
    if (Fx.bassOk) {
        FxRow("Refuerzo de graves", store.bass) { v ->
            store.changeBass(v)
            Fx.update(store)
        }
    }
    if (Fx.virtOk) {
        FxRow("Sonido envolvente", store.virt) { v ->
            store.changeVirt(v)
            Fx.update(store)
        }
    }
    if (!Fx.bassOk && !Fx.virtOk) {
        Text(
            "Tu teléfono no ofrece más efectos.",
            color = C.Mute,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )
    }

    // Restablecer
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .clip(shape)
            .border(1.dp, C.Line, shape)
            .clickable {
                val zeros = List(10) { 0 }
                store.changeEq(zeros)
                store.changeBass(0)
                store.changeVirt(0)
                Fx.update(store)
            }
            .padding(13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Restablecer todo", color = C.Mute, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Menu de tres puntos del reproductor (con subpagina de velocidad)
// ---------------------------------------------------------------------------
private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
fun PlayerMenuSheet(
    song: Song?,
    store: Store,
    onAdd: () -> Unit,
    onSpeed: (Float) -> Unit,
    onHide: () -> Unit,
    close: () -> Unit
) {
    var page by remember { mutableStateOf("menu") }
    if (page == "speed") {
        SubHeader("Velocidad de reproducción") { page = "menu" }
        for (v in speeds) {
            SheetRow(speedText(v), {
                onSpeed(v)
                close()
            }, selected = store.speed == v)
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (song != null) {
                Cover(song, 56.dp)
            } else {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(C.Acc, C.Acc2)))
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    song?.title ?: "Vinilo",
                    color = C.Text,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song?.artist?.ifBlank { null } ?: song?.let { fmtSize(it.size) } ?: "",
                    color = C.Mute,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        SheetRow("Agregar a una playlist", onAdd, icon = R.drawable.ic_playlist_add)
        SheetRow(
            "Velocidad",
            { page = "speed" },
            icon = R.drawable.ic_speed,
            value = speedText(store.speed)
        )
        SheetRow("Ocultar canción", onHide, icon = R.drawable.ic_eye_off)
    }
}

// ---------------------------------------------------------------------------
// Agregar a una lista
// ---------------------------------------------------------------------------
@Composable
fun AddToSheet(
    playlists: List<Playlist>,
    byId: Map<Long, Song>,
    onNew: () -> Unit,
    onPick: (Playlist) -> Unit
) {
    SheetTitle("Agregar a una playlist")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onNew)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, C.Acc, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_add), null, tint = C.Acc, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text("Nueva playlist", color = C.Acc, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
    for (p in playlists) {
        val count = p.ids.count { byId.containsKey(it) }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onPick(p) }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistThumb(p, byId)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(plural(count, "canción", "canciones"), color = C.Mute, fontSize = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Nombre de lista (crear / renombrar)
// ---------------------------------------------------------------------------
@Composable
fun NameSheet(title: String, initial: String, okText: String, onOk: (String) -> Unit) {
    var tf by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    val valid = tf.text.isNotBlank()
    fun submit() {
        if (tf.text.isNotBlank()) onOk(tf.text.trim())
    }
    LaunchedEffect(Unit) {
        delay(200)
        try {
            focus.requestFocus()
        } catch (e: Exception) {
        }
    }
    SheetTitle(title)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.Bg)
            .border(1.dp, C.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        if (tf.text.isEmpty()) {
            Text("Nombre de la lista", color = C.Mute, fontSize = 17.sp)
        }
        BasicTextField(
            value = tf,
            onValueChange = { v ->
                tf = if (v.text.length > 40) v.copy(text = v.text.take(40)) else v
            },
            singleLine = true,
            textStyle = TextStyle(color = C.Text, fontSize = 17.sp),
            cursorBrush = SolidColor(C.Acc),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
        )
    }
    Text(
        "Ponle un nombre fácil de recordar.",
        color = C.Mute,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 16.dp)
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    if (valid) listOf(C.Acc, C.Acc2) else listOf(C.Line, C.Line)
                )
            )
            .clickable(enabled = valid) { submit() }
            .padding(15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            okText,
            color = if (valid) C.Bg else C.Mute,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

// ---------------------------------------------------------------------------
// Letras: agregar / cambiar, buscar en Google, buscar en linea, copiado
// ---------------------------------------------------------------------------
@Composable
fun LyricsAddSheet(
    hasLyrics: Boolean,
    onGoogle: () -> Unit,
    onOnline: () -> Unit,
    onLocal: () -> Unit,
    onPaste: () -> Unit,
    onDelete: () -> Unit
) {
    SheetTitle(if (hasLyrics) "Cambiar la letra" else "Agregar letra")
    SheetRow("Buscar en Google (copiar y se agrega sola)", onGoogle, icon = R.drawable.ic_search)
    SheetRow("Buscar en la base de datos en línea", onOnline, icon = R.drawable.ic_lyrics)
    SheetRow("Añadir letras locales (.lrc o .txt)", onLocal, icon = R.drawable.ic_playlist_add)
    SheetRow("Pegar o escribir la letra", onPaste, icon = R.drawable.ic_edit)
    if (hasLyrics) {
        SheetRow("Quitar la letra", onDelete, icon = R.drawable.ic_delete, danger = true)
    }
}

@Composable
fun LyricsResultsSheet(
    initialQuery: String,
    results: List<LrcResult>?,
    busy: Boolean,
    onSearch: (String) -> Unit,
    onPick: (LrcResult) -> Unit,
    onGoogle: () -> Unit
) {
    var q by remember { mutableStateOf(initialQuery) }
    val shape = RoundedCornerShape(16.dp)
    SheetTitle("Buscar letra")

    // Opcion principal: buscar en Google, copiar la letra y volver
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(C.Acc, C.Acc2)))
            .clickable(onClick = onGoogle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = C.Bg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Buscar en Google", color = C.Bg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Copia la letra y vuelve aquí: se agrega sola.",
                color = C.Bg.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(
        "O busca en la base de datos en línea:",
        color = C.Mute,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Bg)
            .border(1.dp, C.Line, shape)
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) {
                Text("Canción o artista", color = C.Mute, fontSize = 16.sp)
            }
            BasicTextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(C.Acc),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(q) }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        IconBtn(R.drawable.ic_search, "Buscar", { onSearch(q) }, tint = C.Acc)
    }
    Spacer(Modifier.height(10.dp))
    when {
        busy -> {
            Text("Buscando…", color = C.Mute, modifier = Modifier.padding(12.dp))
        }
        results == null -> {
        }
        results.isEmpty() -> {
            Text(
                "No encontré letras aquí. Prueba con Buscar en Google.",
                color = C.Mute,
                fontSize = 14.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
        else -> {
            for (r in results.take(30)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPick(r) }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(
                        r.title,
                        color = C.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(r.artist, r.album).filter { it.isNotBlank() }.joinToString(" · "),
                        color = C.Mute,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (r.durationSec > 0) {
                        Text(
                            fmtTime(r.durationSec * 1000L),
                            color = C.Mute,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/** "Copiaste una letra: usarla?" (despues de buscar en Google). */
@Composable
fun LyricsClipSheet(preview: String, onUse: () -> Unit, onCancel: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    SheetTitle("¿Usar la letra copiada?")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Bg)
            .border(1.dp, C.Line, shape)
            .padding(14.dp)
    ) {
        Text(
            preview,
            color = C.Text,
            fontSize = 15.sp,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis
        )
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .weight(1f)
                .clip(shape)
                .border(1.dp, C.Line, shape)
                .clickable(onClick = onCancel)
                .padding(15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Cancelar", color = C.Text, fontWeight = FontWeight.SemiBold)
        }
        Box(
            Modifier
                .weight(1f)
                .clip(shape)
                .background(Brush.linearGradient(listOf(C.Acc, C.Acc2)))
                .clickable(onClick = onUse)
                .padding(15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Usar como letra", color = C.Bg, fontWeight = FontWeight.Bold)
        }
    }
}
