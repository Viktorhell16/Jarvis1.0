package com.example.ui.dashboard

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.ChatEntity
import com.example.data.local.CustomizationState
import com.example.data.local.DevPostEntity
import com.example.data.local.GemModeEntity
import com.example.data.local.JarvPrefsState
import com.example.data.local.PersonalContext
import com.example.data.local.ProviderSlotEntity
import com.example.data.local.ScheduledActionEntity
import com.example.data.local.TokenUsageEntity
import com.example.ui.animations.BouncingShapesBackground
import com.example.ui.animations.springBounce
import com.example.ui.customization.CustomizationScreen
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.UiStrings
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.EmojiStyle
import com.example.ui.theme.GifImageLoader
import com.example.ui.theme.PalettePreset

private enum class HubSubRoute {
    NONE, TOKEN_DASHBOARD, PERSONAL_CONTEXT, JAR_MANAGER, SCHEDULED, SETTINGS, CUSTOMIZATION, ACTIVITY_HISTORY, PRIVACY, DEV_BLOG
}

@Composable
fun ProfileSettingsHub(
    prefsState: JarvPrefsState,
    customizationState: CustomizationState,
    chats: List<ChatEntity>,
    usageList: List<TokenUsageEntity>,
    gemModes: List<GemModeEntity>,
    slots: List<ProviderSlotEntity>,
    scheduledActions: List<ScheduledActionEntity>,
    devPosts: List<DevPostEntity>,
    todayKey: String,
    monthKey: String,
    strings: UiStrings,
    isProviderKeyConfigured: (String) -> Boolean,
    onClose: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenQuickProviderConfig: () -> Unit,
    onOpenLibrary: () -> Unit,
    onUpdateProfileMedia: (nickname: String, avatarUri: String?, bannerUri: String?) -> Unit,
    onLogout: () -> Unit,
    onUpdatePersonalContext: (PersonalContext) -> Unit,
    onUpdateBudget: (Long, Double, Boolean) -> Unit,
    onResetUsage: () -> Unit,
    onSelectGemMode: (Long) -> Unit,
    onCreateGemMode: (String, String, String, Boolean) -> Unit,
    onDeleteGemMode: (Long) -> Unit,
    onAddScheduledAction: (String, String, String) -> Unit,
    onToggleScheduledAction: (ScheduledActionEntity) -> Unit,
    onDeleteScheduledAction: (Long) -> Unit,
    onAddDevPost: (String, String, String, String) -> Unit,
    onUpdateDevPost: (DevPostEntity) -> Unit,
    onDeleteDevPost: (Long) -> Unit,
    onSelectPalette: (PalettePreset) -> Unit,
    onToggleLiquidGlass: (Boolean) -> Unit,
    onToggleSnowfall: (Boolean) -> Unit,
    onToggleBouncingShapes: (Boolean) -> Unit,
    onToggleAutoScrollPrompts: (Boolean) -> Unit,
    onSelectEmojiStyle: (EmojiStyle) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleSlot: (ProviderSlotEntity) -> Unit,
    onMoveSlot: (ProviderSlotEntity, Int) -> Unit,
    onResetCooldowns: () -> Unit,
    onToggleOverlay: (Boolean) -> Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    onImportArtifact: (title: String, type: String, category: String, content: String) -> Unit
) {
    var subRoute by remember { mutableStateOf(HubSubRoute.NONE) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    val context = LocalContext.current
    val gifImageLoader = remember(context) { GifImageLoader.get(context) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onUpdateProfileMedia(prefsState.userName, it.toString(), prefsState.bannerUri)
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onUpdateProfileMedia(prefsState.userName, prefsState.avatarUri, it.toString())
        }
    }

    val recordImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } ?: ""
            }.onSuccess { text ->
                if (text.isNotBlank()) {
                    onImportArtifact("Импортированная запись", "DOCUMENT", "All", text)
                    showInfoDialog = "Импорт завершён" to "Файл сохранён в вашу локальную Библиотеку (Room DB)."
                }
            }
        }
    }

    BackHandler {
        when (subRoute) {
            HubSubRoute.CUSTOMIZATION -> subRoute = HubSubRoute.SETTINGS
            HubSubRoute.NONE -> onClose()
            else -> subRoute = HubSubRoute.NONE
        }
    }

    AnimatedContent(
        targetState = subRoute,
        transitionSpec = {
            (slideInHorizontally(
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                initialOffsetX = { it / 6 }
            ) + fadeIn()).togetherWith(
                slideOutHorizontally(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                    targetOffsetX = { -it / 8 }
                ) + fadeOut()
            )
        },
        label = "hub_subroute_anim"
    ) { route ->
        when (route) {
            HubSubRoute.TOKEN_DASHBOARD -> {
                TokenSpeedometerScreen(
                    prefsState = prefsState,
                    usageList = usageList,
                    todayKey = todayKey,
                    monthKey = monthKey,
                    strings = strings,
                    onBack = { subRoute = HubSubRoute.NONE },
                    onUpdateBudget = onUpdateBudget,
                    onResetUsage = onResetUsage
                )
            }
            HubSubRoute.PERSONAL_CONTEXT -> {
                PersonalContextScreen(
                    initial = prefsState.personalContext,
                    strings = strings,
                    onBack = { subRoute = HubSubRoute.NONE },
                    onSave = onUpdatePersonalContext
                )
            }
            HubSubRoute.JAR_MANAGER -> {
                GemManagerScreen(
                    modes = gemModes,
                    activeGemId = prefsState.activeGemId,
                    strings = strings,
                    onBack = { subRoute = HubSubRoute.NONE },
                    onSelectMode = onSelectGemMode,
                    onCreateMode = onCreateGemMode,
                    onDeleteMode = onDeleteGemMode
                )
            }
            HubSubRoute.SCHEDULED -> {
                ScheduledActionsScreen(
                    actions = scheduledActions,
                    strings = strings,
                    onBack = { subRoute = HubSubRoute.NONE },
                    onAdd = onAddScheduledAction,
                    onToggle = onToggleScheduledAction,
                    onDelete = onDeleteScheduledAction
                )
            }
            HubSubRoute.DEV_BLOG -> {
                DevBlogScreen(
                    posts = devPosts,
                    strings = strings,
                    onBack = { subRoute = HubSubRoute.NONE },
                    onAddPost = onAddDevPost,
                    onUpdatePost = onUpdateDevPost,
                    onDeletePost = onDeleteDevPost
                )
            }
            HubSubRoute.SETTINGS -> {
                SettingsScreen(
                    prefsState = prefsState,
                    customizationState = customizationState,
                    slots = slots,
                    isProviderKeyConfigured = isProviderKeyConfigured,
                    strings = strings,
                    onBack = { subRoute = HubSubRoute.NONE },
                    onOpenCustomization = { subRoute = HubSubRoute.CUSTOMIZATION },
                    onSelectLanguage = onSelectLanguage,
                    onToggleSlot = onToggleSlot,
                    onMoveSlot = onMoveSlot,
                    onResetCooldowns = onResetCooldowns,
                    onToggleOverlay = onToggleOverlay
                )
            }
            HubSubRoute.CUSTOMIZATION -> {
                CustomizationScreen(
                    customizationState = customizationState,
                    strings = strings,
                    onBack = { subRoute = HubSubRoute.SETTINGS },
                    onSelectPalette = onSelectPalette,
                    onToggleLiquidGlass = onToggleLiquidGlass,
                    onToggleSnowfall = onToggleSnowfall,
                    onToggleBouncingShapes = onToggleBouncingShapes,
                    onToggleAutoScrollPrompts = onToggleAutoScrollPrompts,
                    onSelectEmojiStyle = onSelectEmojiStyle
                )
            }
            else -> {
                val displayNick = prefsState.userName.ifBlank { "JARVIS" }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    BouncingShapesBackground(
                        enabled = customizationState.bouncingShapesEnabled,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SmallUserAvatar(displayNick, prefsState.avatarUri, sizeDp = 24)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "@$displayNick",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = onClose,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .springBounce()
                                        .testTag("hub_close_button")
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close")
                                }
                            }

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(148.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .clickable { bannerPicker.launch(arrayOf("image/*")) }
                                        .testTag("profile_banner_card")
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (!prefsState.bannerUri.isNullOrBlank()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(prefsState.bannerUri)
                                                    .crossfade(true)
                                                    .build(),
                                                imageLoader = gifImageLoader,
                                                contentDescription = "Profile banner",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                                                        )
                                                    )
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.linearGradient(
                                                            colors = listOf(
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                                            )
                                                        )
                                                    )
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color.Black.copy(alpha = 0.45f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Outlined.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("GIF / Баннер", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                            }
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(top = 102.dp)
                                        .size(92.dp)
                                        .clickable { avatarPicker.launch(arrayOf("image/*")) }
                                        .testTag("profile_avatar_picker")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(
                                                Brush.sweepGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.secondary,
                                                        MaterialTheme.colorScheme.tertiary,
                                                        MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            )
                                            .padding(3.dp)
                                    ) {
                                        SmallUserAvatar(
                                            name = displayNick,
                                            avatarUri = prefsState.avatarUri,
                                            sizeDp = 86
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .align(Alignment.BottomEnd)
                                            .offset(x = (-2).dp, y = (-2).dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.CameraAlt, contentDescription = "Change avatar", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${strings.helloUserPrefix}$displayNick!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(Modifier.height(10.dp))

                                OutlinedButton(
                                    onClick = { showProfileDialog = true },
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .springBounce()
                                        .testTag("edit_profile_button")
                                ) {
                                    Text(strings.editProfile)
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .springBounce(0.97f)
                                    .clickable {
                                        onClose()
                                        onLogout()
                                    }
                                    .testTag("logout_account_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                                        contentDescription = strings.logoutAccount,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        text = strings.logoutAccount,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SmallUserAvatar(displayNick, prefsState.avatarUri, sizeDp = 24)
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = strings.otherActionsHeader,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                            )

                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                HubListItem(
                                    icon = Icons.Outlined.AutoAwesome,
                                    title = strings.modelSelectionItem,
                                    testTag = "hub_model_selection",
                                    trailing = {
                                        Text(
                                            text = prefsState.activeModelDisplayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = onOpenQuickProviderConfig
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    HubListItem(Icons.Outlined.DataUsage, strings.usageLimits, testTag = "hub_usage_limits") {
                                        subRoute = HubSubRoute.TOKEN_DASHBOARD
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HubListItem(Icons.Outlined.History, strings.activityHistory) {
                                        subRoute = HubSubRoute.ACTIVITY_HISTORY
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HubListItem(Icons.Outlined.PersonOutline, strings.personalContext, testTag = "hub_personal_context") {
                                        subRoute = HubSubRoute.PERSONAL_CONTEXT
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HubListItem(Icons.Outlined.Extension, strings.connectedApps, testTag = "hub_connected_apps") {
                                        onOpenSources()
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HubListItem(
                                        icon = Icons.Outlined.Notifications,
                                        title = strings.notifications,
                                        trailing = {
                                            Switch(
                                                checked = prefsState.notificationsEnabled,
                                                onCheckedChange = onToggleNotifications
                                            )
                                        },
                                        onClick = { onToggleNotifications(!prefsState.notificationsEnabled) }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HubListItem(Icons.Outlined.UploadFile, strings.importRecords) {
                                        recordImporter.launch(arrayOf("text/*", "application/json", "*/*"))
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HubListItem(Icons.Outlined.Diamond, strings.jarManager, testTag = "hub_jar_manager") {
                                        subRoute = HubSubRoute.JAR_MANAGER
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HubListItem(Icons.Outlined.Schedule, strings.scheduledActions) {
                                        subRoute = HubSubRoute.SCHEDULED
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HubListItem(Icons.Outlined.Book, strings.notebook) {
                                        onClose()
                                        onOpenLibrary()
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HubListItem(Icons.Outlined.Newspaper, strings.newsAndUpdates, testTag = "hub_devblog") {
                                        subRoute = HubSubRoute.DEV_BLOG
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HubListItem(Icons.Outlined.Security, strings.privacyCenter) {
                                        subRoute = HubSubRoute.PRIVACY
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    HubListItem(Icons.Outlined.Settings, strings.settings, testTag = "hub_settings") {
                                        subRoute = HubSubRoute.SETTINGS
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HubListItem(Icons.Outlined.Feedback, strings.feedback) {
                                        showInfoDialog = strings.feedback to "Локальный клиент JARVIS Android работает на Room DB + DataStore + EncryptedSharedPreferences без сторонней телеметрии."
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HubListItem(Icons.Outlined.HelpOutline, strings.help) {
                                        showInfoDialog = strings.help to
                                            "• Офлайн-команды (0 токенов): математика (24*7+15), «таймер 5 мин», «будильник 07:30», «открой YouTube», «пауза» / «следующий трек».\n• Нажмите на иконку API вверху, чтобы задать провайдера, модель и ключ.\n• Доступны экранный агент Accessibility, Кастомизация и JARVIS Mini."
                                    }
                                }
                            }
                        }
                    }

                    if (route == HubSubRoute.ACTIVITY_HISTORY) {
                        ActivityHistoryDialog(chats = chats, usageList = usageList) { subRoute = HubSubRoute.NONE }
                    }
                    if (route == HubSubRoute.PRIVACY) {
                        showInfoDialog = strings.privacyCenter to
                            "100% локальное хранение: переписка и артефакты хранятся в локальной базе Room, настройки внешнего вида — в DataStore, а API-ключи шифруются через Android Keystore (EncryptedSharedPreferences)."
                        subRoute = HubSubRoute.NONE
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        var name by remember { mutableStateOf(prefsState.userName) }
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text(strings.editProfile) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Никнейм") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { avatarPicker.launch(arrayOf("image/*")) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Выбрать аватар (GIF / PNG / JPG)")
                    }
                    OutlinedButton(
                        onClick = { bannerPicker.launch(arrayOf("image/*")) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Выбрать баннер (GIF / PNG / JPG)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateProfileMedia(name, prefsState.avatarUri, prefsState.bannerUri)
                    showProfileDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) { Text("Отмена") }
            }
        )
    }

    showInfoDialog?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = null }) { Text("OK") }
            }
        )
    }
}

@Composable
fun SmallUserAvatar(name: String, avatarUri: String?, sizeDp: Int) {
    val context = LocalContext.current
    val gifImageLoader = remember(context) { GifImageLoader.get(context) }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(sizeDp.dp)
    ) {
        if (!avatarUri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUri)
                    .crossfade(true)
                    .build(),
                imageLoader = gifImageLoader,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "J",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun HubListItem(
    icon: ImageVector,
    title: String,
    testTag: String = "",
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .let { if (testTag.isNotEmpty()) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
private fun ActivityHistoryDialog(
    chats: List<ChatEntity>,
    usageList: List<TokenUsageEntity>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("История действий JARVIS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Сохранено локальных чатов: ${chats.size}", style = MaterialTheme.typography.bodyMedium)
                Text("Записей расхода токенов: ${usageList.size}", style = MaterialTheme.typography.bodyMedium)
                chats.take(5).forEach { chat ->
                    Text("• ${chat.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
