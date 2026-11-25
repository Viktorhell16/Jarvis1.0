package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_users")
data class LocalUserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val passwordHash: String,
    val salt: String,
    val avatarUri: String? = null,
    val bannerUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isPinned: Boolean = false,
    val activeModeId: Long = 1L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val modelTag: String = "",
    val providerName: String = "",
    val reasoningEffort: String = "low",
    val actionPayloadJson: String? = null,
    val actionStatus: String? = null, // "PENDING", "EXECUTED", "CANCELLED"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "artifacts")
data class ArtifactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String, // "FILE", "IMAGE", "PAGE", "DOCUMENT", "PRESENTATION", "BOOK"
    val category: String, // "All", "Images", "Infographics", "Stories", "Posters"
    val contentOrUri: String,
    val previewSummary: String = "",
    val tagsCsv: String = "",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val iconKey: String,
    val isConnected: Boolean = false,
    val isEnabled: Boolean = false,
    val isBuiltIn: Boolean = true,
    val kind: String = "BUILTIN", // "BUILTIN", "SKILL_MD", "WEBHOOK_JSON"
    val skillMarkdown: String = "",
    val webhookUrl: String = "",
    val httpMethod: String = "POST",
    val headersJson: String = "{}",
    val bodyTemplate: String = "{}"
)

@Entity(tableName = "token_usage")
data class TokenUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val dateKey: String, // YYYY-MM-DD
    val monthKey: String, // YYYY-MM
    val providerName: String,
    val modelId: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val estimatedCostUsd: Double
)

@Entity(tableName = "gem_modes")
data class GemModeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val iconKey: String,
    val systemPrompt: String,
    val rawOutputMode: Boolean = false,
    val isBuiltIn: Boolean = true
)

@Entity(tableName = "provider_slots")
data class ProviderSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val priorityOrder: Int,
    val providerId: String,
    val providerDisplayName: String,
    val baseUrl: String,
    val modelId: String,
    val cooldownUntilMs: Long = 0L,
    val lastError: String? = null,
    val isEnabled: Boolean = true
)

@Entity(tableName = "scheduled_actions")
data class ScheduledActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val prompt: String,
    val timeLabel: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dev_posts")
data class DevPostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val category: String, // "Добавлено", "Изменено", "Исправлено"
    val versionTag: String = "v1.1",
    val createdAt: Long = System.currentTimeMillis()
)
