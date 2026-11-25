package com.example.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.CustomizationState
import com.example.data.local.JarvPrefsState
import com.example.data.local.MessageEntity
import com.example.domain.prompts.PromptMarqueeGenerator
import com.example.ui.MainTab
import com.example.ui.animations.liquidGlassBlurLayer
import com.example.ui.animations.springBounce
import com.example.ui.dashboard.SmallUserAvatar
import com.example.ui.i18n.UiStrings
import com.example.ui.sheets.PresetDropdownMenu
import kotlinx.coroutines.delay

private val StatusOptimalGreen = Color(0xFF22C55E)
private val StatusWarningAmber = Color(0xFFF59E0B)

@Composable
fun ChatTopAppBar(
    prefsState: JarvPrefsState,
    activeModeBadge: String,
    isApiReady: Boolean,
    strings: UiStrings,
    onOpenDrawer: () -> Unit,
    onOpenQuickConfig: () -> Unit,
    onToggleTts: () -> Unit,
    onOpenProfileHub: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onSelectReasoning: (String) -> Unit,
    onEvaluateMath: (String) -> String?
) {
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var showQuickUtilities by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val centerTitle = when (prefsState.activePresetId) {
        "DAILY" -> "4 Hydrogen"
        "DEEP" -> "4 Neon"
        else -> when (prefsState.language.code) {
            "uk" -> "Автоматично"
            "en" -> "Auto"
            else -> "Автоматически"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .springBounce()
                .testTag("top_menu_button")
        ) {
            Icon(Icons.Filled.Menu, contentDescription = "Navigation Drawer")
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .springBounce(0.96f)
                    .clickable { presetMenuExpanded = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("top_preset_dropdown_pill"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live API Status Indicator Dot (Green = ready, Amber = fallback/no key/cooldown)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isApiReady) StatusOptimalGreen else StatusWarningAmber)
                        .testTag("top_api_status_dot")
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = centerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                ) {
                    Text(
                        text = activeModeBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Select preset",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PresetDropdownMenu(
                expanded = presetMenuExpanded,
                onDismiss = { presetMenuExpanded = false },
                activePresetId = prefsState.activePresetId,
                reasoningEffort = prefsState.reasoningEffort,
                strings = strings,
                onSelectPreset = onSelectPreset,
                onSelectReasoning = onSelectReasoning
            )
        }

        IconButton(
            onClick = { showQuickUtilities = true },
            modifier = Modifier
                .size(40.dp)
                .springBounce()
                .testTag("top_quick_utilities_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = strings.quickUtilitiesTitle,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onOpenQuickConfig,
            modifier = Modifier
                .size(40.dp)
                .springBounce()
                .testTag("top_quick_api_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = strings.quickConfigTitle,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onToggleTts,
            modifier = Modifier
                .size(40.dp)
                .springBounce()
                .testTag("top_tts_toggle_button")
        ) {
            Icon(
                imageVector = if (prefsState.ttsEnabled) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                contentDescription = strings.ttsEnabledDesc,
                tint = if (prefsState.ttsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .springBounce()
                .clickable(onClick = onOpenProfileHub)
                .padding(4.dp)
                .testTag("top_avatar_button")
        ) {
            SmallUserAvatar(
                name = prefsState.userName.ifBlank { "J" },
                avatarUri = prefsState.avatarUri,
                sizeDp = 30
            )
        }
    }

    if (showQuickUtilities) {
        QuickUtilitiesPopupDialog(
            strings = strings,
            onEvaluateMath = onEvaluateMath,
            onStartTimerMinutes = { minutes ->
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS Timer")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
                showQuickUtilities = false
            },
            onDismiss = { showQuickUtilities = false }
        )
    }
}

@Composable
private fun QuickUtilitiesPopupDialog(
    strings: UiStrings,
    onEvaluateMath: (String) -> String?,
    onStartTimerMinutes: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var expr by remember { mutableStateOf("") }
    var calcResult by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.quickUtilitiesTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.calculator, style = MaterialTheme.typography.titleSmall)
                }
                OutlinedTextField(
                    value = expr,
                    onValueChange = {
                        expr = it
                        calcResult = onEvaluateMath(it)
                    },
                    placeholder = { Text("24 * 12 + sqrt(144)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                calcResult?.let { res ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "= $res",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.timer, style = MaterialTheme.typography.titleSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 15, 30).forEach { min ->
                        AssistChip(
                            onClick = { onStartTimerMinutes(min) },
                            label = { Text("${min}м", maxLines = 1, softWrap = false) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.springBounce()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
fun MainChatContent(
    prefsState: JarvPrefsState,
    customizationState: CustomizationState,
    marqueeSeed: Long,
    messages: List<MessageEntity>,
    isIncognito: Boolean,
    isGenerating: Boolean,
    statusBanner: String?,
    attachedFileSummary: String?,
    composerPrefill: String,
    strings: UiStrings,
    onDismissBanner: () -> Unit,
    onClearAttachment: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onConfirmAction: (MessageEntity, Boolean) -> Unit,
    onOpenToolsSheet: () -> Unit,
    onToggleSkills: () -> Unit,
    onToggleCanvas: () -> Unit,
    onSelectReasoning: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    LaunchedEffect(composerPrefill) {
        if (composerPrefill.isNotEmpty()) {
            inputText = composerPrefill
        }
    }

    val listState = rememberLazyListState()
    val suggestionRowState = rememberLazyListState()

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputText = spoken
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefsState.language.localeTag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, strings.listeningNotice)
            }
            runCatching { speechLauncher.launch(intent) }
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Auto-scroll prompt marquee (10,000 variants), pausing smoothly on touch/drag
    LaunchedEffect(
        messages.isEmpty(),
        customizationState.autoScrollPromptsEnabled,
        marqueeSeed,
        suggestionRowState.isScrollInProgress
    ) {
        if (!messages.isEmpty() || !customizationState.autoScrollPromptsEnabled || suggestionRowState.isScrollInProgress) {
            return@LaunchedEffect
        }
        delay(650L)
        suggestionRowState.scroll(MutatePriority.Default) {
            var lastNanos = 0L
            while (true) {
                var dx = 0f
                withFrameNanos { nanos ->
                    if (lastNanos != 0L) {
                        val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                        dx = 54f * dt
                    }
                    lastNanos = nanos
                }
                if (dx > 0f) {
                    scrollBy(dx)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
    ) {
        if (isIncognito) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = strings.temporaryChatBanner,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        statusBanner?.let { banner ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissBanner, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Empty Hero State (Centered greeting + 10,000-Variant Auto-Scrolling Prompt Marquee) or Active Stream
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    val nick = prefsState.userName.ifBlank { "JARVIS" }
                    Text(
                        text = "${nick}${strings.greetingSuffix}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .testTag("hero_centered_greeting")
                    )

                    LazyRow(
                        state = suggestionRowState,
                        flingBehavior = rememberSnapFlingBehavior(lazyListState = suggestionRowState),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hero_suggestion_carousel")
                    ) {
                        items(count = PromptMarqueeGenerator.TOTAL_COMBINATIONS, key = { it }) { index ->
                            val cleanPrompt = remember(index, marqueeSeed) {
                                PromptMarqueeGenerator.promptAt(index, marqueeSeed)
                            }
                            val displayLabel = remember(index, marqueeSeed, customizationState.emojiStyle) {
                                PromptMarqueeGenerator.displayPromptAt(index, marqueeSeed, customizationState.emojiStyle)
                            }
                            AssistChip(
                                onClick = { onSendMessage(cleanPrompt) },
                                label = {
                                    Text(
                                        text = displayLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .springBounce()
                                    .let { if (index < 3) it.testTag("marquee_chip_$index") else it }
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        strings = strings,
                        onConfirmAction = onConfirmAction
                    )
                }
            }
        }

        if (isGenerating) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = onStopGeneration,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .springBounce()
                        .testTag("stop_generation_button")
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.stopGeneration)
                }
            }
        }

        attachedFileSummary?.let { summary ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = summary.lineSequence().firstOrNull() ?: "File attached",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove attachment",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onClearAttachment)
                    )
                }
            }
        }

        // Input Capsule (28.dp radius)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenToolsSheet,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .springBounce()
                        .testTag("composer_plus_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Tools",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = strings.messageHint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_input_field")
                    )
                }

                val hasText = inputText.isNotBlank()
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .springBounce(0.90f)
                        .clickable {
                            if (hasText) {
                                val toSend = inputText
                                inputText = ""
                                onSendMessage(toSend)
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefsState.language.localeTag)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, strings.listeningNotice)
                                    }
                                    runCatching { speechLauncher.launch(intent) }
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                        .testTag("composer_action_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (hasText) Icons.AutoMirrored.Filled.Send else Icons.Filled.GraphicEq,
                            contentDescription = if (hasText) "Send" else "Voice input",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // 3 Pill Controls directly below input capsule (pushed up smoothly with IME)
        PillActionRow(
            skillsEnabled = prefsState.skillsEnabled,
            canvasEnabled = prefsState.canvasEnabled,
            reasoningEffort = prefsState.reasoningEffort,
            strings = strings,
            onToggleSkills = onToggleSkills,
            onToggleCanvas = onToggleCanvas,
            onSelectReasoning = onSelectReasoning
        )
    }
}

@Composable
private fun PillActionRow(
    skillsEnabled: Boolean,
    canvasEnabled: Boolean,
    reasoningEffort: String,
    strings: UiStrings,
    onToggleSkills: () -> Unit,
    onToggleCanvas: () -> Unit,
    onSelectReasoning: (String) -> Unit
) {
    var reasoningMenuExpanded by remember { mutableStateOf(false) }

    val reasoningLabel = when (reasoningEffort) {
        "medium" -> strings.reasoningMedium
        "high" -> strings.reasoningHigh
        else -> strings.reasoningLow
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = skillsEnabled,
            onClick = onToggleSkills,
            leadingIcon = {
                Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = { Text(strings.skillsPill, maxLines = 1, softWrap = false) },
            shape = RoundedCornerShape(20.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .springBounce()
                .testTag("pill_skills")
        )

        FilterChip(
            selected = canvasEnabled,
            onClick = onToggleCanvas,
            leadingIcon = {
                Icon(Icons.Outlined.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = { Text(strings.canvasPill, maxLines = 1, softWrap = false) },
            shape = RoundedCornerShape(20.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .springBounce()
                .testTag("pill_canvas")
        )

        Box {
            FilterChip(
                selected = reasoningEffort != "low",
                onClick = { reasoningMenuExpanded = true },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${strings.reasoningPrefix}: $reasoningLabel",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Reasoning effort",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .springBounce()
                    .testTag("pill_reasoning")
            )

            DropdownMenu(
                expanded = reasoningMenuExpanded,
                onDismissRequest = { reasoningMenuExpanded = false },
                offset = DpOffset(0.dp, (-196).dp),
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .width(240.dp)
                    .testTag("reasoning_upward_menu")
            ) {
                listOf(
                    Triple("low", strings.reasoningLow, strings.reasoningLowSub),
                    Triple("medium", strings.reasoningMedium, strings.reasoningMediumSub),
                    Triple("high", strings.reasoningHigh, strings.reasoningHighSub)
                ).forEach { (key, title, sub) ->
                    DropdownMenuItem(
                        onClick = {
                            onSelectReasoning(key)
                            reasoningMenuExpanded = false
                        },
                        leadingIcon = {
                            if (reasoningEffort == key) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            } else {
                                Spacer(Modifier.size(18.dp))
                            }
                        },
                        text = {
                            Column {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: MessageEntity,
    strings: UiStrings,
    onConfirmAction: (MessageEntity, Boolean) -> Unit
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "JARVIS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                MarkdownMessageText(markdown = message.content)

                if (message.actionPayloadJson != null && message.actionStatus == "PENDING") {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onConfirmAction(message, true) },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .springBounce()
                                .testTag("action_execute_button")
                        ) {
                            Text(strings.executeAction)
                        }
                        OutlinedButton(
                            onClick = { onConfirmAction(message, false) },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .springBounce()
                                .testTag("action_cancel_button")
                        ) {
                            Text(strings.cancelAction)
                        }
                    }
                }

                if (message.modelTag.isNotBlank()) {
                    Text(
                        text = "${message.providerName} • ${message.modelTag}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingBottomNavigationBar(
    activeTab: MainTab,
    isIncognito: Boolean,
    liquidGlassEnabled: Boolean,
    strings: UiStrings,
    onSelectTab: (MainTab) -> Unit,
    onToggleIncognito: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glassShape = RoundedCornerShape(32.dp)
    val glassGradient = if (liquidGlassEnabled) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF282D3B).copy(alpha = 0.64f),
                Color(0xFF10131A).copy(alpha = 0.80f)
            )
        )
    } else {
        SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    val glassBorder = if (liquidGlassEnabled) {
        BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.36f),
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent
                )
            )
        )
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .clip(glassShape)
                .border(glassBorder, glassShape)
        ) {
            // Blurred frosted backdrop layer behind sharp icons/text
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (liquidGlassEnabled) Modifier.liquidGlassBlurLayer(26f) else Modifier)
                    .background(glassGradient)
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                val tabWidth = maxWidth / 3f
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * activeTab.ordinal,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "liquid_glass_droplet_offset"
                )

                // Sliding Liquid Glass Droplet Pill
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            if (liquidGlassEnabled) {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                        Color.White.copy(alpha = 0.14f)
                                    )
                                )
                            } else {
                                SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)
                            }
                        )
                        .then(
                            if (liquidGlassEnabled) {
                                Modifier.border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.40f),
                                            Color.Transparent
                                        )
                                    ),
                                    RoundedCornerShape(26.dp)
                                )
                            } else Modifier
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavPillItem(
                        icon = Icons.Outlined.Forum,
                        label = strings.navChat,
                        selected = activeTab == MainTab.CHAT,
                        testTag = "nav_tab_chat",
                        onClick = { onSelectTab(MainTab.CHAT) },
                        modifier = Modifier.weight(1f)
                    )
                    NavPillItem(
                        icon = Icons.Outlined.Search,
                        label = strings.navSearch,
                        selected = activeTab == MainTab.SEARCH,
                        testTag = "nav_tab_search",
                        onClick = { onSelectTab(MainTab.SEARCH) },
                        modifier = Modifier.weight(1f)
                    )
                    NavPillItem(
                        icon = Icons.Outlined.CollectionsBookmark,
                        label = strings.navLibrary,
                        selected = activeTab == MainTab.LIBRARY,
                        testTag = "nav_tab_library",
                        onClick = { onSelectTab(MainTab.LIBRARY) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .springBounce(0.91f)
                .border(
                    width = 1.dp,
                    brush = if (isIncognito) {
                        SolidColor(MaterialTheme.colorScheme.primary)
                    } else if (liquidGlassEnabled) {
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.36f), Color.Transparent))
                    } else {
                        SolidColor(MaterialTheme.colorScheme.outlineVariant)
                    },
                    shape = CircleShape
                )
                .clickable(onClick = onToggleIncognito)
                .testTag("incognito_chat_button"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (liquidGlassEnabled) Modifier.liquidGlassBlurLayer(22f) else Modifier)
                    .background(
                        if (isIncognito) {
                            SolidColor(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
                        } else {
                            glassGradient
                        }
                    )
            )
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = strings.temporaryChatTitle,
                tint = if (isIncognito) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun NavPillItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(26.dp))
            .springBounce(0.93f)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
