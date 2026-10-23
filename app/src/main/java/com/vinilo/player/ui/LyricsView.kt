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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinilo.player.PlayerHolder
import com.vinilo.player.R
import com.vinilo.player.data.Lyrics
import com.vinilo.player.data.Song
import kotlinx.coroutines.delay

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

/** Pantalla de letras: cabecera + letra (o "sin letras"). */
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
            lyr.synced -> FollowLyrics(
                holder, lyr,
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

/** Una linea de letra: siempre el mismo estilo, sin resaltados. */
@Composable
private fun LyricText(text: String, plain: Boolean) {
    if (text.isBlank()) {
        if (plain) {
            Spacer(Modifier.height(18.dp))
        } else {
            Text(
                "♪",
                color = C.Text.copy(alpha = 0.4f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp)
            )
        }
    } else {
        Text(
            text,
            color = C.Text.copy(alpha = 0.92f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 31.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp)
        )
    }
}

/**
 * Letra con tiempos: se ve normal (sin resaltado) y la lista baja sola para
 * seguir por donde va cantando. Tambien se puede mover con el dedo.
 */
@Composable
private fun FollowLyrics(holder: PlayerHolder, lyr: Lyrics, modifier: Modifier) {
    val lines = lyr.lines
    var pos by remember { mutableLongStateOf(holder.positionMs) }
    LaunchedEffect(holder.isPlaying) {
        while (holder.isPlaying) {
            pos = holder.livePosition()
            delay(250L)
        }
    }

    // numero de la linea que se esta cantando (-1 = todavia no empieza)
    val idx by remember(lines) {
        derivedStateOf {
            val p = (if (holder.isPlaying) pos else holder.positionMs) + 150L
            var found = -1
            for (i in lines.indices) {
                if (lines[i].timeMs <= p) found = i else break
            }
            found
        }
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
        itemsIndexed(lines) { _, line -> LyricText(line.text, false) }
    }
}

/** Letra sin tiempos: se ve igual y se mueve con el dedo. */
@Composable
private fun PlainLyrics(lyr: Lyrics, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(top = 8.dp, bottom = 220.dp)) {
        itemsIndexed(lyr.lines) { _, l -> LyricText(l.text, true) }
    }
}
