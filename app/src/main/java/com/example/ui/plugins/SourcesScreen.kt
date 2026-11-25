package com.example.ui.plugins

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Webhook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.EncryptedPrefsManager
import com.example.data.local.PluginEntity
import com.example.data.network.GitHubVerificationResult
import com.example.ui.i18n.UiStrings
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    plugins: List<PluginEntity>,
    prefs: EncryptedPrefsManager,
    strings: UiStrings,
    onBack: () -> Unit,
    onVerifyGitHub: suspend (PluginEntity, String) -> Result<GitHubVerificationResult>,
    onSaveGoogleOAuth: (PluginEntity, String, String) -> Unit,
    onVerifyGeneric: suspend (PluginEntity, String, String) -> Result<String>,
    onSaveGeneric: (PluginEntity, String, String) -> Unit,
    onDisconnectPlugin: (PluginEntity) -> Unit,
    onToggleEnabled: (PluginEntity, Boolean) -> Unit,
    onImportSkill: (name: String, desc: String, markdown: String) -> Unit,
    onImportWebhook: (jsonSpec: String) -> Result<Unit>,
    onDeletePlugin: (Long) -> Unit
) {
    BackHandler(onBack = onBack)

    var searchQuery by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var configuringPlugin by remember { mutableStateOf<PluginEntity?>(null) }

    val filtered = remember(plugins, searchQuery) {
        if (searchQuery.isBlank()) plugins
        else plugins.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.sourcesTitle, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("sources_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.testTag("import_plugin_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = strings.importPluginTitle)
                    }
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
                .padding(horizontal = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchPlaceholder) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = strings.searchPlaceholder) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sources_search_input")
                )
            }

            Text(
                text = strings.browseSources,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 4.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.id }) { plugin ->
                    PluginSourceCard(
                        plugin = plugin,
                        connectLabel = strings.connectButton,
                        onOpenConfigure = { configuringPlugin = plugin },
                        onToggleEnabled = { onToggleEnabled(plugin, it) },
                        onDelete = if (!plugin.isBuiltIn) ({ onDeletePlugin(plugin.id) }) else null
                    )
                }
            }
        }
    }

    configuringPlugin?.let { plugin ->
        when (plugin.iconKey) {
            "github" -> GitHubConnectorDialog(
                plugin = plugin,
                initialPat = prefs.getGitHubPat(),
                onDismiss = { configuringPlugin = null },
                onVerifyAndSave = onVerifyGitHub,
                onDisconnect = {
                    onDisconnectPlugin(plugin)
                    configuringPlugin = null
                }
            )
            "gmail", "drive", "calendar" -> GoogleOAuthConnectorDialog(
                plugin = plugin,
                initialToken = prefs.getGoogleOAuthToken(),
                onDismiss = { configuringPlugin = null },
                onSaveToken = { token, label ->
                    onSaveGoogleOAuth(plugin, token, label)
                    configuringPlugin = null
                },
                onDisconnect = {
                    onDisconnectPlugin(plugin)
                    configuringPlugin = null
                }
            )
            else -> GenericApiConnectorDialog(
                plugin = plugin,
                initialEndpoint = prefs.getConnectorEndpoint(plugin.iconKey),
                initialToken = prefs.getConnectorToken(plugin.iconKey),
                onDismiss = { configuringPlugin = null },
                onVerifyAndSave = onVerifyGeneric,
                onSaveDirectly = { ep, tok ->
                    onSaveGeneric(plugin, ep, tok)
                    configuringPlugin = null
                },
                onDisconnect = {
                    onDisconnectPlugin(plugin)
                    configuringPlugin = null
                }
            )
        }
    }

    if (showImportDialog) {
        ImportPluginDialog(
            title = strings.importPluginTitle,
            onDismiss = { showImportDialog = false },
            onImportSkill = { name, desc, md ->
                onImportSkill(name, desc, md)
                showImportDialog = false
            },
            onImportWebhook = { json ->
                val res = onImportWebhook(json)
                if (res.isSuccess) showImportDialog = false
                res
            }
        )
    }
}

@Composable
private fun PluginSourceCard(
    plugin: PluginEntity,
    connectLabel: String,
    onOpenConfigure: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenConfigure)
            .testTag("plugin_card_${plugin.iconKey}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceBrandBadge(iconKey = plugin.iconKey)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            if (!plugin.isConnected) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenConfigure)
                        .testTag("connect_plugin_${plugin.iconKey}")
                ) {
                    Text(
                        text = connectLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Switch(
                    checked = plugin.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("switch_plugin_${plugin.iconKey}")
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete plugin")
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubConnectorDialog(
    plugin: PluginEntity,
    initialPat: String,
    onDismiss: () -> Unit,
    onVerifyAndSave: suspend (PluginEntity, String) -> Result<GitHubVerificationResult>,
    onDisconnect: () -> Unit
) {
    var pat by remember { mutableStateOf(initialPat) }
    var loading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub PAT Подключение") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Введите GitHub Personal Access Token (PAT). Токен проверяется напрямую через GET api.github.com/user и открывает доступ к вашим репозиториям и issues.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pat,
                    onValueChange = { pat = it; statusMsg = null },
                    label = { Text("GitHub Personal Access Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("github_pat_input")
                )
                statusMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !loading && pat.isNotBlank(),
                onClick = {
                    loading = true
                    scope.launch {
                        val res = onVerifyAndSave(plugin, pat)
                        loading = false
                        res.onSuccess { info ->
                            statusMsg = "Подключено: @${info.username} (${info.publicRepos} репо)"
                            onDismiss()
                        }.onFailure { err ->
                            statusMsg = "Ошибка: ${err.message}"
                        }
                    }
                }
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Проверить и подключить")
            }
        },
        dismissButton = {
            Row {
                if (plugin.isConnected) {
                    TextButton(onClick = onDisconnect) { Text("Отключить") }
                }
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    )
}

@Composable
private fun GoogleOAuthConnectorDialog(
    plugin: PluginEntity,
    initialToken: String,
    onDismiss: () -> Unit,
    onSaveToken: (token: String, label: String) -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var manualToken by remember { mutableStateOf(initialToken) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            runCatching {
                val authResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data)
                val accessToken = authResult.accessToken.orEmpty()
                if (accessToken.isNotBlank()) {
                    onSaveToken(accessToken, "Google OAuth 2.0")
                } else {
                    statusMsg = "OAuth токен не получен"
                }
            }.onFailure { statusMsg = "Ошибка OAuth: ${it.message}" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${plugin.name} • OAuth 2.0") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Подключите Google OAuth 2.0 со scopes (gmail.readonly, drive.readonly, calendar.readonly) для реального чтения данных в контекст JARVIS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        if (activity == null) return@Button
                        val req = AuthorizationRequest.builder()
                            .setRequestedScopes(
                                listOf(
                                    Scope("https://www.googleapis.com/auth/gmail.readonly"),
                                    Scope("https://www.googleapis.com/auth/drive.readonly"),
                                    Scope("https://www.googleapis.com/auth/calendar.readonly")
                                )
                            )
                            .build()
                        Identity.getAuthorizationClient(activity)
                            .authorize(req)
                            .addOnSuccessListener { authResult ->
                                if (authResult.hasResolution()) {
                                    authResult.pendingIntent?.let { pi ->
                                        authLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                                    }
                                } else {
                                    val token = authResult.accessToken.orEmpty()
                                    if (token.isNotBlank()) onSaveToken(token, "Google OAuth 2.0")
                                    else statusMsg = "Google OAuth вернул пустой токен"
                                }
                            }
                            .addOnFailureListener { e ->
                                statusMsg = "Google Play OAuth недоступен (${e.localizedMessage}). Вставьте OAuth Bearer токен вручную ниже."
                            }
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Войти через Google OAuth 2.0")
                }

                OutlinedTextField(
                    value = manualToken,
                    onValueChange = { manualToken = it },
                    label = { Text("OAuth 2.0 Access Token (или ручной ввод)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                statusMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = manualToken.isNotBlank(),
                onClick = { onSaveToken(manualToken, "OAuth Bearer") }
            ) { Text("Сохранить токен") }
        },
        dismissButton = {
            Row {
                if (plugin.isConnected) {
                    TextButton(onClick = onDisconnect) { Text("Отключить") }
                }
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    )
}

@Composable
private fun GenericApiConnectorDialog(
    plugin: PluginEntity,
    initialEndpoint: String,
    initialToken: String,
    onDismiss: () -> Unit,
    onVerifyAndSave: suspend (PluginEntity, String, String) -> Result<String>,
    onSaveDirectly: (String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    var endpoint by remember { mutableStateOf(initialEndpoint) }
    var token by remember { mutableStateOf(initialToken) }
    var loading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройка: ${plugin.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it; statusMsg = null },
                    label = { Text("REST API Endpoint URL") },
                    placeholder = { Text("https://api.example.com/v1/...") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; statusMsg = null },
                    label = { Text("Bearer Token / API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                statusMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !loading && endpoint.isNotBlank(),
                onClick = {
                    loading = true
                    scope.launch {
                        val res = onVerifyAndSave(plugin, endpoint, token)
                        loading = false
                        res.onSuccess {
                            onDismiss()
                        }.onFailure { err ->
                            statusMsg = "Проверка не прошла: ${err.message}"
                        }
                    }
                }
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Проверить и подключить")
            }
        },
        dismissButton = {
            Row {
                if (endpoint.isNotBlank()) {
                    TextButton(onClick = { onSaveDirectly(endpoint, token) }) { Text("Сохранить") }
                }
                if (plugin.isConnected) {
                    TextButton(onClick = onDisconnect) { Text("Отключить") }
                }
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    )
}

@Composable
private fun SourceBrandBadge(iconKey: String) {
    val (bg, fg, glyph) = when (iconKey) {
        "atlassian" -> Triple(Color(0xFF0052CC), Color.White, "A")
        "canva" -> Triple(Color(0xFF00C4CC), Color.White, "C")
        "github" -> Triple(Color(0xFF24292E), Color.White, "GH")
        "gmail" -> Triple(Color(0xFFEA4335), Color.White, "M")
        "calendar" -> Triple(Color(0xFF1A73E8), Color.White, "31")
        "contacts" -> Triple(Color(0xFF1967D2), Color.White, "GC")
        "drive" -> Triple(Color(0xFF0F9D58), Color.White, "GD")
        "linear" -> Triple(Color(0xFF5E6AD2), Color.White, "L")
        "linkedin" -> Triple(Color(0xFF0A66C2), Color.White, "in")
        "notion" -> Triple(Color(0xFF191919), Color.White, "N")
        "paypal" -> Triple(Color(0xFF003087), Color.White, "P")
        else -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "")
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (glyph.isNotEmpty()) {
            Text(text = glyph, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        } else {
            Icon(
                imageVector = if (iconKey == "webhook") Icons.Outlined.Webhook else Icons.Outlined.Extension,
                contentDescription = iconKey,
                tint = fg,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ImportPluginDialog(
    title: String,
    onDismiss: () -> Unit,
    onImportSkill: (name: String, desc: String, markdown: String) -> Unit,
    onImportWebhook: (json: String) -> Result<Unit>
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("SKILL") }
    var skillName by remember { mutableStateOf("") }
    var skillDesc by remember { mutableStateOf("") }
    var skillContent by remember { mutableStateOf("") }
    var webhookJson by remember {
        mutableStateOf(
            """{"name":"Weather Webhook","description":"Fetch local forecast JSON","url":"https://api.open-meteo.com/v1/forecast?latitude=52.23&longitude=21.01&current_weather=true","method":"GET"}"""
        )
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } ?: ""
            }.onSuccess { text ->
                if (text.trimStart().startsWith("{")) {
                    mode = "WEBHOOK"
                    webhookJson = text
                } else {
                    mode = "SKILL"
                    skillContent = text
                    if (skillName.isBlank()) skillName = "Imported SKILL.md"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "SKILL", onClick = { mode = "SKILL" }, label = { Text("SKILL.md") })
                    FilterChip(selected = mode == "WEBHOOK", onClick = { mode = "WEBHOOK" }, label = { Text("JSON Webhook") })
                }
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("text/*", "application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выбрать файл (.md / .json)")
                }
                if (mode == "SKILL") {
                    OutlinedTextField(value = skillName, onValueChange = { skillName = it }, label = { Text("Название навыка") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = skillDesc, onValueChange = { skillDesc = it }, label = { Text("Краткое описание") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = skillContent, onValueChange = { skillContent = it }, label = { Text("Инструкции SKILL.md") }, minLines = 4, maxLines = 7, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(value = webhookJson, onValueChange = { webhookJson = it }, label = { Text("JSON-манифест HTTPS-плагина") }, minLines = 5, maxLines = 8, modifier = Modifier.fillMaxWidth())
                }
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (mode == "SKILL") {
                    if (skillContent.isBlank()) errorMsg = "Введите содержимое SKILL.md"
                    else onImportSkill(skillName, skillDesc, skillContent)
                } else {
                    val res = onImportWebhook(webhookJson)
                    if (res.isFailure) errorMsg = res.exceptionOrNull()?.message
                }
            }) { Text("Импортировать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
