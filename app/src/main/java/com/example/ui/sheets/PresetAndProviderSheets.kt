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
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.data.local.EncryptedPrefsManager
import com.example.data.local.JarvPrefsState
import com.example.data.network.ProviderCatalog
import com.example.ui.i18n.UiStrings

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

    var selectedProviderId by remember { mutableStateOf(prefsState.activeProviderId) }
    var endpointUrl by remember { mutableStateOf(prefsState.activeBaseUrl) }
    val providerSpec = remember(selectedProviderId) { ProviderCatalog.findProvider(selectedProviderId) }
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

            // Item 1: URL-адрес
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

            // Item 2: Выбор поставщика
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
                                providerExpanded = false
                            }
                        )
                    }
                }
            }

            // Item 3: Выбор модели (dynamically filtered by Provider)
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
                    providerSpec.models.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(model.apiModelId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            // Item 4: API-ключ
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

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val effectiveModelId = customModelId.trim().ifBlank { selectedModelOption.apiModelId }
                    val effectiveDisplay = if (customModelId.isNotBlank()) customModelId.trim() else selectedModelOption.displayName
                    onSave(
                        selectedProviderId,
                        endpointUrl.ifBlank { providerSpec.defaultBaseUrl },
                        effectiveModelId,
                        effectiveDisplay,
                        apiKey
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
