package com.vinilo.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinilo.player.PlayerHolder
import com.vinilo.player.R
import com.vinilo.player.data.Playlist
import com.vinilo.player.data.Song
import kotlinx.coroutines.delay

/**
 * Pantalla para elegir canciones y meterlas en una playlist.
 * Las que ya estan en la playlist salen marcadas como "Ya esta".
 */
@Composable
fun SongPicker(
    playlist: Playlist,
    songs: List<Song>,
    onClose: () -> Unit,
    onDone: (List<Long>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val already = remember(playlist) { playlist.ids.toSet() }
    val q = query.trim().lowercase()
    val list = remember(songs, q) {
        if (q.isEmpty()) songs else songs.filter { (it.title + " " + it.artist).lowercase().contains(q) }
    }
    BackHandler { onClose() }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(R.drawable.ic_close, "Cerrar", onClose)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text("Agregar canciones", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    playlist.name,
                    color = C.Mute,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val on = picked.isNotEmpty()
            val bg: Brush = if (on) Brush.linearGradient(listOf(C.Acc, C.Acc2)) else SolidColor(C.Line)
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable(enabled = on) {
                        onDone(songs.filter { it.id in picked }.map { it.id })
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    if (on) "Agregar (" + picked.size + ")" else "Agregar",
                    color = if (on) C.Bg else C.Mute,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        SearchBox(query) { query = it }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            if (list.isEmpty()) {
                item {
                    Text(
                        "Sin resultados",
                        color = C.Mute,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    )
                }
            }
            itemsIndexed(list, key = { _, s -> s.id }) { _, s ->
                val inList = s.id in already
                val on = s.id in picked
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(C.Panel)
                        .clickable(enabled = !inList) {
                            picked = if (on) picked - s.id else picked + s.id
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cover(s, 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title,
                            color = if (inList) C.Mute else C.Text,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (s.artist.isNotBlank()) s.artist else fmtSize(s.size),
                            color = C.Mute,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (inList) {
                        Text("Ya está", color = C.Mute, fontSize = 12.sp)
                    } else {
                        Radio(on)
                    }
                }
            }
        }
    }
}

/**
 * Pantalla completa para pegar o escribir una letra. El boton Guardar esta arriba,
 * asi que el teclado nunca lo tapa.
 */
@Composable
fun LyricsPasteScreen(
    initial: String,
    title: String,
    onClose: () -> Unit,
    onSave: (String) -> Unit
) {
    // el cursor empieza arriba, para editar letras largas desde el principio
    var tf by remember { mutableStateOf(TextFieldValue(initial)) }
    val text = tf.text
    val ok = text.isNotBlank()
    val hasTimes = remember(initial) { Regex("\\[\\d{1,3}:\\d{2}").containsMatchIn(initial) }
    val focus = remember { FocusRequester() }
    val shape = RoundedCornerShape(16.dp)
    BackHandler { onClose() }
    LaunchedEffect(Unit) {
        delay(250L)
        try {
            focus.requestFocus()
        } catch (e: Exception) {
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(R.drawable.ic_close, "Cerrar", onClose)
            Text(
                title,
                color = C.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            val bg: Brush = if (ok) Brush.linearGradient(listOf(C.Acc, C.Acc2)) else SolidColor(C.Line)
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable(enabled = ok) { onSave(text) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("Guardar", color = if (ok) C.Bg else C.Mute, fontWeight = FontWeight.Bold)
            }
        }
        if (hasTimes) {
            Text(
                "Los tiempos como [01:23.45] hacen que la letra baje sola: déjalos como están.",
                color = C.Mute,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(shape)
                .background(C.Panel)
                .border(1.dp, C.Line, shape)
                .padding(14.dp)
        ) {
            if (text.isEmpty()) {
                Text("Pega aquí la letra…", color = C.Mute, fontSize = 16.sp)
            }
            BasicTextField(
                value = tf,
                onValueChange = { tf = it },
                textStyle = TextStyle(color = C.Text, fontSize = 17.sp),
                cursorBrush = SolidColor(C.Acc),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focus)
            )
        }
    }
}

/**
 * Cola de reproduccion: lo que viene, con la que suena marcada.
 * Tocar una cancion salta a ella; las flechas la suben o bajan; la X la quita.
 */
@Composable
fun QueueScreen(holder: PlayerHolder, byId: Map<Long, Song>, onClose: () -> Unit) {
    BackHandler { onClose() }
    val ids = holder.queueIds
    val cur = holder.queueIndex
    val state = rememberLazyListState()
    val shape = RoundedCornerShape(16.dp)
    LaunchedEffect(Unit) {
        if (cur > 1) state.scrollToItem(cur - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(R.drawable.ic_close, "Cerrar", onClose)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text("Cola de reproducción", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(plural(ids.size, "canción", "canciones"), color = C.Mute, fontSize = 12.sp)
            }
        }
        if (ids.isEmpty()) {
            EmptyCard(
                "La cola está vacía",
                "Reproduce una canción o agrega algunas con «Reproducir a continuación».",
                Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = state,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(ids, key = { i, id -> "$i-$id" }) { i, id ->
                    val s = byId[id]
                    val isCur = i == cur
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(C.Panel)
                            .border(1.dp, if (isCur) C.Acc else Color.Transparent, shape)
                            .clickable { holder.jumpTo(i) }
                            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (s != null) {
                            Cover(s, 46.dp)
                        } else {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(C.Line)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s?.title ?: "Canción no disponible",
                                color = if (isCur) C.Acc else C.Text,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (isCur) "Sonando ahora" else (s?.artist ?: ""),
                                color = C.Mute,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (i > 0) {
                            IconBtn(R.drawable.ic_arrow_up, "Subir", { holder.moveItem(i, i - 1) }, tint = C.Mute, size = 36.dp, iconSize = 20.dp)
                        }
                        if (i < ids.size - 1) {
                            IconBtn(R.drawable.ic_arrow_down, "Bajar", { holder.moveItem(i, i + 1) }, tint = C.Mute, size = 36.dp, iconSize = 20.dp)
                        }
                        IconBtn(R.drawable.ic_close, "Quitar de la cola", { holder.removeAt(i) }, tint = C.Mute, size = 36.dp, iconSize = 20.dp)
                    }
                }
            }
        }
    }
}
