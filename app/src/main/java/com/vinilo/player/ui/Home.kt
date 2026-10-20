package com.vinilo.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinilo.player.PlayerHolder
import com.vinilo.player.R
import com.vinilo.player.data.Playlist
import com.vinilo.player.data.Song

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(C.Panel)
            .border(1.dp, C.Line, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = C.Mute, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Buscar canción", color = C.Mute, fontSize = 16.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(C.Acc),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                painterResource(R.drawable.ic_close), "Borrar",
                tint = C.Mute,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onQuery("") }
            )
        }
    }
}

@Composable
private fun Radio(on: Boolean) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (on) C.Acc else Color.Transparent)
            .border(2.dp, if (on) C.Acc else C.Mute, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (on) {
            Icon(painterResource(R.drawable.ic_check), null, tint = C.Bg, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    selMode: Boolean,
    selected: Boolean,
    playing: Boolean,
    position: () -> Long,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Panel)
            .border(1.dp, if (isCurrent || selected) C.Acc else Color.Transparent, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selMode) {
            Radio(selected)
            Spacer(Modifier.width(12.dp))
        }
        Cover(song, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) C.Acc else C.Text,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (song.artist.isNotBlank()) song.artist else fmtSize(song.size),
                color = C.Mute,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent && !selMode) {
            Spacer(Modifier.width(8.dp))
            NowBars(playing, C.Acc, position, Modifier.padding(end = 6.dp))
        }
    }
}

@Composable
private fun PlaylistRow(p: Playlist, byId: Map<Long, Song>, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val count = p.ids.count { byId.containsKey(it) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Panel)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistThumb(p, byId, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = C.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(plural(count, "canción", "canciones"), color = C.Mute, fontSize = 12.sp)
        }
    }
}

@Composable
fun HomeScreen(
    holder: PlayerHolder,
    granted: Boolean,
    scanned: Boolean,
    totalVisible: Int,
    query: String,
    onQuery: (String) -> Unit,
    filter: Int,
    onFilter: (Int) -> Unit,
    plSel: Playlist?,
    onPlBack: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    shown: List<Song>,
    byId: Map<Long, Song>,
    sel: Set<Long>?,
    onRowClick: (Song, Int) -> Unit,
    onRowLong: (Song) -> Unit,
    onSort: () -> Unit,
    onSettings: () -> Unit,
    onNewList: () -> Unit,
    onRename: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    onSelClose: () -> Unit,
    onSelAll: () -> Unit,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val store = holder.store
    val searching = query.isNotBlank()

    Column(modifier) {
        // ----- Parte de arriba: cabecera normal o barra de seleccion -----
        if (sel != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(R.drawable.ic_close, "Salir de la selección", onSelClose)
                Text(
                    plural(sel.size, "seleccionado", "seleccionados"),
                    color = C.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                Text(
                    "Todas",
                    color = C.Acc,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onSelAll)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Vinilo",
                    color = C.Text,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    modifier = Modifier.weight(1f)
                )
                IconBtn(R.drawable.ic_sort, "Ordenar canciones", onSort, tint = C.Mute)
                IconBtn(
                    R.drawable.ic_tune, "Ajustes", onSettings,
                    tint = if (holder.sleepEndAt > 0L || holder.sleepEot) C.Acc else C.Mute
                )
            }
            SearchBox(query, onQuery)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip("Todas", filter == 0, { onFilter(0) })
                Chip("Favoritas", filter == 1, { onFilter(1) }, icon = R.drawable.ic_heart_outline)
                Chip("Listas", filter == 2, { onFilter(2) })
            }
        }

        // ----- Contador -----
        val countText = when {
            !granted || !scanned -> ""
            plSel != null -> plural(shown.size, "canción", "canciones")
            filter == 2 -> plural(store.playlists.size, "lista", "listas")
            filter == 1 -> plural(shown.size, "favorita", "favoritas")
            searching -> "${shown.size} de $totalVisible canciones"
            else -> plural(totalVisible, "canción", "canciones")
        }
        if (countText.isNotEmpty() && sel == null) {
            Text(
                countText,
                color = C.Mute,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
            )
        }

        // ----- Contenido -----
        when {
            !granted -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text(
                            "Vinilo necesita permiso para leer la música de tu teléfono.",
                            color = C.Mute,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(26.dp))
                                .background(Brush.linearGradient(listOf(C.Acc, C.Acc2)))
                                .clickable(onClick = onGrant)
                                .padding(horizontal = 28.dp, vertical = 14.dp)
                        ) {
                            Text("Dar permiso", color = C.Bg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            !scanned -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Buscando música en tu teléfono…", color = C.Mute, fontSize = 15.sp)
                }
            }

            filter == 2 && plSel == null -> {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, C.Acc, RoundedCornerShape(14.dp))
                                .clickable(onClick = onNewList)
                                .padding(13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("＋ Nueva lista", color = C.Acc, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (store.playlists.isEmpty()) {
                        item {
                            EmptyCard(
                                "Aún no tienes listas",
                                "Crea una con «＋ Nueva lista», o mantén presionada una canción y toca «Agregar a»."
                            )
                        }
                    } else {
                        itemsIndexed(store.playlists, key = { _, p -> p.id }) { _, p ->
                            PlaylistRow(p, byId) { onOpenPlaylist(p) }
                        }
                    }
                }
            }

            else -> {
                if (plSel != null && sel == null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBtn(R.drawable.ic_chevron_left, "Volver a las listas", onPlBack)
                        Text(
                            plSel.name,
                            color = C.Text,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconBtn(R.drawable.ic_edit, "Renombrar", { onRename(plSel) }, tint = C.Mute)
                        IconBtn(R.drawable.ic_delete, "Eliminar lista", { onDelete(plSel) }, tint = C.Mute)
                    }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (shown.isEmpty()) {
                        item {
                            when {
                                searching -> Text(
                                    "Sin resultados",
                                    color = C.Mute,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    textAlign = TextAlign.Center
                                )
                                plSel != null -> EmptyCard(
                                    "Esta lista está vacía",
                                    "Mantén presionada una canción y toca «Agregar a»."
                                )
                                filter == 1 -> EmptyCard(
                                    "Aún no tienes favoritas",
                                    "Abre una canción y toca el ♡ para guardarla aquí."
                                )
                                else -> EmptyCard(
                                    "No encontramos música",
                                    "Las canciones de tu teléfono aparecerán aquí."
                                )
                            }
                        }
                    }
                    itemsIndexed(shown, key = { _, s -> s.id }) { i, s ->
                        SongRow(
                            song = s,
                            isCurrent = s.id == holder.currentId,
                            selMode = sel != null,
                            selected = sel != null && s.id in sel,
                            playing = s.id == holder.currentId && holder.isPlaying,
                            position = { holder.livePosition() },
                            onClick = { onRowClick(s, i) },
                            onLong = { onRowLong(s) }
                        )
                    }
                }
            }
        }
    }
}
