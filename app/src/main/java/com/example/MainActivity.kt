package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.prompts.PromptMarqueeGenerator
import com.example.firebase.GoogleAuthHelper
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.media.MultimodalMediaProcessor
import com.example.ui.JarvisViewModel
import com.example.ui.MainTab
import com.example.ui.animations.BouncingShapesBackground
import com.example.ui.animations.SnowfallOverlay
import com.example.ui.auth.AuthScreen
import com.example.ui.chat.ChatTopAppBar
import com.example.ui.chat.FloatingBottomNavigationBar
import com.example.ui.chat.MainChatContent
import com.example.ui.chat.NavigationDrawerContent
import com.example.ui.chat.SearchScreen
import com.example.ui.dashboard.ProfileSettingsHub
import com.example.ui.i18n.AppStrings
import com.example.ui.notebook.LibraryScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.plugins.SourcesScreen
import com.example.ui.sheets.QuickProviderConfigBottomSheet
import com.example.ui.sheets.ToolsBottomSheet
import com.example.ui.theme.JarvisTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            val vm: JarvisViewModel = viewModel()
            val prefsState by vm.prefsState.collectAsStateWithLifecycle()
            val customizationState by vm.customizationState.collectAsStateWithLifecycle()
            val activeTab by vm.activeTab.collectAsStateWithLifecycle()
            val activeChatId by vm.activeChatId.collectAsStateWithLifecycle()
            val isIncognito by vm.isIncognito.collectAsStateWithLifecycle()
            val isGenerating by vm.isGenerating.collectAsStateWithLifecycle()
            val statusBanner by vm.statusBanner.collectAsStateWithLifecycle()
            val attachedFileSummary by vm.attachedFileSummary.collectAsStateWithLifecycle()
            val pendingMedia by vm.pendingMedia.collectAsStateWithLifecycle()
            val composerPrefill by vm.composerPrefill.collectAsStateWithLifecycle()
            val drawerQuery by vm.drawerSearchQuery.collectAsStateWithLifecycle()
            val globalQuery by vm.globalSearchQuery.collectAsStateWithLifecycle()

            val chats by vm.chats.collectAsStateWithLifecycle()
            val messages by vm.activeMessages.collectAsStateWithLifecycle()
            val artifacts by vm.artifacts.collectAsStateWithLifecycle()
            val plugins by vm.plugins.collectAsStateWithLifecycle()
            val tokenUsage by vm.tokenUsageList.collectAsStateWithLifecycle()
            val gemModes by vm.gemModes.collectAsStateWithLifecycle()
            val slots by vm.providerSlots.collectAsStateWithLifecycle()
            val scheduledActions by vm.scheduledActions.collectAsStateWithLifecycle()
            val devPosts by vm.devPosts.collectAsStateWithLifecycle()
            val searchMessages by vm.searchMessagesResult.collectAsStateWithLifecycle()
            val searchArtifacts by vm.searchArtifactsResult.collectAsStateWithLifecycle()

            val strings = remember(prefsState.language) { AppStrings.get(prefsState.language) }
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val credentialManager = remember(context) { CredentialManager.create(context) }
            val firebaseUser by remember { Firebase.auth.authStateFlow() }
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            var googleSignInLoading by remember { mutableStateOf(false) }
            var googleAuthError by remember { mutableStateOf<String?>(null) }

            var showQuickConfigSheet by remember { mutableStateOf(false) }
            var showToolsSheet by remember { mutableStateOf(false) }
            var showSourcesScreen by remember { mutableStateOf(false) }
            var showProfileHub by remember { mutableStateOf(false) }
            var marqueeSeed by remember { mutableLongStateOf(System.nanoTime()) }

            LaunchedEffect(prefsState.hasSeenOnboarding, prefsState.loggedInUserId) {
                if (prefsState.hasSeenOnboarding && prefsState.loggedInUserId == null) {
                    GoogleAuthHelper.attemptAutoSignIn(
                        context = context,
                        credentialManager = credentialManager,
                        scope = scope,
                        onAuthSuccess = { user ->
                            googleAuthError = null
                            vm.loginOrSyncGoogleUser(user)
                        },
                        onUnauthenticated = {}
                    )
                }
            }

            val currentFirebaseUid = firebaseUser?.uid
            if (currentFirebaseUid != null) {
                key(currentFirebaseUid) {
                    LaunchedEffect(currentFirebaseUid) {
                        vm.observeCloudSync(currentFirebaseUid)
                    }
                }
            }

            // Re-shuffle marquee prompts when returning to the app (ON_RESUME) or closing overlays / switching back to Chat
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        marqueeSeed = PromptMarqueeGenerator.nextShuffleSeed(marqueeSeed)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(activeTab, showProfileHub, showSourcesScreen) {
                if (activeTab == MainTab.CHAT && !showProfileHub && !showSourcesScreen) {
                    marqueeSeed = PromptMarqueeGenerator.nextShuffleSeed(marqueeSeed)
                }
            }

            val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { picked ->
                    var displayName = "attached_file"
                    context.contentResolver.query(picked, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx)
                        }
                    }
                    val content = runCatching {
                        context.contentResolver.openInputStream(picked)?.bufferedReader()?.use { it.readText() } ?: ""
                    }.getOrDefault("")
                    vm.attachFileContext(displayName, content.ifBlank { "Binary/File URI: $picked" })
                }
            }

            val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                uri?.let(vm::attachImageUri)
            }

            val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                uri?.let(vm::attachVideoUri)
            }

            var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
            val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                if (success) {
                    pendingCameraUri?.let(vm::attachImageUri)
                }
            }
            val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    val uri = MultimodalMediaProcessor.createCameraPhotoUri(context)
                    pendingCameraUri = uri
                    runCatching { takePictureLauncher.launch(uri) }
                        .onFailure { vm.showBanner("Не удалось открыть камеру: ${it.localizedMessage}") }
                } else {
                    vm.showBanner("Разрешение на камеру отклонено. Разрешите доступ в настройках для съёмки фото.")
                }
            }

            val launchCameraFlow: () -> Unit = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    val uri = MultimodalMediaProcessor.createCameraPhotoUri(context)
                    pendingCameraUri = uri
                    runCatching { takePictureLauncher.launch(uri) }
                        .onFailure { vm.showBanner("Не удалось открыть камеру: ${it.localizedMessage}") }
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    vm.setNotificationsEnabled(true)
                } else {
                    vm.setNotificationsEnabled(false)
                    vm.showBanner("Разрешение на уведомления отклонено. Включите его в настройках Android для получения патчноутов.")
                }
            }

            val toggleNotificationsFlow: (Boolean) -> Unit = { enabled ->
                if (!enabled) {
                    vm.setNotificationsEnabled(false)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.setNotificationsEnabled(true)
                }
            }

            val triggerGoogleSignIn: () -> Unit = {
                googleSignInLoading = true
                googleAuthError = null
                GoogleAuthHelper.onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    scope = scope,
                    onAuthSuccess = { user ->
                        googleSignInLoading = false
                        googleAuthError = null
                        vm.loginOrSyncGoogleUser(user)
                    },
                    onAuthError = { msg ->
                        googleSignInLoading = false
                        googleAuthError = msg
                        vm.showBanner(msg)
                    },
                    onAuthCancelled = {
                        googleSignInLoading = false
                    }
                )
            }

            val activeModeBadge = remember(gemModes, prefsState.activeGemId) {
                val modeName = gemModes.find { it.id == prefsState.activeGemId }?.name ?: "Агент"
                modeName.substringBefore(' ').take(12)
            }
            val isApiReady = remember(prefsState.activeProviderId, slots, prefsState.activeModelId) {
                vm.isActiveApiReady()
            }

            JarvisTheme(palettePreset = customizationState.palettePreset) {
                when {
                    !prefsState.hasSeenOnboarding -> {
                        OnboardingScreen(onFinish = vm::completeOnboarding)
                    }

                    prefsState.loggedInUserId == null -> {
                        AuthScreen(
                            bouncingShapesEnabled = customizationState.bouncingShapesEnabled,
                            googleSignInLoading = googleSignInLoading,
                            externalError = googleAuthError,
                            onGoogleSignIn = triggerGoogleSignIn,
                            onLogin = { nick, pass -> vm.loginLocalUser(nick, pass).map { } },
                            onRegister = { nick, pass -> vm.registerLocalUser(nick, pass).map { } }
                        )
                    }

                    else -> {
                        val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            BouncingShapesBackground(
                                enabled = customizationState.bouncingShapesEnabled,
                                modifier = Modifier.fillMaxSize()
                            )

                            ModalNavigationDrawer(
                                drawerState = drawerState,
                                gesturesEnabled = !showSourcesScreen && !showProfileHub,
                                drawerContent = {
                                    NavigationDrawerContent(
                                        chats = chats,
                                        activeChatId = activeChatId,
                                        searchQuery = drawerQuery,
                                        gemModes = gemModes,
                                        activeGemId = prefsState.activeGemId,
                                        strings = strings,
                                        onSearchQueryChange = vm::setDrawerSearchQuery,
                                        onNewChat = {
                                            vm.startNewChat(incognito = false)
                                            scope.launch { drawerState.close() }
                                        },
                                        onSelectChat = { id ->
                                            vm.selectChat(id)
                                            scope.launch { drawerState.close() }
                                        },
                                        onTogglePinChat = vm::togglePinChat,
                                        onDeleteChat = vm::deleteChat,
                                        onSelectGemMode = vm::selectGemMode,
                                        onEvaluateMath = vm::evaluateMathInline,
                                        onTranslatePrompt = { prompt ->
                                            scope.launch { drawerState.close() }
                                            vm.sendMessage(prompt)
                                        }
                                    )
                                }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .imePadding()
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            AnimatedContent(
                                                targetState = activeTab,
                                                transitionSpec = {
                                                    val forward = targetState.ordinal >= initialState.ordinal
                                                    (slideInHorizontally(
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        ),
                                                        initialOffsetX = { if (forward) it / 8 else -it / 8 }
                                                    ) + fadeIn()).togetherWith(
                                                        slideOutHorizontally(
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                                stiffness = Spring.StiffnessMediumLow
                                                            ),
                                                            targetOffsetX = { if (forward) -it / 10 else it / 10 }
                                                        ) + fadeOut()
                                                    )
                                                },
                                                label = "main_tab_transition"
                                            ) { tab ->
                                                when (tab) {
                                                    MainTab.CHAT -> {
                                                        Column(modifier = Modifier.fillMaxSize()) {
                                                            ChatTopAppBar(
                                                                prefsState = prefsState,
                                                                activeModeBadge = activeModeBadge,
                                                                isApiReady = isApiReady,
                                                                strings = strings,
                                                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                                                onOpenQuickConfig = { showQuickConfigSheet = true },
                                                                onToggleTts = vm::toggleTts,
                                                                onOpenProfileHub = { showProfileHub = true },
                                                                onSelectPreset = vm::setPreset,
                                                                onSelectReasoning = vm::setReasoningEffort,
                                                                onEvaluateMath = vm::evaluateMathInline
                                                            )
                                                            MainChatContent(
                                                                prefsState = prefsState,
                                                                customizationState = customizationState,
                                                                marqueeSeed = marqueeSeed,
                                                                messages = messages,
                                                                isIncognito = isIncognito,
                                                                isGenerating = isGenerating,
                                                                statusBanner = statusBanner,
                                                                attachedFileSummary = attachedFileSummary,
                                                                pendingMedia = pendingMedia,
                                                                composerPrefill = composerPrefill,
                                                                strings = strings,
                                                                onDismissBanner = vm::dismissBanner,
                                                                onClearAttachment = vm::clearAttachedFile,
                                                                onClearMedia = vm::clearPendingMedia,
                                                                onSendMessage = vm::sendMessage,
                                                                onStopGeneration = vm::stopGeneration,
                                                                onConfirmAction = vm::confirmMessageAction,
                                                                onOpenToolsSheet = { showToolsSheet = true },
                                                                onToggleSkills = vm::toggleSkills,
                                                                onToggleCanvas = vm::toggleCanvas,
                                                                onSelectReasoning = vm::setReasoningEffort,
                                                                onSelectTemperature = vm::setTemperatureLevel,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                    }

                                                    MainTab.SEARCH -> {
                                                        SearchScreen(
                                                            query = globalQuery,
                                                            messages = searchMessages,
                                                            artifacts = searchArtifacts,
                                                            strings = strings,
                                                            onQueryChange = vm::setGlobalSearchQuery,
                                                            onOpenDrawer = { scope.launch { drawerState.open() } },
                                                            onSelectChat = vm::selectChat,
                                                            onOpenLibrary = { vm.setTab(MainTab.LIBRARY) }
                                                        )
                                                    }

                                                    MainTab.LIBRARY -> {
                                                        LibraryScreen(
                                                            artifacts = artifacts,
                                                            strings = strings,
                                                            onOpenDrawer = { scope.launch { drawerState.open() } },
                                                            onCreateArtifact = vm::createArtifact,
                                                            onUpdateArtifact = vm::updateArtifact,
                                                            onTogglePinArtifact = vm::toggleArtifactPin,
                                                            onDeleteArtifact = vm::deleteArtifact,
                                                            onRestoreArtifact = vm::restoreArtifact,
                                                            onInsertIntoChat = vm::injectArtifactIntoChat
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (!isImeVisible) {
                                            FloatingBottomNavigationBar(
                                                activeTab = activeTab,
                                                isIncognito = isIncognito,
                                                liquidGlassEnabled = customizationState.liquidGlassEnabled,
                                                strings = strings,
                                                onSelectTab = vm::setTab,
                                                onToggleIncognito = vm::toggleIncognitoChat
                                            )
                                        }
                                    }

                                    // Subtle multi-depth holiday snowfall across Chat (empty & active)
                                    SnowfallOverlay(
                                        enabled = customizationState.snowfallEnabled &&
                                            activeTab == MainTab.CHAT &&
                                            !showProfileHub &&
                                            !showSourcesScreen,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = showProfileHub,
                                enter = slideInVertically(
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                                    initialOffsetY = { it / 8 }
                                ) + fadeIn(),
                                exit = slideOutVertically(
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                                    targetOffsetY = { it / 8 }
                                ) + fadeOut()
                            ) {
                                ProfileSettingsHub(
                                    prefsState = prefsState,
                                    customizationState = customizationState,
                                    chats = chats,
                                    usageList = tokenUsage,
                                    gemModes = gemModes,
                                    slots = slots,
                                    scheduledActions = scheduledActions,
                                    devPosts = devPosts,
                                    todayKey = vm.todayDateKey(),
                                    monthKey = vm.currentMonthKey(),
                                    strings = strings,
                                    isProviderKeyConfigured = vm::isProviderKeyConfigured,
                                    onClose = { showProfileHub = false },
                                    onOpenSources = { showSourcesScreen = true },
                                    onOpenQuickProviderConfig = { showQuickConfigSheet = true },
                                    onOpenLibrary = { vm.setTab(MainTab.LIBRARY) },
                                    onUpdateProfileMedia = vm::updateUserProfileMedia,
                                    firebaseUserEmail = firebaseUser?.email ?: prefsState.googleAccountLabel.takeIf { firebaseUser != null && it.isNotBlank() },
                                    onConnectGoogleFirebase = triggerGoogleSignIn,
                                    onLogout = {
                                        showProfileHub = false
                                        GoogleAuthHelper.signOutGoogle(credentialManager, scope) {
                                            vm.logoutLocalUser()
                                        }
                                    },
                                    onUpdatePersonalContext = vm::updatePersonalContext,
                                    onUpdateBudget = vm::updateBudgetSettings,
                                    onResetUsage = vm::resetTokenUsage,
                                    onSelectGemMode = vm::selectGemMode,
                                    onCreateGemMode = vm::saveCustomGemMode,
                                    onDeleteGemMode = vm::deleteCustomGemMode,
                                    onAddScheduledAction = vm::addScheduledAction,
                                    onToggleScheduledAction = vm::toggleScheduledAction,
                                    onDeleteScheduledAction = vm::deleteScheduledAction,
                                    onAddDevPost = vm::addDevPost,
                                    onUpdateDevPost = vm::updateDevPost,
                                    onDeleteDevPost = vm::deleteDevPost,
                                    onSelectPalette = vm::setPalettePreset,
                                    onToggleLiquidGlass = vm::setLiquidGlassEnabled,
                                    onToggleSnowfall = vm::setSnowfallEnabled,
                                    onToggleBouncingShapes = vm::setBouncingShapesEnabled,
                                    onToggleAutoScrollPrompts = vm::setAutoScrollPromptsEnabled,
                                    onSelectEmojiStyle = vm::setEmojiStyle,
                                    onSelectLanguage = vm::setLanguage,
                                    onToggleSlot = vm::toggleSlotEnabled,
                                    onMoveSlot = vm::moveSlotPriority,
                                    onResetCooldowns = vm::resetSlotCooldowns,
                                    onToggleOverlay = vm::toggleFloatingOverlay,
                                    onToggleNotifications = toggleNotificationsFlow,
                                    onToggleTelemetry = vm::setTelemetryEnabled,
                                    onImportArtifact = { title, type, category, content ->
                                        vm.createArtifact(title, type, category, content)
                                    }
                                )
                            }

                            AnimatedVisibility(
                                visible = showSourcesScreen,
                                enter = slideInHorizontally(
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                                    initialOffsetX = { it / 6 }
                                ) + fadeIn(),
                                exit = slideOutHorizontally(
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                                    targetOffsetX = { it / 6 }
                                ) + fadeOut()
                            ) {
                                SourcesScreen(
                                    plugins = plugins,
                                    prefs = vm.prefs,
                                    strings = strings,
                                    onBack = { showSourcesScreen = false },
                                    onVerifyGitHub = vm::verifyAndConnectGitHub,
                                    onSaveGoogleOAuth = vm::saveGoogleOAuthAndConnect,
                                    onVerifyGeneric = vm::verifyAndConnectGenericPlugin,
                                    onSaveGeneric = vm::saveGenericPluginWithoutPing,
                                    onDisconnectPlugin = vm::disconnectPlugin,
                                    onToggleEnabled = vm::togglePluginEnabled,
                                    onImportSkill = vm::importSkillPlugin,
                                    onImportWebhook = vm::importWebhookPlugin,
                                    onDeletePlugin = vm::deletePlugin
                                )
                            }

                            if (showQuickConfigSheet) {
                                QuickProviderConfigBottomSheet(
                                    prefsState = prefsState,
                                    prefsManager = vm.prefs,
                                    strings = strings,
                                    onDismiss = { showQuickConfigSheet = false },
                                    onSave = vm::saveProviderConfig
                                )
                            }

                            if (showToolsSheet) {
                                ToolsBottomSheet(
                                    strings = strings,
                                    onDismiss = { showToolsSheet = false },
                                    onPickPhoto = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onPickVideo = {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    onLaunchCamera = launchCameraFlow,
                                    onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                                    onOpenSources = { showSourcesScreen = true },
                                    onOpenLibrary = { vm.setTab(MainTab.LIBRARY) },
                                    onLaunchMaps = {
                                        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
                                        runCatching { context.startActivity(mapIntent) }
                                    },
                                    onSelectToolPrompt = { prefill, enableCanvas ->
                                        if (enableCanvas && !prefsState.canvasEnabled) {
                                            vm.toggleCanvas()
                                        }
                                        if (prefill.isNotBlank()) {
                                            vm.setComposerPrefill(prefill)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
