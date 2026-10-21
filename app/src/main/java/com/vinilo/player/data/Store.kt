package com.vinilo.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

class Playlist(val id: String, val name: String, val ids: List<Long>)

/**
 * Todo lo que la app recuerda: favoritos, listas, canciones ocultas,
 * reproducciones, orden, modo, velocidad, ecualizador y la ultima cancion.
 * Es un unico objeto compartido por la pantalla y el servicio.
 */
class Store private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("vinilo_store", Context.MODE_PRIVATE)

    var favs by mutableStateOf(readSet("fav"))
        private set
    var hidden by mutableStateOf(readSet("hid"))
        private set
    /** Audios "no musica" que el usuario decidio mostrar. */
    var forced by mutableStateOf(readSet("show"))
        private set
    var plays by mutableStateOf(readPlays())
        private set
    var playlists by mutableStateOf(readPlaylists())
        private set
    var autoHide by mutableStateOf(sp.getBoolean("auto", true))
        private set
    var accentOn by mutableStateOf(sp.getBoolean("acc", false))
        private set
    var sortKey by mutableStateOf(sp.getString("sortk", "az") ?: "az")
        private set
    var sortDir by mutableStateOf(sp.getInt("sortd", 1))
        private set
    /** 0 = repetir todas, 1 = repetir una, 2 = aleatorio */
    var mode by mutableStateOf(sp.getInt("mode", 0).coerceIn(0, 2))
        private set
    var speed by mutableStateOf(sp.getFloat("speed", 1f))
        private set
    var eq by mutableStateOf(readEq())
        private set
    var eqOn by mutableStateOf(sp.getBoolean("eqOn", true))
        private set
    /** Refuerzo de graves, sonido envolvente y volumen extra: de 0 a 100 */
    var bass by mutableStateOf(sp.getInt("bass", 0))
        private set
    var virt by mutableStateOf(sp.getInt("virt", 0))
        private set
    var boost by mutableStateOf(sp.getInt("boost", 0))
        private set

    // ---------- Favoritos ----------
    fun toggleFav(id: Long) {
        favs = if (id in favs) favs - id else favs + id
        writeSet("fav", favs)
    }

    // ---------- Ocultas ----------
    fun hide(ids: Collection<Long>) {
        hidden = hidden + ids
        forced = forced - ids.toSet()
        writeSet("hid", hidden)
        writeSet("show", forced)
    }

    fun unhide(songs: List<Song>) {
        val ids = songs.map { it.id }.toSet()
        hidden = hidden - ids
        forced = forced + songs.filter { it.junk.isNotEmpty() }.map { it.id }
        writeSet("hid", hidden)
        writeSet("show", forced)
    }

    fun isHidden(s: Song): Boolean =
        s.id in hidden || (autoHide && s.junk.isNotEmpty() && s.id !in forced)

    fun changeAutoHide(v: Boolean) {
        autoHide = v
        sp.edit().putBoolean("auto", v).apply()
    }

    fun changeAccentOn(v: Boolean) {
        accentOn = v
        sp.edit().putBoolean("acc", v).apply()
    }

    // ---------- Reproducciones ----------
    fun addPlay(id: Long) {
        plays = plays + (id to ((plays[id] ?: 0) + 1))
        val o = JSONObject()
        for ((k, v) in plays) o.put(k.toString(), v)
        sp.edit().putString("plays", o.toString()).apply()
    }

    // ---------- Orden ----------
    fun sortBy(key: String) {
        if (sortKey == key) {
            sortDir = -sortDir
        } else {
            sortKey = key
            sortDir = Sorts.defaultDir(key)
        }
        sp.edit().putString("sortk", sortKey).putInt("sortd", sortDir).apply()
    }

    // ---------- Modo, velocidad, ecualizador ----------
    fun changeMode(m: Int) {
        mode = m
        sp.edit().putInt("mode", m).apply()
    }

    fun changeSpeed(v: Float) {
        speed = v
        sp.edit().putFloat("speed", v).apply()
    }

    fun changeEq(g: List<Int>) {
        eq = g
        sp.edit().putString("eq", g.joinToString(",")).apply()
    }

    fun changeEqOn(v: Boolean) {
        eqOn = v
        sp.edit().putBoolean("eqOn", v).apply()
    }

    fun changeBass(v: Int) {
        bass = v.coerceIn(0, 100)
        sp.edit().putInt("bass", bass).apply()
    }

    fun changeVirt(v: Int) {
        virt = v.coerceIn(0, 100)
        sp.edit().putInt("virt", virt).apply()
    }

    fun changeBoost(v: Int) {
        boost = v.coerceIn(0, 100)
        sp.edit().putInt("boost", boost).apply()
    }

    // ---------- Ultima cancion ----------
    fun lastId(): Long = sp.getLong("lastId", -1L)
    fun lastPos(): Long = sp.getLong("lastPos", 0L)
    fun saveLast(id: Long, pos: Long) {
        sp.edit().putLong("lastId", id).putLong("lastPos", pos).apply()
    }

    // ---------- Listas ----------
    fun createPlaylist(name: String, ids: List<Long>): Playlist {
        val p = Playlist("p" + System.currentTimeMillis(), name.trim(), ids.distinct())
        playlists = playlists + p
        writePlaylists()
        return p
    }

    fun renamePlaylist(id: String, name: String) {
        playlists = playlists.map { if (it.id == id) Playlist(it.id, name.trim(), it.ids) else it }
        writePlaylists()
    }

    fun deletePlaylist(id: String) {
        playlists = playlists.filter { it.id != id }
        writePlaylists()
    }

    /** Devuelve cuantas canciones se agregaron de verdad (sin repetir). */
    fun addToPlaylist(id: String, ids: List<Long>): Int {
        var added = 0
        playlists = playlists.map { p ->
            if (p.id != id) {
                p
            } else {
                val have = p.ids.toMutableList()
                for (i in ids) {
                    if (i !in have) {
                        have.add(i)
                        added++
                    }
                }
                Playlist(p.id, p.name, have)
            }
        }
        writePlaylists()
        return added
    }

    fun removeFromPlaylist(id: String, ids: Set<Long>) {
        playlists = playlists.map { p ->
            if (p.id == id) Playlist(p.id, p.name, p.ids.filter { it !in ids }) else p
        }
        writePlaylists()
    }

    // ---------- Lectura y escritura ----------
    private fun readSet(key: String): Set<Long> {
        val s = sp.getString(key, "") ?: ""
        if (s.isEmpty()) return emptySet()
        return s.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun writeSet(key: String, set: Set<Long>) {
        sp.edit().putString(key, set.joinToString(",")).apply()
    }

    private fun readPlays(): Map<Long, Int> = try {
        val o = JSONObject(sp.getString("plays", "{}") ?: "{}")
        val m = HashMap<Long, Int>()
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            val id = k.toLongOrNull()
            if (id != null) m[id] = o.getInt(k)
        }
        m
    } catch (e: Exception) {
        emptyMap()
    }

    private fun readPlaylists(): List<Playlist> = try {
        val arr = JSONArray(sp.getString("pl", "[]") ?: "[]")
        val out = ArrayList<Playlist>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ids = o.getJSONArray("ids")
            val list = ArrayList<Long>()
            for (j in 0 until ids.length()) list.add(ids.getLong(j))
            out.add(Playlist(o.getString("id"), o.getString("name"), list))
        }
        out
    } catch (e: Exception) {
        emptyList()
    }

    private fun writePlaylists() {
        val arr = JSONArray()
        for (p in playlists) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            val ids = JSONArray()
            for (i in p.ids) ids.put(i)
            o.put("ids", ids)
            arr.put(o)
        }
        sp.edit().putString("pl", arr.toString()).apply()
    }

    private fun readEq(): List<Int> {
        val s = sp.getString("eq", "") ?: ""
        val l = s.split(",").mapNotNull { it.toIntOrNull() }
        return if (l.size == 5) l else listOf(0, 0, 0, 0, 0)
    }

    companion object {
        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}
