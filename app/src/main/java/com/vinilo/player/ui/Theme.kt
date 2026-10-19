package com.vinilo.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Los mismos colores del dise\u00f1o original. */
object C {
    val Bg = Color(0xFF1A1424)
    val Panel = Color(0xFF251D33)
    val Line = Color(0xFF3A2F4D)
    val Text = Color(0xFFF1E9DC)
    val Mute = Color(0xFFA79BB8)
    val Acc = Color(0xFFF2A53A)
    val Acc2 = Color(0xFFE8644F)
}

/** Colores del reproductor: por defecto los de siempre; cambian con la carátula. */
data class Look(
    val acc: Color = C.Acc,
    val acc2: Color = C.Acc2,
    val soft: Color = C.Text,
    val top: Color = C.Bg,
    val bottom: Color = C.Bg
)

@Composable
fun VinyloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Acc,
            onPrimary = C.Bg,
            secondary = C.Acc2,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Panel,
            onSurface = C.Text,
            outline = C.Line
        ),
        content = content
    )
}
