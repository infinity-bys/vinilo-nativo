package com.vinilo.player.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val durationMs: Long,
    val size: Long,
    val added: Long,
    val displayName: String,
    val path: String,
    val mime: String,
    val isMusic: Boolean,
    /** Si no esta vacio, es un audio que no es musica (nota de voz, grabacion...). */
    val junk: String
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

fun Song.toMediaItem(): MediaItem {
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(if (artist.isBlank()) null else artist)
        .setArtworkUri(Uri.parse("content://media/external/audio/albumart/" + albumId))
        .build()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(meta)
        .build()
}

object Library {

    private val waName = Regex("^(AUD|PTT)-\\d{8}-WA\\d+", RegexOption.IGNORE_CASE)
    private val waPath = Regex("whatsapp (voice notes|audio)", RegexOption.IGNORE_CASE)
    private val recName = Regex("^(voice|recording|rec|grabaci[o\u00f3]n|nota de voz|voz)[\\s_-]?\\d", RegexOption.IGNORE_CASE)
    private val recPath = Regex(
        "(recordings?|voice ?(recorder|memos?)|sound.?recorder|call.?recordings?|grabaciones|notas de voz)",
        RegexOption.IGNORE_CASE
    )
    private val voiceMime = Regex("amr|3gpp", RegexOption.IGNORE_CASE)
    private val extension = Regex("\\.[^.]+$")

    private val cleanBracket = Regex(
        "\\s*[(\\[][^)\\]]*?(?<![a-z])(mp3|audio|\\d{2,3}\\s?k(bps)?|official|oficial|lyrics?|letra|video|v\u00eddeo|hd|hq)(?![a-z])[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE
    )
    private val trailKbps = Regex("[\\s-]*\\b\\d{2,3}\\s?k(bps)?\\s*$", RegexOption.IGNORE_CASE)
    private val multiSpace = Regex("\\s{2,}")
    private val dash = Regex("\\s[-\u2013]\\s")

    /** Quita basura de los nombres: (Official Video), _, 320kbps, etc. */
    fun clean(n: String): String {
        val r = n.replace(cleanBracket, " ").replace('_', ' ').replace(trailKbps, "")
            .replace(multiSpace, " ").trim()
        return if (r.isEmpty()) n else r
    }

    /** Devuelve (titulo, artista) ya limpios. Si no hay artista, intenta separar "Artista - Titulo". */
    fun display(title: String, artist: String): Pair<String, String> {
        val t = clean(title)
        if (artist.isBlank()) {
            val parts = t.split(dash)
            if (parts.size > 1) {
                return Pair(parts.drop(1).joinToString(" - ").trim(), parts[0].trim())
            }
        }
        return Pair(t, artist)
    }

    /** Devuelve el motivo por el que un audio no es musica, o "" si es musica. */
    private fun junkReason(name: String, path: String, mime: String, isMusic: Boolean, durationMs: Long): String {
        if (waName.containsMatchIn(name) || waPath.containsMatchIn(path)) return "Nota de voz o audio de WhatsApp"
        if (recName.containsMatchIn(name) || recPath.containsMatchIn(path)) return "Grabaci\u00f3n"
        if (voiceMime.containsMatchIn(mime)) return "Audio de voz"
        if (!isMusic && durationMs > 0 && durationMs < 45000) return "Audio muy corto"
        return ""
    }

    @Suppress("DEPRECATION")
    fun scan(context: Context): List<Song> {
        val list = ArrayList<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA
        )
        val selection = MediaStore.Audio.Media.IS_RINGTONE + " = 0 AND " +
            MediaStore.Audio.Media.IS_ALARM + " = 0 AND " +
            MediaStore.Audio.Media.IS_NOTIFICATION + " = 0"

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null
        ) ?: return list

        cursor.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iMusic = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (c.moveToNext()) {
                val name = c.getString(iName) ?: ""
                val rawTitle = c.getString(iTitle)
                val rawArtist = c.getString(iArtist)
                val path = c.getString(iData) ?: ""
                val mime = c.getString(iMime) ?: ""
                val isMusic = c.getInt(iMusic) != 0
                val duration = c.getLong(iDur)
                val title0 = if (rawTitle.isNullOrBlank() || rawTitle == "<unknown>") name.replace(extension, "") else rawTitle
                val artist0 = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "" else rawArtist
                val (title, artist) = display(title0, artist0)
                list.add(
                    Song(
                        id = c.getLong(iId),
                        title = title,
                        artist = artist,
                        albumId = c.getLong(iAlbum),
                        durationMs = duration,
                        size = c.getLong(iSize),
                        added = c.getLong(iAdded),
                        displayName = name,
                        path = path,
                        mime = mime,
                        isMusic = isMusic,
                        junk = junkReason(name, path, mime, isMusic, duration)
                    )
                )
            }
        }
        return list.sortedBy { it.title.lowercase() }
    }
}

/** Guarda en memoria las caratulas ya leidas para que la lista no vaya lenta. */
object CoverCache {
    private val cache = LruCache<Long, ImageBitmap>(150)
    private val big = LruCache<Long, Bitmap>(6)
    private val missing = HashSet<Long>()

    fun get(id: Long): ImageBitmap? = cache.get(id)

    fun load(context: Context, song: Song): ImageBitmap? {
        cache.get(song.id)?.let { return it }
        synchronized(missing) {
            if (missing.contains(song.id)) return null
        }
        return try {
            val bmp = context.contentResolver.loadThumbnail(song.uri, Size(192, 192), null)
            val image = bmp.asImageBitmap()
            cache.put(song.id, image)
            image
        } catch (e: Exception) {
            synchronized(missing) { missing.add(song.id) }
            null
        }
    }

    /** Caratula grande (512 px) para el disco y para sacar el color. */
    fun loadBig(context: Context, song: Song): Bitmap? {
        big.get(song.id)?.let { return it }
        synchronized(missing) {
            if (missing.contains(song.id)) return null
        }
        return try {
            val bmp = context.contentResolver.loadThumbnail(song.uri, Size(512, 512), null)
            big.put(song.id, bmp)
            bmp
        } catch (e: Exception) {
            synchronized(missing) { missing.add(song.id) }
            null
        }
    }
}
