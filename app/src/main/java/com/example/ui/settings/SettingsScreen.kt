package com.example.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.accessibility.JarvisAccessibilityService
import com.example.data.local.CustomizationState
import com.example.data.local.JarvPrefsState
import com.example.data.local.ProviderSlotEntity
import com.example.telemetry.JarvisCrashReporter
import com.example.ui.animations.BouncingShapesBackground
import com.example.ui.animations.springBounce
import com.example.ui.customization.PalettePreviewCircle
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.UiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefsState: JarvPrefsState,
    customizationState: CustomizationState,
    slots: List<ProviderSlotEntity>,
    isProviderKeyConfigured: (String) -> Boolean,
    strings: UiStrings,
    onBack: () -> Unit,
    onOpenCustomization: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleSlot: (ProviderSlotEntity) -> Unit,
    onMoveSlot: (ProviderSlotEntity, Int) -> Unit,
    onResetCooldowns: () -> Unit,
    onToggleOverlay: (Boolean) -> Boolean,
    onToggleTelemetry: (Boolean) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var showTestCrashDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BouncingShapesBackground(
            enabled = customizationState.bouncingShapesEnabled,
            modifier = Modifier.fillMaxSize()
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(strings.settingsTitle) },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dedicated "Кастомизация" Navigation Card (replaces Light/System & 4-chip rows)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .springBounce(0.97f)
                        .clickable(onClick = onOpenCustomization)
                        .testTag("settings_customization_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Palette,
                                    contentDescription = strings.customizationTitle,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.customizationTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = strings.customizationSub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PalettePreviewCircle(preset = customizationState.palettePreset, sizeDp = 14)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = customizationState.palettePreset.titleRu,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Multi-language switcher
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            AppLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = prefsState.language == lang,
                                    onClick = { onSelectLanguage(lang) },
                                    label = { Text(lang.displayName, maxLines = 1, softWrap = false) },
                                    modifier = Modifier
                                        .springBounce()
                                        .testTag("lang_chip_${lang.code}")
                                )
                            }
                        }
                    }
                }

                // Floating Overlay & Accessibility Screen Agent
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.floatingOverlayLabel, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Плавающий пузырь JARVIS Mini с жестом закрытия",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefsState.floatingOverlayEnabled,
                                onCheckedChange = { onToggleOverlay(it) },
                                modifier = Modifier
                                    .springBounce(0.92f)
                                    .testTag("overlay_switch")
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.accessibilityAgentLabel, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (JarvisAccessibilityService.isConnected()) "Подключено (on-demand)" else "Отключено — нажмите для настройки",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (JarvisAccessibilityService.isConnected()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                                modifier = Modifier.springBounce()
                            ) {
                                Text("Открыть", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                // Model Failover Queue — Title on top, "Сбросить cooldown" cleanly on its own row below (never wraps!)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = strings.failoverQueueLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            OutlinedButton(
                                onClick = onResetCooldowns,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .springBounce()
                                    .testTag("reset_cooldowns_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = strings.resetCooldownButton,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        val now = System.currentTimeMillis()
                        slots.sortedBy { it.priorityOrder }.forEach { slot ->
                            val inCooldown = slot.cooldownUntilMs > now
                            val hasKey = isProviderKeyConfigured(slot.providerId)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "#${slot.priorityOrder} ${slot.providerDisplayName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = buildString {
                                            append(slot.modelId)
                                            append(if (hasKey) " • Ключ OK" else " • Без ключа")
                                            if (inCooldown) append(" • Cooldown ${(slot.cooldownUntilMs - now) / 60000}м")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            inCooldown -> MaterialTheme.colorScheme.error
                                            hasKey -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                IconButton(onClick = { onMoveSlot(slot, -1) }) {
                                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Up")
                                }
                                IconButton(onClick = { onMoveSlot(slot, 1) }) {
                                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "Down")
                                }
                                Switch(
                                    checked = slot.isEnabled,
                                    onCheckedChange = { onToggleSlot(slot) },
                                    modifier = Modifier.springBounce(0.92f)
                                )
                            }
                        }
                    }
                }
                // Telemetry & Stability (Crashlytics + Performance Monitoring)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = strings.telemetrySectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = strings.telemetryToggleLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = strings.telemetryToggleSub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = customizationState.telemetryEnabled,
                                onCheckedChange = onToggleTelemetry,
                                modifier = Modifier
                                    .springBounce(0.92f)
                                    .testTag("telemetry_switch")
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        OutlinedButton(
                            onClick = { showTestCrashDialog = true },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .springBounce()
                                .testTag("test_crash_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BugReport,
                                contentDescription = strings.testCrashButtonLabel,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.testCrashButtonLabel,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        if (showTestCrashDialog) {
            AlertDialog(
                onDismissRequest = { showTestCrashDialog = false },
                title = { Text(strings.testCrashDialogTitle) },
                text = { Text(strings.testCrashDialogBody) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showTestCrashDialog = false
                            JarvisCrashReporter.triggerTestCrash()
                        },
                        modifier = Modifier.testTag("test_crash_confirm_button")
                    ) {
                        Text(
                            text = strings.testCrashDialogConfirm,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showTestCrashDialog = false },
                        modifier = Modifier.testTag("test_crash_cancel_button")
                    ) {
                        Text(strings.cancelAction)
                    }
                }
            )
        }
    }
}
