package com.example.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ArtifactEntity
import com.example.data.local.ChatEntity
import com.example.data.local.CustomizationDataStore
import com.example.data.local.CustomizationState
import com.example.data.local.DevPostEntity
import com.example.data.local.EncryptedPrefsManager
import com.example.data.local.GemModeEntity
import com.example.data.local.JarvisDatabase
import com.example.data.local.JarvPrefsState
import com.example.data.local.LocalUserEntity
import com.example.data.local.MessageEntity
import com.example.data.local.PasswordHasher
import com.example.data.local.PersonalContext
import com.example.data.local.PluginEntity
import com.example.data.local.ProviderSlotEntity
import com.example.data.local.ScheduledActionEntity
import com.example.data.local.TokenUsageEntity
import com.example.data.network.ExternalIntegrationsClient
import com.example.data.network.GitHubVerificationResult
import com.example.data.network.LlmFailoverClient
import com.example.data.network.ProviderCatalog
import com.example.data.network.StreamEvent
import com.example.domain.router.CommandRouter
import com.example.domain.router.RouteDecision
import com.example.overlay.FloatingJarvisService
import com.example.tts.JarvisTtsManager
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.EmojiStyle
import com.example.ui.theme.PalettePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainTab { CHAT, SEARCH, LIBRARY }

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val db = JarvisDatabase.getInstance(application)
    val prefs = EncryptedPrefsManager(application)
    private val customizationStore = CustomizationDataStore(application)
    private val ttsManager = JarvisTtsManager(application)
    private val commandRouter = CommandRouter(application)
    private val llmClient = LlmFailoverClient(prefs, db.providerSlotDao(), db.tokenUsageDao())
    private val externalClient = ExternalIntegrationsClient()

    val prefsState: StateFlow<JarvPrefsState> = prefs.state
    val customizationState: StateFlow<CustomizationState> = customizationStore.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, CustomizationState(palettePreset = prefs.state.value.palettePreset))

    private val _activeTab = MutableStateFlow(MainTab.CHAT)
    val activeTab: StateFlow<MainTab> = _activeTab.asStateFlow()

    private val _activeChatId = MutableStateFlow<Long?>(null)
    val activeChatId: StateFlow<Long?> = _activeChatId.asStateFlow()

    private val _isIncognito = MutableStateFlow(false)
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    private val _incognitoMessages = MutableStateFlow<List<MessageEntity>>(emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _attachedFileSummary = MutableStateFlow<String?>(null)
    val attachedFileSummary: StateFlow<String?> = _attachedFileSummary.asStateFlow()

    private val _composerPrefill = MutableStateFlow("")
    val composerPrefill: StateFlow<String> = _composerPrefill.asStateFlow()

    private val _drawerSearchQuery = MutableStateFlow("")
    val drawerSearchQuery: StateFlow<String> = _drawerSearchQuery.asStateFlow()

    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    private var streamingJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            JarvisDatabase.ensureSeeded(db)
            if (!prefs.state.value.artifactsSeeded) {
                JarvisDatabase.defaultArtifacts().forEach { db.artifactDao().insertArtifact(it) }
                prefs.markArtifactsSeeded()
            }
        }
        viewModelScope.launch {
            customizationStore.state.collect { custom ->
                prefs.setPalettePresetMirror(custom.palettePreset)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val chats: StateFlow<List<ChatEntity>> = _drawerSearchQuery
        .flatMapLatest { q ->
            if (q.isBlank()) db.chatDao().observeChats() else db.chatDao().searchChats(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<MessageEntity>> = _isIncognito
        .flatMapLatest { incognito ->
            if (incognito) {
                _incognitoMessages
            } else {
                _activeChatId.flatMapLatest { id ->
                    if (id == null) flowOf(emptyList())
                    else db.messageDao().observeMessagesForChat(id)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artifacts: StateFlow<List<ArtifactEntity>> = db.artifactDao().observeArtifacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val plugins: StateFlow<List<PluginEntity>> = db.pluginDao().observePlugins()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tokenUsageList: StateFlow<List<TokenUsageEntity>> = db.tokenUsageDao().observeAllUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val gemModes: StateFlow<List<GemModeEntity>> = db.gemModeDao().observeGemModes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val providerSlots: StateFlow<List<ProviderSlotEntity>> = db.providerSlotDao().observeSlots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduledActions: StateFlow<List<ScheduledActionEntity>> = db.scheduledActionDao().observeActions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val devPosts: StateFlow<List<DevPostEntity>> = db.devPostDao().observePosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchMessagesResult: StateFlow<List<MessageEntity>> = _globalSearchQuery
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else db.messageDao().searchMessages(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchArtifactsResult: StateFlow<List<ArtifactEntity>> = _globalSearchQuery
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else db.artifactDao().searchArtifacts(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Onboarding & Local Auth ---

    fun completeOnboarding() = prefs.markOnboardingSeen()

    suspend fun registerLocalUser(nickname: String, password: String): Result<LocalUserEntity> =
        withContext(Dispatchers.IO) {
            val cleanNick = nickname.trim()
            if (cleanNick.length < 2) return@withContext Result.failure(IllegalArgumentException("Никнейм должен содержать минимум 2 символа"))
            if (password.length < 4) return@withContext Result.failure(IllegalArgumentException("Пароль должен содержать минимум 4 символа"))
            val existing = db.localUserDao().getUserByNickname(cleanNick)
            if (existing != null) {
                return@withContext Result.failure(IllegalArgumentException("Пользователь с таким логином уже существует"))
            }
            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hashPassword(password, salt)
            val entity = LocalUserEntity(nickname = cleanNick, passwordHash = hash, salt = salt)
            val newId = db.localUserDao().insertUser(entity)
            val saved = entity.copy(id = newId)
            prefs.setLoggedInUser(saved)
            Result.success(saved)
        }

    suspend fun loginLocalUser(nickname: String, password: String): Result<LocalUserEntity> =
        withContext(Dispatchers.IO) {
            val cleanNick = nickname.trim()
            if (cleanNick.isEmpty() || password.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Введите логин и пароль"))
            }
            val user = db.localUserDao().getUserByNickname(cleanNick)
                ?: return@withContext Result.failure(IllegalArgumentException("Пользователь не найден"))
            if (!PasswordHasher.verify(password, user.salt, user.passwordHash)) {
                return@withContext Result.failure(IllegalArgumentException("Неверный пароль"))
            }
            prefs.setLoggedInUser(user)
            Result.success(user)
        }

    fun logoutLocalUser() {
        stopGeneration()
        prefs.setLoggedInUser(null)
    }

    fun updateUserProfileMedia(nickname: String, avatarUri: String?, bannerUri: String?) {
        prefs.updateUserMediaAndName(nickname, avatarUri, bannerUri)
        val uid = prefsState.value.loggedInUserId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.localUserDao().getUserById(uid)?.let { current ->
                db.localUserDao().updateUser(
                    current.copy(
                        nickname = nickname.trim().ifBlank { current.nickname },
                        avatarUri = avatarUri,
                        bannerUri = bannerUri
                    )
                )
            }
        }
    }

    // --- Navigation & Composer ---

    fun setTab(tab: MainTab) {
        _activeTab.value = tab
    }

    fun startNewChat(incognito: Boolean = false) {
        stopGeneration()
        _isIncognito.value = incognito
        _activeChatId.value = null
        _incognitoMessages.value = emptyList()
        _activeTab.value = MainTab.CHAT
    }

    fun toggleIncognitoChat() {
        startNewChat(incognito = !_isIncognito.value)
    }

    fun selectChat(chatId: Long) {
        stopGeneration()
        _isIncognito.value = false
        _activeChatId.value = chatId
        _activeTab.value = MainTab.CHAT
    }

    fun togglePinChat(chat: ChatEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.chatDao().updateChat(chat.copy(isPinned = !chat.isPinned))
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.messageDao().deleteMessagesForChat(chatId)
            db.chatDao().deleteChat(chatId)
            if (_activeChatId.value == chatId) {
                _activeChatId.value = null
            }
        }
    }

    fun setDrawerSearchQuery(query: String) {
        _drawerSearchQuery.value = query
    }

    fun setGlobalSearchQuery(query: String) {
        _globalSearchQuery.value = query
    }

    fun setComposerPrefill(text: String) {
        _composerPrefill.value = text
    }

    fun injectArtifactIntoChat(artifact: ArtifactEntity) {
        _composerPrefill.value = "${artifact.title}\n\n${artifact.contentOrUri}"
        _activeTab.value = MainTab.CHAT
    }

    fun attachFileContext(name: String, previewContent: String) {
        _attachedFileSummary.value = "[$name]\n${previewContent.take(1500)}"
    }

    fun clearAttachedFile() {
        _attachedFileSummary.value = null
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun sendMessage(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        _composerPrefill.value = ""
        val language = prefsState.value.language

        val currentMsgs = activeMessages.value
        val pendingActionMsg = currentMsgs.lastOrNull { it.actionPayloadJson != null && it.actionStatus == "PENDING" }
        if (pendingActionMsg != null) {
            val lower = trimmed.lowercase()
            if (lower in listOf("+", "да", "так", "yes", "выполнить", "виконати", "execute")) {
                confirmMessageAction(pendingActionMsg, execute = true)
                return
            } else if (lower in listOf("-", "нет", "ні", "no", "отмена", "скасувати", "cancel")) {
                confirmMessageAction(pendingActionMsg, execute = false)
                return
            }
        }

        val attachment = _attachedFileSummary.value
        _attachedFileSummary.value = null
        val fullUserText = if (attachment != null) "$trimmed\n\n$attachment" else trimmed

        viewModelScope.launch {
            val chatId = ensureChatId(trimmed)
            val userMsg = MessageEntity(
                chatId = chatId,
                role = "user",
                content = fullUserText,
                reasoningEffort = prefsState.value.reasoningEffort
            )
            appendMessage(chatId, userMsg)

            when (val decision = commandRouter.route(trimmed, language)) {
                is RouteDecision.LocalHandled -> {
                    val reply = MessageEntity(
                        chatId = chatId,
                        role = "assistant",
                        content = decision.replyText,
                        modelTag = decision.actionTag,
                        providerName = "JARVIS Local"
                    )
                    appendMessage(chatId, reply)
                    maybeSpeak(decision.replyText)
                    return@launch
                }
                is RouteDecision.ConfirmationRequired -> {
                    val reply = MessageEntity(
                        chatId = chatId,
                        role = "assistant",
                        content = decision.replyText,
                        modelTag = decision.actionTag,
                        providerName = "JARVIS Agent",
                        actionPayloadJson = decision.actionPayloadJson,
                        actionStatus = "PENDING"
                    )
                    appendMessage(chatId, reply)
                    maybeSpeak(decision.replyText)
                    return@launch
                }
                is RouteDecision.ForwardToLlm -> Unit
            }

            startLlmStream(chatId, trimmed)
        }
    }

    private suspend fun startLlmStream(chatId: Long, userQuery: String) {
        streamingJob?.cancel()
        _isGenerating.value = true

        val state = prefsState.value
        val placeholderId = appendMessage(
            chatId,
            MessageEntity(
                chatId = chatId,
                role = "assistant",
                content = "...",
                modelTag = state.activeModelDisplayName,
                providerName = ProviderCatalog.findProvider(state.activeProviderId).displayName,
                reasoningEffort = state.reasoningEffort
            )
        )

        val history = withContext(Dispatchers.IO) {
            if (_isIncognito.value) _incognitoMessages.value.dropLast(1)
            else db.messageDao().getMessagesForChatOnce(chatId).dropLast(1)
        }

        val systemPrompt = buildSystemPrompt(userQuery)

        streamingJob = viewModelScope.launch {
            try {
                llmClient.streamCompletion(
                    history = history,
                    systemPrompt = systemPrompt,
                    reasoningEffort = state.reasoningEffort
                ).collect { event ->
                    when (event) {
                        is StreamEvent.TokenDelta -> {
                            updateMessageText(placeholderId, event.accumulatedText, event.modelTag, event.providerName)
                        }
                        is StreamEvent.Completed -> {
                            updateMessageText(placeholderId, event.fullText, event.modelTag, event.providerName)
                            _isGenerating.value = false
                            maybeSpeak(event.fullText)
                        }
                        is StreamEvent.FailoverNotice -> {
                            _statusBanner.value = event.message
                        }
                        is StreamEvent.Error -> {
                            val errText = if (event.message == "NO_API_KEY") {
                                AppStrings.get(state.language).noApiKeyNotice
                            } else {
                                "⚠ ${event.message}"
                            }
                            updateMessageText(placeholderId, errText, "System", "JARVIS")
                            _isGenerating.value = false
                        }
                    }
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private suspend fun buildSystemPrompt(userQuery: String): String = withContext(Dispatchers.IO) {
        val state = prefsState.value
        val mode = db.gemModeDao().getModeById(state.activeGemId) ?: db.gemModeDao().getModeById(1L)
        val basePrompt = mode?.systemPrompt ?: "You are JARVIS, a helpful local-first AI agent."
        val langRule = AppStrings.languageInstruction(state.language)
        val userCtx = state.personalContext.toCompressedPrompt()
        val canvasRule = if (state.canvasEnabled) {
            "Canvas workspace mode is ON: provide structured, ready-to-use code, documents, or step-by-step plans."
        } else ""
        val rawRule = if (mode?.rawOutputMode == true) {
            "Raw output mode: avoid conversational filler and output clean direct text or code."
        } else ""

        val pluginContext = if (state.skillsEnabled) {
            val activePlugins = db.pluginDao().getActivePluginsOnce()
            if (activePlugins.isEmpty()) ""
            else {
                val realIntegrationData = mutableListOf<String>()
                activePlugins.forEach { plugin ->
                    when {
                        plugin.iconKey == "github" -> {
                            val pat = prefs.getGitHubPat()
                            if (pat.isNotBlank()) {
                                val data = externalClient.fetchGitHubContext(pat, userQuery)
                                if (data.isNotBlank()) realIntegrationData.add(data)
                            }
                        }
                        plugin.iconKey == "gmail" -> {
                            val tok = prefs.getGoogleOAuthToken()
                            if (tok.isNotBlank()) {
                                val data = externalClient.fetchGmailContext(tok)
                                if (data.isNotBlank()) realIntegrationData.add(data)
                            }
                        }
                        plugin.iconKey == "drive" -> {
                            val tok = prefs.getGoogleOAuthToken()
                            if (tok.isNotBlank()) {
                                val data = externalClient.fetchDriveContext(tok)
                                if (data.isNotBlank()) realIntegrationData.add(data)
                            }
                        }
                        plugin.iconKey == "calendar" -> {
                            val tok = prefs.getGoogleOAuthToken()
                            if (tok.isNotBlank()) {
                                val data = externalClient.fetchCalendarContext(tok)
                                if (data.isNotBlank()) realIntegrationData.add(data)
                            }
                        }
                        plugin.kind == "WEBHOOK_JSON" && plugin.webhookUrl.isNotBlank() -> {
                            llmClient.executeWebhookPlugin(plugin, userQuery).getOrNull()?.let { res ->
                                realIntegrationData.add("[Webhook:${plugin.name}] -> $res")
                            }
                        }
                        else -> {
                            val ep = prefs.getConnectorEndpoint(plugin.iconKey)
                            val tok = prefs.getConnectorToken(plugin.iconKey)
                            if (ep.isNotBlank()) {
                                externalClient.fetchGenericBearerApi(ep, tok).getOrNull()?.let { res ->
                                    realIntegrationData.add("[${plugin.name}]: $res")
                                }
                            }
                        }
                    }
                }
                val skillGuides = activePlugins.filter { it.skillMarkdown.isNotBlank() }
                    .joinToString("\n") { "- ${it.name}: ${it.skillMarkdown.take(350)}" }
                buildString {
                    if (skillGuides.isNotBlank()) append("\nActive Skills/Sources:\n$skillGuides")
                    if (realIntegrationData.isNotEmpty()) append("\nLive Connected Data:\n${realIntegrationData.joinToString("\n")}")
                }
            }
        } else ""

        listOf(basePrompt, langRule, userCtx, canvasRule, rawRule, pluginContext)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun stopGeneration() {
        streamingJob?.cancel()
        streamingJob = null
        _isGenerating.value = false
        ttsManager.stop()
    }

    fun confirmMessageAction(message: MessageEntity, execute: Boolean) {
        val payload = message.actionPayloadJson ?: return
        val lang = prefsState.value.language
        viewModelScope.launch {
            val nextStatus = if (execute) "EXECUTED" else "CANCELLED"
            if (_isIncognito.value) {
                _incognitoMessages.value = _incognitoMessages.value.map {
                    if (it.id == message.id) it.copy(actionStatus = nextStatus) else it
                }
            } else {
                withContext(Dispatchers.IO) {
                    db.messageDao().updateActionStatus(message.id, nextStatus)
                }
            }
            val resultText = if (execute) {
                commandRouter.executeConfirmedAction(payload, lang)
            } else {
                when (lang) {
                    AppLanguage.RU -> "Действие отменено."
                    AppLanguage.UK -> "Дію скасовано."
                    AppLanguage.EN -> "Action cancelled."
                }
            }
            appendMessage(
                message.chatId,
                MessageEntity(
                    chatId = message.chatId,
                    role = "assistant",
                    content = resultText,
                    modelTag = "Device Agent",
                    providerName = "JARVIS"
                )
            )
            maybeSpeak(resultText)
        }
    }

    private suspend fun ensureChatId(firstPrompt: String): Long {
        if (_isIncognito.value) return -1L
        val existing = _activeChatId.value
        if (existing != null) {
            withContext(Dispatchers.IO) {
                db.chatDao().getChatById(existing)?.let { chat ->
                    db.chatDao().updateChat(chat.copy(updatedAt = System.currentTimeMillis()))
                }
            }
            return existing
        }
        val autoTitle = firstPrompt.replace("\n", " ").take(42).ifBlank { "Новый чат" }
        val newId = withContext(Dispatchers.IO) {
            db.chatDao().insertChat(
                ChatEntity(
                    title = autoTitle,
                    activeModeId = prefsState.value.activeGemId
                )
            )
        }
        _activeChatId.value = newId
        return newId
    }

    private suspend fun appendMessage(chatId: Long, msg: MessageEntity): Long {
        if (_isIncognito.value) {
            val id = System.currentTimeMillis() + _incognitoMessages.value.size
            _incognitoMessages.value = _incognitoMessages.value + msg.copy(id = id, chatId = -1L)
            return id
        }
        return withContext(Dispatchers.IO) {
            db.messageDao().insertMessage(msg.copy(chatId = chatId))
        }
    }

    private suspend fun updateMessageText(msgId: Long, text: String, modelTag: String, providerName: String) {
        if (_isIncognito.value) {
            _incognitoMessages.value = _incognitoMessages.value.map {
                if (it.id == msgId) it.copy(content = text, modelTag = modelTag, providerName = providerName) else it
            }
            return
        }
        withContext(Dispatchers.IO) {
            db.messageDao().updateMessageContent(msgId, text, modelTag, providerName)
        }
    }

    private fun maybeSpeak(text: String) {
        if (prefsState.value.ttsEnabled) {
            ttsManager.speak(text, prefsState.value.language)
        }
    }

    // --- Settings & Preferences Controls ---

    fun toggleTts() {
        val next = !prefsState.value.ttsEnabled
        prefs.setTtsEnabled(next)
        if (!next) ttsManager.stop()
    }

    fun toggleSkills() = prefs.setSkillsEnabled(!prefsState.value.skillsEnabled)
    fun toggleCanvas() = prefs.setCanvasEnabled(!prefsState.value.canvasEnabled)
    fun setReasoningEffort(effort: String) = prefs.setReasoningEffort(effort)
    fun setPreset(presetId: String) {
        prefs.setPreset(presetId)
        when (presetId) {
            "DAILY" -> prefs.saveProviderConfig(
                "gemini",
                "https://generativelanguage.googleapis.com/v1beta",
                "gemini-4-hydrogen",
                "Gemini 4 Hydrogen",
                prefs.getApiKey("gemini")
            )
            "DEEP" -> prefs.saveProviderConfig(
                "gemini",
                "https://generativelanguage.googleapis.com/v1beta",
                "gemini-4-neon",
                "Gemini 4 Neon",
                prefs.getApiKey("gemini")
            )
        }
    }

    fun saveProviderConfig(
        providerId: String,
        baseUrl: String,
        modelId: String,
        modelDisplayName: String,
        apiKey: String
    ) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val cleanModel = modelId.trim()
        prefs.saveProviderConfig(providerId, cleanUrl, cleanModel, modelDisplayName, apiKey)
        viewModelScope.launch(Dispatchers.IO) {
            val slots = db.providerSlotDao().getSlotsOnce().sortedBy { it.priorityOrder }
            val match = slots.find { it.providerId == providerId }
            if (match != null) {
                val updatedTop = match.copy(
                    priorityOrder = 1,
                    baseUrl = cleanUrl,
                    modelId = cleanModel,
                    isEnabled = true,
                    cooldownUntilMs = 0L,
                    lastError = null
                )
                db.providerSlotDao().updateSlot(updatedTop)
                slots.filter { it.id != match.id }.forEachIndexed { idx, other ->
                    db.providerSlotDao().updateSlot(other.copy(priorityOrder = idx + 2))
                }
            } else {
                db.providerSlotDao().insertSlot(
                    ProviderSlotEntity(
                        priorityOrder = 1,
                        providerId = providerId,
                        providerDisplayName = ProviderCatalog.findProvider(providerId).displayName,
                        baseUrl = cleanUrl,
                        modelId = cleanModel,
                        isEnabled = true
                    )
                )
                slots.forEachIndexed { idx, other ->
                    db.providerSlotDao().updateSlot(other.copy(priorityOrder = idx + 2))
                }
            }
        }
    }

    fun isProviderKeyConfigured(providerId: String): Boolean = prefs.getApiKey(providerId).isNotBlank()

    fun isActiveApiReady(): Boolean {
        val activeId = prefsState.value.activeProviderId
        if (prefs.getApiKey(activeId).isBlank()) return false
        val activeSlot = providerSlots.value.find { it.providerId == activeId }
        val now = System.currentTimeMillis()
        return activeSlot == null || activeSlot.cooldownUntilMs <= now
    }

    fun setPalettePreset(preset: PalettePreset) {
        prefs.setPalettePresetMirror(preset)
        viewModelScope.launch { customizationStore.setPalettePreset(preset) }
    }

    fun setLiquidGlassEnabled(enabled: Boolean) {
        viewModelScope.launch { customizationStore.setLiquidGlassEnabled(enabled) }
    }

    fun setSnowfallEnabled(enabled: Boolean) {
        viewModelScope.launch { customizationStore.setSnowfallEnabled(enabled) }
    }

    fun setBouncingShapesEnabled(enabled: Boolean) {
        viewModelScope.launch { customizationStore.setBouncingShapesEnabled(enabled) }
    }

    fun setAutoScrollPromptsEnabled(enabled: Boolean) {
        viewModelScope.launch { customizationStore.setAutoScrollPromptsEnabled(enabled) }
    }

    fun setEmojiStyle(style: EmojiStyle) {
        viewModelScope.launch { customizationStore.setEmojiStyle(style) }
    }

    fun setLanguage(lang: AppLanguage) {
        prefs.setLanguage(lang)
        ttsManager.setLanguage(lang)
    }

    fun updatePersonalContext(ctx: PersonalContext) = prefs.updatePersonalContext(ctx)

    fun updateBudgetSettings(dailyLimit: Long, monthlyUsd: Double, hardStop: Boolean) =
        prefs.updateBudgetSettings(dailyLimit, monthlyUsd, hardStop)

    fun setNotificationsEnabled(enabled: Boolean) = prefs.setNotificationsEnabled(enabled)

    fun resetTokenUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            db.tokenUsageDao().clearAllUsage()
        }
    }

    fun toggleFloatingOverlay(enabled: Boolean): Boolean {
        val ctx = getApplication<Application>()
        if (enabled && !Settings.canDrawOverlays(ctx)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            return false
        }
        prefs.setFloatingOverlayEnabled(enabled)
        val serviceIntent = Intent(ctx, FloatingJarvisService::class.java)
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(serviceIntent)
            } else {
                ctx.startService(serviceIntent)
            }
        } else {
            ctx.stopService(serviceIntent)
        }
        return true
    }

    // --- Real External Integrations / Sources ---

    suspend fun verifyAndConnectGitHub(plugin: PluginEntity, pat: String): Result<GitHubVerificationResult> {
        val res = externalClient.verifyGitHubPat(pat)
        if (res.isSuccess) {
            val info = res.getOrThrow()
            prefs.setGitHubAuth(pat, info.username)
            withContext(Dispatchers.IO) {
                db.pluginDao().updatePlugin(
                    plugin.copy(
                        isConnected = true,
                        isEnabled = true,
                        description = "Connected as @${info.username} (${info.publicRepos} repos)"
                    )
                )
            }
        }
        return res
    }

    fun saveGoogleOAuthAndConnect(plugin: PluginEntity, token: String, accountLabel: String) {
        prefs.setGoogleOAuthToken(token, accountLabel)
        viewModelScope.launch(Dispatchers.IO) {
            db.pluginDao().updatePlugin(
                plugin.copy(
                    isConnected = true,
                    isEnabled = true,
                    description = if (accountLabel.isNotBlank()) "OAuth 2.0: $accountLabel" else "OAuth 2.0 Connected"
                )
            )
        }
    }

    suspend fun verifyAndConnectGenericPlugin(plugin: PluginEntity, endpoint: String, token: String): Result<String> {
        val res = externalClient.fetchGenericBearerApi(endpoint, token)
        if (res.isSuccess) {
            prefs.setConnectorCredentials(plugin.iconKey, endpoint, token)
            withContext(Dispatchers.IO) {
                db.pluginDao().updatePlugin(plugin.copy(isConnected = true, isEnabled = true))
            }
        }
        return res
    }

    fun saveGenericPluginWithoutPing(plugin: PluginEntity, endpoint: String, token: String) {
        prefs.setConnectorCredentials(plugin.iconKey, endpoint, token)
        viewModelScope.launch(Dispatchers.IO) {
            db.pluginDao().updatePlugin(plugin.copy(isConnected = true, isEnabled = true))
        }
    }

    fun disconnectPlugin(plugin: PluginEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pluginDao().updatePlugin(plugin.copy(isConnected = false, isEnabled = false))
        }
    }

    fun togglePluginEnabled(plugin: PluginEntity, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pluginDao().updatePlugin(plugin.copy(isEnabled = enabled))
        }
    }

    fun importSkillPlugin(name: String, description: String, markdown: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pluginDao().insertPlugin(
                PluginEntity(
                    name = name.ifBlank { "Custom Skill" },
                    description = description.ifBlank { "Imported SKILL.md instruction module" },
                    iconKey = "skill",
                    isConnected = true,
                    isEnabled = true,
                    isBuiltIn = false,
                    kind = "SKILL_MD",
                    skillMarkdown = markdown
                )
            )
        }
    }

    fun importWebhookPlugin(jsonSpec: String): Result<Unit> {
        return try {
            val obj = JSONObject(jsonSpec)
            val name = obj.optString("name", "Custom Webhook")
            val desc = obj.optString("description", "HTTPS JSON web-plugin")
            val url = obj.getString("url")
            val method = obj.optString("method", "POST")
            val headers = obj.optJSONObject("headers")?.toString() ?: "{}"
            val body = obj.optString("bodyTemplate", "{\"query\":\"{{query}}\"}")
            viewModelScope.launch(Dispatchers.IO) {
                db.pluginDao().insertPlugin(
                    PluginEntity(
                        name = name,
                        description = desc,
                        iconKey = "webhook",
                        isConnected = true,
                        isEnabled = true,
                        isBuiltIn = false,
                        kind = "WEBHOOK_JSON",
                        webhookUrl = url,
                        httpMethod = method,
                        headersJson = headers,
                        bodyTemplate = body
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deletePlugin(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.pluginDao().deletePlugin(id) }
    }

    // --- Library / Samsung Notes Artifacts ---

    fun createArtifact(title: String, type: String, category: String, content: String, tagsCsv: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            db.artifactDao().insertArtifact(
                ArtifactEntity(
                    title = title.ifBlank { "Новая заметка" },
                    type = type,
                    category = category,
                    tagsCsv = tagsCsv,
                    contentOrUri = content,
                    previewSummary = content.replace("\n", " ").take(110)
                )
            )
        }
    }

    fun updateArtifact(artifact: ArtifactEntity) {
        viewModelScope.launch(Dispatchers.IO) { db.artifactDao().updateArtifact(artifact) }
    }

    fun toggleArtifactPin(artifact: ArtifactEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.artifactDao().updateArtifact(artifact.copy(isPinned = !artifact.isPinned))
        }
    }

    fun deleteArtifact(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.artifactDao().deleteArtifact(id) }
    }

    fun restoreArtifact(artifact: ArtifactEntity) {
        viewModelScope.launch(Dispatchers.IO) { db.artifactDao().insertArtifact(artifact) }
    }

    // --- JAR-Bots ---

    fun selectGemMode(id: Long) = prefs.setActiveGemId(id)

    fun saveCustomGemMode(name: String, desc: String, systemPrompt: String, rawOutput: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.gemModeDao().insertMode(
                GemModeEntity(
                    name = name.ifBlank { "Custom JAR-bot" },
                    description = desc.ifBlank { "Пользовательский режим" },
                    iconKey = "custom",
                    systemPrompt = systemPrompt,
                    rawOutputMode = rawOutput,
                    isBuiltIn = false
                )
            )
        }
    }

    fun deleteCustomGemMode(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.gemModeDao().deleteCustomMode(id)
            if (prefsState.value.activeGemId == id) prefs.setActiveGemId(1L)
        }
    }

    // --- Failover Slots & Scheduled Actions & DevBlog ---

    fun toggleSlotEnabled(slot: ProviderSlotEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.providerSlotDao().updateSlot(slot.copy(isEnabled = !slot.isEnabled))
        }
    }

    fun moveSlotPriority(slot: ProviderSlotEntity, delta: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.providerSlotDao().updateSlot(slot.copy(priorityOrder = (slot.priorityOrder + delta).coerceAtLeast(1)))
        }
    }

    fun resetSlotCooldowns() {
        viewModelScope.launch(Dispatchers.IO) { db.providerSlotDao().resetAllCooldowns() }
    }

    fun addScheduledAction(title: String, prompt: String, timeLabel: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.scheduledActionDao().insertAction(
                ScheduledActionEntity(title = title, prompt = prompt, timeLabel = timeLabel)
            )
        }
    }

    fun toggleScheduledAction(action: ScheduledActionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.scheduledActionDao().updateAction(action.copy(isEnabled = !action.isEnabled))
        }
    }

    fun deleteScheduledAction(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.scheduledActionDao().deleteAction(id) }
    }

    fun addDevPost(title: String, body: String, category: String, versionTag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.devPostDao().insertPost(
                DevPostEntity(
                    title = title.ifBlank { "Обновление JARVIS" },
                    body = body,
                    category = category,
                    versionTag = versionTag.ifBlank { "v1.1" }
                )
            )
        }
    }

    fun updateDevPost(post: DevPostEntity) {
        viewModelScope.launch(Dispatchers.IO) { db.devPostDao().updatePost(post) }
    }

    fun deleteDevPost(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.devPostDao().deletePost(id) }
    }

    fun evaluateMathInline(expr: String): String? = commandRouter.tryEvaluateMath(expr)

    fun todayDateKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    fun currentMonthKey(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    override fun onCleared() {
        ttsManager.shutdown()
        super.onCleared()
    }
}
