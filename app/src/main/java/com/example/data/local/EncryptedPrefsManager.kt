package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.PalettePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.MessageDigest
import java.security.SecureRandom

object PasswordHasher {
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, saltBase64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val combined = "$saltBase64:$password".toByteArray(Charsets.UTF_8)
        val hashed = digest.digest(combined)
        return Base64.encodeToString(hashed, Base64.NO_WRAP)
    }

    fun verify(password: String, saltBase64: String, expectedHash: String): Boolean {
        return hashPassword(password, saltBase64) == expectedHash
    }
}

data class PersonalContext(
    val music: String = "",
    val movies: String = "",
    val hobbies: String = "",
    val communicationStyle: String = "Кратко, по делу, с техническими деталями",
    val exclusions: String = ""
) {
    fun toCompressedPrompt(): String {
        val parts = buildList {
            if (music.isNotBlank()) add("Music:$music")
            if (movies.isNotBlank()) add("Media:$movies")
            if (hobbies.isNotBlank()) add("Interests:$hobbies")
            if (communicationStyle.isNotBlank()) add("Style:$communicationStyle")
            if (exclusions.isNotBlank()) add("Avoid:$exclusions")
        }
        return if (parts.isEmpty()) "" else "UserContext[${parts.joinToString("; ")}]"
    }
}

data class JarvPrefsState(
    val hasSeenOnboarding: Boolean = false,
    val loggedInUserId: Long? = null,
    val userName: String = "",
    val avatarUri: String? = null,
    val bannerUri: String? = null,
    val palettePreset: PalettePreset = PalettePreset.NEON_SAPPHIRE,
    val language: AppLanguage = AppLanguage.RU,
    val ttsEnabled: Boolean = false,
    val floatingOverlayEnabled: Boolean = false,
    val activeProviderId: String = "firebase_ai",
    val activeBaseUrl: String = "firebase://ai-logic",
    val activeModelId: String = "gemini-2.5-flash",
    val activeModelDisplayName: String = "Gemini 2.5 Flash (Firebase AI)",
    val activePresetId: String = "AUTO", // AUTO, DAILY, DEEP
    val reasoningEffort: String = "low", // low, medium, high
    val temperatureLevel: String = "medium", // low (0.2), medium (0.7), high (1.15)
    val skillsEnabled: Boolean = true,
    val canvasEnabled: Boolean = false,
    val activeGemId: Long = 1L,
    val dailyTokenLimit: Long = 250_000L,
    val monthlyBudgetUsd: Double = 20.0,
    val hardStopEnabled: Boolean = false,
    val manualTotalTokenBudget: Long = 5_000_000L,
    val budgetResetTimestampMs: Long = 0L,
    val notificationsEnabled: Boolean = true,
    val personalContext: PersonalContext = PersonalContext(),
    val artifactsSeeded: Boolean = false,
    val githubUsername: String = "",
    val googleAccountLabel: String = ""
) {
    fun resolveTemperatureFloat(): Float = when (temperatureLevel) {
        "low" -> 0.2f
        "high" -> 1.15f
        else -> 0.7f
    }
}

class EncryptedPrefsManager(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<JarvPrefsState> = _state.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: EncryptedPrefsManager? = null

        fun getInstance(context: Context): EncryptedPrefsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EncryptedPrefsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                "jarvis_encrypted_vault",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.applicationContext.getSharedPreferences("jarvis_encrypted_vault_fallback", Context.MODE_PRIVATE)
        }
    }

    private fun loadState(): JarvPrefsState {
        val uid = prefs.getLong("logged_in_user_id", -1L).takeIf { it > 0L }

        val rawProvider = prefs.getString("active_provider_id", "firebase_ai") ?: "firebase_ai"
        val rawBaseUrl = prefs.getString("active_base_url", "firebase://ai-logic") ?: "firebase://ai-logic"
        val rawModelId = prefs.getString("active_model_id", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        val rawDisplay = prefs.getString("active_model_display", "Gemini 2.5 Flash (Firebase AI)")
            ?: "Gemini 2.5 Flash (Firebase AI)"

        var migrated = false
        val migratedProvider = when (rawProvider) {
            "custom" -> {
                migrated = true
                "anymodels"
            }
            "openai", "anthropic", "deepseek", "azure" -> {
                migrated = true
                "gemini"
            }
            "firebase_ai", "gemini", "openrouter", "anymodels" -> rawProvider
            else -> {
                migrated = true
                "firebase_ai"
            }
        }

        val migratedModelId = when (rawModelId) {
            "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon" -> {
                migrated = true
                "gemini-2.5-flash"
            }
            else -> if (rawProvider in listOf("openai", "anthropic", "deepseek", "azure")) {
                migrated = true
                "gemini-2.5-flash"
            } else {
                rawModelId
            }
        }

        val migratedBaseUrl = when {
            rawProvider in listOf("openai", "anthropic", "deepseek", "azure") -> {
                migrated = true
                "https://generativelanguage.googleapis.com/v1beta"
            }
            migratedProvider == "anymodels" && rawBaseUrl.isBlank() -> {
                migrated = true
                "https://api.anymodels.ai/v1"
            }
            migratedProvider == "firebase_ai" -> "firebase://ai-logic"
            else -> rawBaseUrl
        }

        val migratedDisplay = when {
            rawModelId in listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon") ||
                rawDisplay.contains("3.8", ignoreCase = true) ||
                rawDisplay.contains("3.5", ignoreCase = true) ||
                rawDisplay.contains("Argon", ignoreCase = true) -> {
                migrated = true
                if (migratedProvider == "firebase_ai") "Gemini 2.5 Flash (Firebase AI)" else "Gemini 2.5 Flash"
            }
            rawProvider in listOf("openai", "anthropic", "deepseek", "azure") -> {
                migrated = true
                "Gemini 2.5 Flash"
            }
            else -> rawDisplay
        }

        if (migrated) {
            val edit = prefs.edit()
                .putString("active_provider_id", migratedProvider)
                .putString("active_base_url", migratedBaseUrl)
                .putString("active_model_id", migratedModelId)
                .putString("active_model_display", migratedDisplay)
            // Migrate saved legacy "custom" API key to "anymodels" if anymodels has no key yet
            if (rawProvider == "custom" && (prefs.getString("api_key_anymodels", "") ?: "").isBlank()) {
                val legacyCustomKey = prefs.getString("api_key_custom", "") ?: ""
                if (legacyCustomKey.isNotBlank()) {
                    edit.putString("api_key_anymodels", legacyCustomKey)
                }
            }
            edit.apply()
        }

        return JarvPrefsState(
            hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding", false),
            loggedInUserId = uid,
            userName = prefs.getString("user_name", "") ?: "",
            avatarUri = prefs.getString("avatar_uri", null),
            bannerUri = prefs.getString("banner_uri", null),
            palettePreset = PalettePreset.fromId(prefs.getString("palette_preset", PalettePreset.NEON_SAPPHIRE.id)),
            language = AppLanguage.fromCode(prefs.getString("language", AppLanguage.RU.code)),
            ttsEnabled = prefs.getBoolean("tts_enabled", false),
            floatingOverlayEnabled = prefs.getBoolean("floating_overlay", false),
            activeProviderId = migratedProvider,
            activeBaseUrl = migratedBaseUrl,
            activeModelId = migratedModelId,
            activeModelDisplayName = migratedDisplay,
            activePresetId = prefs.getString("active_preset_id", "AUTO") ?: "AUTO",
            reasoningEffort = prefs.getString("reasoning_effort", "low") ?: "low",
            temperatureLevel = prefs.getString("temperature_level", "medium") ?: "medium",
            skillsEnabled = prefs.getBoolean("skills_enabled", true),
            canvasEnabled = prefs.getBoolean("canvas_enabled", false),
            activeGemId = prefs.getLong("active_gem_id", 1L),
            dailyTokenLimit = prefs.getLong("daily_token_limit", 250_000L),
            monthlyBudgetUsd = prefs.getFloat("monthly_budget_usd", 20.0f).toDouble(),
            hardStopEnabled = prefs.getBoolean("hard_stop_enabled", false),
            manualTotalTokenBudget = prefs.getLong("manual_total_token_budget", 5_000_000L).coerceAtLeast(1_000L),
            budgetResetTimestampMs = prefs.getLong("budget_reset_timestamp_ms", 0L).coerceAtLeast(0L),
            notificationsEnabled = prefs.getBoolean("notifications_enabled", true),
            personalContext = PersonalContext(
                music = prefs.getString("ctx_music", "") ?: "",
                movies = prefs.getString("ctx_movies", "") ?: "",
                hobbies = prefs.getString("ctx_hobbies", "") ?: "",
                communicationStyle = prefs.getString("ctx_style", "Кратко, по делу, с техническими деталями") ?: "",
                exclusions = prefs.getString("ctx_exclusions", "") ?: ""
            ),
            artifactsSeeded = prefs.getBoolean("artifacts_seeded", false),
            githubUsername = prefs.getString("github_username", "") ?: "",
            googleAccountLabel = prefs.getString("google_account_label", "") ?: ""
        )
    }

    fun markOnboardingSeen() {
        prefs.edit().putBoolean("has_seen_onboarding", true).apply()
        _state.update { it.copy(hasSeenOnboarding = true) }
    }

    fun setLoggedInUser(user: LocalUserEntity?) {
        if (user == null) {
            prefs.edit().remove("logged_in_user_id").apply()
            _state.update { it.copy(loggedInUserId = null) }
        } else {
            prefs.edit()
                .putLong("logged_in_user_id", user.id)
                .putString("user_name", user.nickname)
                .putString("avatar_uri", user.avatarUri)
                .putString("banner_uri", user.bannerUri)
                .apply()
            _state.update {
                it.copy(
                    loggedInUserId = user.id,
                    userName = user.nickname,
                    avatarUri = user.avatarUri,
                    bannerUri = user.bannerUri
                )
            }
        }
    }

    fun updateUserMediaAndName(nickname: String, avatarUri: String?, bannerUri: String?) {
        val cleanName = nickname.trim().ifBlank { _state.value.userName.ifBlank { "JARVIS User" } }
        prefs.edit()
            .putString("user_name", cleanName)
            .putString("avatar_uri", avatarUri)
            .putString("banner_uri", bannerUri)
            .apply()
        _state.update {
            it.copy(
                userName = cleanName,
                avatarUri = avatarUri,
                bannerUri = bannerUri
            )
        }
    }

    fun getApiKey(providerId: String): String = prefs.getString("api_key_$providerId", "") ?: ""

    fun setApiKey(providerId: String, apiKey: String) {
        prefs.edit().putString("api_key_$providerId", apiKey.trim()).apply()
    }

    fun getGitHubPat(): String = prefs.getString("github_pat", "") ?: ""

    fun setGitHubAuth(pat: String, username: String) {
        prefs.edit()
            .putString("github_pat", pat.trim())
            .putString("github_username", username)
            .apply()
        _state.update { it.copy(githubUsername = username) }
    }

    fun getGoogleOAuthToken(): String = prefs.getString("google_oauth_token", "") ?: ""

    fun setGoogleOAuthToken(token: String, accountLabel: String) {
        prefs.edit()
            .putString("google_oauth_token", token.trim())
            .putString("google_account_label", accountLabel)
            .apply()
        _state.update { it.copy(googleAccountLabel = accountLabel) }
    }

    fun setGoogleAccountLabel(accountLabel: String) {
        prefs.edit()
            .putString("google_account_label", accountLabel.trim())
            .apply()
        _state.update { it.copy(googleAccountLabel = accountLabel.trim()) }
    }

    fun getConnectorToken(iconKey: String): String = prefs.getString("connector_token_$iconKey", "") ?: ""
    fun getConnectorEndpoint(iconKey: String): String = prefs.getString("connector_endpoint_$iconKey", "") ?: ""

    fun setConnectorCredentials(iconKey: String, endpoint: String, token: String) {
        prefs.edit()
            .putString("connector_endpoint_$iconKey", endpoint.trim())
            .putString("connector_token_$iconKey", token.trim())
            .apply()
    }

    fun saveProviderConfig(
        providerId: String,
        baseUrl: String,
        modelId: String,
        modelDisplayName: String,
        apiKey: String
    ) {
        setApiKey(providerId, apiKey)
        val cleanBase = baseUrl.trim().trimEnd('/')
        val cleanModel = modelId.trim()
        val cleanDisplay = modelDisplayName.trim()
        prefs.edit()
            .putString("active_provider_id", providerId)
            .putString("active_base_url", cleanBase)
            .putString("active_model_id", cleanModel)
            .putString("active_model_display", cleanDisplay)
            .apply()
        _state.update {
            it.copy(
                activeProviderId = providerId,
                activeBaseUrl = cleanBase,
                activeModelId = cleanModel,
                activeModelDisplayName = cleanDisplay
            )
        }
    }

    fun setPreset(presetId: String) {
        prefs.edit().putString("active_preset_id", presetId).apply()
        _state.update { it.copy(activePresetId = presetId) }
    }

    fun setReasoningEffort(effort: String) {
        val safe = if (effort in listOf("low", "medium", "high")) effort else "low"
        prefs.edit().putString("reasoning_effort", safe).apply()
        _state.update { it.copy(reasoningEffort = safe) }
    }

    fun setTemperatureLevel(level: String) {
        val safe = if (level in listOf("low", "medium", "high")) level else "medium"
        prefs.edit().putString("temperature_level", safe).apply()
        _state.update { it.copy(temperatureLevel = safe) }
    }

    fun setSkillsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("skills_enabled", enabled).apply()
        _state.update { it.copy(skillsEnabled = enabled) }
    }

    fun setCanvasEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("canvas_enabled", enabled).apply()
        _state.update { it.copy(canvasEnabled = enabled) }
    }

    fun setTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("tts_enabled", enabled).apply()
        _state.update { it.copy(ttsEnabled = enabled) }
    }

    fun setPalettePresetMirror(preset: PalettePreset) {
        prefs.edit().putString("palette_preset", preset.id).apply()
        _state.update { it.copy(palettePreset = preset) }
    }

    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putString("language", lang.code).apply()
        _state.update { it.copy(language = lang) }
    }

    fun setFloatingOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("floating_overlay", enabled).apply()
        _state.update { it.copy(floatingOverlayEnabled = enabled) }
    }

    fun setActiveGemId(gemId: Long) {
        prefs.edit().putLong("active_gem_id", gemId).apply()
        _state.update { it.copy(activeGemId = gemId) }
    }

    fun updatePersonalContext(ctx: PersonalContext) {
        prefs.edit()
            .putString("ctx_music", ctx.music)
            .putString("ctx_movies", ctx.movies)
            .putString("ctx_hobbies", ctx.hobbies)
            .putString("ctx_style", ctx.communicationStyle)
            .putString("ctx_exclusions", ctx.exclusions)
            .apply()
        _state.update { it.copy(personalContext = ctx) }
    }

    fun updateBudgetSettings(dailyTokenLimit: Long, monthlyBudgetUsd: Double, hardStopEnabled: Boolean) {
        prefs.edit()
            .putLong("daily_token_limit", dailyTokenLimit.coerceAtLeast(1000L))
            .putFloat("monthly_budget_usd", monthlyBudgetUsd.toFloat().coerceAtLeast(0.5f))
            .putBoolean("hard_stop_enabled", hardStopEnabled)
            .apply()
        _state.update {
            it.copy(
                dailyTokenLimit = dailyTokenLimit.coerceAtLeast(1000L),
                monthlyBudgetUsd = monthlyBudgetUsd.coerceAtLeast(0.5),
                hardStopEnabled = hardStopEnabled
            )
        }
    }

    fun updateManualTokenBudget(budgetTokens: Long) {
        val safe = budgetTokens.coerceAtLeast(1_000L)
        prefs.edit()
            .putLong("manual_total_token_budget", safe)
            .apply()
        _state.update { it.copy(manualTotalTokenBudget = safe) }
    }

    fun resetManualBudgetCounter(nowMs: Long = System.currentTimeMillis()) {
        val safeTs = nowMs.coerceAtLeast(0L)
        prefs.edit()
            .putLong("budget_reset_timestamp_ms", safeTs)
            .apply()
        _state.update { it.copy(budgetResetTimestampMs = safeTs) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        _state.update { it.copy(notificationsEnabled = enabled) }
    }

    fun markArtifactsSeeded() {
        prefs.edit().putBoolean("artifacts_seeded", true).apply()
        _state.update { it.copy(artifactsSeeded = true) }
    }
}
