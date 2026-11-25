package com.example.ui.customization

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomizationState
import com.example.ui.animations.BouncingShapesBackground
import com.example.ui.animations.springBounce
import com.example.ui.i18n.UiStrings
import com.example.ui.theme.EmojiStyle
import com.example.ui.theme.PalettePreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
    customizationState: CustomizationState,
    strings: UiStrings,
    onBack: () -> Unit,
    onSelectPalette: (PalettePreset) -> Unit,
    onToggleLiquidGlass: (Boolean) -> Unit,
    onToggleSnowfall: (Boolean) -> Unit,
    onToggleBouncingShapes: (Boolean) -> Unit,
    onToggleAutoScrollPrompts: (Boolean) -> Unit,
    onSelectEmojiStyle: (EmojiStyle) -> Unit
) {
    BackHandler(onBack = onBack)
    var paletteExpanded by remember { mutableStateOf(false) }

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
                    title = { Text(strings.customizationTitle) },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("customization_back")) {
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
                // Section 1: 14 Dark & Gradient Color Palettes Dropdown
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = strings.paletteDropdownLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )

                        ExposedDropdownMenuBox(
                            expanded = paletteExpanded,
                            onExpandedChange = { paletteExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = customizationState.palettePreset.titleRu,
                                onValueChange = {},
                                readOnly = true,
                                leadingIcon = {
                                    PalettePreviewCircle(
                                        preset = customizationState.palettePreset,
                                        sizeDp = 24
                                    )
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = paletteExpanded)
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .testTag("palette_dropdown_field")
                            )

                            ExposedDropdownMenu(
                                expanded = paletteExpanded,
                                onDismissRequest = { paletteExpanded = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                PalettePreset.entries.forEach { preset ->
                                    val selected = preset == customizationState.palettePreset
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                PalettePreviewCircle(preset = preset, sizeDp = 22)
                                                Text(
                                                    text = preset.titleRu,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (preset.isGradient) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = preset.primaryContainer
                                                    ) {
                                                        Text(
                                                            text = "GRAD",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = preset.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        trailingIcon = {
                                            if (selected) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            onSelectPalette(preset)
                                            paletteExpanded = false
                                        },
                                        modifier = Modifier.testTag("palette_option_${preset.id}")
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: 4 DataStore-Backed Animation & Visual Toggles
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CustomizationToggleRow(
                            title = strings.liquidGlassLabel,
                            subtitle = strings.liquidGlassSub,
                            checked = customizationState.liquidGlassEnabled,
                            testTag = "toggle_liquid_glass",
                            onCheckedChange = onToggleLiquidGlass
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        CustomizationToggleRow(
                            title = strings.snowfallLabel,
                            subtitle = strings.snowfallSub,
                            checked = customizationState.snowfallEnabled,
                            testTag = "toggle_snowfall",
                            onCheckedChange = onToggleSnowfall
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        CustomizationToggleRow(
                            title = strings.bouncingShapesLabel,
                            subtitle = strings.bouncingShapesSub,
                            checked = customizationState.bouncingShapesEnabled,
                            testTag = "toggle_bouncing_shapes",
                            onCheckedChange = onToggleBouncingShapes
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        CustomizationToggleRow(
                            title = strings.autoScrollPromptsLabel,
                            subtitle = strings.autoScrollPromptsSub,
                            checked = customizationState.autoScrollPromptsEnabled,
                            testTag = "toggle_auto_scroll_prompts",
                            onCheckedChange = onToggleAutoScrollPrompts
                        )
                    }
                }

                // Section 3: Emoji Style Selector (4 Options)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.emojiStyleLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        EmojiStyle.entries.forEach { style ->
                            val sample = when (style) {
                                EmojiStyle.SYSTEM -> "✨ ⚡ 🧠"
                                EmojiStyle.IOS_FLUENT -> "🫧 💠 🪐"
                                EmojiStyle.MINIMAL_KAOMOJI -> "(★) [⚡] ✦"
                                EmojiStyle.NONE -> "Только текст"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .springBounce(0.97f)
                                    .clickable { onSelectEmojiStyle(style) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("emoji_style_${style.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = customizationState.emojiStyle == style,
                                    onClick = { onSelectEmojiStyle(style) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = style.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = sample,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun PalettePreviewCircle(
    preset: PalettePreset,
    sizeDp: Int
) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(preset.previewStart, preset.previewEnd)))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
    )
}

@Composable
private fun CustomizationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .springBounce(0.92f)
                .testTag(testTag)
        )
    }
}
