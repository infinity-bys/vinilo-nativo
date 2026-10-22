package com.vinilo.player.data

import android.content.Context
import java.io.File

/** Una linea de letra. timeMs = -1 cuando la letra no esta sincronizada. */
class LyricLine(val timeMs: Long, val text: String)

class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val estimated: Boolean = false)

/** Lee letras en formato LRC ([01:23.45] texto) o texto simple. */
object LyricsParser {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offsetTag = Regex("\\[offset:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    private val metaTag = Regex("^\\[[a-zA-Z]+:.*]$")

    fun parse(raw: String): Lyrics {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        val timed = ArrayList<LyricLine>()
        for (line in text.split('\n')) {
            val l = line.trim()
            val marks = stamp.findAll(l).toList()
            if (marks.isEmpty() || marks[0].range.first != 0) continue
            val content = l.substring(marks.last().range.last + 1).trim()
            for (m in marks) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val frac = m.groupValues[3]
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> (frac.toLongOrNull() ?: 0L) * 100L
                    2 -> (frac.toLongOrNull() ?: 0L) * 10L
                    else -> frac.toLongOrNull() ?: 0L
                }
                val ms = (min * 60_000L + sec * 1000L + fracMs - offset).coerceAtLeast(0L)
                timed.add(LyricLine(ms, content))
            }
        }
        if (timed.isNotEmpty()) {
            return Lyrics(timed.sortedBy { it.timeMs }, true)
        }

        // Texto simple: se quitan etiquetas como [ar:Artista] y los vacios de los extremos
        val plain = text.split('\n')
            .map { it.trimEnd() }
            .filter { !metaTag.matches(it.trim()) }
        val start = plain.indexOfFirst { it.isNotBlank() }
        val end = plain.indexOfLast { it.isNotBlank() }
        val lines = if (start < 0) emptyList() else plain.subList(start, end + 1)
        return Lyrics(lines.map { LyricLine(-1L, it) }, false)
    }
}

/** Guarda la letra de cada cancion en un archivo propio dentro de la app. */
object LyricsStore {
    private fun file(context: Context, id: Long): File {
        val dir = File(context.filesDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$id.txt")
    }

    fun load(context: Context, id: Long): Lyrics? {
        val f = file(context, id)
        if (!f.exists()) return null
        val l = try {
            LyricsParser.parse(f.readText())
        } catch (e: Exception) {
            return null
        }
        return if (l.lines.isEmpty()) null else l
    }

    fun save(context: Context, id: Long, raw: String) {
        try {
            file(context, id).writeText(raw)
        } catch (e: Exception) {
        }
    }

    fun delete(context: Context, id: Long) {
        try {
            file(context, id).delete()
        } catch (e: Exception) {
        }
    }
}
