package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        LocalUserEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        ArtifactEntity::class,
        PluginEntity::class,
        TokenUsageEntity::class,
        GemModeEntity::class,
        ProviderSlotEntity::class,
        ScheduledActionEntity::class,
        DevPostEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun localUserDao(): LocalUserDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun pluginDao(): PluginDao
    abstract fun tokenUsageDao(): TokenUsageDao
    abstract fun gemModeDao(): GemModeDao
    abstract fun providerSlotDao(): ProviderSlotDao
    abstract fun scheduledActionDao(): ScheduledActionDao
    abstract fun devPostDao(): DevPostDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { db ->
                        INSTANCE = db
                        CoroutineScope(Dispatchers.IO).launch {
                            ensureSeeded(db)
                        }
                    }
            }
        }

        suspend fun ensureSeeded(db: JarvisDatabase) {
            if (db.pluginDao().count() == 0) {
                db.pluginDao().insertAll(defaultPlugins())
            }
            if (db.gemModeDao().count() == 0) {
                db.gemModeDao().insertAll(defaultGemModes())
            }
            if (db.providerSlotDao().count() == 0) {
                db.providerSlotDao().insertAll(defaultProviderSlots())
            }
            if (db.devPostDao().count() == 0) {
                defaultDevPosts().forEach { db.devPostDao().insertPost(it) }
            }
        }

        fun defaultArtifacts(): List<ArtifactEntity> = listOf(
            ArtifactEntity(
                title = "План локального AI-агента JARVIS",
                type = "PAGE",
                category = "All",
                tagsCsv = "Архитектура,Локально",
                isPinned = true,
                contentOrUri = "# План локального AI-агента JARVIS\n\n- [x] Настроить Room DB и EncryptedSharedPreferences\n- [x] Подключить 0-токенный роутер команд\n- [ ] Протестировать GitHub PAT и Google OAuth 2.0",
                previewSummary = "Чек-лист разработки и локальной архитектуры"
            ),
            ArtifactEntity(
                title = "Шпаргалка по Kotlin Coroutines",
                type = "DOCUMENT",
                category = "All",
                tagsCsv = "Kotlin,Dev",
                isPinned = false,
                contentOrUri = "# Kotlin Coroutines & Flow\n\nИспользуйте `StateFlow` для состояния UI, `flowOn(Dispatchers.IO)` для сети и батчинг 85мс для SSE-потока.",
                previewSummary = "Технические заметки по многопоточности"
            )
        )

        fun defaultDevPosts(): List<DevPostEntity> = listOf(
            DevPostEntity(
                title = "JARVIS 1.1 — Онбординг, GIF-профили и Samsung Notes",
                body = "Добавлен первый запуск с живым синим эквалайзером, локальная авторизация с хешированием пароля, поддержка анимированных GIF в аватарках и баннерах, а также контекстное меню долгого нажатия в Библиотеке.",
                category = "Добавлено",
                versionTag = "v1.1"
            ),
            DevPostEntity(
                title = "Исправлена работа IME-клавиатуры и кнопка Сбросить",
                body = "Поле ввода и 3 нижние пилюли теперь плавно поднимаются над экранной клавиатурой благодаря imePadding(), а кнопка сброса Failover не переносится по 3 буквы.",
                category = "Исправлено",
                versionTag = "v1.1"
            )
        )

        fun defaultPlugins(): List<PluginEntity> = listOf(
            PluginEntity(
                name = "GitHub",
                description = "Real PAT integration: search repos, files, and issues",
                iconKey = "github",
                isBuiltIn = true,
                skillMarkdown = "Use real GitHub API repository & issue context injected from the user's PAT."
            ),
            PluginEntity(
                name = "Gmail",
                description = "OAuth 2.0 gmail.readonly: read & summarize recent emails",
                iconKey = "gmail",
                isBuiltIn = true,
                skillMarkdown = "Analyze and summarize real email subjects and snippets fetched via Gmail REST API."
            ),
            PluginEntity(
                name = "Google Calendar",
                description = "OAuth 2.0 calendar.readonly: read upcoming events",
                iconKey = "calendar",
                isBuiltIn = true,
                skillMarkdown = "Organize schedules and summarize real upcoming events from Google Calendar."
            ),
            PluginEntity(
                name = "Google Drive",
                description = "OAuth 2.0 drive.readonly: search & list Drive files",
                iconKey = "drive",
                isBuiltIn = true,
                skillMarkdown = "Reference real Google Drive file metadata fetched via Drive REST API."
            ),
            PluginEntity(
                name = "Notion MCP",
                description = "Connect Notion Integration Token & database endpoint",
                iconKey = "notion",
                isBuiltIn = true,
                skillMarkdown = "Query and structure Notion pages via configured Bearer integration token."
            ),
            PluginEntity(
                name = "Linear",
                description = "Connect Linear API key to read active issues & sprints",
                iconKey = "linear",
                isBuiltIn = true,
                skillMarkdown = "Break tasks into Linear issues using real team context."
            ),
            PluginEntity(
                name = "Atlassian Rovo",
                description = "Jira / Confluence REST API token connector",
                iconKey = "atlassian",
                isBuiltIn = true,
                skillMarkdown = "Structure queries for Jira issues and Confluence pages."
            ),
            PluginEntity(
                name = "Canva",
                description = "Canva REST API / design webhook connector",
                iconKey = "canva",
                isBuiltIn = true,
                skillMarkdown = "Prepare Canva design briefs and template metadata."
            ),
            PluginEntity(
                name = "Google Contacts",
                description = "Contacts API / OAuth bearer token lookup",
                iconKey = "contacts",
                isBuiltIn = true,
                skillMarkdown = "Format contact lookups from connected Google Contacts."
            ),
            PluginEntity(
                name = "LinkedIn Profile",
                description = "LinkedIn REST bearer profile connector",
                iconKey = "linkedin",
                isBuiltIn = true,
                skillMarkdown = "Draft career updates and profile summaries."
            ),
            PluginEntity(
                name = "PayPal",
                description = "PayPal REST API invoices & transaction reader",
                iconKey = "paypal",
                isBuiltIn = true,
                skillMarkdown = "Summarize PayPal invoice statuses via configured API credentials."
            )
        )

        fun defaultGemModes(): List<GemModeEntity> = listOf(
            GemModeEntity(
                id = 1L,
                name = "Ассистент / Агент",
                description = "Универсальный интеллектуальный агент для задач и управления Android",
                iconKey = "assistant",
                systemPrompt = "You are JARVIS, a helpful, concise, local-first Android AI agent. Provide clear, accurate, actionable answers.",
                rawOutputMode = false,
                isBuiltIn = true
            ),
            GemModeEntity(
                id = 2L,
                name = "Разработчик",
                description = "Чистый код без лишней воды, архитектура, Kotlin/Python/C++",
                iconKey = "developer",
                systemPrompt = "You are an expert senior software engineer. Output clean, production-grade code and concise technical explanations without fluff.",
                rawOutputMode = true,
                isBuiltIn = true
            ),
            GemModeEntity(
                id = 3L,
                name = "Сценарист",
                description = "Креативные истории, диалоги, видеосценарии и сторителлинг",
                iconKey = "scriptwriter",
                systemPrompt = "You are a creative director and screenwriter. Write vivid scenes, compelling dialogue, and structured scripts.",
                rawOutputMode = false,
                isBuiltIn = true
            ),
            GemModeEntity(
                id = 4L,
                name = "Переводчик",
                description = "Точный перевод с сохранением стиля и терминологии (RU / UK / EN / PL)",
                iconKey = "translator",
                systemPrompt = "You are a professional translator. Output only the accurate, natural-sounding translation without unnecessary commentary unless asked.",
                rawOutputMode = true,
                isBuiltIn = true
            )
        )

        fun defaultProviderSlots(): List<ProviderSlotEntity> = listOf(
            ProviderSlotEntity(
                priorityOrder = 1,
                providerId = "gemini",
                providerDisplayName = "Google Gemini (AI Studio Free/Key)",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                modelId = "gemini-2.5-flash",
                isEnabled = true
            ),
            ProviderSlotEntity(
                priorityOrder = 2,
                providerId = "openrouter",
                providerDisplayName = "OpenRouter (Free & Paid Models)",
                baseUrl = "https://openrouter.ai/api/v1",
                modelId = "openrouter/free",
                isEnabled = true
            ),
            ProviderSlotEntity(
                priorityOrder = 3,
                providerId = "anymodels",
                providerDisplayName = "AnyModels / Custom Proxy",
                baseUrl = "https://api.anymodels.ai/v1",
                modelId = "gemini-2.5-flash",
                isEnabled = true
            )
        )
    }
}
