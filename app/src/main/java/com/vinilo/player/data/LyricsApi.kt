package com.vinilo.player.data

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** Una letra encontrada en internet. */
class LrcResult(
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val synced: String?,
    val plain: String?
) {
    val hasSynced: Boolean get() = !synced.isNullOrBlank()

    /** El texto a guardar: la version sincronizada si existe, si no la simple. */
    val raw: String?
        get() = if (hasSynced) synced else if (!plain.isNullOrBlank()) plain else null
}

/**
 * Busca letras en LRCLIB (lrclib.net), una base de datos abierta con letras sincronizadas.
 * Hay que llamarlo desde un hilo secundario, nunca desde la pantalla.
 */
object LyricsApi {
    private const val BASE = "https://lrclib.net/api/search"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "Vinilo/0.2 (reproductor de musica para Android)")
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } catch (e: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun parse(json: String): List<LrcResult> {
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<LrcResult>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val synced = if (o.isNull("syncedLyrics")) null else o.optString("syncedLyrics")
                val plain = if (o.isNull("plainLyrics")) null else o.optString("plainLyrics")
                val r = LrcResult(
                    o.optString("trackName", ""),
                    o.optString("artistName", ""),
                    o.optString("albumName", ""),
                    o.optDouble("duration", 0.0).toInt(),
                    synced,
                    plain
                )
                if (r.raw != null) out.add(r)
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Busqueda libre (la que escribe el usuario). */
    fun search(query: String): List<LrcResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val body = get("$BASE?q=" + enc(q)) ?: return emptyList()
        return parse(body)
    }

    /** Busqueda por titulo y artista de la cancion. */
    fun searchTrack(title: String, artist: String): List<LrcResult> {
        var url = "$BASE?track_name=" + enc(title)
        if (artist.isNotBlank()) url += "&artist_name=" + enc(artist)
        var res = get(url)?.let { parse(it) } ?: emptyList()
        if (res.isEmpty()) res = search((title + " " + artist).trim())
        return res
    }

    /** La mejor letra para esta cancion: prefiere sincronizada y de duracion parecida. */
    fun best(song: Song): String? {
        val list = searchTrack(song.title, song.artist)
        if (list.isEmpty()) return null
        var bestRes: LrcResult? = null
        var bestScore = Long.MAX_VALUE
        for (r in list) {
            val diff = if (song.durationMs > 0L && r.durationSec > 0) {
                abs(r.durationSec * 1000L - song.durationMs)
            } else {
                0L
            }
            if (song.durationMs > 0L && r.durationSec > 0 && diff > 8000L) continue
            val score = diff + (if (r.hasSynced) 0L else 4000L)
            if (score < bestScore) {
                bestScore = score
                bestRes = r
            }
        }
        return bestRes?.raw
    }
}
