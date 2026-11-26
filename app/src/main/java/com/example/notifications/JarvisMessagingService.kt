package com.example.notifications

import com.example.data.local.EncryptedPrefsManager
import com.example.telemetry.JarvisCrashReporter
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class JarvisMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Never log raw push tokens; log only the lifecycle event.
        JarvisCrashReporter.logBreadcrumb("FCM token refreshed")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val prefs = EncryptedPrefsManager.getInstance(applicationContext)
        if (!prefs.state.value.notificationsEnabled) return

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Обновление JARVIS"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["changelog"]
            ?: ""
        val version = remoteMessage.data["version"] ?: "Release"
        val category = remoteMessage.data["category"] ?: "Обновление"

        if (title.isNotBlank() || body.isNotBlank()) {
            JarvisNotificationHelper.showUpdateChangelogNotification(
                context = applicationContext,
                title = title,
                versionTag = version,
                summary = body.ifBlank { title },
                category = category
            )
        }
    }
}
