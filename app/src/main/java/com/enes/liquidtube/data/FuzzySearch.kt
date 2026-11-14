package com.enes.liquidtube.data

import java.text.Normalizer

/**
 * Forgiving search for the app's own lists (Downloaded, Favorites, Playlists…), so they
 * behave like the main search bar: case, accents and Turkish letters don't matter
 * ("sebnem" finds "Şebnem", "beyonce" finds "Beyoncé"), small typos are fine ("mariha
 * carey", "rihana"), words can come in any order, and the start of a word is enough
 * ("ari gra"). Results come back best match first.
 */
object FuzzySearch {

    /** [items] matching [query], best first; [fields] gives the texts to search in. */
    fun <T> filter(items: List<T>, query: String, fields: (T) -> List<String?>): List<T> {
        val words = tokens(query)
        if (words.isEmpty()) return items
        return items.mapIndexedNotNull { i, item ->
            val score = score(words, fields(item))
            if (score > 0) Triple(item, score, i) else null
        }
            // Best score first; equal scores keep the list's own order.
            .sortedWith(compareByDescending<Triple<T, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    /** 0 = no match. Every query word must match some word in the item. */
    private fun score(query: List<String>, fields: List<String?>): Int {
        val texts = fields.filterNotNull()
        val words = texts.flatMap(::tokens)
        if (words.isEmpty()) return 0
        val joined = texts.joinToString(" ") { normalize(it) }
        var total = 0
        for (q in query) {
            val best = words.maxOf { w -> wordScore(q, w) }
            if (best == 0) {
                // Typed without spaces ("arianagrande") or with a broken word: try the whole text.
                if (q.length >= 4 && joined.replace(" ", "").contains(q)) total += 40 else return 0
            } else total += best
        }
        // The whole query as typed, in order, ranks highest.
        val phrase = query.joinToString(" ")
        if (joined.contains(phrase)) total += 30
        if (joined.startsWith(phrase)) total += 20
        return total
    }

    private fun wordScore(q: String, w: String): Int = when {
        w == q -> 100
        w.startsWith(q) -> 80                      // "ari" → "ariana"
        q.length >= 3 && w.contains(q) -> 55       // "ndra" → "alessandra"
        else -> {
            // Typos: one edit for short words, two for long ones; compared against the
            // word, or its start when you've typed only part of it.
            val allowed = when {
                q.length <= 3 -> 0
                q.length <= 6 -> 1
                else -> 2
            }
            if (allowed == 0) 0
            else {
                val whole = distance(q, w, allowed)
                val prefix = if (w.length > q.length) distance(q, w.take(q.length), allowed) else whole
                val d = minOf(whole, prefix)
                if (d <= allowed) 60 - d * 15 else 0
            }
        }
    }

    fun tokens(text: String): List<String> =
        normalize(text).split(' ').filter { it.isNotBlank() }

    /** Lowercase, no accents, Turkish letters folded, punctuation as spaces. */
    fun normalize(text: String): String {
        val lower = text.lowercase()
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
            .replace('ç', 'c').replace('ö', 'o').replace('ü', 'u')
            .replace("&", " and ").replace("$", "s")
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(MARKS, "")
        return stripped.replace(PUNCT, " ").replace(SPACES, " ").trim()
    }

    /** Damerau–Levenshtein distance, giving up early past [max]. */
    private fun distance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        val n = a.length
        val m = b.length
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }

    private val MARKS = Regex("\\p{Mn}+")
    private val PUNCT = Regex("[^\\p{L}\\p{N} ]")
    private val SPACES = Regex("\\s+")
}
