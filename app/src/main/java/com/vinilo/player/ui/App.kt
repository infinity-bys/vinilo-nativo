package com.vinilo.player.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vinilo.player.PlayerHolder
import com.vinilo.player.R
import com.vinilo.player.data.CoverCache
import com.vinilo.player.data.Library
import com.vinilo.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun hasAudioPermission(context: Context): Boolean {
    val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}

private fun permissionsToAsk(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

@Composable
fun VinyloApp(holder: PlayerHolder) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var scanned by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAudioPermission(context)
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(permissionsToAsk())
    }
    LaunchedEffect(holder.resumeTick) {
        granted = hasAudioPermission(context)
    }
    LaunchedEffect(granted, holder.resumeTick) {
        if (granted) {
            songs = withContext(Dispatchers.IO) { Library.scan(context) }
            scanned = true
        }
    }

    val q = query.trim().lowercase()
    val visible = remember(songs, q) {
        songs.filter { s ->
            s.junk.isEmpty() && (q.isEmpty() || (s.title + " " + s.artist).lowercase().contains(q))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Header(count = visible.size, showCount = granted && scanned, query = query, onQuery = { query = it })

        if (!granted) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text(
                        "Vinilo necesita permiso para leer la m\u00fasica de tu tel\u00e9fono.",
                        color = C.Mute,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(26.dp))
                            .background(Brush.linearGradient(listOf(C.Acc, C.Acc2)))
                            .clickable { launcher.launch(permissionsToAsk()) }
                            .padding(horizontal = 28.dp, vertical = 14.dp)
                    ) {
                        Text("Dar permiso", color = C.Bg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        } else if (!scanned) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Buscando m\u00fasica\u2026", color = C.Mute, fontSize = 15.sp)
            }
        } else if (visible.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (q.isEmpty()) "No encontramos m\u00fasica en tu tel\u00e9fono." else "Sin resultados.",
                    color = C.Mute,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(visible, key = { _, s -> s.id }) { index, s ->
                    SongRow(song = s, isCurrent = s.id == holder.currentId) {
                        holder.playList(visible, index)
                    }
                }
            }
        }

        if (holder.currentId != -1L) {
            MiniPlayer(holder = holder, songs = songs)
        }
    }
}

@Composable
private fun Header(count: Int, showCount: Boolean, query: String, onQuery: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Vinilo",
            color = C.Text,
            fontSize = 34.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        SearchBox(query, onQuery)
        Spacer(Modifier.height(10.dp))
        Text(
            if (showCount) "$count canciones" else " ",
            color = C.Mute,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = TextStyle(color = C.Text, fontSize = 16.sp),
        cursorBrush = SolidColor(C.Acc),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(C.Panel)
                    .border(1.dp, C.Line, shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.ic_search), null, tint = C.Mute, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Buscar canci\u00f3n", color = C.Mute, fontSize = 16.sp)
                    inner()
                }
            }
        }
    )
}

@Composable
private fun SongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Panel)
            .border(1.dp, if (isCurrent) C.Acc else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(song, 54.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) C.Acc else C.Text,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (song.artist.isBlank()) formatSize(song.size) else song.artist,
                color = C.Mute,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / 1048576.0
    return String.format("%.1f MB", mb)
}

@Composable
fun Cover(song: Song, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(CoverCache.get(song.id), song.id) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { CoverCache.load(context, song) }
        }
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(C.Line),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(painterResource(R.drawable.ic_note), null, tint = C.Mute, modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
private fun MiniPlayer(holder: PlayerHolder, songs: List<Song>) {
    val song = songs.firstOrNull { it.id == holder.currentId }
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Panel)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (song != null) Cover(song, 48.dp) else Box(Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                holder.title,
                color = C.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (holder.artist.isBlank()) "Vinilo" else holder.artist,
                color = C.Mute,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { holder.prev() }) {
            Icon(painterResource(R.drawable.ic_prev), "Anterior", tint = C.Text, modifier = Modifier.size(24.dp))
        }
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(C.Acc, C.Acc2)))
                .clickable { holder.toggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(if (holder.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                if (holder.isPlaying) "Pausa" else "Reproducir",
                tint = C.Bg,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = { holder.next() }) {
            Icon(painterResource(R.drawable.ic_next), "Siguiente", tint = C.Text, modifier = Modifier.size(24.dp))
        }
    }
}
