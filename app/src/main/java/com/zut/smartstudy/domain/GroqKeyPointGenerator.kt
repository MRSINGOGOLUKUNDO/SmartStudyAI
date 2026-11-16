package com.zut.smartstudy.domain

import android.util.Log
import com.zut.smartstudy.domain.AiKeyPointGenerator.AiException
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
 * Groq fallback provider for locally generated study notes.
 * Supply the key from secure app configuration; never hardcode it here.
 */
object GroqKeyPointGenerator {
    private const val TAG = "GroqKeyPoints"
    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

    // The previous Llama model IDs in this project were deprecated in August 2026.
    // Keep the fast model first; the larger model is a fallback.
    val DEFAULT_MODELS = listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b")

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_ATTEMPTS_PER_MODEL = 2
    private const val MAX_COMPLETION_TOKENS = 1000
    private const val MAX_DRAFT_POINTS = 25
    private const val MAX_DRAFT_CHARS = 400

    suspend fun refineOnIo(
        apiKey: String,
        topic: String,
        draftPoints: List<String>,
        maxPoints: Int = 8,
        models: List<String> = DEFAULT_MODELS
    ): List<String> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) throw AiException("Enter your Groq API key to use AI generation.")
        if (draftPoints.isEmpty()) {
            throw AiException("No draft key points were found for \"$topic\". Try another document.")
        }

        val safeModels = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (safeModels.isEmpty()) throw AiException("No Groq models have been configured.")

        val limit = maxPoints.coerceIn(1, 20)
        val drafts = draftPoints.asSequence()
            .map { it.trim().take(MAX_DRAFT_CHARS) }
            .filter { it.isNotBlank() }
            .take(MAX_DRAFT_POINTS)
            .toList()
        val prompt = buildPrompt(topic.take(160), drafts, limit)
        var lastError: AiException? = null

        for (model in safeModels) {
            var attempt = 0
            while (attempt < MAX_ATTEMPTS_PER_MODEL) {
                try {
                    val raw = postChat(model, key, prompt)
                    val content = extractContent(raw)
                    val points = AiKeyPointGenerator.parseBullets(content, limit)
                    if (points.isNotEmpty()) return@withContext points
                    throw AiException("Groq returned text, but no usable key points were found.")
                } catch (e: AiException) {
                    lastError = e
                    // Try the next model for timeouts, transient server errors and rate limits.
                    val canMoveOn = e.retryable || e.retryAfterMs != null ||
                        e.message?.contains("model", ignoreCase = true) == true
                    if (!canMoveOn) throw e

                    if (e.retryAfterMs != null && e.retryAfterMs > 0) {
                        delay(e.retryAfterMs.coerceAtMost(30_000L))
                    } else if (attempt + 1 < MAX_ATTEMPTS_PER_MODEL) {
                        delay(1_000L * (attempt + 1))
                    }
                    attempt++
                    if (e.retryAfterMs != null || e.message?.contains("model", true) == true) break
                }
            }
        }
        throw lastError ?: AiException("All configured Groq models failed. Try again later.")
    }

    private fun buildPrompt(topic: String, points: List<String>, limit: Int) = buildString {
        appendLine("You are a careful university study assistant.")
        appendLine("Topic: $topic")
        appendLine("Rewrite these notes into at most $limit accurate revision bullets.")
        appendLine("Preserve the facts. Do not invent information.")
        appendLine("Merge repetition and clarify wording.")
        appendLine("Return only bullets, one per line, beginning with '- '.")
        appendLine()
        points.forEach { appendLine("- $it") }
    }

    private fun postChat(model: String, key: String, prompt: String): String {
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("content", prompt)))
            // GPT-OSS uses max_completion_tokens; avoid temperature settings that
            // may not be supported by reasoning-model endpoints.
            .put("max_completion_tokens", MAX_COMPLETION_TOKENS)

        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status in 200..299) return response

            val detail = errorDetail(response)
            Log.w(TAG, "Groq HTTP $status ($model): ${detail.take(250)}")
            val retryMs = connection.getHeaderField("retry-after")
                ?.toDoubleOrNull()?.let { (it * 1000).toLong() }
                ?: parseRetryDelayMs(detail)

            when {
                status == 401 || status == 403 ->
                    throw AiException("Groq rejected the API key or account access (HTTP $status).")
                status == 404 || (status == 400 && detail.contains("model", true)) ->
                    throw AiException("Groq model '$model' is unavailable. Check the current model list.")
                status == 429 ->
                    throw AiException("Groq rate limit reached.", retryAfterMs = retryMs ?: 5_000L)
                status == 408 || status == 425 || status in 500..599 ->
                    throw AiException("Groq is temporarily unavailable (HTTP $status).", retryable = true)
                status == 413 -> throw AiException("The Groq request is too large. Reduce the notes.")
                else -> throw AiException("Groq request failed (HTTP $status). $detail")
            }
        } catch (e: AiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AiException("Groq timed out. Trying another model may help.", e, retryable = true)
        } catch (e: UnknownHostException) {
            throw AiException("Could not reach Groq. Check your internet connection or DNS.", e)
        } catch (e: IOException) {
            throw AiException("Groq network error: ${e.message ?: "unknown error"}", e, retryable = true)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractContent(raw: String): String {
        val json = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw AiException("Groq returned an unreadable response.", e)
        }
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: throw AiException("Groq returned no answer.")
        val content = choice.optJSONObject("message")?.optString("content").orEmpty()
            .replace(Regex("(?s)<think>.*?</think>"), "")
            .trim()
        if (content.isBlank()) throw AiException("Groq returned an empty answer.")
        return content
    }

    private fun errorDetail(body: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
    } catch (_: Exception) {
        body.take(300)
    }

    private fun parseRetryDelayMs(detail: String): Long? {
        Regex("""try again in (\d+(?:\.\d+)?)ms""", RegexOption.IGNORE_CASE)
            .find(detail)?.let { return it.groupValues[1].toDoubleOrNull()?.toLong() }
        val value = Regex(
            """try again in ((?:\d+h)?(?:\d+m)?(?:\d+(?:\.\d+)?s)?)""",
            RegexOption.IGNORE_CASE
        ).find(detail)?.groupValues?.get(1) ?: return null
        val h = Regex("""(\d+)h""").find(value)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val m = Regex("""(\d+)m""").find(value)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val s = Regex("""(\d+(?:\.\d+)?)s""").find(value)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return ((h * 3600 + m * 60) * 1000 + (s * 1000).toLong()).coerceAtLeast(0)
    }
}
