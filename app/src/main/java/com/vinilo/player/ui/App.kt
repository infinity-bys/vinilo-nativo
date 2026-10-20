package com.vinilo.player.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vinilo.player.Fx
import com.vinilo.player.PlayerHolder
import com.vinilo.player.R
import com.vinilo.player.data.CoverCache
import com.vinilo.player.data.Library
import com.vinilo.player.data.Playlist
import com.vinilo.player.data.Song
import com.vinilo.player.data.sortSongs
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

/** La hoja inferior que esta abierta en este momento (solo una a la vez). */
sealed interface Sheet {
    data object Settings : Sheet
    data object Sort : Sheet
    data object Eq : Sheet
    data object PlayerMenu : Sheet
    class AddTo(val ids: List<Long>) : Sheet
    class Name(val title: String, val initial: String, val okText: String, val onOk: (String) -> Unit) : Sheet
}

private val modeLabels = listOf("Repetir todas", "Repetir una", "Aleatorio")

@Composable
private fun SelActions(inPlaylist: Boolean, onAdd: () -> Unit, onHide: () -> Unit, onRemove: () -> Unit) {
    @Composable
    fun Act(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier) {
        Column(
            modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(painterResource(icon), null, tint = C.Acc, modifier = Modifier.size(22.dp))
            Text(label, color = C.Text, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(C.Panel)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Act(R.drawable.ic_playlist_add, "Agregar a", onAdd, Modifier.weight(1f))
        Act(R.drawable.ic_eye_off, "Ocultar", onHide, Modifier.weight(1f))
        if (inPlaylist) Act(R.drawable.ic_close, "Quitar", onRemove, Modifier.weight(1f))
    }
}

@Composable
fun VinyloApp(holder: PlayerHolder) {
    val context = LocalContext.current
    val store = holder.store

    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var scanned by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) } // 0 todas, 1 favoritas, 2 listas
    var plSelId by remember { mutableStateOf<String?>(null) }
    var sel by remember { mutableStateOf<Set<Long>?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var confirmDel by remember { mutableStateOf<Playlist?>(null) }

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
            allSongs = withContext(Dispatchers.IO) { Library.scan(context) }
            scanned = true
        }
    }

    // ---------- Listas derivadas ----------
    val sorted = remember(allSongs, store.sortKey, store.sortDir, if (store.sortKey == "plays") store.plays else null) {
        sortSongs(allSongs, store.sortKey, store.sortDir, store.plays)
    }
    val visible = remember(sorted, store.hidden, store.forced, store.autoHide) {
        sorted.filter { !store.isHidden(it) }
    }
    val hiddenSongs = remember(sorted, store.hidden, store.forced, store.autoHide) {
        sorted.filter { store.isHidden(it) }
    }
    val byId = remember(allSongs) { allSongs.associateBy { it.id } }
    val visibleById = remember(visible) { visible.associateBy { it.id } }

    val plSel = plSelId?.let { id -> store.playlists.firstOrNull { it.id == id } }
    val q = query.trim().lowercase()
    val shown = remember(visible, visibleById, plSel, filter, q, store.favs) {
        val base = if (plSel != null) plSel.ids.mapNotNull { visibleById[it] } else visible
        base.filter { s ->
            (q.isEmpty() || (s.title + " " + s.artist).lowercase().contains(q)) &&
                (plSel != null || filter != 1 || s.id in store.favs)
        }
    }

    // ---------- Cancion actual, caratula y colores ----------
    val current = byId[holder.currentId]
    val bigBmp by produceState<Bitmap?>(null, current?.id) {
        value = null
        val s = current
        value = if (s == null) null else withContext(Dispatchers.IO) { CoverCache.loadBig(context, s) }
    }
    val look = remember(bigBmp, store.accentOn) { computeLook(bigBmp, store.accentOn) }
    val coverImg = remember(bigBmp) { bigBmp?.asImageBitmap() }

    // Al abrir la app deja lista la ultima cancion donde quedo
    LaunchedEffect(holder.ready, scanned) {
        if (holder.ready && scanned) holder.restore(visible)
    }

    // ---------- Acciones ----------
    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun hideSongs(ids: List<Long>) {
        if (ids.isEmpty()) return
        store.hide(ids)
        if (holder.currentId in ids) {
            holder.stopAndClear()
            showPlayer = false
        }
        toast(if (ids.size == 1) "Canción oculta" else "${ids.size} canciones ocultas")
    }

    fun play(list: List<Song>, index: Int) {
        if (index !in list.indices) return
        if (list[index].id != holder.currentId) holder.playList(list, index)
        showPlayer = true
    }

    fun selectedIds(): List<Long> {
        val cur = sel ?: return emptyList()
        return shown.filter { it.id in cur }.map { it.id }
    }

    BackHandler(enabled = sel != null || showPlayer || plSelId != null || filter != 0) {
        when {
            sel != null -> sel = null
            showPlayer -> showPlayer = false
            plSelId != null -> plSelId = null
            else -> filter = 0
        }
    }

    // ---------- Pantalla ----------
    Box(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            HomeScreen(
                holder = holder,
                granted = granted,
                scanned = scanned,
                totalVisible = visible.size,
                query = query,
                onQuery = { query = it },
                filter = filter,
                onFilter = { f ->
                    filter = f
                    plSelId = null
                    sel = null
                },
                plSel = plSel,
                onPlBack = {
                    plSelId = null
                    sel = null
                },
                onOpenPlaylist = { p -> plSelId = p.id },
                shown = shown,
                byId = byId,
                sel = sel,
                onRowClick = { s, i ->
                    val cur = sel
                    if (cur != null) {
                        val n = if (s.id in cur) cur - s.id else cur + s.id
                        sel = if (n.isEmpty()) null else n
                    } else {
                        play(shown, i)
                    }
                },
                onRowLong = { s -> if (sel == null) sel = setOf(s.id) },
                onSort = { sheet = Sheet.Sort },
                onSettings = { sheet = Sheet.Settings },
                onNewList = {
                    sheet = Sheet.Name("Nueva lista", "", "Crear lista") { name ->
                        store.createPlaylist(name, emptyList())
                        sheet = null
                    }
                },
                onRename = { p ->
                    sheet = Sheet.Name("Renombrar lista", p.name, "Guardar") { name ->
                        store.renamePlaylist(p.id, name)
                        sheet = null
                    }
                },
                onDelete = { p -> confirmDel = p },
                onSelClose = { sel = null },
                onSelAll = { sel = shown.map { it.id }.toSet() },
                onGrant = { launcher.launch(permissionsToAsk()) },
                modifier = Modifier.weight(1f)
            )

            if (sel != null) {
                SelActions(
                    inPlaylist = plSel != null,
                    onAdd = { sheet = Sheet.AddTo(selectedIds()) },
                    onHide = {
                        val ids = selectedIds()
                        sel = null
                        hideSongs(ids)
                    },
                    onRemove = {
                        val p = plSel
                        if (p != null) {
                            store.removeFromPlaylist(p.id, selectedIds().toSet())
                            toast("Quitada de la lista")
                        }
                        sel = null
                    }
                )
            } else if (holder.currentId != -1L) {
                MiniPlayer(holder, current, look) { showPlayer = true }
            }
        }

        AnimatedVisibility(
            visible = showPlayer && holder.currentId != -1L,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PlayerScreen(
                holder = holder,
                song = current,
                cover = coverImg,
                look = look,
                isFav = holder.currentId in store.favs,
                onBack = { showPlayer = false },
                onSettings = { sheet = Sheet.Settings },
                onMenu = { sheet = Sheet.PlayerMenu },
                onEq = { sheet = Sheet.Eq },
                onFav = {
                    if (holder.currentId != -1L) store.toggleFav(holder.currentId)
                },
                onMode = {
                    val m = (store.mode + 1) % 3
                    holder.applyMode(m)
                    toast(modeLabels[m])
                }
            )
        }
    }

    // ---------- Hojas inferiores ----------
    val s = sheet
    if (s != null) {
        VSheet(onClose = { sheet = null }) { close ->
            when (s) {
                is Sheet.Settings -> SettingsSheet(
                    store = store,
                    holder = holder,
                    hidden = hiddenSongs,
                    onRescan = {
                        holder.resumeTick++
                        toast("Buscando música…")
                    },
                    onUnhide = { list ->
                        store.unhide(list)
                        toast(if (list.size == 1) "Canción visible" else "Canciones visibles")
                    },
                    toast = { m -> toast(m) }
                )

                is Sheet.Sort -> SortSheet(store, close)

                is Sheet.Eq -> EqSheet(store) { g ->
                    store.changeEq(g)
                    Fx.apply(g)
                }

                is Sheet.PlayerMenu -> PlayerMenuSheet(
                    song = current,
                    store = store,
                    onAdd = {
                        if (holder.currentId != -1L) sheet = Sheet.AddTo(listOf(holder.currentId))
                    },
                    onSpeed = { v -> holder.applySpeed(v) },
                    onHide = {
                        hideSongs(listOf(holder.currentId))
                        sheet = null
                    },
                    close = close
                )

                is Sheet.AddTo -> AddToSheet(
                    playlists = store.playlists,
                    byId = byId,
                    onNew = {
                        sheet = Sheet.Name("Nueva lista", "", "Crear lista") { name ->
                            val p = store.createPlaylist(name, s.ids)
                            toast("Agregada a «" + p.name + "»")
                            sel = null
                            sheet = null
                        }
                    },
                    onPick = { p ->
                        val n = store.addToPlaylist(p.id, s.ids)
                        toast(if (n > 0) "Agregada a «" + p.name + "»" else "Ya estaba en «" + p.name + "»")
                        sel = null
                        close()
                    }
                )

                is Sheet.Name -> NameSheet(s.title, s.initial, s.okText) { name -> s.onOk(name) }
            }
        }
    }

    // ---------- Confirmar borrar lista ----------
    val del = confirmDel
    if (del != null) {
        AlertDialog(
            onDismissRequest = { confirmDel = null },
            containerColor = C.Panel,
            title = { Text("¿Eliminar la lista?", color = C.Text) },
            text = { Text("«" + del.name + "» se eliminará. Las canciones no se borran.", color = C.Mute) },
            confirmButton = {
                Text(
                    "Eliminar",
                    color = C.Acc2,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            store.deletePlaylist(del.id)
                            plSelId = null
                            sel = null
                            confirmDel = null
                        }
                        .padding(12.dp)
                )
            },
            dismissButton = {
                Text(
                    "Cancelar",
                    color = C.Mute,
                    modifier = Modifier
                        .clickable { confirmDel = null }
                        .padding(12.dp)
                )
            }
        )
    }
}
