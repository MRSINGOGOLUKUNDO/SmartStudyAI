
package com.zut.smartstudy.domain

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Offline extractive summarizer.
 *
 * Selects important sentences from the entire document.
 * The selected topic influences ranking but is NOT a mandatory filter.
 *
 * No internet or API key is required.
 */
object KeyPointGenerator {

    private val STOP_WORDS = setOf(
        "the", "and", "for", "are", "but", "not", "you", "all",
        "any", "can", "had", "her", "was", "one", "our", "out",
        "has", "have", "this", "that", "with", "from", "they",
        "will", "would", "there", "their", "what", "when",
        "which", "who", "how", "why", "about", "into", "than",
        "then", "them", "these", "those", "been", "being",
        "were", "also", "such", "its", "may", "more", "most",
        "other", "some", "use", "used", "using", "while",
        "where", "each", "per", "a", "an", "of", "in", "on",
        "to", "is", "it", "as", "by", "at", "or", "be",
        "if", "we", "he", "she", "his", "him", "hers",
        "do", "does", "did", "done", "being", "than",
        "very", "just", "only", "because", "through"
    )

    private val IMPORTANT_CUES = listOf(
        "is defined as",
        "are defined as",
        "refers to",
        "is known as",
        "consists of",
        "is responsible for",
        "the purpose of",
        "the main function",
        "an important",
        "the importance",
        "as a result",
        "therefore",
        "consequently",
        "in conclusion",
        "in summary",
        "for example",
        "such as",
        "includes",
        "involves",
        "causes",
        "results in"
    )

    private val WEAK_OPENERS = Regex(
        "^(this|these|they|it|he|she|we|thus|therefore|" +
                "hence|moreover|furthermore|however|also)\\b",
        RegexOption.IGNORE_CASE
    )

    private data class SentenceData(
        val index: Int,
        val text: String,
        val tokens: List<String>,
        val isFirstInParagraph: Boolean,
        val isLastInParagraph: Boolean
    )

    /**
     * Main entry point.
     *
     * Existing calls remain compatible:
     * KeyPointGenerator.generate(text, topic)
     */
    fun generate(
        text: String,
        topic: String,
        maxPoints: Int = 8
    ): List<String> {

        if (text.isBlank() || maxPoints <= 0) {
            return emptyList()
        }

        val sentences = collectSentences(text)

        if (sentences.isEmpty()) {
            return emptyList()
        }

        val topicTokens = tokenize(topic)

        // Calculate how frequently each word occurs across sentences.
        val documentFrequency = mutableMapOf<String, Int>()

        sentences.forEach { sentence ->
            sentence.tokens.toSet().forEach { token ->
                documentFrequency[token] =
                    (documentFrequency[token] ?: 0) + 1
            }
        }

        val sentenceCount = sentences.size.toDouble()

        // Inverse document frequency.
        val idf = documentFrequency.mapValues { (_, frequency) ->
            ln(1.0 + (sentenceCount + 1.0) / (frequency + 1.0))
        }

        // Build weighted word vectors for each sentence.
        val vectors = sentences.map { sentence ->
            buildVector(sentence.tokens, idf)
        }

        val scores = sentences.indices.map { index ->

            val sentence = sentences[index]
            val vector = vectors[index]

            val lexicalScore = vector.values.sum() /
                    sqrt(sentence.tokens.size.toDouble().coerceAtLeast(1.0))

            val centralityScore = calculateCentrality(
                index,
                vectors
            )

            val topicScore = calculateTopicScore(
                sentence.text,
                sentence.tokens,
                topic,
                topicTokens
            )

            val positionScore = when {
                sentence.index < 2 -> 0.8
                sentence.isFirstInParagraph -> 0.6
                sentence.isLastInParagraph -> 0.25
                else -> 0.0
            }

            val cueScore = if (
                IMPORTANT_CUES.any {
                    sentence.text.contains(it, ignoreCase = true)
                }
            ) 0.8 else 0.0

            val weakOpenerPenalty =
                if (WEAK_OPENERS.containsMatchIn(sentence.text)) {
                    0.45
                } else {
                    0.0
                }

            val lengthPenalty = when {
                sentence.text.length < 45 -> 0.35
                sentence.text.length > 300 -> 0.55
                else -> 0.0
            }

            // Topic relevance is a bonus, not a requirement.
            lexicalScore * 2.0 +
                    centralityScore * 3.0 +
                    topicScore * 2.0 +
                    positionScore +
                    cueScore -
                    weakOpenerPenalty -
                    lengthPenalty
        }

        // Select diverse sentences instead of simply taking the
        // highest-scoring sentences, which may repeat one another.
        val selected = mutableListOf<Int>()
        val remaining = sentences.indices.toMutableSet()

        while (selected.size < maxPoints && remaining.isNotEmpty()) {

            val bestIndex = remaining.maxByOrNull { candidate ->

                val relevance = scores[candidate]

                val redundancy = selected.maxOfOrNull { chosen ->
                    cosineSimilarity(
                        vectors[candidate],
                        vectors[chosen]
                    )
                } ?: 0.0

                // Maximal Marginal Relevance:
                // balance importance against repetition.
                relevance - (redundancy * 1.8)
            } ?: break

            selected.add(bestIndex)
            remaining.remove(bestIndex)
        }

        // Restore the document's original reading order.
        return selected
            .sorted()
            .map { sentences[it].text }
            .distinctBy { normalize(it) }
    }

    private fun collectSentences(text: String): List<SentenceData> {

        val paragraphs = text
            .replace("\r", "")
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val result = mutableListOf<SentenceData>()
        var globalIndex = 0

        paragraphs.forEach { paragraph ->

            val paragraphSentences = splitSentences(paragraph)

            paragraphSentences.forEachIndexed { index, sentence ->

                val cleaned = cleanSentence(sentence)
                val tokens = tokenize(cleaned)

                // Avoid headings, page numbers and fragments.
                if (
                    cleaned.length >= 25 &&
                    cleaned.length <= 500 &&
                    tokens.size >= 4
                ) {
                    result.add(
                        SentenceData(
                            index = globalIndex++,
                            text = cleaned,
                            tokens = tokens,
                            isFirstInParagraph = index == 0,
                            isLastInParagraph =
                                index == paragraphSentences.lastIndex
                        )
                    )
                }
            }
        }

        return result
    }

    private fun buildVector(
        tokens: List<String>,
        idf: Map<String, Double>
    ): Map<String, Double> {

        if (tokens.isEmpty()) return emptyMap()

        val frequencies = tokens.groupingBy { it }.eachCount()
        val total = tokens.size.toDouble()

        return frequencies.mapValues { (word, count) ->
            val tf = count / total
            tf * (idf[word] ?: 0.0)
        }
    }

    private fun calculateCentrality(
        currentIndex: Int,
        vectors: List<Map<String, Double>>
    ): Double {

        if (vectors.size <= 1) return 0.0

        var totalSimilarity = 0.0
        var comparisons = 0

        vectors.indices.forEach { otherIndex ->

            if (otherIndex != currentIndex) {
                totalSimilarity += cosineSimilarity(
                    vectors[currentIndex],
                    vectors[otherIndex]
                )
                comparisons++
            }
        }

        return if (comparisons == 0) {
            0.0
        } else {
            totalSimilarity / comparisons
        }
    }

    private fun calculateTopicScore(
        sentence: String,
        sentenceTokens: List<String>,
        topic: String,
        topicTokens: List<String>
    ): Double {

        if (topic.isBlank() || topicTokens.isEmpty()) {
            return 0.0
        }

        val sentenceWords = sentenceTokens.toSet()
        val matched = topicTokens.count { it in sentenceWords }

        val wordCoverage = matched.toDouble() / topicTokens.size

        val phraseBonus =
            if (sentence.contains(topic.trim(), ignoreCase = true)) {
                0.75
            } else {
                0.0
            }

        return wordCoverage + phraseBonus
    }

    private fun cosineSimilarity(
        first: Map<String, Double>,
        second: Map<String, Double>
    ): Double {

        if (first.isEmpty() || second.isEmpty()) return 0.0

        val smaller = if (first.size <= second.size) first else second
        val larger = if (first.size <= second.size) second else first

        var dotProduct = 0.0

        smaller.forEach { (word, weight) ->
            dotProduct += weight * (larger[word] ?: 0.0)
        }

        val firstMagnitude = sqrt(first.values.sumOf { it * it })
        val secondMagnitude = sqrt(second.values.sumOf { it * it })

        if (firstMagnitude == 0.0 || secondMagnitude == 0.0) {
            return 0.0
        }

        return dotProduct / (firstMagnitude * secondMagnitude)
    }

    internal fun splitSentences(text: String): List<String> {

        val cleaned = text
            .replace("\r", "")
            .replace(Regex("\\n\\s*\\n"), " ¶ ")
            .replace(Regex("\\s*\\n\\s*"), " ")

        return cleaned
            .split(
                Regex(
                    "(?<=[.!?])\\s+|¶|\\s[•●▪]\\s*"
                )
            )
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= 20 }
    }

    private fun cleanSentence(sentence: String): String {

        return sentence
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^[\\d\\s.)•▪-]+"), "")
            .trim()
            .let {
                if (it.isNotEmpty()) {
                    it.replaceFirstChar { char ->
                        char.uppercase()
                    }
                } else {
                    it
                }
            }
    }

    internal fun tokenize(text: String): List<String> {

        return Regex("[\\p{L}\\p{N}]+")
            .findAll(text.lowercase())
            .map { it.value }
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toList()
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
}
