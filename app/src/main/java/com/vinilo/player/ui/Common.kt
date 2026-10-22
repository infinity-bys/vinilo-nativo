package com.vinilo.player.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinilo.player.R
import com.vinilo.player.data.CoverCache
import com.vinilo.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun fmtTime(ms: Long): String {
    val t = (ms / 1000L).coerceAtLeast(0L)
    return (t / 60L).toString() + ":" + (t % 60L).toString().padStart(2, '0')
}

fun fmtSize(bytes: Long): String = String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)

fun plural(n: Int, one: String, many: String): String = "$n " + (if (n == 1) one else many)

/** Caratula de una cancion (o una nota musical si no tiene). */
@Composable
fun Cover(song: Song, size: Dp, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(14.dp)) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(CoverCache.get(song.id), song.id) {
        // al cambiar de cancion hay que soltar la imagen anterior (si no, se queda la vieja)
        value = CoverCache.get(song.id)
        if (value == null) {
            value = withContext(Dispatchers.IO) { CoverCache.load(context, song) }
        }
    }
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(C.Line),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(painterResource(R.drawable.ic_note), null, tint = C.Mute, modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
fun IconBtn(
    res: Int,
    desc: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = C.Text,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    bg: Color = Color.Transparent
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(res), desc, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit, icon: Int? = null) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(if (on) C.Acc else Color.Transparent)
            .border(1.dp, if (on) C.Acc else C.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = if (on) C.Bg else C.Mute, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = if (on) C.Bg else C.Mute,
            fontSize = 13.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** Interruptor con el estilo del diseno original. */
@Composable
fun Sw(on: Boolean) {
    val x by animateDpAsState(if (on) 18.dp else 0.dp, label = "sw")
    Box(
        Modifier
            .size(46.dp, 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) C.Acc else C.Line)
    ) {
        Box(
            Modifier
                .offset(x = 3.dp + x, y = 3.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(C.Text)
        )
    }
}

/** Fila de las hojas (ajustes, menu, ordenar...). */
@Composable
fun SheetRow(
    label: String,
    onClick: () -> Unit,
    icon: Int? = null,
    value: String? = null,
    selected: Boolean = false,
    danger: Boolean = false,
    showCheck: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = C.Acc, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (danger) C.Acc2 else if (selected) C.Acc else C.Text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (value != null) {
            Text(value, color = C.Mute, fontSize = 14.sp, maxLines = 1)
        }
        if (trailing != null) trailing()
        if (selected && showCheck) {
            Icon(painterResource(R.drawable.ic_check), null, tint = C.Acc, modifier = Modifier.size(22.dp))
        }
    }
}

/** Mensaje de "no hay nada aqui" con borde. */
@Composable
fun EmptyCard(title: String, body: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(2.dp, C.Line, shape)
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = C.Text,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(body, color = C.Mute, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
