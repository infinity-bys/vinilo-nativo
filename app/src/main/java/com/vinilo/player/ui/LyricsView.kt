package com.vinilo.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinilo.player.PlayerHolder
import com.vinilo.player.R
import com.vinilo.player.data.LyricLine
import com.vinilo.player.data.Lyrics
import com.vinilo.player.data.LyricsParser
import com.vinilo.player.data.Song
import kotlin.math.max
import kotlin.math.min

/** La letra cargada para una cancion (id = de que cancion es). */
class LyricsSlot(val id: Long, val lyrics: Lyrics?)

/** Todo lo que la pantalla de letras necesita del exterior. */
class LyricsUi(
    val lyrics: Lyrics?,
    val busy: Boolean,
    val onAdd: () -> Unit,
    val onSearch: () -> Unit,
    val onEdit: () -> Unit,
    val onSaveSynced: (String) -> Unit
)

/** Pantalla de letras: cabecera + letra (sincronizada, simple o "sin letras"). */
@Composable
fun LyricsPanel(holder: PlayerHolder, ui: LyricsUi, song: Song?, look: Look, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (song != null) {
                Cover(song, 52.dp, shape = CircleShape)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (holder.title.isNotEmpty()) holder.title else song?.title ?: "",
                    color = C.Text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (holder.artist.isNotEmpty()) holder.artist else song?.artist ?: "",
                    color = C.Mute,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (ui.lyrics != null) {
                IconBtn(R.drawable.ic_edit, "Cambiar letra", ui.onEdit, tint = C.Mute)
            }
        }
        val lyr = ui.lyrics
        var syncing by remember { mutableStateOf(false) }
        LaunchedEffect(song?.id) { syncing = false }

        // Letra sin tiempos: se avisa y se ofrecen las dos formas de sincronizarla de verdad
        if (lyr != null && !lyr.synced && !syncing) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sin tiempos", color = C.Mute, fontSize = 12.sp)
                ChipText("Sincronizar tocando", look) { syncing = true }
                ChipText("Buscar sincronizada", look, ui.onSearch)
            }
        }
        when {
            lyr == null && ui.busy -> {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Buscando letra…", color = C.Mute, fontSize = 16.sp)
                }
            }
            lyr == null -> NoLyrics(
                ui, look,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            syncing && !lyr.synced -> SyncTapper(
                holder, lyr, look,
                { raw ->
                    syncing = false
                    ui.onSaveSynced(raw)
                },
                { syncing = false },
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            lyr.synced -> SyncedLyrics(
                holder, lyr, look, true,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            else -> PlainLyrics(
                lyr, look,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ChipText(text: String, look: Look, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        color = look.acc,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, look.acc, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun PillBtn(text: String, filled: Boolean, look: Look, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val bg: Brush = if (filled) Brush.linearGradient(listOf(look.acc, look.acc2)) else SolidColor(Color.Transparent)
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (filled) Color.Transparent else C.Line, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (filled) C.Bg else C.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

/**
 * Sincronizar a mano: se reproduce la cancion y se toca el boton justo cuando empieza
 * cada linea. Al terminar se guarda como letra sincronizada exacta.
 */
@Composable
private fun SyncTapper(
    holder: PlayerHolder,
    lyr: Lyrics,
    look: Look,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier
) {
    val texts = remember(lyr) { lyr.lines.map { it.text.trim() }.filter { it.isNotEmpty() } }
    var marks by remember(lyr) { mutableStateOf(listOf<Long>()) }
    val done = marks.size
    val state = rememberLazyListState()
    LaunchedEffect(done) {
        if (done < texts.size) {
            val vh = state.layoutInfo.viewportSize.height
            state.animateScrollToItem(done, -(vh * 0.2f).toInt())
        }
    }
    Column(modifier) {
        Text(
            if (done >= texts.size) {
                "¡Listo! Guarda la sincronización."
            } else {
                "Reproduce la canción y toca el botón justo cuando empiece a cantarse la línea resaltada."
            },
            color = C.Mute,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn(
            state = state,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            itemsIndexed(texts) { i, t ->
                LyricRow(t, if (i == done) 1 else if (i < done) 2 else 0, { 0f }, look) {}
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PillBtn("Cancelar", false, look, onCancel, Modifier.weight(1f))
            PillBtn(
                "Deshacer", false, look,
                { if (marks.isNotEmpty()) marks = marks.dropLast(1) },
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        PillBtn(
            if (done < texts.size) "Marcar: empieza esta línea" else "Guardar sincronización",
            true, look,
            {
                if (done < texts.size) {
                    // se resta un poco porque el dedo reacciona despues de oir la voz
                    val t = (holder.livePosition() - 250L).coerceAtLeast(0L)
                    val prev = marks.lastOrNull() ?: 0L
                    marks = marks + max(t, prev + 100L)
                } else {
                    onSave(LyricsParser.toLrc(texts, marks))
                }
            },
            Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NoLyrics(ui: LyricsUi, look: Look, modifier: Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_lyrics), null,
            tint = C.Mute.copy(alpha = 0.35f),
            modifier = Modifier.size(88.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Sin letras", color = C.Mute, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            ActionCircle(R.drawable.ic_add, "Agregar", false, look, ui.onAdd)
            ActionCircle(R.drawable.ic_search, "Buscar", true, look, ui.onSearch)
        }
    }
}

@Composable
private fun ActionCircle(icon: Int, label: String, filled: Boolean, look: Look, onClick: () -> Unit) {
    val bg: Brush = if (filled) Brush.linearGradient(listOf(look.acc, look.acc2)) else SolidColor(Color.Transparent)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(bg)
                .border(1.5.dp, if (filled) Color.Transparent else C.Mute, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(icon), label,
                tint = if (filled) C.Bg else C.Text,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = C.Mute, fontSize = 13.sp)
    }
}

/**
 * Letra sincronizada: la linea que suena se resalta, las palabras se van
 * iluminando al ritmo del habla y la lista se desplaza sola. Tocar una linea salta ahi.
 */
@Composable
private fun SyncedLyrics(holder: PlayerHolder, lyr: Lyrics, look: Look, canSeek: Boolean, modifier: Modifier) {
    val lines = lyr.lines
    var pos by remember { mutableLongStateOf(holder.positionMs) }
    // mientras suena, la posicion se lee en cada fotograma
    LaunchedEffect(holder.isPlaying) {
        while (holder.isPlaying) {
            withFrameNanos { }
            pos = holder.livePosition()
        }
    }

    fun nowMs(): Long = if (holder.isPlaying) pos else holder.positionMs

    // numero de la linea que suena ahora (-1 = todavia no empieza)
    val idx by remember(lines) {
        derivedStateOf {
            val p = nowMs() + 150L
            var found = -1
            for (i in lines.indices) {
                if (lines[i].timeMs <= p) found = i else break
            }
            found
        }
    }

    // cuanto de la linea ya se ha cantado (0..1), repartido segun la duracion estimada
    fun wordFrac(i: Int): Float {
        val start = lines[i].timeMs
        val next = if (i + 1 < lines.size) lines[i + 1].timeMs else start + 6000L
        val gap = max(1L, next - start)
        val chars = lines[i].text.length
        // Normalmente una linea dura hasta que empieza la siguiente: se usa casi todo ese
        // tiempo. Solo si hay un silencio largo (instrumental) se limita al canto normal.
        val natural = max(1800L, chars * 150L)
        val dur = if (gap <= natural * 1.8f) gap * 0.95f else natural * 1.1f
        return ((nowMs() - start).toFloat() / dur).coerceIn(0f, 1f)
    }

    val state = rememberLazyListState()
    LaunchedEffect(idx) {
        if (idx >= 0 && !state.isScrollInProgress) {
            val vh = state.layoutInfo.viewportSize.height
            state.animateScrollToItem(idx, -(vh * 0.28f).toInt())
        }
    }

    LazyColumn(
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 220.dp)
    ) {
        itemsIndexed(lines) { i, line ->
            val mode = if (i == idx) 1 else if (i < idx) 2 else 0
            LyricRow(line.text, mode, { wordFrac(i) }, look) {
                if (canSeek && line.timeMs >= 0L) holder.seekTo(line.timeMs)
            }
        }
    }
}

/** mode: 0 = por cantar, 1 = sonando ahora, 2 = ya cantada, 3 = letra sin tiempos (quieta) */
@Composable
private fun LyricRow(text: String, mode: Int, frac: () -> Float, look: Look, onClick: () -> Unit) {
    val shown = if (text.isBlank()) "♪" else text
    val words = remember(shown) { shown.split(" ").filter { it.isNotEmpty() } }
    val sung by remember(words, mode) {
        derivedStateOf {
            if (mode != 1) {
                0
            } else {
                val f = frac()
                val total = max(1, words.sumOf { it.length + 1 })
                var cum = 0
                var count = 0
                for (w in words) {
                    if (cum.toFloat() / total < f) count++ else break
                    cum += w.length + 1
                }
                count
            }
        }
    }
    val styled: AnnotatedString = if (mode == 1) {
        buildAnnotatedString {
            words.forEachIndexed { i, w ->
                withStyle(SpanStyle(color = if (i < sung) look.acc else C.Text.copy(alpha = 0.6f))) {
                    append(w)
                }
                if (i < words.size - 1) append(" ")
            }
        }
    } else {
        AnnotatedString(shown)
    }
    Text(
        styled,
        color = C.Text.copy(alpha = if (mode == 3) 0.85f else if (mode == 2) 0.5f else 0.38f),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 31.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp)
    )
}

@Composable
private fun PlainLyrics(lyr: Lyrics, look: Look, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(top = 8.dp, bottom = 220.dp)) {
        itemsIndexed(lyr.lines) { _, l ->
            LyricRow(l.text, 3, { 0f }, look) {}
        }
    }
}
