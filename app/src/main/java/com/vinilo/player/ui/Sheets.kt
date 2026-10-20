package com.vinilo.player.ui

import androidx.activity.compose.BackHandler
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
// Ecualizador de 5 bandas
// ---------------------------------------------------------------------------
private val eqNames = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
private val eqPresets = listOf(
    "Normal" to listOf(0, 0, 0, 0, 0),
    "Graves" to listOf(6, 4, 0, 0, 0),
    "Voces" to listOf(-2, -1, 3, 4, 1),
    "Agudos" to listOf(0, 0, 1, 4, 6),
    "Rock" to listOf(4, 2, -2, 3, 4)
)

@Composable
fun EqSheet(store: Store, onChange: (List<Int>) -> Unit) {
    SheetTitle("Ecualizador")
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for ((name, v) in eqPresets) {
            Chip(name, store.eq == v, { onChange(v) })
        }
    }
    Spacer(Modifier.height(8.dp))
    for (i in 0 until 5) {
        val v = store.eq[i]
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(eqNames[i], color = C.Mute, fontSize = 13.sp, modifier = Modifier.width(58.dp))
            Slider(
                value = v.toFloat(),
                onValueChange = { x ->
                    val n = store.eq.toMutableList()
                    n[i] = x.roundToInt()
                    onChange(n)
                },
                valueRange = -12f..12f,
                steps = 23,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = C.Text,
                    activeTrackColor = C.Acc,
                    inactiveTrackColor = C.Line,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
            Text(
                (if (v > 0) "+" else "") + v + " dB",
                color = C.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.width(56.dp)
            )
        }
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
        SheetRow("Agregar a una lista", onAdd, icon = R.drawable.ic_playlist_add)
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
    SheetTitle("Agregar a la lista")
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
        Text("Nueva lista", color = C.Acc, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
