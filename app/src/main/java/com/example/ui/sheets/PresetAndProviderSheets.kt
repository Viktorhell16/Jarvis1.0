package com.example.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.data.local.EncryptedPrefsManager
import com.example.data.local.JarvPrefsState
import com.example.data.network.LiveModelFetcher
import com.example.data.network.ModelOption
import com.example.data.network.ProviderCatalog
import com.example.data.network.ProviderUrlNormalizer
import com.example.ui.i18n.UiStrings
import com.example.ui.theme.StatusHealthyGreen
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@Composable
fun PresetDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    activePresetId: String,
    reasoningEffort: String,
    strings: UiStrings,
    onSelectPreset: (String) -> Unit,
    onSelectReasoning: (String) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 8.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .width(276.dp)
            .testTag("preset_dropdown_menu")
    ) {
        PresetMenuRow(
            title = strings.presetAuto,
            subtitle = strings.presetAutoSub,
            selected = activePresetId == "AUTO",
            onClick = { onSelectPreset("AUTO"); onDismiss() }
        )
        PresetMenuRow(
            title = strings.presetDaily,
            subtitle = strings.presetDailySub,
            selected = activePresetId == "DAILY",
            onClick = { onSelectPreset("DAILY"); onDismiss() }
        )
        PresetMenuRow(
            title = strings.presetDeep,
            subtitle = strings.presetDeepSub,
            selected = activePresetId == "DEEP",
            onClick = { onSelectPreset("DEEP"); onDismiss() }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        PresetMenuRow(
            title = strings.reasoningLow,
            subtitle = strings.reasoningLowSub,
            selected = reasoningEffort == "low",
            onClick = { onSelectReasoning("low"); onDismiss() }
        )
        PresetMenuRow(
            title = strings.reasoningMedium,
            subtitle = strings.reasoningMediumSub,
            selected = reasoningEffort == "medium",
            onClick = { onSelectReasoning("medium"); onDismiss() }
        )
        PresetMenuRow(
            title = strings.reasoningHigh,
            subtitle = strings.reasoningHighSub,
            selected = reasoningEffort == "high",
            onClick = { onSelectReasoning("high"); onDismiss() }
        )
    }
}

@Composable
private fun PresetMenuRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        onClick = onClick,
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Spacer(Modifier.size(18.dp))
            }
        },
        text = {
            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickProviderConfigBottomSheet(
    prefsState: JarvPrefsState,
    prefsManager: EncryptedPrefsManager,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onSave: (providerId: String, baseUrl: String, modelId: String, displayName: String, apiKey: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedProviderId by remember { mutableStateOf(prefsState.activeProviderId) }
    var endpointUrl by remember { mutableStateOf(prefsState.activeBaseUrl) }
    val providerSpec = remember(selectedProviderId) { ProviderCatalog.findProvider(selectedProviderId) }
    var liveModels by remember(selectedProviderId) { mutableStateOf<List<ModelOption>>(emptyList()) }
    val availableModels = remember(providerSpec, liveModels) {
        (liveModels + providerSpec.models).distinctBy { it.apiModelId }
    }

    var selectedModelOption by remember(selectedProviderId) {
        mutableStateOf(
            providerSpec.models.find { it.apiModelId == prefsState.activeModelId }
                ?: providerSpec.models.first()
        )
    }
    var customModelId by remember(selectedProviderId) {
        mutableStateOf(
            if (providerSpec.models.any { it.apiModelId == prefsState.activeModelId }) ""
            else prefsState.activeModelId
        )
    }
    var apiKey by remember(selectedProviderId) {
        mutableStateOf(prefsManager.getApiKey(selectedProviderId))
    }
    var showKey by remember { mutableStateOf(false) }

    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchModelsStatus by remember(selectedProviderId) { mutableStateOf<Pair<Boolean, String>?>(null) }
    var openRouterKeyStatus by remember(selectedProviderId) { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = strings.quickConfigTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Item 1: Выбор поставщика
            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = !providerExpanded }
            ) {
                OutlinedTextField(
                    value = providerSpec.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(strings.providerLabel) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .testTag("provider_selector")
                )
                ExposedDropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false }
                ) {
                    ProviderCatalog.providers.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.displayName) },
                            onClick = {
                                selectedProviderId = provider.id
                                endpointUrl = provider.defaultBaseUrl
                                selectedModelOption = provider.models.first()
                                customModelId = ""
                                apiKey = prefsManager.getApiKey(provider.id)
                                liveModels = emptyList()
                                fetchModelsStatus = null
                                openRouterKeyStatus = null
                                providerExpanded = false
                            }
                        )
                    }
                }
            }

            if (providerSpec.requiresApiKey) {
                // Item 2: URL-адрес
                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    label = { Text(strings.endpointUrlLabel) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("provider_url_input")
                )

                // Item 3: API-ключ
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(strings.apiKeyLabel) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                clipboard.getText()?.text?.let { pasted -> apiKey = pasted.trim() }
                            }) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste API Key")
                            }
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "Toggle key visibility"
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )

                // Live GET /models fetch button (+ OpenRouter /auth/key check)
                OutlinedButton(
                    onClick = {
                        if (isFetchingModels) return@OutlinedButton
                        isFetchingModels = true
                        fetchModelsStatus = null
                        openRouterKeyStatus = null
                        scope.launch {
                            val modelsDeferred = async {
                                LiveModelFetcher.fetchAvailableModels(
                                    providerId = selectedProviderId,
                                    baseUrl = endpointUrl,
                                    apiKey = apiKey
                                )
                            }
                            val keyDeferred = if (selectedProviderId == "openrouter" && apiKey.isNotBlank()) {
                                async { LiveModelFetcher.fetchOpenRouterKeyStatus(apiKey) }
                            } else null

                            val res = modelsDeferred.await()
                            keyDeferred?.await()?.onSuccess { status ->
                                openRouterKeyStatus = status
                            }
                            isFetchingModels = false
                            res.onSuccess { fetched ->
                                liveModels = fetched
                                if (customModelId.isBlank() && fetched.isNotEmpty() && fetched.none { it.apiModelId == selectedModelOption.apiModelId }) {
                                    selectedModelOption = fetched.first()
                                }
                                fetchModelsStatus = true to "Загружено моделей: ${fetched.size}"
                            }.onFailure { err ->
                                fetchModelsStatus = false to (err.localizedMessage ?: "Ошибка загрузки списка моделей")
                            }
                        }
                    },
                    enabled = !isFetchingModels,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fetch_live_models_button")
                ) {
                    if (isFetchingModels) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(strings.fetchModelsButton)
                }

                fetchModelsStatus?.let { (ok, msg) ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (ok) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                            openRouterKeyStatus?.let { keyStatus ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = keyStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.testTag("openrouter_key_status")
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Firebase AI Logic работает через App Check — ручной API-ключ и URL не требуются.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Item 4: Выбор модели (dynamically merged with live models)
            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = !modelExpanded }
            ) {
                OutlinedTextField(
                    value = selectedModelOption.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(strings.modelLabel) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .testTag("model_selector")
                )
                ExposedDropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false }
                ) {
                    availableModels.forEach { model ->
                        val ctxBadge = ProviderCatalog.formatContextWindow(model.contextLength)
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = model.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (model.isFree) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = StatusHealthyGreen.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    text = "FREE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = StatusHealthyGreen,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (ctxBadge.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                                            ) {
                                                Text(
                                                    text = ctxBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = model.apiModelId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedModelOption = model
                                customModelId = ""
                                modelExpanded = false
                            }
                        )
                    }
                }
            }

            // Selected model capability chips (FREE + context window)
            val selectedCtx = ProviderCatalog.formatContextWindow(selectedModelOption.contextLength)
            if (selectedModelOption.isFree || selectedCtx.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedModelOption.isFree) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusHealthyGreen.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "FREE",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusHealthyGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (selectedCtx.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = selectedCtx,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Unspecified,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = customModelId,
                onValueChange = { customModelId = it },
                label = { Text(strings.customModelIdLabel) },
                placeholder = { Text(selectedModelOption.apiModelId) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_model_input")
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val effectiveModelId = customModelId.trim().ifBlank { selectedModelOption.apiModelId }
                    val effectiveDisplay = if (customModelId.isNotBlank()) customModelId.trim() else selectedModelOption.displayName
                    val normalizedUrl = if (selectedProviderId == "firebase_ai") {
                        providerSpec.defaultBaseUrl
                    } else {
                        ProviderUrlNormalizer.normalizeBaseUrl(endpointUrl, providerSpec.defaultBaseUrl)
                    }
                    onSave(
                        selectedProviderId,
                        normalizedUrl,
                        effectiveModelId,
                        effectiveDisplay,
                        if (providerSpec.requiresApiKey) apiKey else ""
                    )
                    onDismiss()
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_provider_button")
            ) {
                Text(strings.saveAndApply, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
