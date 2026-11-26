package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ArtifactEntity
import com.example.data.local.CustomizationState
import com.example.data.local.DevPostEntity
import com.example.data.local.PasswordHasher
import com.example.data.local.PersonalContext
import com.example.domain.prompts.PromptMarqueeGenerator
import com.example.domain.router.CommandRouter
import com.example.domain.router.RouteDecision
import com.example.telemetry.JarvisCrashReporter
import com.example.telemetry.JarvisPerformanceTracker
import com.example.tts.JarvisTtsManager
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.EmojiStyle
import com.example.ui.theme.PalettePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches JARVIS`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("JARVIS", appName)
    }

    @Test
    fun `local math router evaluates expressions with zero tokens`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val router = CommandRouter(context)
        val decision = router.route("24 * 5 + sqrt(144)", AppLanguage.RU)
        assertTrue(decision is RouteDecision.LocalHandled)
        val reply = (decision as RouteDecision.LocalHandled).replyText
        assertTrue(reply.contains("132"))
    }

    @Test
    fun `tts sanitizer strips markdown urls and code blocks`() {
        val raw = "### Привет!\n**Ответ**: проверьте https://example.com и код:\n```kotlin\nval x = 1\n```"
        val clean = JarvisTtsManager.sanitizeForSpeech(raw)
        assertFalse(clean.contains("###"))
        assertFalse(clean.contains("**"))
        assertFalse(clean.contains("https://"))
        assertFalse(clean.contains("val x = 1"))
        assertTrue(clean.contains("Привет!"))
    }

    @Test
    fun `personal context compresses cleanly for system prompt`() {
        val ctx = PersonalContext(music = "Synthwave", movies = "Dune", hobbies = "Android", communicationStyle = "Кратко", exclusions = "Без воды")
        val prompt = ctx.toCompressedPrompt()
        assertTrue(prompt.contains("Music:Synthwave"))
        assertTrue(prompt.contains("Avoid:Без воды"))
    }

    @Test
    fun `password hasher produces salted sha256 and verifies correctly`() {
        val salt1 = PasswordHasher.generateSalt()
        val salt2 = PasswordHasher.generateSalt()
        assertNotEquals(salt1, salt2)
        val hash = PasswordHasher.hashPassword("secret123", salt1)
        assertTrue(PasswordHasher.verify("secret123", salt1, hash))
        assertFalse(PasswordHasher.verify("wrong", salt1, hash))
    }

    @Test
    fun `artifact and devpost entities hold expected metadata`() {
        val note = ArtifactEntity(
            title = "Заметка",
            type = "DOCUMENT",
            category = "All",
            contentOrUri = "- [ ] Сделать сборку",
            tagsCsv = "work,dev",
            isPinned = true
        )
        val post = DevPostEntity(title = "Релиз", body = "Добавлен DevBlog", category = "Добавлено", versionTag = "v1.1")
        assertTrue(note.isPinned)
        assertEquals("work,dev", note.tagsCsv)
        assertEquals("Добавлено", post.category)
    }

    @Test
    fun `fourteen dark palettes and emoji styles are configured`() {
        assertEquals(14, PalettePreset.entries.size)
        assertEquals(4, PalettePreset.entries.count { it.isGradient })
        assertEquals(4, EmojiStyle.entries.size)
    }

    @Test
    fun `prompt marquee generator spans 10000 variants with shuffle and emoji styles`() {
        assertEquals(100, PromptMarqueeGenerator.verbCount())
        assertEquals(100, PromptMarqueeGenerator.topicCount())
        assertEquals(10_000, PromptMarqueeGenerator.TOTAL_COMBINATIONS)

        val p1 = PromptMarqueeGenerator.promptAt(10, 12345L)
        val p2 = PromptMarqueeGenerator.promptAt(11, 12345L)
        val pShuffled = PromptMarqueeGenerator.promptAt(10, 99999L)
        assertNotEquals(p1, p2)
        assertNotEquals(p1, pShuffled)

        val plain = PromptMarqueeGenerator.displayPromptAt(10, 12345L, EmojiStyle.NONE)
        assertEquals(p1, plain)
        val decorated = PromptMarqueeGenerator.displayPromptAt(10, 12345L, EmojiStyle.MINIMAL_KAOMOJI)
        assertTrue(decorated.length > plain.length)
    }

    @Test
    fun `crash reporter redacts secrets and safely handles disabled or uninitialized firebase`() {
        val redacted = JarvisCrashReporter.sanitizeMessage("Authorization: Bearer sk-1234567890abcdef and key=AIzaSyAbcdefghijklmno")
        assertFalse(redacted.contains("sk-1234567890abcdef"))
        assertFalse(redacted.contains("AIzaSyAbcdefghijklmno"))

        val hash1 = JarvisCrashReporter.sha256Hex("1:Viktor")
        val hash2 = JarvisCrashReporter.sha256Hex("1:Viktor")
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
        assertFalse(hash1.contains("Viktor"))

        JarvisCrashReporter.setCollectionEnabled(false)
        assertFalse(JarvisCrashReporter.isCollectionEnabled())
        JarvisCrashReporter.logBreadcrumb("should_not_crash")
        JarvisCrashReporter.recordNonFatal(IllegalStateException("safe"), "unit_test")
        JarvisCrashReporter.setCollectionEnabled(true)
        assertTrue(JarvisCrashReporter.isCollectionEnabled())
    }

    @Test
    fun `performance tracker strips query tokens from urls and runs traces safely`() {
        val sanitized = JarvisPerformanceTracker.sanitizeUrl("https://generativelanguage.googleapis.com/v1beta/models/gemini:streamGenerateContent?key=SECRET123&alt=sse")
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini:streamGenerateContent", sanitized)
        assertFalse(sanitized.contains("SECRET123"))

        val trace = JarvisPerformanceTracker.startLlmChatCompletionTrace("gemini", "gemini-2.5-flash", hasMediaAttachment = true)
        trace.putMetric("prompt_tokens", 120L)
        trace.putMetric("completion_tokens", 80L)
        trace.putMetric("retry_count", 0L)
        trace.stop()

        val httpMetric = JarvisPerformanceTracker.startHttpMetric(sanitized, "POST")
        httpMetric.setResponseCode(200)
        httpMetric.stop()

        assertTrue(CustomizationState().telemetryEnabled)
    }

    @Test
    fun `provider catalog contains only 4 billing-free or aggregator presets without hallucinated models`() {
        val ids = com.example.data.network.ProviderCatalog.providers.map { it.id }
        assertEquals(listOf("firebase_ai", "gemini", "openrouter", "anymodels"), ids)
        val allModels = com.example.data.network.ProviderCatalog.providers.flatMap { it.models }.map { it.apiModelId }
        assertFalse(allModels.contains("gemini-3.8-flash"))
        assertFalse(allModels.contains("gemini-3.5-flash-lite"))
        assertFalse(allModels.contains("gemini-4-argon"))
    }

    @Test
    fun `url normalizer maps openrouter api path to api v1 chat completions`() {
        val url = com.example.data.network.ProviderUrlNormalizer.buildChatCompletionsUrl(
            providerId = "openrouter",
            rawBaseUrl = "https://openrouter.ai/api",
            fallbackBaseUrl = "https://openrouter.ai/api/v1"
        )
        assertEquals("https://openrouter.ai/api/v1/chat/completions", url)

        val modelsUrl = com.example.data.network.ProviderUrlNormalizer.buildModelsUrl(
            providerId = "openrouter",
            rawBaseUrl = "https://openrouter.ai/api",
            fallbackBaseUrl = "https://openrouter.ai/api/v1"
        )
        assertEquals("https://openrouter.ai/api/v1/models", modelsUrl)
    }

    @Test
    fun `llm client strips placeholders errors and enforces cyrillic 2800 token budget even on short history`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefs = com.example.data.local.EncryptedPrefsManager(context)
            val client = com.example.data.network.LlmFailoverClient(prefs, db.providerSlotDao(), db.tokenUsageDao())

            val bigText = "Привет ".repeat(1500) // ~10,500 chars > 5,600 chars (~2800 Cyrillic tokens)
            val history = listOf(
                com.example.data.local.MessageEntity(chatId = 1L, role = "user", content = bigText),
                com.example.data.local.MessageEntity(chatId = 1L, role = "assistant", content = "..."),
                com.example.data.local.MessageEntity(chatId = 1L, role = "assistant", content = "⚠ Ошибка"),
                com.example.data.local.MessageEntity(chatId = 1L, role = "user", content = "Короткий вопрос")
            )

            val trimmed = client.trimContextForBudget(history, maxEstimatedTokens = 2800)
            assertFalse(trimmed.any { it.content == "..." || it.content.startsWith("⚠") })
            assertTrue(trimmed.sumOf { it.content.length } <= 5600)
            assertTrue(trimmed.any { it.content == "Короткий вопрос" })
        } finally {
            db.close()
        }
    }

    @Test
    fun `llm client handles reasoning_effort allowlist thinking hint diagnostics retry-after and escaping`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefs = com.example.data.local.EncryptedPrefsManager(context)
            val client = com.example.data.network.LlmFailoverClient(prefs, db.providerSlotDao(), db.tokenUsageDao())

            assertFalse(client.supportsReasoningEffort("google/gemini-2.5-flash"))
            assertTrue(client.supportsReasoningEffort("openai/o3-mini"))
            assertTrue(client.supportsReasoningEffort("deepseek/deepseek-reasoner"))

            val normalPayload = client.buildOpenAiCompatiblePayloadJson(
                modelId = "google/gemini-2.5-flash",
                messages = org.json.JSONArray(),
                reasoningEffort = "high",
                temperature = 0.7f,
                includeStreamOptions = true
            )
            assertFalse(normalPayload.has("reasoning_effort"))
            assertEquals(4096, normalPayload.getInt("max_tokens"))
            assertTrue(normalPayload.getJSONObject("stream_options").getBoolean("include_usage"))

            val reasoningChunk = client.parseSseChunk(
                "openrouter",
                """{"choices":[{"delta":{"reasoning_content":"step 1"}}]}"""
            )
            assertTrue(reasoningChunk.isReasoningHint)
            assertEquals("[Думает...]", reasoningChunk.delta)

            val diag402 = client.formatHttpDiagnostic("OpenRouter", "x", 402, "", "Payment Required")
            val diag403 = client.formatHttpDiagnostic("OpenRouter", "x", 403, "", "Forbidden")
            assertEquals("Модель недоступна на текущем тарифе или бонусном пакете (HTTP 402/403). Выберите другую модель из списка.", diag402)
            assertEquals(diag402, diag403)

            assertEquals(1000L, client.parseRetryAfterMs("0.2"))
            assertEquals(8000L, client.parseRetryAfterMs("30"))
            assertEquals("""Line1\n\"Quote\"""", client.escapeJsonStringContent("Line1\n\"Quote\""))
        } finally {
            db.close()
        }
    }

    @Test
    fun `encrypted prefs migrates legacy custom provider and non-destructively resets manual budget`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fallbackPrefs = context.getSharedPreferences("jarvis_encrypted_vault_fallback", Context.MODE_PRIVATE)
        fallbackPrefs.edit().clear().commit()
        fallbackPrefs.edit()
            .putString("active_provider_id", "custom")
            .putString("active_model_id", "gemini-3.8-flash")
            .putString("active_model_display", "Gemini 3.8 Flash")
            .putString("api_key_custom", "sk-legacy-key")
            .commit()

        val vaultPrefs = runCatching {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "jarvis_encrypted_vault",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
        vaultPrefs?.edit()?.apply {
            putString("active_provider_id", "custom")
            putString("active_model_id", "gemini-3.8-flash")
            putString("active_model_display", "Gemini 3.8 Flash")
            putString("api_key_custom", "sk-legacy-key")
            commit()
        }

        val manager = com.example.data.local.EncryptedPrefsManager(context)
        assertEquals("anymodels", manager.state.value.activeProviderId)
        assertEquals("gemini-2.5-flash", manager.state.value.activeModelId)
        assertEquals("sk-legacy-key", manager.getApiKey("anymodels"))
        assertEquals(5_000_000L, manager.state.value.manualTotalTokenBudget)

        manager.updateManualTokenBudget(10_000_000L)
        assertEquals(10_000_000L, manager.state.value.manualTotalTokenBudget)

        manager.resetManualBudgetCounter(nowMs = 123_456_789L)
        assertEquals(123_456_789L, manager.state.value.budgetResetTimestampMs)
    }

    @Test
    fun `live model fetcher sorts openrouter free models first and parses auth key balance`() {
        val rawModels = """
            {
              "data": [
                {"id": "openai/gpt-4o", "name": "GPT-4o", "context_length": 128000, "pricing": {"prompt": "0.0000025", "completion": "0.00001"}},
                {"id": "google/gemini-2.5-pro-exp:free", "name": "Gemini Pro Exp", "context_length": 1048576, "pricing": {"prompt": "0", "completion": "0"}}
              ]
            }
        """.trimIndent()
        val parsed = com.example.data.network.LiveModelFetcher.parseModelsPayload("openrouter", rawModels)
        assertEquals(2, parsed.size)
        assertTrue(parsed[0].isFree)
        assertEquals("google/gemini-2.5-pro-exp:free", parsed[0].apiModelId)
        assertEquals(1_048_576, parsed[0].contextLength)
        assertFalse(parsed[1].isFree)

        val keyStatus = com.example.data.network.LiveModelFetcher.parseOpenRouterKeyStatus(
            """{"data":{"limit_remaining":4.25,"usage":0.75}}"""
        )
        assertTrue(keyStatus.contains("4.2500"))
        assertTrue(keyStatus.contains("0.7500"))
    }
}
