package com.zut.smartstudy.domain

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Refines existing offline key-point drafts using the Gemini Developer API.
 *
 * Call [refineOnIo] from a coroutine (e.g. viewModelScope.launch). [refine] is
 * blocking and must never run on the main thread.
 */
object AiKeyPointGenerator {

    private const val TAG = "AiKeyPoints"

    const val DEFAULT_MODEL = "gemini-3.8-flash"

    private const val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /** Retries only for transient failures (timeouts, HTTP 429 / 5xx). */
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2_000L

    private const val MAX_OUTPUT_TOKENS = 4096

    /**
     * Gemini 3 models "think" before answering and default to a deep level,
     * which can be slow. A rewrite task doesn't need it. Valid levels vary by
     * model (e.g. "minimal", "low", "medium", "high").
     */
    private const val THINKING_LEVEL = "low"

    /** Keep the prompt small: huge prompts are slower and more likely to time out. */
    private const val MAX_DRAFT_POINTS = 40
    private const val MAX_DRAFT_CHARS = 500

    private const val MIN_BULLET_LENGTH = 8

    class AiException(
        message: String,
        cause: Throwable? = null,
        val retryable: Boolean = false,
        internal val thinkingRejected: Boolean = false
    ) : Exception(message, cause)

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /** Safe entry point for UI code: runs [refine] on the IO dispatcher. */
    suspend fun refineOnIo(
        apiKey: String,
        topic: String,
        draftPoints: List<String>,
        maxPoints: Int = 8,
        model: String = DEFAULT_MODEL
    ): List<String> = withContext(Dispatchers.IO) {
        refine(apiKey, topic, draftPoints, maxPoints, model)
    }

    /** Blocking call. Throws [AiException] with a user-friendly message on failure. */
    fun refine(
        apiKey: String,
        topic: String,
        draftPoints: List<String>,
        maxPoints: Int = 8,
        model: String = DEFAULT_MODEL
    ): List<String> {
        val key = apiKey.trim()

        if (key.isEmpty()) {
            throw AiException("Enter your Gemini API key to use AI generation.")
        }
        if (draftPoints.isEmpty()) {
            throw AiException(
                "No relevant draft key points were found for \"$topic\". " +
                    "Try another topic or document."
            )
        }

        val limit = maxPoints.coerceAtLeast(1)
        val drafts = draftPoints.take(MAX_DRAFT_POINTS).map { it.take(MAX_DRAFT_CHARS) }
        val prompt = buildPrompt(topic, drafts, limit)
        val url = "$BASE_URL/${model.trim()}:generateContent"

        var attempt = 1
        var useThinkingConfig = true

        while (true) {
            try {
                val responseText = postJson(
                    url = url,
                    apiKey = key,
                    body = buildRequestBody(prompt, useThinkingConfig)
                )
                val rawText = extractText(responseText)

                return parseBullets(rawText, limit).ifEmpty {
                    throw AiException(
                        "Gemini's answer could not be converted into key points. " +
                            "Please try again."
                    )
                }
            } catch (e: AiException) {
                when {
                    // This model didn't accept the thinking setting: retry without it.
                    useThinkingConfig && e.thinkingRejected -> {
                        Log.w(TAG, "Thinking config rejected; retrying without it")
                        useThinkingConfig = false
                    }
                    e.retryable && attempt < MAX_ATTEMPTS -> {
                        Log.w(TAG, "Attempt $attempt failed (${e.message}); retrying")
                        Thread.sleep(RETRY_DELAY_MS * attempt)
                        attempt++
                    }
                    else -> throw e
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Request building
    // ---------------------------------------------------------------------

    private fun buildPrompt(
        topic: String,
        draftPoints: List<String>,
        maxPoints: Int
    ): String = buildString {
        appendLine("You are a careful university study assistant.")
        appendLine("Topic: $topic")
        appendLine(
            "Rewrite the draft study points below into no more than $maxPoints " +
                "accurate, useful revision bullets."
        )
        appendLine("Rules:")
        appendLine("- Preserve the meaning and facts in the drafts; do not invent facts.")
        appendLine("- Keep only information relevant to the topic.")
        appendLine("- Merge repetition and improve unclear wording.")
        appendLine("- Use one complete idea per bullet.")
        appendLine("- Do not add an introduction, conclusion, headings, or commentary.")
        appendLine("- Output only bullets, one per line, each beginning with '- '.")
        appendLine()
        appendLine("Draft key points:")
        draftPoints.forEach { appendLine("- $it") }
    }

    private fun buildRequestBody(prompt: String, includeThinkingConfig: Boolean): JSONObject {
        val generationConfig = JSONObject().put("maxOutputTokens", MAX_OUTPUT_TOKENS)

        if (includeThinkingConfig) {
            generationConfig.put(
                "thinkingConfig",
                JSONObject().put("thinkingLevel", THINKING_LEVEL)
            )
        }

        return JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            .put("generationConfig", generationConfig)
    }

    // ---------------------------------------------------------------------
    // Networking
    // ---------------------------------------------------------------------

    private fun postJson(url: String, apiKey: String, body: JSONObject): String {
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val stream =
                if (status in 200..299) connection.inputStream else connection.errorStream

            val text = stream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (status !in 200..299) {
                Log.w(TAG, "HTTP $status from Gemini: ${text.take(300)}")
                throw AiException(
                    message = friendlyError(status, text),
                    retryable = status == 429 || status in 500..599,
                    thinkingRejected = status == 400 &&
                        text.contains("thinking", ignoreCase = true)
                )
            }
            return text

        } catch (e: AiException) {
            throw e
        } catch (e: android.os.NetworkOnMainThreadException) {
            Log.e(TAG, "Called on main thread", e)
            throw AiException(
                "Internal error: the AI request ran on the main thread. Use refineOnIo().", e
            )
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout", e)
            throw AiException(
                "Gemini took too long to respond (${e.message ?: "timeout"}). " +
                    "Try again, or check that your network can reach Google.",
                e,
                retryable = true
            )
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown host", e)
            throw AiException(
                "Could not reach Gemini. Check your internet connection, VPN or DNS.", e
            )
        } catch (e: IOException) {
            Log.e(TAG, "Network error", e)
            throw AiException(
                "Network error while contacting Gemini: ${e.message ?: "unknown error"}",
                e,
                retryable = true
            )
        } finally {
            connection.disconnect()
        }
    }

    // ---------------------------------------------------------------------
    // Response parsing
    // ---------------------------------------------------------------------

    private fun extractText(responseText: String): String {
        val response = try {
            JSONObject(responseText)
        } catch (e: JSONException) {
            Log.e(TAG, "Unreadable response", e)
            throw AiException("Gemini sent an unreadable response. Please try again.", e)
        }

        val blockReason = response.optJSONObject("promptFeedback")
            ?.optString("blockReason")
            .orEmpty()
        if (blockReason.isNotBlank()) {
            throw AiException(
                "Gemini blocked this request ($blockReason). " +
                    "Try a different topic or document."
            )
        }

        val candidate = response.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw AiException("Gemini returned no answer. Please try again.")

        val finishReason = candidate.optString("finishReason")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")

        val text = buildString {
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (part.optBoolean("thought", false)) continue // skip reasoning parts
                    val piece = part.optString("text")
                    if (piece.isNotBlank()) appendLine(piece)
                }
            }
        }

        if (text.isBlank()) {
            Log.w(TAG, "Empty text, finishReason=$finishReason")
            throw AiException(
                when (finishReason) {
                    "MAX_TOKENS" ->
                        "Gemini ran out of output space before answering. " +
                            "Try again with fewer or shorter draft points."
                    "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST" ->
                        "Gemini declined to answer this content. Try a different document."
                    else ->
                        "Gemini returned an empty answer. Please try again."
                }
            )
        }
        return text
    }

    private val bulletPrefix = Regex("""^\s*(?:[-*•]|\d+[.)])\s*""")

    internal fun parseBullets(raw: String, maxPoints: Int): List<String> =
        raw.lines()
            .map { it.trim().trim('`') } // also turns ``` fence lines into blanks
            .filter { it.isNotBlank() }
            .map { bulletPrefix.replace(it, "").trim() }
            .filter { it.length >= MIN_BULLET_LENGTH && !it.endsWith(":") }
            .distinct()
            .take(maxPoints)

    // ---------------------------------------------------------------------
    // Error mapping
    // ---------------------------------------------------------------------

    private fun friendlyError(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }
        val suffix = if (detail.isBlank()) "" else " Details: $detail"

        return when (code) {
            400 ->
                if (detail.contains("API key", ignoreCase = true)) {
                    "Gemini rejected the API key (HTTP 400). Check that it was pasted correctly.$suffix"
                } else {
                    "Gemini rejected the request (HTTP 400).$suffix"
                }
            401, 403 ->
                "Gemini rejected the API key or access (HTTP $code). Check your API key and API access.$suffix"
            404 ->
                "Gemini model or endpoint not found (HTTP 404). Check the model name.$suffix"
            429 ->
                "Gemini rate limit or quota reached (HTTP 429). Wait a moment or check your quota.$suffix"
            in 500..599 ->
                "Gemini is temporarily unavailable (HTTP $code). Try again shortly.$suffix"
            else ->
                "Gemini request failed (HTTP $code).$suffix"
        }
    }
}
