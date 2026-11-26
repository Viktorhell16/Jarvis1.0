package com.example

import android.app.Application
import com.example.data.local.CustomizationDataStore
import com.example.notifications.JarvisNotificationHelper
import com.example.telemetry.JarvisCrashReporter
import com.example.telemetry.JarvisPerformanceTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Application entry point that initializes Firebase Crashlytics and Performance Monitoring
 * safely and syncs collection state with the user's persisted DataStore telemetry preference.
 * Note: Firebase App Check initialization remains strictly in MainActivity.onCreate().
 */
class JarvisApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        JarvisNotificationHelper.ensureUpdateChannel(this)
        JarvisCrashReporter.init(this, enabled = true)
        JarvisPerformanceTracker.init(this, enabled = true)

        appScope.launch {
            val enabled = runCatching {
                CustomizationDataStore(this@JarvisApplication).state.firstOrNull()?.telemetryEnabled ?: true
            }.getOrDefault(true)
            JarvisCrashReporter.setCollectionEnabled(enabled)
            JarvisPerformanceTracker.setCollectionEnabled(enabled)
            JarvisCrashReporter.logBreadcrumb("JarvisApplication initialized (telemetry=$enabled)")
        }
    }
}
