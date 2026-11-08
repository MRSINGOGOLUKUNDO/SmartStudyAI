package com.zut.smartstudy.domain

/**
 * Offline, extractive "AI" summariser.
 *
 * 1. Splits the document into sentences.
 * 2. Scores every sentence against the topic (keyword/stem matches, exact phrase,
 *    and how central its words are to the document).
 * 3. Returns the best sentences in their original reading order as bullet points.
 *
 * Pure Kotlin (no Android classes) so it is easy to unit-test.
 */
object KeyPointGenerator {

    private val STOP_WORDS = setOf(
        "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her", "was",
        "one", "our", "out", "has", "have", "this", "that", "with", "from", "they", "will", "would",
        "there", "their", "what", "when", "which", "who", "how", "why", "about", "into", "than",
        "then", "them", "these", "those", "been", "being", "were", "also", "such", "its", "may",
        "more", "most", "other", "some", "use", "used", "using", "while", "where", "each", "per"
    )

    fun generate(text: String, topic: String, maxPoints: Int = 8): List<String> {
        val topicTokens = tokenize(topic)
        if (topicTokens.isEmpty() || text.isBlank()) return emptyList()

        val sentences = splitSentences(text)
        if (sentences.isEmpty()) return emptyList()

        val freq = HashMap<String, Int>()
        sentences.forEach { s -> tokenize(s).forEach { freq[it] = (freq[it] ?: 0) + 1 } }
        val maxFreq = (freq.values.maxOrNull() ?: 1).toDouble()
        val phrase = topic.trim().lowercase()

        val scored = sentences.mapIndexedNotNull { index, sentence ->
            val words = tokenize(sentence)
            val wordSet = words.toSet()
            val hits = topicTokens.count { it in wordSet }
            if (hits == 0) return@mapIndexedNotNull null

            var score = hits * 3.0
            if (sentence.lowercase().contains(phrase)) score += 4.0
            if (hits == topicTokens.size) score += 2.0
            val salience = words.sumOf { (freq[it] ?: 0) / maxFreq } / words.size.coerceAtLeast(1)
            score += salience * 2
            if (sentence.length > 220) score -= 1.0
            Triple(index, sentence, score)
        }

        return scored
            .sortedByDescending { it.third }
            .take(maxPoints)
            .sortedBy { it.first }
            .map { it.second }
            .distinct()
    }

    internal fun splitSentences(text: String): List<String> {
        val cleaned = text
            .replace("\r", "")
            .replace(Regex("\\n\\s*\\n"), "¶")
            .replace(Regex("\\s*\\n\\s*"), " ")
        return cleaned
            .split(Regex("(?<=[.!?])\\s+|¶|\\s[•●▪]\\s*"))
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length in 25..400 }
    }

    internal fun tokenize(s: String): List<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(s.lowercase())
            .map { it.value }
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .map { stem(it) }
            .toList()

    private fun stem(w: String): String {
        if (w.length <= 4) return w
        for (suffix in listOf("ing", "ion", "ed", "es", "ly", "s")) {
            if (w.endsWith(suffix) && w.length - suffix.length >= 3) return w.removeSuffix(suffix)
        }
        return w
    }
}
