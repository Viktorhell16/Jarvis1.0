package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object JarvisNotificationHelper {

    const val UPDATE_CHANNEL_ID = "jarvis_updates"
    private const val UPDATE_CHANNEL_NAME = "Обновления и патчноуты JARVIS"

    fun ensureUpdateChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(UPDATE_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    UPDATE_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Уведомления о новых версиях, патчноутах и изменениях JARVIS"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        val managerEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!managerEnabled) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showUpdateChangelogNotification(
        context: Context,
        title: String,
        versionTag: String,
        summary: String,
        category: String = "Добавлено"
    ): Boolean {
        ensureUpdateChannel(context)
        if (!canPostNotifications(context)) return false

        val cleanTitle = title.trim().ifBlank { "Обновление JARVIS" }
        val cleanVersion = versionTag.trim().ifBlank { "Update" }
        val cleanSummary = summary.trim().ifBlank { cleanTitle }
        val formattedTitle = "[$cleanVersion • $category] $cleanTitle"

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() and 0x7FFF).toInt(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(formattedTitle)
            .setContentText(cleanSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cleanSummary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val uniqueId = ((System.currentTimeMillis() % 900_000L) + 10_000L).toInt()
        return runCatching {
            NotificationManagerCompat.from(context).notify(uniqueId, notification)
            true
        }.getOrDefault(false)
    }
}
