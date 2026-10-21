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
import com.vinilo.player.data.Lyrics
import com.vinilo.player.data.Song
import kotlin.math.ceil
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
    val onEdit: () -> Unit
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
            lyr.synced -> SyncedLyrics(
                holder, lyr, look,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            else -> PlainLyrics(
                lyr,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
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
private fun SyncedLyrics(holder: PlayerHolder, lyr: Lyrics, look: Look, modifier: Modifier) {
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
        val chars = lines[i].text.length
        val dur = min(next - start, max(1500L, chars * 110L)).coerceAtLeast(300L)
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
                if (line.timeMs >= 0L) holder.seekTo(line.timeMs)
            }
        }
    }
}

/** mode: 0 = por cantar, 1 = sonando ahora, 2 = ya cantada */
@Composable
private fun LyricRow(text: String, mode: Int, frac: () -> Float, look: Look, onClick: () -> Unit) {
    val shown = if (text.isBlank()) "♪" else text
    val words = remember(shown) { shown.split(" ").filter { it.isNotEmpty() } }
    val sung by remember(words, mode) {
        derivedStateOf {
            if (mode != 1) 0 else ceil(frac() * words.size).toInt().coerceIn(0, words.size)
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
        color = C.Text.copy(alpha = if (mode == 2) 0.5f else 0.38f),
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
private fun PlainLyrics(lyr: Lyrics, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(top = 4.dp, bottom = 200.dp)) {
        item {
            Text(
                "Letra sin sincronizar",
                color = C.Mute,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        itemsIndexed(lyr.lines) { _, l ->
            Text(
                if (l.text.isBlank()) " " else l.text,
                color = C.Text.copy(alpha = 0.88f),
                fontSize = 19.sp,
                lineHeight = 27.sp,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }
    }
}
