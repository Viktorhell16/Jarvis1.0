package com.example.ui.chat

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ChatEntity
import com.example.data.local.GemModeEntity
import com.example.ui.i18n.UiStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay

@Composable
fun NavigationDrawerContent(
    chats: List<ChatEntity>,
    activeChatId: Long?,
    searchQuery: String,
    gemModes: List<GemModeEntity>,
    activeGemId: Long,
    strings: UiStrings,
    onSearchQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (Long) -> Unit,
    onTogglePinChat: (ChatEntity) -> Unit,
    onDeleteChat: (Long) -> Unit,
    onSelectGemMode: (Long) -> Unit,
    onEvaluateMath: (String) -> String?,
    onTranslatePrompt: (String) -> Unit
) {
    var activeUtility by remember { mutableStateOf<String?>(null) }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(316.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Button(
                onClick = onNewChat,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("drawer_new_chat")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.newChat)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(strings.searchChats) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_search_input")
            )

            Spacer(Modifier.height(12.dp))

            // Quick Mode Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                gemModes.forEach { mode ->
                    FilterChip(
                        selected = mode.id == activeGemId,
                        onClick = { onSelectGemMode(mode.id) },
                        label = { Text(mode.name, maxLines = 1) },
                        shape = RoundedCornerShape(18.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // Chat History List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val pinned = chats.filter { it.isPinned }
                val regular = chats.filterNot { it.isPinned }

                if (pinned.isNotEmpty()) {
                    item {
                        Text(
                            text = strings.pinnedChats,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                        )
                    }
                    items(pinned, key = { "pin_${it.id}" }) { chat ->
                        DrawerChatRow(
                            chat = chat,
                            isSelected = chat.id == activeChatId,
                            onSelect = { onSelectChat(chat.id) },
                            onTogglePin = { onTogglePinChat(chat) },
                            onDelete = { onDeleteChat(chat.id) }
                        )
                    }
                }

                item {
                    Text(
                        text = strings.recentChats,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                    )
                }
                if (regular.isEmpty() && pinned.isEmpty()) {
                    item {
                        Text(
                            text = "История пуста",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    items(regular, key = { it.id }) { chat ->
                        DrawerChatRow(
                            chat = chat,
                            isSelected = chat.id == activeChatId,
                            onSelect = { onSelectChat(chat.id) },
                            onTogglePin = { onTogglePinChat(chat) },
                            onDelete = { onDeleteChat(chat.id) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // Built-in Offline Utilities
            Text(
                text = strings.offlineUtilities,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UtilityChip(Icons.Outlined.Calculate, strings.calculator) { activeUtility = "CALC" }
                UtilityChip(Icons.Outlined.Timer, strings.timer) { activeUtility = "TIMER" }
                UtilityChip(Icons.Outlined.WatchLater, strings.stopwatch) { activeUtility = "STOPWATCH" }
                UtilityChip(Icons.Outlined.Alarm, strings.alarm) { activeUtility = "ALARM" }
                UtilityChip(Icons.Outlined.Translate, strings.translator) { activeUtility = "TRANSLATOR" }
            }
        }
    }

    activeUtility?.let { util ->
        OfflineUtilityDialog(
            utility = util,
            strings = strings,
            onDismiss = { activeUtility = null },
            onEvaluateMath = onEvaluateMath,
            onSendTranslation = { query ->
                activeUtility = null
                onTranslatePrompt(query)
            }
        )
    }
}

@Composable
private fun DrawerChatRow(
    chat: ChatEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = chat.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (chat.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Pin",
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun UtilityChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun OfflineUtilityDialog(
    utility: String,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onEvaluateMath: (String) -> String?,
    onSendTranslation: (String) -> Unit
) {
    val context = LocalContext.current
    when (utility) {
        "CALC" -> {
            var expr by remember { mutableStateOf("256 * 12 + sqrt(144)") }
            val result = remember(expr) { onEvaluateMath(expr) ?: "—" }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(strings.calculator) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = expr,
                            onValueChange = { expr = it },
                            label = { Text("Выражение") },
                            singleLine = true
                        )
                        Text(
                            text = "= $result",
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
            )
        }
        "STOPWATCH" -> {
            var running by remember { mutableStateOf(false) }
            var elapsedMs by remember { mutableLongStateOf(0L) }
            LaunchedEffect(running) {
                while (running) {
                    delay(100L)
                    elapsedMs += 100L
                }
            }
            val secs = (elapsedMs / 1000L) % 60
            val mins = (elapsedMs / 60_000L)
            val tenths = (elapsedMs % 1000L) / 100L
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(strings.stopwatch) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = String.format("%02d:%02d.%d", mins, secs, tenths),
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { running = !running }) {
                                Text(if (running) "Пауза" else "Старт")
                            }
                            OutlinedButton(onClick = { running = false; elapsedMs = 0L }) {
                                Text("Сброс")
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
            )
        }
        "TIMER" -> {
            var minutes by remember { mutableStateOf("5") }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(strings.timer) },
                text = {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Минуты") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val secs = (minutes.toIntOrNull() ?: 5) * 60
                        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                            putExtra(AlarmClock.EXTRA_LENGTH, secs)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        }
                        runCatching { context.startActivity(intent) }
                        onDismiss()
                    }) { Text("Запустить") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
            )
        }
        "ALARM" -> {
            var hour by remember { mutableStateOf("07") }
            var minute by remember { mutableStateOf("30") }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(strings.alarm) },
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = hour,
                            onValueChange = { hour = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Час") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minute,
                            onValueChange = { minute = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Мин") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val h = (hour.toIntOrNull() ?: 7).coerceIn(0, 23)
                        val m = (minute.toIntOrNull() ?: 30).coerceIn(0, 59)
                        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, h)
                            putExtra(AlarmClock.EXTRA_MINUTES, m)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        }
                        runCatching { context.startActivity(intent) }
                        onDismiss()
                    }) { Text("Установить") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
            )
        }
        "TRANSLATOR" -> {
            var sourceText by remember { mutableStateOf("") }
            var targetLang by remember { mutableStateOf("English") }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(strings.translator) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("English", "Русский", "Українська", "Polski").forEach { lang ->
                                FilterChip(
                                    selected = targetLang == lang,
                                    onClick = { targetLang = lang },
                                    label = { Text(lang) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = sourceText,
                            onValueChange = { sourceText = it },
                            label = { Text("Текст для перевода") },
                            minLines = 3
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (sourceText.isNotBlank()) {
                            onSendTranslation("Переведи на $targetLang без лишних комментариев:\n$sourceText")
                        }
                    }) { Text("Перевести") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
            )
        }
    }
}
