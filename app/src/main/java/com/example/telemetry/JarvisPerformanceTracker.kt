package com.example.telemetry

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.HttpMetric
import com.google.firebase.perf.metrics.Trace

/**
 * Thread-safe wrapper around Firebase Performance Monitoring.
 * Provides sanitized HttpMetric instrumentation and custom traces:
 *  - llm_chat_completion_trace
 *  - multimodal_media_encode_trace
 *  - room_db_cold_load_trace
 *  - overlay_bubble_startup_trace
 */
object JarvisPerformanceTracker {

    private const val TAG = "JarvisPerfTracker"
    private const val MAX_ATTR_LEN = 96

    @Volatile
    private var collectionEnabled: Boolean = true

    private fun instanceOrNull(): FirebasePerformance? = runCatching {
        FirebaseApp.getInstance()
        FirebasePerformance.getInstance()
    }.getOrNull()

    fun init(context: Context, enabled: Boolean) {
        collectionEnabled = enabled
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            instanceOrNull()?.isPerformanceCollectionEnabled = enabled
        }.onFailure { err ->
            Log.w(TAG, "Performance Monitoring init skipped: ${err.message}")
        }
    }

    fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
        runCatching {
            instanceOrNull()?.isPerformanceCollectionEnabled = enabled
        }.onFailure { err ->
            Log.w(TAG, "Failed to update Performance collection flag: ${err.message}")
        }
    }

    fun isCollectionEnabled(): Boolean = collectionEnabled

    class SafeTrace internal constructor(private val trace: Trace?) {
        fun putAttribute(name: String, value: String) {
            val t = trace ?: return
            runCatching {
                t.putAttribute(name.take(40), JarvisCrashReporter.sanitizeValue(value).take(MAX_ATTR_LEN))
            }
        }

        fun putMetric(name: String, value: Long) {
            val t = trace ?: return
            runCatching {
                t.putMetric(name.take(100), value)
            }
        }

        fun incrementMetric(name: String, by: Long = 1L) {
            val t = trace ?: return
            runCatching {
                t.incrementMetric(name.take(100), by)
            }
        }

        fun stop() {
            val t = trace ?: return
            runCatching { t.stop() }
        }
    }

    class SafeHttpMetric internal constructor(private val metric: HttpMetric?) {
        fun setRequestPayloadSize(bytes: Long) {
            val m = metric ?: return
            if (bytes < 0L) return
            runCatching { m.setRequestPayloadSize(bytes) }
        }

        fun setResponseCode(code: Int) {
            val m = metric ?: return
            if (code <= 0) return
            runCatching { m.setHttpResponseCode(code) }
        }

        fun setResponseContentType(contentType: String?) {
            val m = metric ?: return
            if (contentType.isNullOrBlank()) return
            runCatching { m.setResponseContentType(contentType.substringBefore(';').trim()) }
        }

        fun setResponsePayloadSize(bytes: Long) {
            val m = metric ?: return
            if (bytes < 0L) return
            runCatching { m.setResponsePayloadSize(bytes) }
        }

        fun putAttribute(name: String, value: String) {
            val m = metric ?: return
            runCatching {
                m.putAttribute(name.take(40), JarvisCrashReporter.sanitizeValue(value).take(MAX_ATTR_LEN))
            }
        }

        fun stop() {
            val m = metric ?: return
            runCatching { m.stop() }
        }
    }

    fun startTrace(traceName: String): SafeTrace {
        if (!collectionEnabled) return SafeTrace(null)
        val trace = runCatching {
            instanceOrNull()?.newTrace(traceName)?.apply { start() }
        }.getOrNull()
        return SafeTrace(trace)
    }

    fun startHttpMetric(rawUrl: String, method: String): SafeHttpMetric {
        if (!collectionEnabled) return SafeHttpMetric(null)
        val sanitizedUrl = sanitizeUrl(rawUrl)
        val normalizedMethod = normalizeMethod(method)
        val metric = runCatching {
            instanceOrNull()?.newHttpMetric(sanitizedUrl, normalizedMethod)?.apply { start() }
        }.getOrNull()
        return SafeHttpMetric(metric)
    }

    fun startLlmChatCompletionTrace(
        provider: String,
        model: String,
        hasMediaAttachment: Boolean
    ): SafeTrace {
        return startTrace("llm_chat_completion_trace").apply {
            putAttribute("provider", provider)
            putAttribute("model", model)
            putAttribute("has_media_attachment", hasMediaAttachment.toString())
        }
    }

    fun startMultimodalMediaEncodeTrace(mediaType: String): SafeTrace {
        return startTrace("multimodal_media_encode_trace").apply {
            putAttribute("media_type", mediaType)
        }
    }

    fun startRoomDbColdLoadTrace(): SafeTrace = startTrace("room_db_cold_load_trace")

    fun startOverlayBubbleStartupTrace(): SafeTrace = startTrace("overlay_bubble_startup_trace")

    internal fun sanitizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return "https://unknown"
        return runCatching {
            val uri = Uri.parse(trimmed)
            val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: "https"
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return "https://unknown"
            val portSuffix = if (uri.port != -1) ":${uri.port}" else ""
            val path = uri.encodedPath?.takeIf { it.isNotBlank() } ?: "/"
            "$scheme://$host$portSuffix$path"
        }.getOrDefault("https://unknown")
    }

    private fun normalizeMethod(method: String): String = when (method.uppercase()) {
        "GET" -> FirebasePerformance.HttpMethod.GET
        "PUT" -> FirebasePerformance.HttpMethod.PUT
        "POST" -> FirebasePerformance.HttpMethod.POST
        "DELETE" -> FirebasePerformance.HttpMethod.DELETE
        "HEAD" -> FirebasePerformance.HttpMethod.HEAD
        "PATCH" -> FirebasePerformance.HttpMethod.PATCH
        "OPTIONS" -> FirebasePerformance.HttpMethod.OPTIONS
        "TRACE" -> FirebasePerformance.HttpMethod.TRACE
        "CONNECT" -> FirebasePerformance.HttpMethod.CONNECT
        else -> FirebasePerformance.HttpMethod.POST
    }
}
