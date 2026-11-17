package com.bane23.iptvplayer.domain

/** Raspoređivanje kategorija: čist kod bez Androida, da može da se testira. */
object CategoryArrange {

    /**
     * [items] poređane po sačuvanom redosledu [order] (ključevi). Stavke kojih u [order] nema
     * (npr. nove kategorije na serveru) idu na kraj, redom kojim su stigle.
     */
    fun <T> ordered(items: List<T>, order: List<String>, key: (T) -> String): List<T> {
        if (order.isEmpty()) return items
        val rank = HashMap<String, Int>(order.size * 2)
        order.forEachIndexed { index, id -> if (id !in rank) rank[id] = index }
        // sortedBy je stabilan, pa nepoznate stavke zadržavaju međusobni redosled.
        return items.sortedBy { rank[key(it)] ?: Int.MAX_VALUE }
    }

    /** Stavka sa mesta [index] pomerena za [delta] mesta; van granica spisak ostaje isti. */
    fun <T> moved(list: List<T>, index: Int, delta: Int): List<T> {
        val to = index + delta
        if (index !in list.indices || to !in list.indices || delta == 0) return list
        return list.toMutableList().apply { add(to, removeAt(index)) }
    }

    /** Stavka sa mesta [index] prebačena na vrh. */
    fun <T> movedToTop(list: List<T>, index: Int): List<T> = moved(list, index, -index)

    /** Stavke čiji je ključ u [bottomKeys] idu na dno; redosled unutar obe grupe ostaje isti. */
    fun <T> withBottom(list: List<T>, bottomKeys: Set<String>, key: (T) -> String): List<T> {
        if (bottomKeys.isEmpty()) return list
        val (bottom, top) = list.partition { key(it) in bottomKeys }
        return top + bottom
    }
}
