package com.example.data.network

import android.util.Log
import com.example.data.local.EncryptedPrefsManager
import com.example.data.local.MessageEntity
import com.example.data.local.PluginEntity
import com.example.data.local.ProviderSlotDao
import com.example.data.local.ProviderSlotEntity
import com.example.data.local.TokenUsageDao
import com.example.data.local.TokenUsageEntity
import com.example.media.MediaAttachmentType
import com.example.media.MessageMediaPayload
import com.example.media.MultimodalMediaProcessor
import com.example.telemetry.JarvisCrashReporter
import com.example.telemetry.JarvisPerformanceTracker
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLException
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

private const val TAG = "LlmFailoverClient"
private const val DEFAULT_MAX_OUTPUT_TOKENS = 4096

class LlmFailoverClient(
    private val prefs: EncryptedPrefsManager,
    @Suppress("UNUSED_PARAMETER") private val slotDao: ProviderSlotDao,
    private val tokenUsageDao: TokenUsageDao
) {
    private val client = ProviderHttpClient.client
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Cyrillic-calibrated token-budget context trimmer:
     * Strips "..." placeholders and "⚠" error bubbles from outbound history,
     * estimates tokens at totalChars / 2.0, and enforces maxEstimatedTokens (default 2800 = ~5600 chars)
     * across ALL history sizes (even when history.size <= 6).
     */
    fun trimContextForBudget(history: List<MessageEntity>, maxEstimatedTokens: Int = 2800): List<MessageEntity> {
        val cleanHistory = history.filter { msg ->
            val trimmed = msg.content.trim()
            trimmed.isNotEmpty() && trimmed != "..." && !trimmed.startsWith("⚠")
        }
        if (cleanHistory.isEmpty()) return emptyList()

        val maxChars = (maxEstimatedTokens * 2.0).toInt().coerceAtLeast(200)
        val totalChars = cleanHistory.sumOf { it.content.length }
        if (totalChars / 2.0 <= maxEstimatedTokens) return cleanHistory

        // Keep as many recent messages as fit within 80% of budget (max 6), always keeping at least the last message.
        val recentBudget = (maxChars * 0.80).toInt().coerceAtLeast(160)
        val reversedRecent = mutableListOf<MessageEntity>()
        var recentChars = 0
        for (idx in cleanHistory.indices.reversed()) {
            if (reversedRecent.size >= 6) break
            val msg = cleanHistory[idx]
            if (reversedRecent.isEmpty()) {
                val safeMsg = if (msg.content.length > maxChars) {
                    msg.copy(content = msg.content.take(maxChars))
                } else {
                    msg
                }
                reversedRecent.add(safeMsg)
                recentChars += safeMsg.content.length
            } else if (recentChars + msg.content.length <= recentBudget) {
                reversedRecent.add(msg)
                recentChars += msg.content.length
            } else {
                break
            }
        }
        val recent = reversedRecent.reversed()
        val olderCount = cleanHistory.size - recent.size
        if (olderCount <= 0) return recent

        val remainingForSummary = maxChars - recentChars
        val summaryPrefix = "Compressed earlier conversation context: "
        if (remainingForSummary <= summaryPrefix.length + 24) {
            return recent
        }

        val older = cleanHistory.take(olderCount)
        val summaryBullets = older.takeLast(8).joinToString(" | ") { msg ->
            val snippet = msg.content.replace("\n", " ").take(80)
            "${msg.role}: $snippet"
        }
        val maxBulletsLen = (remainingForSummary - summaryPrefix.length).coerceAtLeast(16)
        val summaryText = summaryPrefix + summaryBullets.take(maxBulletsLen)
        val summaryEntity = MessageEntity(
            chatId = cleanHistory.first().chatId,
            role = "system",
            content = summaryText
        )
        return listOf(summaryEntity) + recent
    }

    fun streamCompletion(
        history: List<MessageEntity>,
        systemPrompt: String,
        reasoningEffort: String,
        temperature: Float,
        mediaPayload: MessageMediaPayload? = null
    ): Flow<StreamEvent> = flow {
        val state = prefs.state.value
        val trimmedHistory = trimContextForBudget(history)
        val now = System.currentTimeMillis()

        // Check hard daily token limit
        if (state.hardStopEnabled) {
            val todayKey = dateKey(now)
            val usedToday = tokenUsageDao.getDailyTokensOnce(todayKey)
            if (usedToday >= state.dailyTokenLimit) {
                emit(StreamEvent.Error("Достигнут дневной лимит токенов ($usedToday / ${state.dailyTokenLimit}). Измените лимит в разделе Лимиты."))
                return@flow
            }
        }

        val activeSpec = ProviderCatalog.findProvider(state.activeProviderId)
        val effectiveModelId = state.activeModelId.trim().ifBlank { activeSpec.models.first().apiModelId }
        val activeSlot = ProviderSlotEntity(
            id = -1L,
            priorityOrder = 0,
            providerId = activeSpec.id,
            providerDisplayName = activeSpec.displayName,
            baseUrl = state.activeBaseUrl.ifBlank { activeSpec.defaultBaseUrl },
            modelId = effectiveModelId,
            cooldownUntilMs = 0L,
            isEnabled = true
        )

        val apiKey = if (activeSlot.providerId == "firebase_ai") "" else prefs.getApiKey(activeSlot.providerId).trim()
        if (activeSlot.providerId != "firebase_ai" && apiKey.isBlank()) {
            emit(StreamEvent.Error("NO_API_KEY"))
            return@flow
        }

        // Strict single-provider routing: no silent cross-provider failover on errors.
        val llmTrace = JarvisPerformanceTracker.startLlmChatCompletionTrace(
            provider = activeSlot.providerId,
            model = activeSlot.modelId,
            hasMediaAttachment = (mediaPayload != null && mediaPayload.frames.isNotEmpty())
        )
        try {
            val result = executeWithRetry(
                slot = activeSlot,
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                history = trimmedHistory,
                reasoningEffort = reasoningEffort,
                temperature = temperature,
                mediaPayload = mediaPayload,
                onNotice = { notice -> emit(StreamEvent.FailoverNotice(notice)) },
                onBatchEmit = { accumulated ->
                    if (accumulated.isNotBlank()) {
                        emit(
                            StreamEvent.TokenDelta(
                                accumulatedText = accumulated,
                                modelTag = activeSlot.modelId,
                                providerName = activeSlot.providerDisplayName
                            )
                        )
                    }
                }
            )

            val cleanText = result.fullText.trim()
            if (cleanText.isEmpty()) {
                throw IOException("Модель вернула пустой ответ.")
            }

            val promptTokens = result.promptTokens.takeIf { it > 0 }
                ?: estimateTokens(systemPrompt + trimmedHistory.joinToString { it.content })
            val completionTokens = result.completionTokens.takeIf { it > 0 }
                ?: estimateTokens(cleanText)
            val cost = ProviderCatalog.estimateCostUsd(activeSlot.modelId, promptTokens, completionTokens)

            llmTrace.putMetric("prompt_tokens", promptTokens)
            llmTrace.putMetric("completion_tokens", completionTokens)
            llmTrace.putMetric("retry_count", result.retryCount.toLong())

            tokenUsageDao.insertUsage(
                TokenUsageEntity(
                    timestamp = System.currentTimeMillis(),
                    dateKey = dateKey(System.currentTimeMillis()),
                    monthKey = monthKey(System.currentTimeMillis()),
                    providerName = activeSlot.providerDisplayName,
                    modelId = activeSlot.modelId,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    estimatedCostUsd = cost
                )
            )

            emit(
                StreamEvent.Completed(
                    fullText = cleanText,
                    modelTag = activeSlot.modelId,
                    providerName = activeSlot.providerDisplayName,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpClientDiagnosticException) {
            Log.w(TAG, "HTTP diagnostic on ${activeSlot.providerId}: ${e.code} -> ${e.userMessage}")
            JarvisCrashReporter.recordNonFatal(e, "llm_http_${e.code}_${activeSlot.providerId}")
            emit(StreamEvent.Error(e.userMessage))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Generation timed out on ${activeSlot.providerId}", e)
            JarvisCrashReporter.recordNonFatal(e, "llm_timeout_${activeSlot.providerId}")
            emit(StreamEvent.Error("${activeSlot.providerDisplayName}: превышено время ожидания ответа (60 сек)."))
        } catch (e: IOException) {
            Log.e(TAG, "Network error on ${activeSlot.providerId}", e)
            JarvisCrashReporter.recordNonFatal(e, "llm_io_${activeSlot.providerId}")
            emit(StreamEvent.Error(formatNetworkException(activeSlot.providerDisplayName, e)))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error on ${activeSlot.providerId}", e)
            JarvisCrashReporter.recordNonFatal(e, "llm_unexpected_${activeSlot.providerId}")
            emit(StreamEvent.Error("${activeSlot.providerDisplayName}: ${e.localizedMessage ?: "неизвестная ошибка"}"))
        } finally {
            llmTrace.stop()
        }
    }.flowOn(Dispatchers.IO)

    private data class RequestResult(
        val fullText: String,
        val promptTokens: Long,
        val completionTokens: Long,
        val retryCount: Int = 0
    )

    internal class HttpClientDiagnosticException(
        val code: Int,
        val userMessage: String,
        val retryAfterMs: Long? = null,
        val mentionsStreamOptions: Boolean = false
    ) : Exception(userMessage)

    private suspend fun executeWithRetry(
        slot: ProviderSlotEntity,
        apiKey: String,
        systemPrompt: String,
        history: List<MessageEntity>,
        reasoningEffort: String,
        temperature: Float,
        mediaPayload: MessageMediaPayload?,
        onNotice: suspend (String) -> Unit,
        onBatchEmit: suspend (String) -> Unit
    ): RequestResult {
        if (slot.providerId == "firebase_ai") {
            return executeFirebaseAiStream(
                modelId = slot.modelId,
                systemPrompt = systemPrompt,
                history = history,
                temperature = temperature,
                mediaPayload = mediaPayload,
                onBatchEmit = onBatchEmit
            )
        }

        val backoffMs = longArrayOf(1500L, 3000L)
        var attempt = 0
        var includeStreamOptions = true
        var streamOptionsRetried = false

        while (true) {
            try {
                return executeHttpStreamingRequest(
                    slot = slot,
                    apiKey = apiKey,
                    systemPrompt = systemPrompt,
                    history = history,
                    reasoningEffort = reasoningEffort,
                    temperature = temperature,
                    mediaPayload = mediaPayload,
                    includeStreamOptions = includeStreamOptions,
                    onBatchEmit = onBatchEmit
                ).copy(retryCount = attempt)
            } catch (e: HttpClientDiagnosticException) {
                if (e.code == 400 && e.mentionsStreamOptions && includeStreamOptions && !streamOptionsRetried) {
                    streamOptionsRetried = true
                    includeStreamOptions = false
                    JarvisCrashReporter.logBreadcrumb("Retrying ${slot.providerId} without stream_options")
                    continue
                }
                if (e.code in listOf(429, 439, 503) && attempt < backoffMs.size) {
                    val waitMs = (e.retryAfterMs ?: backoffMs[attempt]).coerceIn(1000L, 8000L)
                    attempt++
                    JarvisCrashReporter.logBreadcrumb("LLM retry $attempt for ${slot.providerId} HTTP ${e.code} wait=${waitMs}ms")
                    onNotice("${slot.providerDisplayName}: HTTP ${e.code}, повтор ${attempt}/${backoffMs.size} через ${waitMs / 1000.0}с...")
                    delay(waitMs)
                } else {
                    throw e
                }
            }
        }
    }

    private suspend fun executeFirebaseAiStream(
        modelId: String,
        systemPrompt: String,
        history: List<MessageEntity>,
        temperature: Float,
        mediaPayload: MessageMediaPayload?,
        onBatchEmit: suspend (String) -> Unit
    ): RequestResult {
        val safeModel = modelId.trim().ifBlank { "gemini-2.5-flash" }
        val genConfig = generationConfig {
            this.temperature = temperature
            this.maxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS
        }
        val sysContent = if (systemPrompt.isNotBlank()) {
            content { text(systemPrompt) }
        } else null

        val model = Firebase.ai.generativeModel(
            modelName = safeModel,
            generationConfig = genConfig,
            systemInstruction = sysContent
        )

        val chatHistory = history.dropLast(1)
            .filter { it.role != "system" && it.content.isNotBlank() }
            .map { msg ->
                val role = if (msg.role == "assistant") "model" else "user"
                content(role = role) { text(msg.content) }
            }

        val latestUserText = history.lastOrNull()?.content.orEmpty()
        val promptContent = content(role = "user") {
            if (mediaPayload != null && mediaPayload.frames.isNotEmpty()) {
                val header = if (mediaPayload.type == MediaAttachmentType.VIDEO) {
                    "Анализ видео (${mediaPayload.summaryLabel}). Ниже приложены ключевые кадры с таймкодами:"
                } else {
                    "Приложено изображение (${mediaPayload.summaryLabel})."
                }
                text(header)
                mediaPayload.frames.forEachIndexed { idx, frame ->
                    val label = frame.label ?: if (mediaPayload.frames.size > 1) "[Кадр ${idx + 1}]" else null
                    if (!label.isNullOrBlank()) text(label)
                    val bmp = MultimodalMediaProcessor.decodeBase64ToBitmap(frame.base64Jpeg)
                    if (bmp != null) {
                        image(bmp)
                    }
                }
            }
            if (latestUserText.isNotBlank()) {
                text(latestUserText)
            } else if (mediaPayload == null || mediaPayload.frames.isEmpty()) {
                text("Привет!")
            }
        }

        val allContents = chatHistory + promptContent

        val accumulated = StringBuilder()
        var lastEmitMs = 0L
        var promptTokens = 0L
        var completionTokens = 0L

        withTimeout(60_000L) {
            val flow = model.generateContentStream(allContents)
            flow.collect { chunk ->
                chunk.usageMetadata?.let { meta ->
                    val p = meta.promptTokenCount.toLong()
                    val c = meta.candidatesTokenCount?.toLong() ?: 0L
                    if (p > 0L) promptTokens = p
                    if (c > 0L) completionTokens = c
                }
                val piece = chunk.text?.takeIf { it.isNotEmpty() && it != "null" }.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    val now = System.currentTimeMillis()
                    if (now - lastEmitMs >= 85L) {
                        lastEmitMs = now
                        onBatchEmit(accumulated.toString())
                    }
                }
            }
        }

        val finalText = accumulated.toString()
        if (finalText.isNotEmpty()) {
            onBatchEmit(finalText)
        }
        return RequestResult(finalText, promptTokens, completionTokens)
    }

    private suspend fun executeHttpStreamingRequest(
        slot: ProviderSlotEntity,
        apiKey: String,
        systemPrompt: String,
        history: List<MessageEntity>,
        reasoningEffort: String,
        temperature: Float,
        mediaPayload: MessageMediaPayload?,
        includeStreamOptions: Boolean,
        onBatchEmit: suspend (String) -> Unit
    ): RequestResult {
        val request = buildProviderRequest(
            slot = slot,
            apiKey = apiKey,
            systemPrompt = systemPrompt,
            history = history,
            reasoningEffort = reasoningEffort,
            temperature = temperature,
            mediaPayload = mediaPayload,
            includeStreamOptions = includeStreamOptions
        )
        val httpMetric = JarvisPerformanceTracker.startHttpMetric(request.url.toString(), request.method)
        httpMetric.putAttribute("provider", slot.providerId)
        runCatching { request.body?.contentLength() ?: -1L }.getOrDefault(-1L).let { reqLen ->
            if (reqLen >= 0L) httpMetric.setRequestPayloadSize(reqLen)
        }
        try {
            client.newCall(request).execute().use { response ->
                httpMetric.setResponseCode(response.code)
                httpMetric.setResponseContentType(response.header("Content-Type"))
                val retryAfterMs = parseRetryAfterMs(response.header("Retry-After"))
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    httpMetric.setResponsePayloadSize(errBody.toByteArray(Charsets.UTF_8).size.toLong())
                    val msg = formatHttpDiagnostic(slot.providerDisplayName, slot.modelId, response.code, errBody, response.message)
                    throw HttpClientDiagnosticException(
                        code = response.code,
                        userMessage = msg,
                        retryAfterMs = retryAfterMs,
                        mentionsStreamOptions = errBody.contains("stream_options", ignoreCase = true)
                    )
                }

                val body = response.body ?: throw IOException("Сервер вернул пустое тело ответа.")
                val source = body.source()
                val accumulated = StringBuilder()
                val rawNonSseBuffer = StringBuilder()
                var promptTokens = 0L
                var completionTokens = 0L
                var lastEmitMs = 0L
                var sawSseFrame = false
                var sentThinkingIndicator = false
                var bytesReceived = 0L

                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    bytesReceived += line.toByteArray(Charsets.UTF_8).size + 1L
                    if (line.isBlank()) continue
                    if (line.startsWith(":")) continue

                    if (!line.startsWith("data:")) {
                        if (!line.startsWith("event:") && !line.startsWith("id:") && !line.startsWith("retry:")) {
                            if (rawNonSseBuffer.length < 65_536) {
                                rawNonSseBuffer.append(line).append('\n')
                            }
                        }
                        continue
                    }

                    sawSseFrame = true
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") continue

                    val parsed = parseSseChunk(slot.providerId, data)
                    if (parsed.errorCode in listOf(429, 439, 503)) {
                        val msg = formatHttpDiagnostic(slot.providerDisplayName, slot.modelId, parsed.errorCode, data, parsed.errorMessage)
                        throw HttpClientDiagnosticException(parsed.errorCode, msg, retryAfterMs)
                    }
                    if (parsed.errorMessage.isNotBlank()) {
                        if (parsed.errorCode > 0) {
                            val msg = formatHttpDiagnostic(slot.providerDisplayName, slot.modelId, parsed.errorCode, data, parsed.errorMessage)
                            throw HttpClientDiagnosticException(parsed.errorCode, msg, retryAfterMs)
                        }
                        throw IOException(parsed.errorMessage)
                    }

                    if (parsed.isReasoningHint) {
                        if (accumulated.isEmpty() && !sentThinkingIndicator) {
                            sentThinkingIndicator = true
                            onBatchEmit(parsed.delta)
                        }
                    } else if (parsed.delta.isNotEmpty()) {
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

                httpMetric.setResponsePayloadSize(bytesReceived)

                if (accumulated.isEmpty() && rawNonSseBuffer.isNotBlank()) {
                    val rawStr = rawNonSseBuffer.toString()
                    val fallbackParsed = parseNonSseResponse(slot.providerId, rawStr)
                    if (fallbackParsed.errorCode in listOf(429, 439, 503)) {
                        val msg = formatHttpDiagnostic(slot.providerDisplayName, slot.modelId, fallbackParsed.errorCode, rawStr, fallbackParsed.errorMessage)
                        throw HttpClientDiagnosticException(fallbackParsed.errorCode, msg, retryAfterMs)
                    }
                    if (fallbackParsed.errorMessage.isNotBlank()) {
                        if (fallbackParsed.errorCode > 0) {
                            val msg = formatHttpDiagnostic(slot.providerDisplayName, slot.modelId, fallbackParsed.errorCode, rawStr, fallbackParsed.errorMessage)
                            throw HttpClientDiagnosticException(fallbackParsed.errorCode, msg, retryAfterMs)
                        }
                        throw IOException(fallbackParsed.errorMessage)
                    }
                    if (fallbackParsed.delta.isNotEmpty()) {
                        accumulated.append(fallbackParsed.delta)
                    }
                    if (fallbackParsed.promptTokens > 0) promptTokens = fallbackParsed.promptTokens
                    if (fallbackParsed.completionTokens > 0) completionTokens = fallbackParsed.completionTokens
                } else if (accumulated.isEmpty() && sawSseFrame && rawNonSseBuffer.isNotBlank()) {
                    val fallbackParsed = parseNonSseResponse(slot.providerId, rawNonSseBuffer.toString())
                    if (fallbackParsed.delta.isNotEmpty()) accumulated.append(fallbackParsed.delta)
                }

                val text = accumulated.toString()
                if (text.isNotEmpty()) {
                    onBatchEmit(text)
                }
                return RequestResult(text, promptTokens, completionTokens)
            }
        } finally {
            httpMetric.stop()
        }
    }

    internal fun buildProviderRequest(
        slot: ProviderSlotEntity,
        apiKey: String,
        systemPrompt: String,
        history: List<MessageEntity>,
        reasoningEffort: String,
        temperature: Float,
        mediaPayload: MessageMediaPayload?,
        includeStreamOptions: Boolean = true
    ): Request {
        val spec = ProviderCatalog.findProvider(slot.providerId)
        val lastUserIdx = history.indexOfLast { it.role == "user" }

        return when (slot.providerId) {
            "gemini" -> {
                val url = ProviderUrlNormalizer.buildGeminiStreamUrl(slot.baseUrl, slot.modelId)
                val contents = JSONArray()
                history.filter { it.role != "system" }.forEach { msg ->
                    val geminiRole = if (msg.role == "assistant") "model" else "user"
                    val isTargetMediaTurn = (mediaPayload != null && mediaPayload.frames.isNotEmpty() && msg.role == "user" && msg == history.getOrNull(lastUserIdx))
                    val parts = JSONArray()
                    if (isTargetMediaTurn && mediaPayload != null) {
                        if (mediaPayload.type == MediaAttachmentType.VIDEO) {
                            parts.put(JSONObject().put("text", "Анализ видео (${mediaPayload.summaryLabel}). Ключевые кадры:"))
                        }
                        mediaPayload.frames.forEachIndexed { frameIdx, frame ->
                            val label = frame.label ?: if (mediaPayload.frames.size > 1) "[Кадр ${frameIdx + 1}]" else null
                            if (!label.isNullOrBlank()) parts.put(JSONObject().put("text", label))
                            parts.put(
                                JSONObject().put(
                                    "inlineData",
                                    JSONObject().apply {
                                        put("mimeType", "image/jpeg")
                                        put("data", frame.base64Jpeg)
                                    }
                                )
                            )
                        }
                    }
                    parts.put(JSONObject().put("text", msg.content))
                    contents.put(
                        JSONObject().apply {
                            put("role", geminiRole)
                            put("parts", parts)
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
                        put("temperature", temperature.toDouble())
                        put("maxOutputTokens", DEFAULT_MAX_OUTPUT_TOKENS)
                        if (slot.modelId.contains("2.5")) {
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

            else -> {
                // OpenRouter & AnyModels / Custom Proxy (OpenAI-compatible)
                val url = ProviderUrlNormalizer.buildChatCompletionsUrl(slot.providerId, slot.baseUrl, spec.defaultBaseUrl)
                val messages = JSONArray()
                if (systemPrompt.isNotBlank()) {
                    messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
                }
                history.forEachIndexed { idx, msg ->
                    val isTargetMediaTurn = (mediaPayload != null && mediaPayload.frames.isNotEmpty() && idx == lastUserIdx)
                    if (isTargetMediaTurn && mediaPayload != null) {
                        val parts = JSONArray()
                        val textIntro = if (mediaPayload.type == MediaAttachmentType.VIDEO) {
                            "${msg.content}\n\n[Вложение: видео ${mediaPayload.summaryLabel}. Проанализируй последовательность кадров ниже]"
                        } else {
                            msg.content
                        }
                        parts.put(JSONObject().put("type", "text").put("text", textIntro))
                        mediaPayload.frames.forEach { frame ->
                            if (!frame.label.isNullOrBlank()) {
                                parts.put(JSONObject().put("type", "text").put("text", frame.label))
                            }
                            parts.put(
                                JSONObject().apply {
                                    put("type", "image_url")
                                    put(
                                        "image_url",
                                        JSONObject().put("url", "data:image/jpeg;base64,${frame.base64Jpeg}")
                                    )
                                }
                            )
                        }
                        messages.put(JSONObject().put("role", msg.role).put("content", parts))
                    } else {
                        messages.put(JSONObject().put("role", msg.role).put("content", msg.content))
                    }
                }

                val payload = buildOpenAiCompatiblePayloadJson(
                    modelId = slot.modelId,
                    messages = messages,
                    reasoningEffort = reasoningEffort,
                    temperature = temperature,
                    includeStreamOptions = includeStreamOptions
                )
                Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            }
        }
    }

    internal fun buildOpenAiCompatiblePayloadJson(
        modelId: String,
        messages: JSONArray,
        reasoningEffort: String,
        temperature: Float,
        includeStreamOptions: Boolean
    ): JSONObject {
        val isO1OrO3 = isO1OrO3Model(modelId)
        val supportsReasoningEffort = supportsReasoningEffort(modelId)

        return JSONObject().apply {
            put("model", modelId)
            put("stream", true)
            if (includeStreamOptions) {
                put("stream_options", JSONObject().put("include_usage", true))
            }
            if (isO1OrO3) {
                put("max_completion_tokens", DEFAULT_MAX_OUTPUT_TOKENS)
            } else {
                put("max_tokens", DEFAULT_MAX_OUTPUT_TOKENS)
                put("temperature", temperature.toDouble())
            }
            if (supportsReasoningEffort) {
                put("reasoning_effort", reasoningEffort)
            }
            put("messages", messages)
        }
    }

    internal fun supportsReasoningEffort(modelId: String): Boolean {
        val lower = modelId.lowercase(Locale.US).substringAfterLast('/')
        return lower.startsWith("o1") ||
            lower.startsWith("o3") ||
            lower.startsWith("o4-mini") ||
            lower.contains("deepseek-reasoner") ||
            lower.startsWith("gpt-5")
    }

    private fun isO1OrO3Model(modelId: String): Boolean {
        val lower = modelId.lowercase(Locale.US).substringAfterLast('/')
        return lower.startsWith("o1") || lower.startsWith("o3")
    }

    internal data class ParsedChunk(
        val delta: String = "",
        val promptTokens: Long = 0L,
        val completionTokens: Long = 0L,
        val errorMessage: String = "",
        val errorCode: Int = 0,
        val isReasoningHint: Boolean = false
    )

    private fun JSONObject?.safeOptString(key: String): String {
        if (this == null || !has(key) || isNull(key)) return ""
        return optString(key, "").takeIf { it != "null" } ?: ""
    }

    internal fun parseSseChunk(providerId: String, jsonStr: String): ParsedChunk {
        return try {
            val obj = JSONObject(jsonStr)
            if (obj.has("error") && !obj.isNull("error")) {
                val errObj = obj.optJSONObject("error")
                val errCode = errObj?.optInt("code", 0)?.takeIf { it > 0 }
                    ?: obj.optInt("code", 0).takeIf { it > 0 }
                    ?: 0
                val errText = errObj?.safeOptString("message").orEmpty()
                    .ifBlank { obj.safeOptString("message") }
                    .ifBlank { obj.safeOptString("error") }
                if (errText.isNotBlank() || errCode > 0) {
                    return ParsedChunk(errorMessage = errText.ifBlank { "HTTP $errCode" }, errorCode = errCode)
                }
            }

            when (providerId) {
                "gemini" -> {
                    val parts = obj.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                    val textBuilder = StringBuilder()
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val p = parts.optJSONObject(i)
                            val t = p.safeOptString("text")
                            if (t.isNotEmpty() && !p.optBoolean("thought", false)) {
                                textBuilder.append(t)
                            }
                        }
                    }
                    val usage = obj.optJSONObject("usageMetadata")
                    ParsedChunk(
                        delta = textBuilder.toString(),
                        promptTokens = usage?.optLong("promptTokenCount", 0L) ?: 0L,
                        completionTokens = usage?.optLong("candidatesTokenCount", 0L) ?: 0L
                    )
                }
                else -> {
                    val choice = obj.optJSONArray("choices")?.optJSONObject(0)
                    val deltaObj = choice?.optJSONObject("delta")
                    val messageObj = choice?.optJSONObject("message")
                    val content = deltaObj.safeOptString("content")
                        .ifEmpty { messageObj.safeOptString("content") }
                    val reasoningContent = deltaObj.safeOptString("reasoning_content")
                        .ifEmpty { messageObj.safeOptString("reasoning_content") }
                    val usage = obj.optJSONObject("usage")
                    if (content.isEmpty() && reasoningContent.isNotEmpty()) {
                        ParsedChunk(
                            delta = "[Думает...]",
                            promptTokens = usage?.optLong("prompt_tokens", 0L) ?: 0L,
                            completionTokens = usage?.optLong("completion_tokens", 0L) ?: 0L,
                            isReasoningHint = true
                        )
                    } else {
                        ParsedChunk(
                            delta = content,
                            promptTokens = usage?.optLong("prompt_tokens", 0L) ?: 0L,
                            completionTokens = usage?.optLong("completion_tokens", 0L) ?: 0L
                        )
                    }
                }
            }
        } catch (e: Exception) {
            JarvisCrashReporter.recordNonFatal(e, "llm_sse_parse_${providerId}")
            ParsedChunk()
        }
    }

    internal fun parseNonSseResponse(providerId: String, rawBody: String): ParsedChunk {
        return try {
            val obj = JSONObject(rawBody.trim())
            if (obj.has("error") && !obj.isNull("error")) {
                val errObj = obj.optJSONObject("error")
                val errCode = errObj?.optInt("code", 0)?.takeIf { it > 0 }
                    ?: obj.optInt("code", 0).takeIf { it > 0 }
                    ?: 0
                val errMsg = errObj.safeOptString("message")
                    .ifBlank { obj.safeOptString("message") }
                    .ifBlank { obj.safeOptString("error") }
                if (errMsg.isNotBlank() || errCode > 0) {
                    return ParsedChunk(errorMessage = errMsg.ifBlank { "HTTP $errCode" }, errorCode = errCode)
                }
            }
            when (providerId) {
                "gemini" -> {
                    val text = obj.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        .safeOptString("text")
                    val usage = obj.optJSONObject("usageMetadata")
                    ParsedChunk(
                        delta = text,
                        promptTokens = usage?.optLong("promptTokenCount", 0L) ?: 0L,
                        completionTokens = usage?.optLong("candidatesTokenCount", 0L) ?: 0L
                    )
                }
                else -> {
                    val choice = obj.optJSONArray("choices")?.optJSONObject(0)
                    val msg = choice?.optJSONObject("message").safeOptString("content")
                        .ifEmpty { choice.safeOptString("text") }
                    val usage = obj.optJSONObject("usage")
                    ParsedChunk(
                        delta = msg,
                        promptTokens = usage?.optLong("prompt_tokens", 0L) ?: 0L,
                        completionTokens = usage?.optLong("completion_tokens", 0L) ?: 0L
                    )
                }
            }
        } catch (e: Exception) {
            JarvisCrashReporter.recordNonFatal(e, "llm_json_parse_${providerId}")
            ParsedChunk()
        }
    }

    internal fun parseRetryAfterMs(headerValue: String?): Long? {
        val raw = headerValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val seconds = raw.toDoubleOrNull() ?: return null
        return (seconds * 1000.0).toLong().coerceIn(1000L, 8000L)
    }

    internal fun formatHttpDiagnostic(
        providerName: String,
        modelId: String,
        code: Int,
        rawBody: String,
        httpMessage: String
    ): String {
        val serverDetail = extractShortServerMessage(rawBody)
        return when (code) {
            401 -> "$providerName — Ошибка авторизации (HTTP 401): проверьте API-ключ и права доступа.${detailSuffix(serverDetail)}"
            402, 403 -> "Модель недоступна на текущем тарифе или бонусном пакете (HTTP 402/403). Выберите другую модель из списка."
            404 -> "$providerName — Модель «$modelId» или путь API не найдены (HTTP 404). Проверьте URL и точный ID модели.${detailSuffix(serverDetail)}"
            429, 439 -> "$providerName — Превышен лимит запросов / квота (HTTP $code). Попробуйте повторить запрос чуть позже.${detailSuffix(serverDetail)}"
            503 -> "$providerName — Сервер временно перегружен (HTTP 503) после автоматических повторов.${detailSuffix(serverDetail)}"
            500, 502, 504 -> "$providerName — Временный сбой на стороне сервера (HTTP $code).${detailSuffix(serverDetail)}"
            else -> "$providerName — Ошибка HTTP $code: ${serverDetail.ifBlank { httpMessage.ifBlank { "некорректный запрос" } }}"
        }
    }

    private fun extractShortServerMessage(rawBody: String): String {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val obj = JSONObject(trimmed)
            val msg = obj.optJSONObject("error")?.safeOptString("message").orEmpty()
                .ifBlank { obj.safeOptString("message") }
                .ifBlank { obj.safeOptString("detail") }
            msg.replace("\n", " ").take(160)
        } catch (_: Exception) {
            if (trimmed.startsWith("<")) "" else trimmed.replace("\n", " ").take(140)
        }
    }

    private fun detailSuffix(detail: String): String =
        if (detail.isBlank()) "" else "\nДетали: $detail"

    private fun formatNetworkException(providerName: String, e: IOException): String {
        val detail = when (e) {
            is UnknownHostException -> "не найден хост (проверьте интернет или URL-адрес)"
            is SocketTimeoutException -> "таймаут соединения с сервером"
            is SSLException -> "ошибка TLS/SSL-сертификата"
            else -> e.localizedMessage?.takeIf { it.isNotBlank() } ?: "сетевой сбой"
        }
        return "$providerName — Сетевая ошибка: $detail."
    }

    internal fun escapeJsonStringContent(raw: String): String {
        val quoted = JSONObject.quote(raw)
        return if (quoted.length >= 2) quoted.substring(1, quoted.length - 1) else quoted
    }

    suspend fun executeWebhookPlugin(plugin: PluginEntity, userQuery: String): Result<String> = withContext(Dispatchers.IO) {
        val httpMetric = JarvisPerformanceTracker.startHttpMetric(plugin.webhookUrl, plugin.httpMethod)
        try {
            if (plugin.webhookUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Webhook URL is empty"))
            }
            val builder = Request.Builder().url(plugin.webhookUrl)
            runCatching {
                val headers = JSONObject(plugin.headersJson)
                headers.keys().forEach { key ->
                    val valStr = headers.safeOptString(key)
                    if (valStr.isNotEmpty()) builder.addHeader(key, valStr)
                }
            }
            if (plugin.httpMethod.equals("GET", ignoreCase = true)) {
                builder.get()
            } else {
                val escapedQuery = escapeJsonStringContent(userQuery)
                val bodyStr = plugin.bodyTemplate.replace("{{query}}", escapedQuery)
                val reqBody = bodyStr.toRequestBody(jsonMediaType)
                runCatching { reqBody.contentLength() }.getOrDefault(-1L).let { len ->
                    if (len >= 0L) httpMetric.setRequestPayloadSize(len)
                }
                builder.post(reqBody)
            }
            client.newCall(builder.build()).execute().use { resp ->
                httpMetric.setResponseCode(resp.code)
                httpMetric.setResponseContentType(resp.header("Content-Type"))
                val text = resp.body?.string().orEmpty()
                httpMetric.setResponsePayloadSize(text.toByteArray(Charsets.UTF_8).size.toLong())
                if (resp.isSuccessful) {
                    Result.success(text.take(2000))
                } else {
                    val err = IOException("HTTP ${resp.code}: ${text.take(200)}")
                    JarvisCrashReporter.recordNonFatal(err, "webhook_http_${resp.code}")
                    Result.failure(err)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            JarvisCrashReporter.recordNonFatal(e, "webhook_plugin_error")
            Result.failure(e)
        } finally {
            httpMetric.stop()
        }
    }

    private fun estimateTokens(text: String): Long = max(1L, (text.length / 2.0).toLong())

    private fun dateKey(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
    private fun monthKey(ms: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(ms))
}
