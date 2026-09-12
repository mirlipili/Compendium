package vet.derichs.compendium.utils

import java.text.Normalizer

object SearchNormalizer {

    private val reDiacritics = Regex("\\p{M}")
    private val rePunct = Regex("[®™°%·/\\-–—,.()'\"\\[\\]+]")
    private val reSpaces = Regex("\\s+")

    fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return nfd
            .replace(reDiacritics, "")
            .replace(rePunct, " ")
            .replace(reSpaces, " ")
            .trim()
    }

    /**
     * Jaro-Winkler similarity in [0, 1].  Higher prefix weight makes it
     * well-suited for drug-name typos ("metcam" vs "metacam").
     */
    fun jaroWinkler(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val matchWindow = maxOf(s1.length, s2.length) / 2 - 1
        val s1Matched = BooleanArray(s1.length)
        val s2Matched = BooleanArray(s2.length)

        var matches = 0
        for (i in s1.indices) {
            val lo = maxOf(0, i - matchWindow)
            val hi = minOf(i + matchWindow + 1, s2.length)
            for (j in lo until hi) {
                if (s2Matched[j] || s1[i] != s2[j]) continue
                s1Matched[i] = true
                s2Matched[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in s1.indices) {
            if (!s1Matched[i]) continue
            while (!s2Matched[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val jaro = (matches.toDouble() / s1.length +
                matches.toDouble() / s2.length +
                (matches - transpositions / 2.0) / matches) / 3.0

        var prefix = 0
        val limit = minOf(4, minOf(s1.length, s2.length))
        while (prefix < limit && s1[prefix] == s2[prefix]) prefix++

        return jaro + prefix * 0.1 * (1.0 - jaro)
    }
}
