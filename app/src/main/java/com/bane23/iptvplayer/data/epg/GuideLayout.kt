package com.bane23.iptvplayer.data.epg

/**
 * Raspored jednog reda vodiča u vremenskom prozoru: emisije i praznine između njih,
 * svaka sa udelom širine reda koji joj pripada.
 */
object GuideLayout {

    /** [index] je redni broj emisije u ulaznoj listi, ili null za prazninu bez podataka. */
    data class Cell(val index: Int?, val startMs: Long, val stopMs: Long, val weight: Float)

    /**
     * [programs] su parovi (početak, kraj) poređani po početku. Emisije se seku na granicama
     * prozora od [from] do [to]; one koje su potpuno van prozora ili se preklapaju sa prethodnom
     * emisijom se preskaču, odnosno skraćuju.
     */
    fun cells(programs: List<Pair<Long, Long>>, from: Long, to: Long): List<Cell> {
        if (to <= from) return emptyList()
        val span = (to - from).toFloat()
        val result = ArrayList<Cell>(programs.size + 2)
        var cursor = from
        programs.forEachIndexed { index, (start, stop) ->
            val clippedStart = maxOf(start, cursor)
            val clippedStop = minOf(stop, to)
            if (clippedStop <= clippedStart) return@forEachIndexed
            if (clippedStart > cursor) {
                result.add(Cell(null, cursor, clippedStart, (clippedStart - cursor) / span))
            }
            result.add(Cell(index, clippedStart, clippedStop, (clippedStop - clippedStart) / span))
            cursor = clippedStop
        }
        if (cursor < to) result.add(Cell(null, cursor, to, (to - cursor) / span))
        return result
    }
}
