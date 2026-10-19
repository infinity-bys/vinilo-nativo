package com.vinilo.player.data

class SortOpt(val key: String, val name: String, val defaultDir: Int)

object Sorts {
    val all = listOf(
        SortOpt("recent", "Más recientes", -1),
        SortOpt("plays", "Más reproducidas", -1),
        SortOpt("az", "A a Z", 1),
        SortOpt("artist", "Artista", 1),
        SortOpt("dur", "Duración más larga", -1)
    )

    fun defaultDir(key: String): Int = all.firstOrNull { it.key == key }?.defaultDir ?: 1
}

fun sortSongs(list: List<Song>, key: String, dir: Int, plays: Map<Long, Int>): List<Song> {
    val byTitle = Comparator<Song> { a, b -> a.title.compareTo(b.title, ignoreCase = true) }
    return when (key) {
        "az" -> list.sortedWith(Comparator { a, b -> byTitle.compare(a, b) * dir })
        "artist" -> list.sortedWith(Comparator { a, b ->
            val x = if (a.artist.isBlank()) "\uffff" else a.artist
            val y = if (b.artist.isBlank()) "\uffff" else b.artist
            val c = x.compareTo(y, ignoreCase = true) * dir
            if (c != 0) c else byTitle.compare(a, b)
        })
        else -> {
            val num: (Song) -> Long = when (key) {
                "plays" -> ({ s: Song -> (plays[s.id] ?: 0).toLong() })
                "dur" -> ({ s: Song -> s.durationMs })
                else -> ({ s: Song -> s.added })
            }
            list.sortedWith(Comparator { a, b ->
                val c = num(a).compareTo(num(b)) * dir
                if (c != 0) c else byTitle.compare(a, b)
            })
        }
    }
}
