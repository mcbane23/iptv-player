package com.bane23.iptvplayer.ui.common

/** Oznake kvaliteta i formata koje ne govore ništa o samom kanalu, pa se preskaču. */
private val QUALITY_WORDS = setOf("HD", "FHD", "UHD", "SD", "4K", "8K", "HEVC", "H265", "H.265", "HQ", "LQ", "RAW", "50FPS", "60FPS")

/** Prefiks zemlje ili grupe na početku naziva, npr. „RS: ”, „|RS| ”, „[RS] ”. */
private val PREFIX = Regex("""^\s*(\|[^|]{1,6}\||\[[^\]]{1,6}]|\([^)]{1,6}\)|[A-Za-z0-9]{1,4}\s*[:|]+)\s*""")

/**
 * Kratka oznaka kanala (najviše četiri znaka) za mesto logoa kad logo ne postoji:
 * „RTS 1 HD” daje „RTS1”, „Nova Sport” daje „NS”, „Discovery Channel” daje „DC”.
 * Prefiksi zemlje i oznake kvaliteta se preskaču. Za prazan naziv vraća prazan tekst.
 */
fun channelInitials(name: String): String {
    val cleaned = name.replace(PREFIX, "")
    val words = cleaned
        .split(' ', '-', '_', '.', '|', '/')
        .map { word -> word.filter { it.isLetterOrDigit() } }
        .filter { it.isNotEmpty() && it.uppercase() !in QUALITY_WORDS }
    if (words.isEmpty()) return ""
    val first = words.first()
    val second = words.getOrNull(1)
    val result = when {
        // Kratka reč je najčešće skraćenica (RTS, HBO, N1), a broj iza nje je deo naziva.
        first.length <= 4 && first.any { it.isUpperCase() || it.isDigit() } && first == first.uppercase() ->
            if (second != null && second.all { it.isDigit() }) first + second else first

        second != null && second.all { it.isDigit() } -> first.first().toString() + second
        second != null -> "${first.first()}${second.first()}"
        else -> first.take(2)
    }
    return result.uppercase().take(4)
}
