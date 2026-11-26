package com.example.telemetry

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.security.MessageDigest

/**
 * Thread-safe, zero-crash singleton wrapper around Firebase Crashlytics.
 * Never leaks secrets/API keys/raw user identifiers and safely no-ops if Firebase
 * is unavailable (e.g., local Robolectric JVM tests or unconfigured environments).
 */
object JarvisCrashReporter {

    private const val TAG = "JarvisCrashReporter"
    private const val MAX_LOG_LEN = 240
    private const val MAX_KEY_VAL_LEN = 96

    @Volatile
    private var collectionEnabled: Boolean = true

    private fun instanceOrNull(): FirebaseCrashlytics? = runCatching {
        FirebaseApp.getInstance()
        FirebaseCrashlytics.getInstance()
    }.getOrNull()

    fun init(context: Context, enabled: Boolean) {
        collectionEnabled = enabled
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            instanceOrNull()?.setCrashlyticsCollectionEnabled(enabled)
        }.onFailure { err ->
            Log.w(TAG, "Crashlytics init skipped: ${err.message}")
        }
    }

    fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
        runCatching {
            instanceOrNull()?.setCrashlyticsCollectionEnabled(enabled)
        }.onFailure { err ->
            Log.w(TAG, "Failed to update Crashlytics collection flag: ${err.message}")
        }
    }

    fun isCollectionEnabled(): Boolean = collectionEnabled

    fun setAnonymizedUserId(rawIdentifier: String?) {
        val clean = rawIdentifier?.trim().orEmpty()
        val hashed = if (clean.isEmpty()) "" else sha256Hex(clean).take(32)
        runCatching {
            instanceOrNull()?.setUserId(hashed)
        }
    }

    fun updateContext(
        activeProvider: String,
        activeModel: String,
        reasoningEffort: String,
        uiThemePalette: String,
        overlayActive: Boolean
    ) {
        runCatching {
            val crashlytics = instanceOrNull() ?: return
            val keys = CustomKeysAndValues.Builder()
                .putString("active_provider", sanitizeValue(activeProvider))
                .putString("active_model", sanitizeValue(activeModel))
                .putString("reasoning_effort", sanitizeValue(reasoningEffort))
                .putString("ui_theme_palette", sanitizeValue(uiThemePalette))
                .putBoolean("overlay_active", overlayActive)
                .build()
            crashlytics.setCustomKeys(keys)
        }
    }

    fun setOverlayActive(active: Boolean) {
        runCatching {
            instanceOrNull()?.setCustomKey("overlay_active", active)
        }
    }

    fun logBreadcrumb(message: String) {
        if (!collectionEnabled) return
        val safeMsg = sanitizeMessage(message)
        if (safeMsg.isBlank()) return
        runCatching {
            instanceOrNull()?.log(safeMsg)
        }
    }

    fun recordNonFatal(throwable: Throwable, contextTag: String) {
        if (!collectionEnabled) return
        val safeTag = sanitizeValue(contextTag).ifBlank { "unknown" }
        runCatching {
            val crashlytics = instanceOrNull() ?: return
            crashlytics.log("non_fatal[$safeTag]: ${throwable.javaClass.simpleName}")
            val keys = CustomKeysAndValues.Builder()
                .putString("error_context", safeTag)
                .build()
            crashlytics.recordException(throwable, keys)
        }.onFailure { err ->
            Log.w(TAG, "Failed to record non-fatal [$safeTag]: ${err.message}")
        }
    }

    fun triggerTestCrash(): Nothing {
        logBreadcrumb("User triggered manual Crashlytics test crash")
        throw RuntimeException("JARVIS Crashlytics Test Crash")
    }

    internal fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    internal fun sanitizeValue(raw: String): String =
        redactSecrets(raw).replace('\n', ' ').trim().take(MAX_KEY_VAL_LEN)

    internal fun sanitizeMessage(raw: String): String =
        redactSecrets(raw).replace('\n', ' ').trim().take(MAX_LOG_LEN)

    private fun redactSecrets(input: String): String {
        return input
            .replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-+/=]+"), "$1[REDACTED]")
            .replace(Regex("(?i)(api[-_]?key|token|secret|password|authorization)\\s*[:=]\\s*\\S+"), "$1=[REDACTED]")
            .replace(Regex("(sk-[A-Za-z0-9_\\-]{8,}|AIza[0-9A-Za-z_\\-]{12,}|ghp_[A-Za-z0-9]{10,}|github_pat_[A-Za-z0-9_]{10,})"), "[REDACTED_KEY]")
    }
}
