package com.zut.smartstudy.domain

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Refines locally generated study points with Gemini.
 *
 * Always call refineOnIo() from a coroutine. API keys must be supplied by the
 * caller; never put a real key in this source file.
 */
object AiKeyPointGenerator {
    private const val TAG = "AiKeyPoints"
    const val DEFAULT_MODEL = "gemini-3.8-flash"
    private const val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 35_000
    private const val MAX_ATTEMPTS = 2
    private const val RETRY_DELAY_MS = 1_500L
    private const val MAX_OUTPUT_TOKENS = 1200
    private const val MAX_DRAFT_POINTS = 25
    private const val MAX_DRAFT_CHARS = 400
    private const val MIN_BULLET_LENGTH = 5

    class AiException(
        message: String,
        cause: Throwable? = null,
        val retryable: Boolean = false,
        internal val retryAfterMs: Long? = null
    ) : Exception(message, cause)

    suspend fun refineOnIo(
        apiKey: String,
        topic: String,
        draftPoints: List<String>,
        maxPoints: Int = 8,
        model: String = DEFAULT_MODEL
    ): List<String> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        require(key.isNotEmpty()) { "Enter your Gemini API key to use AI generation." }
        require(draftPoints.isNotEmpty()) {
            "No draft key points were found for \"$topic\". Try another topic or document."
        }

        val safeModel = model.trim()
        if (safeModel.isEmpty()) throw AiException("Gemini model name is missing.")

        val limit = maxPoints.coerceIn(1, 20)
        val drafts = draftPoints.asSequence()
            .map { it.trim().take(MAX_DRAFT_CHARS) }
            .filter { it.isNotBlank() }
            .take(MAX_DRAFT_POINTS)
            .toList()

        if (drafts.isEmpty()) throw AiException("There are no usable draft points to refine.")

        val prompt = buildPrompt(topic.take(160), drafts, limit)
        val url = "$BASE_URL/$safeModel:generateContent"
        var attempt = 0

        while (true) {
            try {
                val response = postJson(url, key, buildRequestBody(prompt))
                val answer = extractText(response)
                val bullets = parseBullets(answer, limit)
                if (bullets.isEmpty()) {
                    throw AiException("Gemini returned text, but no usable key points were found.")
                }
                return@withContext bullets
            } catch (e: AiException) {
                if (e.retryable && attempt < MAX_ATTEMPTS - 1) {
                    attempt++
                    delay(RETRY_DELAY_MS * attempt)
                } else {
                    throw e
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        emptyList()
    }

    private fun buildPrompt(topic: String, points: List<String>, limit: Int) = buildString {
        appendLine("You are a careful university study assistant.")
        appendLine("Topic: $topic")
        appendLine("Rewrite these draft notes into at most $limit accurate revision bullets.")
        appendLine("Preserve the supplied facts. Do not invent information.")
        appendLine("Merge repetition, clarify wording, and keep one idea per bullet.")
        appendLine("Return only bullets, one per line, beginning with '- '.")
        appendLine()
        points.forEach { appendLine("- $it") }
    }

    private fun buildRequestBody(prompt: String): JSONObject =
        JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("maxOutputTokens", MAX_OUTPUT_TOKENS)
            )

    private fun postJson(url: String, apiKey: String, body: JSONObject): String {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("x-goog-api-key", apiKey)

            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                val detail = errorDetail(response)
                Log.w(TAG, "Gemini HTTP $status: ${detail.take(250)}")
                val retryable = status == 408 || status == 425 || status == 429 || status in 500..599
                throw AiException(
                    friendlyError(status, detail),
                    retryable = retryable,
                    retryAfterMs = if (status == 429) parseRetryDelayMs(detail) else null
                )
            }
            return response
        } catch (e: AiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AiException(
                "Gemini timed out. Check your connection and try again; the service may be busy.",
                e,
                retryable = true
            )
        } catch (e: UnknownHostException) {
            throw AiException("Could not reach Gemini. Check your internet connection or DNS.", e)
        } catch (e: IOException) {
            throw AiException("Network error while contacting Gemini: ${e.message ?: "unknown error"}", e, true)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractText(raw: String): String {
        val json = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw AiException("Gemini returned an unreadable response.", e)
        }

        val blockReason = json.optJSONObject("promptFeedback")
            ?.optString("blockReason").orEmpty()
        if (blockReason.isNotBlank()) {
            throw AiException("Gemini blocked this request ($blockReason). Try different notes.")
        }

        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw AiException("Gemini returned no answer. Please try again.")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (!part.optBoolean("thought", false)) {
                        val value = part.optString("text")
                        if (value.isNotBlank()) appendLine(value)
                    }
                }
            }
        }.trim()

        if (text.isBlank()) {
            val reason = candidate.optString("finishReason")
            throw AiException(
                if (reason == "MAX_TOKENS") "Gemini reached its output limit. Reduce the notes and retry."
                else "Gemini returned an empty answer. Please try again."
            )
        }
        return text
    }

    internal fun parseBullets(raw: String, maxPoints: Int): List<String> {
        val prefix = Regex("""^\s*(?:[-*•]|\d+[.)])\s*""")
        return raw.lines()
            .map { it.trim().removePrefix("```").removeSuffix("```").trim() }
            .filter { it.isNotBlank() }
            .map { prefix.replace(it, "").trim() }
            .filter { it.length >= MIN_BULLET_LENGTH && !it.endsWith(":") }
            .distinct()
            .take(maxPoints.coerceAtLeast(1))
    }

    private fun errorDetail(body: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
    } catch (_: Exception) {
        body.take(300)
    }

    private fun parseRetryDelayMs(detail: String): Long? {
        val match = Regex(
            """retry in ((?:\d+h)?(?:\d+m)?(?:\d+(?:\.\d+)?s)?)""",
            RegexOption.IGNORE_CASE
        ).find(detail)?.groupValues?.get(1) ?: return null
        val h = Regex("""(\d+)h""").find(match)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val m = Regex("""(\d+)m""").find(match)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val s = Regex("""(\d+(?:\.\d+)?)s""").find(match)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return ((h * 3600 + m * 60) * 1000 + (s * 1000).toLong()).coerceAtLeast(0)
    }

    private fun friendlyError(code: Int, detail: String): String {
        val extra = if (detail.isBlank()) "" else " Details: $detail"
        return when (code) {
            400 -> "Gemini rejected the request. Check the request/model settings.$extra"
            401, 403 -> "Gemini rejected the API key or access. Check the key and API permissions.$extra"
            404 -> "Gemini model or endpoint not found. Check the model ID: $DEFAULT_MODEL.$extra"
            429 -> "Gemini rate limit reached. Wait before trying again.$extra"
            in 500..599 -> "Gemini is temporarily unavailable (HTTP $code). Try again shortly.$extra"
            else -> "Gemini request failed (HTTP $code).$extra"
        }
    }
}
