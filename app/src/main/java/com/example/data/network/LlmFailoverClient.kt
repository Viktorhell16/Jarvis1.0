package com.example.data.network

import com.example.data.local.EncryptedPrefsManager
import com.example.data.local.MessageEntity
import com.example.data.local.PluginEntity
import com.example.data.local.ProviderSlotDao
import com.example.data.local.ProviderSlotEntity
import com.example.data.local.TokenUsageDao
import com.example.data.local.TokenUsageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

sealed class StreamEvent {
    data class TokenDelta(
        val accumulatedText: String,
        val modelTag: String,
        val providerName: String
    ) : StreamEvent()

    data class Completed(
        val fullText: String,
        val modelTag: String,
        val providerName: String,
        val promptTokens: Long,
        val completionTokens: Long
    ) : StreamEvent()

    data class FailoverNotice(val message: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

class LlmFailoverClient(
    private val prefs: EncryptedPrefsManager,
    private val slotDao: ProviderSlotDao,
    private val tokenUsageDao: TokenUsageDao
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Token-budget context trimmer:
     * Keeps system prompt + recent messages within ~6000 estimated tokens,
     * compressing older turns into a single compact summary turn.
     */
    fun trimContextForBudget(history: List<MessageEntity>, maxEstimatedTokens: Int = 6000): List<MessageEntity> {
        if (history.size <= 6) return history
        val totalChars = history.sumOf { it.content.length }
        if (totalChars / 3.5 <= maxEstimatedTokens) return history

        val keepRecentCount = 6
        val older = history.dropLast(keepRecentCount)
        val recent = history.takeLast(keepRecentCount)
        val summaryBullets = older.takeLast(8).joinToString(" | ") { msg ->
            val snippet = msg.content.replace("\n", " ").take(90)
            "${msg.role}: $snippet"
        }
        val summaryEntity = MessageEntity(
            chatId = history.first().chatId,
            role = "system",
            content = "Compressed earlier conversation context: $summaryBullets"
        )
        return listOf(summaryEntity) + recent
    }

    fun streamCompletion(
        history: List<MessageEntity>,
        systemPrompt: String,
        reasoningEffort: String
    ): Flow<StreamEvent> = flow {
        val state = prefs.state.value
        val trimmedHistory = trimContextForBudget(history)
        val now = System.currentTimeMillis()

        // Check hard daily token limit
        if (state.hardStopEnabled) {
            val todayKey = dateKey(now)
            val usedToday = tokenUsageDao.getDailyTokensOnce(todayKey)
            if (usedToday >= state.dailyTokenLimit) {
                emit(StreamEvent.Error("Daily hard token limit reached ($usedToday / ${state.dailyTokenLimit}). Increase limit or disable hard stop in Settings -> Usage Limits."))
                return@flow
            }
        }

        // Build candidate execution queue: active user config first, then failover slots
        val activeSlot = ProviderSlotEntity(
            id = -1L,
            priorityOrder = 0,
            providerId = state.activeProviderId,
            providerDisplayName = ProviderCatalog.findProvider(state.activeProviderId).displayName,
            baseUrl = state.activeBaseUrl,
            modelId = resolveModelForPreset(state.activeProviderId, state.activeModelId, state.activePresetId),
            cooldownUntilMs = 0L,
            isEnabled = true
        )

        val dbSlots = slotDao.getSlotsOnce()
            .filter { it.isEnabled && (it.cooldownUntilMs <= now) }
            .filterNot { it.providerId == activeSlot.providerId && it.modelId == activeSlot.modelId }

        val candidates = (listOf(activeSlot) + dbSlots)
            .filter { prefs.getApiKey(it.providerId).isNotBlank() }

        if (candidates.isEmpty()) {
            emit(StreamEvent.Error("NO_API_KEY"))
            return@flow
        }

        var lastErrorMsg = "All configured providers failed."
        for ((index, candidate) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()
            val apiKey = prefs.getApiKey(candidate.providerId)
            if (apiKey.isBlank()) continue

            if (index > 0) {
                emit(StreamEvent.FailoverNotice("Failover -> ${candidate.providerDisplayName} (${candidate.modelId})"))
            }

            try {
                val result = executeStreamingRequest(
                    slot = candidate,
                    apiKey = apiKey,
                    systemPrompt = systemPrompt,
                    history = trimmedHistory,
                    reasoningEffort = reasoningEffort,
                    onBatchEmit = { accumulated ->
                        emit(
                            StreamEvent.TokenDelta(
                                accumulatedText = accumulated,
                                modelTag = candidate.modelId,
                                providerName = candidate.providerDisplayName
                            )
                        )
                    }
                )

                // Record token usage in Room DB
                val promptTokens = result.promptTokens.takeIf { it > 0 }
                    ?: estimateTokens(systemPrompt + trimmedHistory.joinToString { it.content })
                val completionTokens = result.completionTokens.takeIf { it > 0 }
                    ?: estimateTokens(result.fullText)
                val cost = ProviderCatalog.estimateCostUsd(candidate.modelId, promptTokens, completionTokens)
                tokenUsageDao.insertUsage(
                    TokenUsageEntity(
                        timestamp = System.currentTimeMillis(),
                        dateKey = dateKey(System.currentTimeMillis()),
                        monthKey = monthKey(System.currentTimeMillis()),
                        providerName = candidate.providerDisplayName,
                        modelId = candidate.modelId,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        estimatedCostUsd = cost
                    )
                )

                emit(
                    StreamEvent.Completed(
                        fullText = result.fullText,
                        modelTag = candidate.modelId,
                        providerName = candidate.providerDisplayName,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens
                    )
                )
                return@flow
            } catch (e: HttpFailoverException) {
                lastErrorMsg = "${candidate.providerDisplayName}: HTTP ${e.code} (${e.reason})"
                if (candidate.id > 0) {
                    val cooldownMinutes = if (e.code == 401 || e.code == 403 || e.code == 402) 60L else 15L
                    slotDao.setCooldown(
                        id = candidate.id,
                        untilMs = System.currentTimeMillis() + cooldownMinutes * 60_000L,
                        error = lastErrorMsg
                    )
                }
            } catch (e: IOException) {
                lastErrorMsg = "${candidate.providerDisplayName}: ${e.message ?: "Network timeout"}"
                if (candidate.id > 0) {
                    slotDao.setCooldown(
                        id = candidate.id,
                        untilMs = System.currentTimeMillis() + 15 * 60_000L,
                        error = lastErrorMsg
                    )
                }
            }
        }

        emit(StreamEvent.Error(lastErrorMsg))
    }.flowOn(Dispatchers.IO)

    private data class RequestResult(
        val fullText: String,
        val promptTokens: Long,
        val completionTokens: Long
    )

    private class HttpFailoverException(val code: Int, val reason: String) : Exception("HTTP $code: $reason")

    private suspend fun executeStreamingRequest(
        slot: ProviderSlotEntity,
        apiKey: String,
        systemPrompt: String,
        history: List<MessageEntity>,
        reasoningEffort: String,
        onBatchEmit: suspend (String) -> Unit
    ): RequestResult {
        val request = buildProviderRequest(slot, apiKey, systemPrompt, history, reasoningEffort)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(240) ?: response.message
                if (response.code in listOf(401, 402, 403, 429, 500, 502, 503, 504)) {
                    throw HttpFailoverException(response.code, errBody)
                }
                throw IOException("HTTP ${response.code}: $errBody")
            }

            val body = response.body ?: throw IOException("Empty response body")
            val source = body.source()
            val accumulated = StringBuilder()
            var promptTokens = 0L
            var completionTokens = 0L
            var lastEmitMs = 0L

            while (!source.exhausted()) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break

                val parsed = parseSseChunk(slot.providerId, data)
                if (parsed.delta.isNotEmpty()) {
                    accumulated.append(parsed.delta)
                    val now = System.currentTimeMillis()
                    if (now - lastEmitMs >= 85L) {
                        lastEmitMs = now
                        onBatchEmit(accumulated.toString())
                    }
                }
                if (parsed.promptTokens > 0) promptTokens = parsed.promptTokens
                if (parsed.completionTokens > 0) completionTokens = parsed.completionTokens
            }

            // Fallback if endpoint returned non-SSE plain JSON
            val text = accumulated.toString()
            if (text.isNotEmpty()) {
                onBatchEmit(text)
            }
            return RequestResult(text, promptTokens, completionTokens)
        }
    }

    private fun buildProviderRequest(
        slot: ProviderSlotEntity,
        apiKey: String,
        systemPrompt: String,
        history: List<MessageEntity>,
        reasoningEffort: String
    ): Request {
        val cleanBase = slot.baseUrl.trimEnd('/')
        return when (slot.providerId) {
            "gemini" -> {
                val url = "$cleanBase/models/${slot.modelId}:streamGenerateContent?alt=sse"
                val contents = JSONArray()
                history.filter { it.role != "system" }.forEach { msg ->
                    val geminiRole = if (msg.role == "assistant") "model" else "user"
                    contents.put(
                        JSONObject().apply {
                            put("role", geminiRole)
                            put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                        }
                    )
                }
                val thinkingBudget = when (reasoningEffort) {
                    "high" -> 8192
                    "medium" -> 2048
                    else -> 0
                }
                val payload = JSONObject().apply {
                    if (systemPrompt.isNotBlank()) {
                        put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                    }
                    put("contents", contents)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", if (reasoningEffort == "high") 0.5 else 0.7)
                        if (slot.modelId.contains("2.5") || slot.modelId.contains("4") || slot.modelId.contains("3.6")) {
                            put("thinkingConfig", JSONObject().put("thinkingBudget", thinkingBudget))
                        }
                    })
                }
                Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            }

            "anthropic" -> {
                val url = if (cleanBase.endsWith("/messages")) cleanBase else "$cleanBase/messages"
                val messages = JSONArray()
                history.filter { it.role != "system" }.forEach { msg ->
                    messages.put(
                        JSONObject().apply {
                            put("role", if (msg.role == "assistant") "assistant" else "user")
                            put("content", msg.content)
                        }
                    )
                }
                val payload = JSONObject().apply {
                    put("model", slot.modelId)
                    put("max_tokens", 4096)
                    put("stream", true)
                    if (systemPrompt.isNotBlank()) put("system", systemPrompt)
                    put("messages", messages)
                    if (reasoningEffort != "low") {
                        put("thinking", JSONObject().apply {
                            put("type", "enabled")
                            put("budget_tokens", if (reasoningEffort == "high") 4096 else 1536)
                        })
                    }
                }
                Request.Builder()
                    .url(url)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            }

            else -> {
                // OpenAI, DeepSeek, Azure OpenAI, OpenRouter, aniModels, Custom
                val url = when {
                    cleanBase.endsWith("/chat/completions") -> cleanBase
                    slot.providerId == "azure" -> "$cleanBase/chat/completions?api-version=2024-10-21"
                    else -> "$cleanBase/chat/completions"
                }
                val messages = JSONArray()
                if (systemPrompt.isNotBlank()) {
                    messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
                }
                history.forEach { msg ->
                    messages.put(JSONObject().put("role", msg.role).put("content", msg.content))
                }
                val payload = JSONObject().apply {
                    put("model", slot.modelId)
                    put("stream", true)
                    put("messages", messages)
                    if (slot.modelId.startsWith("o3") || slot.modelId.startsWith("o1") || slot.modelId.contains("reasoner")) {
                        put("reasoning_effort", reasoningEffort)
                    }
                }
                val builder = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                if (slot.providerId == "azure") {
                    builder.addHeader("api-key", apiKey)
                } else {
                    builder.addHeader("Authorization", "Bearer $apiKey")
                }
                builder.post(payload.toString().toRequestBody(jsonMediaType)).build()
            }
        }
    }

    private data class ParsedChunk(
        val delta: String = "",
        val promptTokens: Long = 0L,
        val completionTokens: Long = 0L
    )

    private fun parseSseChunk(providerId: String, jsonStr: String): ParsedChunk {
        return try {
            val obj = JSONObject(jsonStr)
            when (providerId) {
                "gemini" -> {
                    val delta = obj.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text", "") ?: ""
                    val usage = obj.optJSONObject("usageMetadata")
                    ParsedChunk(
                        delta = delta,
                        promptTokens = usage?.optLong("promptTokenCount", 0L) ?: 0L,
                        completionTokens = usage?.optLong("candidatesTokenCount", 0L) ?: 0L
                    )
                }
                "anthropic" -> {
                    val type = obj.optString("type")
                    val delta = if (type == "content_block_delta") {
                        obj.optJSONObject("delta")?.optString("text", "") ?: ""
                    } else ""
                    val usage = obj.optJSONObject("usage") ?: obj.optJSONObject("message")?.optJSONObject("usage")
                    ParsedChunk(
                        delta = delta,
                        promptTokens = usage?.optLong("input_tokens", 0L) ?: 0L,
                        completionTokens = usage?.optLong("output_tokens", 0L) ?: 0L
                    )
                }
                else -> {
                    val choice = obj.optJSONArray("choices")?.optJSONObject(0)
                    val deltaObj = choice?.optJSONObject("delta")
                    val content = deltaObj?.optString("content", "") ?: ""
                    val reasoning = deltaObj?.optString("reasoning_content", "") ?: ""
                    val combined = if (reasoning.isNotEmpty() && content.isEmpty()) "" else content
                    val usage = obj.optJSONObject("usage")
                    ParsedChunk(
                        delta = combined,
                        promptTokens = usage?.optLong("prompt_tokens", 0L) ?: 0L,
                        completionTokens = usage?.optLong("completion_tokens", 0L) ?: 0L
                    )
                }
            }
        } catch (_: Exception) {
            ParsedChunk()
        }
    }

    suspend fun executeWebhookPlugin(plugin: PluginEntity, userQuery: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (plugin.webhookUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Webhook URL is empty"))
            }
            val builder = Request.Builder().url(plugin.webhookUrl)
            runCatching {
                val headers = JSONObject(plugin.headersJson)
                headers.keys().forEach { key ->
                    builder.addHeader(key, headers.optString(key))
                }
            }
            if (plugin.httpMethod.equals("GET", ignoreCase = true)) {
                builder.get()
            } else {
                val bodyStr = plugin.bodyTemplate.replace("{{query}}", JSONObject.quote(userQuery).trim('"'))
                builder.post(bodyStr.toRequestBody(jsonMediaType))
            }
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (resp.isSuccessful) Result.success(text.take(2000))
                else Result.failure(IOException("HTTP ${resp.code}: ${text.take(200)}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveModelForPreset(providerId: String, configuredModelId: String, presetId: String): String {
        if (providerId != "gemini") return configuredModelId
        return when (presetId) {
            "DAILY" -> "gemini-4-hydrogen"
            "DEEP" -> "gemini-4-neon"
            else -> configuredModelId
        }
    }

    private fun estimateTokens(text: String): Long = max(1L, (text.length / 3.5).toLong())

    private fun dateKey(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
    private fun monthKey(ms: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(ms))
}
