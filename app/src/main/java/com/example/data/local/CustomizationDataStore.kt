package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.EmojiStyle
import com.example.ui.theme.PalettePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.customizationDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_customization")

data class CustomizationState(
    val palettePreset: PalettePreset = PalettePreset.NEON_SAPPHIRE,
    val liquidGlassEnabled: Boolean = true,
    val snowfallEnabled: Boolean = true,
    val bouncingShapesEnabled: Boolean = true,
    val autoScrollPromptsEnabled: Boolean = true,
    val emojiStyle: EmojiStyle = EmojiStyle.SYSTEM
)

class CustomizationDataStore(private val context: Context) {

    private object Keys {
        val PALETTE_PRESET = stringPreferencesKey("palette_preset")
        val LIQUID_GLASS = booleanPreferencesKey("liquid_glass_enabled")
        val SNOWFALL = booleanPreferencesKey("snowfall_enabled")
        val BOUNCING_SHAPES = booleanPreferencesKey("bouncing_shapes_enabled")
        val AUTO_SCROLL_PROMPTS = booleanPreferencesKey("auto_scroll_prompts_enabled")
        val EMOJI_STYLE = stringPreferencesKey("emoji_style")
    }

    val state: Flow<CustomizationState> = context.customizationDataStore.data.map { prefs ->
        CustomizationState(
            palettePreset = PalettePreset.fromId(prefs[Keys.PALETTE_PRESET]),
            liquidGlassEnabled = prefs[Keys.LIQUID_GLASS] ?: true,
            snowfallEnabled = prefs[Keys.SNOWFALL] ?: true,
            bouncingShapesEnabled = prefs[Keys.BOUNCING_SHAPES] ?: true,
            autoScrollPromptsEnabled = prefs[Keys.AUTO_SCROLL_PROMPTS] ?: true,
            emojiStyle = EmojiStyle.fromId(prefs[Keys.EMOJI_STYLE])
        )
    }

    suspend fun setPalettePreset(preset: PalettePreset) {
        context.customizationDataStore.edit { it[Keys.PALETTE_PRESET] = preset.id }
    }

    suspend fun setLiquidGlassEnabled(enabled: Boolean) {
        context.customizationDataStore.edit { it[Keys.LIQUID_GLASS] = enabled }
    }

    suspend fun setSnowfallEnabled(enabled: Boolean) {
        context.customizationDataStore.edit { it[Keys.SNOWFALL] = enabled }
    }

    suspend fun setBouncingShapesEnabled(enabled: Boolean) {
        context.customizationDataStore.edit { it[Keys.BOUNCING_SHAPES] = enabled }
    }

    suspend fun setAutoScrollPromptsEnabled(enabled: Boolean) {
        context.customizationDataStore.edit { it[Keys.AUTO_SCROLL_PROMPTS] = enabled }
    }

    suspend fun setEmojiStyle(style: EmojiStyle) {
        context.customizationDataStore.edit { it[Keys.EMOJI_STYLE] = style.id }
    }
}
