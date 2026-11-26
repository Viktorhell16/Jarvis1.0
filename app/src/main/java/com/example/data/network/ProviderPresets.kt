package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ModelOption(
    val displayName: String,
    val apiModelId: String,
    val inputPricePer1M: Double,
    val outputPricePer1M: Double,
    val contextLength: Int = 0,
    val isFree: Boolean = false
)

data class ProviderSpec(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val requiresApiKey: Boolean = true,
    val models: List<ModelOption>
)

object ProviderHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}

object ProviderUrlNormalizer {

    fun normalizeBaseUrl(rawUrl: String, fallbackUrl: String = ""): String {
        var url = rawUrl.trim()
        if (url.isEmpty()) url = fallbackUrl.trim()
        if (url.isEmpty()) return ""

        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            val isLocal = url.startsWith("localhost", ignoreCase = true) ||
                url.startsWith("127.0.0.1") ||
                url.startsWith("10.") ||
                url.startsWith("192.168.")
            url = (if (isLocal) "http://" else "https://") + url
        }

        url = url.trimEnd('/')

        // Collapse duplicate version segments like /v1/v1 -> /v1
        while (url.contains("/v1/v1")) {
            url = url.replace("/v1/v1", "/v1")
        }

        // Strip trailing endpoint suffixes if user pasted full URL
        val suffixes = listOf(
            "/chat/completions",
            "/completions",
            "/messages",
            "/models"
        )
        var changed = true
        while (changed) {
            changed = false
            for (suffix in suffixes) {
                if (url.endsWith(suffix, ignoreCase = true)) {
                    url = url.dropLast(suffix.length).trimEnd('/')
                    changed = true
                }
            }
        }
        return url
    }

    fun buildChatCompletionsUrl(providerId: String, rawBaseUrl: String, fallbackBaseUrl: String): String {
        val rawTrimmed = rawBaseUrl.trim()
        val queryIndex = rawTrimmed.indexOf('?')
        val querySuffix = if (queryIndex >= 0) rawTrimmed.substring(queryIndex) else ""
        val withoutQuery = if (queryIndex >= 0) rawTrimmed.substring(0, queryIndex) else rawTrimmed

        val cleanBase = normalizeBaseUrl(withoutQuery, fallbackBaseUrl)
        val path = runCatching { URI(cleanBase).path.orEmpty().trimEnd('/') }.getOrDefault("")
        val baseWithVersion = when {
            path.isEmpty() || path.endsWith("/api", ignoreCase = true) -> "$cleanBase/v1"
            else -> cleanBase
        }
        return "$baseWithVersion/chat/completions$querySuffix"
    }

    fun buildModelsUrl(providerId: String, rawBaseUrl: String, fallbackBaseUrl: String): String {
        val cleanBase = normalizeBaseUrl(rawBaseUrl, fallbackBaseUrl)
        return when (providerId) {
            "gemini" -> "$cleanBase/models"
            else -> {
                val path = runCatching { URI(cleanBase).path.orEmpty().trimEnd('/') }.getOrDefault("")
                val withVersion = if (path.isEmpty() || path.endsWith("/api", ignoreCase = true)) {
                    "$cleanBase/v1"
                } else {
                    cleanBase
                }
                "$withVersion/models"
            }
        }
    }

    fun buildGeminiStreamUrl(rawBaseUrl: String, modelId: String): String {
        var cleanBase = normalizeBaseUrl(rawBaseUrl, "https://generativelanguage.googleapis.com/v1beta")
        val modelsIdx = cleanBase.indexOf("/models/")
        if (modelsIdx > 0) {
            cleanBase = cleanBase.substring(0, modelsIdx)
        }
        val cleanModel = modelId.trim().removePrefix("models/")
        return "$cleanBase/models/$cleanModel:streamGenerateContent?alt=sse"
    }
}

object LiveModelFetcher {

    suspend fun fetchAvailableModels(
        providerId: String,
        baseUrl: String,
        apiKey: String
    ): Result<List<ModelOption>> = withContext(Dispatchers.IO) {
        if (providerId == "firebase_ai") {
            return@withContext Result.success(ProviderCatalog.findProvider("firebase_ai").models)
        }
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Сначала введите API-ключ для этого провайдера."))
        }

        val spec = ProviderCatalog.findProvider(providerId)
        val url = ProviderUrlNormalizer.buildModelsUrl(providerId, baseUrl, spec.defaultBaseUrl)
        if (url.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Укажите корректный URL-адрес провайдера."))
        }

        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")

            if (providerId == "gemini") {
                reqBuilder.addHeader("x-goog-api-key", cleanKey)
            } else {
                reqBuilder.addHeader("Authorization", "Bearer $cleanKey")
            }

            ProviderHttpClient.client.newCall(reqBuilder.build()).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val serverMsg = extractErrorMessage(rawBody)
                    val msg = when (resp.code) {
                        401 -> "Ошибка 401: неверный или недействительный API-ключ."
                        402, 403 -> "Модель недоступна на текущем тарифе или бонусном пакете (HTTP 402/403). Выберите другую модель из списка."
                        404 -> "Ошибка 404: эндпоинт /models не найден по адресу $url."
                        else -> "HTTP ${resp.code}: ${serverMsg.ifBlank { resp.message }}"
                    }
                    return@withContext Result.failure(IllegalStateException(msg))
                }

                val parsed = parseModelsPayload(providerId, rawBody)
                if (parsed.isEmpty()) {
                    Result.failure(IllegalStateException("Сервер вернул пустой список моделей."))
                } else {
                    Result.success(parsed)
                }
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Не удалось загрузить модели: ${e.localizedMessage ?: "ошибка сети"}"))
        }
    }

    suspend fun fetchOpenRouterKeyStatus(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Введите API-ключ OpenRouter."))
        }
        try {
            val req = Request.Builder()
                .url("https://openrouter.ai/api/v1/auth/key")
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer $cleanKey")
                .build()
            ProviderHttpClient.client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("OpenRouter /auth/key HTTP ${resp.code}"))
                }
                val statusText = parseOpenRouterKeyStatus(raw)
                Result.success(statusText)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Не удалось проверить ключ OpenRouter: ${e.localizedMessage ?: "ошибка сети"}"))
        }
    }

    internal fun parseOpenRouterKeyStatus(rawJson: String): String {
        val root = JSONObject(rawJson)
        val data = root.optJSONObject("data") ?: root
        return if (!data.isNull("limit_remaining")) {
            val remaining = data.optDouble("limit_remaining", 0.0)
            val usage = data.optDouble("usage", 0.0)
            "Баланс OpenRouter: $${String.format(Locale.US, "%.4f", remaining)} (израсходовано $${String.format(Locale.US, "%.4f", usage)})"
        } else if (data.optBoolean("is_free_tier", false)) {
            "Бесплатный тариф OpenRouter"
        } else {
            "Ключ OpenRouter активен (без жёсткого лимита)"
        }
    }

    internal fun parseModelsPayload(providerId: String, rawBody: String): List<ModelOption> {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return emptyList()
        val list = mutableListOf<ModelOption>()

        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: id
                val ctx = item.optInt("context_length", 0).coerceAtLeast(0)
                list.add(
                    ModelOption(
                        displayName = name,
                        apiModelId = id,
                        inputPricePer1M = 0.0,
                        outputPricePer1M = 0.0,
                        contextLength = ctx,
                        isFree = id.endsWith(":free", ignoreCase = true)
                    )
                )
            }
            return list.distinctBy { it.apiModelId }
        }

        val root = JSONObject(trimmed)
        if (providerId == "gemini" || root.has("models")) {
            val arr = root.optJSONArray("models") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val rawName = item.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: continue
                val id = rawName.removePrefix("models/")
                val display = item.optString("displayName").takeIf { it.isNotBlank() && it != "null" } ?: id
                val ctx = item.optInt("inputTokenLimit", 0).coerceAtLeast(0)
                val isFreeTier = id.contains("flash", ignoreCase = true)
                list.add(
                    ModelOption(
                        displayName = display,
                        apiModelId = id,
                        inputPricePer1M = if (id.contains("pro")) 1.25 else 0.15,
                        outputPricePer1M = if (id.contains("pro")) 10.00 else 0.60,
                        contextLength = ctx,
                        isFree = isFreeTier
                    )
                )
            }
        } else {
            val arr = root.optJSONArray("data") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                val display = item.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: id
                val ctx = item.optInt("context_length", 0).coerceAtLeast(0)

                val pricing = item.optJSONObject("pricing")
                val promptPriceStr = pricing?.optString("prompt")?.takeIf { it.isNotBlank() && it != "null" }
                val completionPriceStr = pricing?.optString("completion")?.takeIf { it.isNotBlank() && it != "null" }
                val promptPerTok = promptPriceStr?.toDoubleOrNull() ?: 0.0
                val completionPerTok = completionPriceStr?.toDoubleOrNull() ?: 0.0

                val isOpenRouterFree = providerId == "openrouter" && (
                    ((promptPriceStr == "0" || promptPerTok == 0.0) &&
                        (completionPriceStr == "0" || completionPerTok == 0.0) &&
                        pricing != null) ||
                        id.endsWith(":free", ignoreCase = true) ||
                        id.equals("openrouter/free", ignoreCase = true)
                    )

                list.add(
                    ModelOption(
                        displayName = display,
                        apiModelId = id,
                        inputPricePer1M = if (pricing != null) promptPerTok * 1_000_000.0 else 0.50,
                        outputPricePer1M = if (pricing != null) completionPerTok * 1_000_000.0 else 1.50,
                        contextLength = ctx,
                        isFree = isOpenRouterFree
                    )
                )
            }
        }

        val deduped = list.distinctBy { it.apiModelId }
        return if (providerId == "openrouter") {
            deduped.sortedWith(
                compareByDescending<ModelOption> { it.isFree }
                    .thenBy { it.displayName.lowercase() }
            )
        } else {
            deduped.sortedBy { it.displayName.lowercase() }
        }
    }

    private fun extractErrorMessage(rawBody: String): String {
        return try {
            val obj = JSONObject(rawBody)
            val err = obj.optJSONObject("error")
            val msg = err?.optString("message")?.takeIf { it.isNotBlank() && it != "null" }
                ?: obj.optString("message").takeIf { it.isNotBlank() && it != "null" }
            msg?.take(140) ?: ""
        } catch (_: Exception) {
            rawBody.take(140)
        }
    }
}

object ProviderCatalog {
    val providers: List<ProviderSpec> = listOf(
        ProviderSpec(
            id = "firebase_ai",
            displayName = "Firebase AI Logic (Gemini — Без ключа)",
            defaultBaseUrl = "firebase://ai-logic",
            requiresApiKey = false,
            models = listOf(
                ModelOption("Gemini 2.5 Flash (Firebase AI)", "gemini-2.5-flash", 0.0, 0.0, 1_048_576, true),
                ModelOption("Gemini 2.5 Flash-Lite (Firebase AI)", "gemini-2.5-flash-lite", 0.0, 0.0, 1_048_576, true),
                ModelOption("Gemini 2.5 Pro (Firebase AI)", "gemini-2.5-pro", 0.0, 0.0, 1_048_576, true)
            )
        ),
        ProviderSpec(
            id = "gemini",
            displayName = "Google Gemini (AI Studio Free/Key)",
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
            requiresApiKey = true,
            models = listOf(
                ModelOption("Gemini 2.5 Flash", "gemini-2.5-flash", 0.15, 0.60, 1_048_576, true),
                ModelOption("Gemini 2.5 Flash-Lite", "gemini-2.5-flash-lite", 0.075, 0.30, 1_048_576, true),
                ModelOption("Gemini 2.5 Pro", "gemini-2.5-pro", 1.25, 10.00, 1_048_576, false)
            )
        ),
        ProviderSpec(
            id = "openrouter",
            displayName = "OpenRouter (Free & Paid Models)",
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            requiresApiKey = true,
            models = listOf(
                ModelOption("OpenRouter Free Router", "openrouter/free", 0.0, 0.0, 128_000, true),
                ModelOption("Gemini 2.5 Pro Exp (Free)", "google/gemini-2.5-pro-exp-03-25:free", 0.0, 0.0, 1_048_576, true),
                ModelOption("DeepSeek R1 (Free)", "deepseek/deepseek-r1:free", 0.0, 0.0, 64_000, true),
                ModelOption("Gemini 2.5 Flash (OR)", "google/gemini-2.5-flash", 0.15, 0.60, 1_048_576, false)
            )
        ),
        ProviderSpec(
            id = "anymodels",
            displayName = "AnyModels / Custom Proxy",
            defaultBaseUrl = "https://api.anymodels.ai/v1",
            requiresApiKey = true,
            models = listOf(
                ModelOption("Gemini 2.5 Flash (Proxy)", "gemini-2.5-flash", 0.15, 0.60, 1_048_576, false),
                ModelOption("DeepSeek Chat (Proxy)", "deepseek-chat", 0.27, 1.10, 64_000, false),
                ModelOption("Claude Sonnet 4 (Proxy)", "claude-sonnet-4", 3.00, 15.00, 200_000, false)
            )
        )
    )

    fun findProvider(id: String): ProviderSpec =
        providers.find { it.id == id } ?: providers.first()

    fun estimateCostUsd(modelId: String, promptTokens: Long, completionTokens: Long): Double {
        val option = providers.flatMap { it.models }
            .find { it.apiModelId.equals(modelId, ignoreCase = true) || it.displayName.equals(modelId, ignoreCase = true) }
        val inPrice = option?.inputPricePer1M ?: 0.50
        val outPrice = option?.outputPricePer1M ?: 1.50
        return (promptTokens / 1_000_000.0) * inPrice + (completionTokens / 1_000_000.0) * outPrice
    }

    fun formatContextWindow(contextLength: Int): String {
        if (contextLength <= 0) return ""
        return when {
            contextLength >= 1_000_000 -> "${contextLength / 1_000_000}M ctx"
            contextLength >= 1_000 -> "${contextLength / 1_000}K ctx"
            else -> "$contextLength ctx"
        }
    }
}
