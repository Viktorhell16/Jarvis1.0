package com.example.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.GemModeEntity
import com.example.data.local.PersonalContext
import com.example.data.local.ScheduledActionEntity
import com.example.ui.i18n.UiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalContextScreen(
    initial: PersonalContext,
    strings: UiStrings,
    onBack: () -> Unit,
    onSave: (PersonalContext) -> Unit
) {
    BackHandler(onBack = onBack)
    var music by remember { mutableStateOf(initial.music) }
    var movies by remember { mutableStateOf(initial.movies) }
    var hobbies by remember { mutableStateOf(initial.hobbies) }
    var style by remember { mutableStateOf(initial.communicationStyle) }
    var exclusions by remember { mutableStateOf(initial.exclusions) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.personalContext) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Сжатый профиль автоматически добавляется в system-промпт LLM для персонализации без лишних токенов.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(value = music, onValueChange = { music = it }, label = { Text("Музыка") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = movies, onValueChange = { movies = it }, label = { Text("Фильмы и сериалы") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = hobbies, onValueChange = { hobbies = it }, label = { Text("Игры и хобби") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = style, onValueChange = { style = it }, label = { Text("Стиль общения") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = exclusions, onValueChange = { exclusions = it }, label = { Text("Исключения (чего избегать)") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    onSave(PersonalContext(music, movies, hobbies, style, exclusions))
                    onBack()
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Сохранить контекст")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemManagerScreen(
    modes: List<GemModeEntity>,
    activeGemId: Long,
    strings: UiStrings,
    onBack: () -> Unit,
    onSelectMode: (Long) -> Unit,
    onCreateMode: (name: String, desc: String, prompt: String, raw: Boolean) -> Unit,
    onDeleteMode: (Long) -> Unit
) {
    BackHandler(onBack = onBack)
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.jarManager) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, contentDescription = "Add JAR-bot") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            modes.forEach { mode ->
                val selected = mode.id == activeGemId
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectMode(mode.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (mode.rawOutputMode) {
                                Text("RAW output (код/текст без обёртки)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected")
                        if (!mode.isBuiltIn) {
                            IconButton(onClick = { onDeleteMode(mode.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var prompt by remember { mutableStateOf("") }
        var raw by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Новый JAR-бот") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Описание") }, singleLine = true)
                    OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("System Prompt") }, minLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Прямой вывод (RAW)", modifier = Modifier.weight(1f))
                        Switch(checked = raw, onCheckedChange = { raw = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onCreateMode(name, desc, prompt, raw)
                    showCreate = false
                }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Отмена") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledActionsScreen(
    actions: List<ScheduledActionEntity>,
    strings: UiStrings,
    onBack: () -> Unit,
    onAdd: (title: String, prompt: String, timeLabel: String) -> Unit,
    onToggle: (ScheduledActionEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    BackHandler(onBack = onBack)
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.scheduledActions) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "Add") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (actions.isEmpty()) {
                Text("Нет запланированных сценариев. Нажмите + чтобы добавить.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                actions.forEach { action ->
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(action.title, style = MaterialTheme.typography.titleSmall)
                                Text("${action.timeLabel} • ${action.prompt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = action.isEnabled, onCheckedChange = { onToggle(action) })
                            IconButton(onClick = { onDelete(action.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var prompt by remember { mutableStateOf("") }
        var time by remember { mutableStateOf("09:00") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Новое действие") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Время / расписание") }, singleLine = true)
                    OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Промпт для JARVIS") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank() && prompt.isNotBlank()) {
                        onAdd(title, prompt, time)
                        showAdd = false
                    }
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } }
        )
    }
}
