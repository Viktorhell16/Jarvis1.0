package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class EmojiStyle(val id: String, val displayName: String) {
    SYSTEM("system", "Системные (Android)"),
    IOS_FLUENT("ios_fluent", "В стиле iOS / Fluent"),
    MINIMAL_KAOMOJI("kaomoji", "Минималистичные символы (Kaomoji / ASCII)"),
    NONE("none", "Без эмодзи");

    companion object {
        fun fromId(id: String?): EmojiStyle = entries.find { it.id == id } ?: SYSTEM
    }
}

enum class PalettePreset(
    val id: String,
    val titleRu: String,
    val isGradient: Boolean,
    val previewStart: Color,
    val previewEnd: Color,
    val backgroundHex: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val tertiary: Color,
    val overlayBubbleColorInt: Int,
    val overlayStrokeColorInt: Int
) {
    AMOLED_OBSIDIAN(
        id = "amoled_obsidian",
        titleRu = "AMOLED Обсидиан",
        isGradient = false,
        previewStart = Color(0xFF000000),
        previewEnd = Color(0xFFA1A1AA),
        backgroundHex = Color(0xFF000000),
        primary = Color(0xFFE4E4E7),
        onPrimary = Color(0xFF09090B),
        primaryContainer = Color(0xFF27272A),
        onPrimaryContainer = Color(0xFFF4F4F5),
        secondary = Color(0xFFA1A1AA),
        tertiary = Color(0xFF71717A),
        overlayBubbleColorInt = 0xE60B0C10.toInt(),
        overlayStrokeColorInt = 0xFFE4E4E7.toInt()
    ),
    DEEP_INDIGO(
        id = "deep_indigo",
        titleRu = "Глубокий индиго",
        isGradient = false,
        previewStart = Color(0xFF1E1B4B),
        previewEnd = Color(0xFF818CF8),
        backgroundHex = Color(0xFF070913),
        primary = Color(0xFF818CF8),
        onPrimary = Color(0xFF0F102E),
        primaryContainer = Color(0xFF1E224A),
        onPrimaryContainer = Color(0xFFE0E7FF),
        secondary = Color(0xFFA5B4FC),
        tertiary = Color(0xFF6366F1),
        overlayBubbleColorInt = 0xE6101329.toInt(),
        overlayStrokeColorInt = 0xFF818CF8.toInt()
    ),
    DARK_VIOLET(
        id = "dark_violet",
        titleRu = "Тёмный фиолет",
        isGradient = false,
        previewStart = Color(0xFF3B0764),
        previewEnd = Color(0xFFC084FC),
        backgroundHex = Color(0xFF0B0812),
        primary = Color(0xFFC084FC),
        onPrimary = Color(0xFF24093E),
        primaryContainer = Color(0xFF31164E),
        onPrimaryContainer = Color(0xFFF3E8FF),
        secondary = Color(0xFFD8B4FE),
        tertiary = Color(0xFFA855F7),
        overlayBubbleColorInt = 0xE6190E29.toInt(),
        overlayStrokeColorInt = 0xFFC084FC.toInt()
    ),
    NEON_SAPPHIRE(
        id = "neon_sapphire",
        titleRu = "Неоновый сапфир",
        isGradient = false,
        previewStart = Color(0xFF0C4A6E),
        previewEnd = Color(0xFF38BDF8),
        backgroundHex = Color(0xFF060B14),
        primary = Color(0xFF38BDF8),
        onPrimary = Color(0xFF002438),
        primaryContainer = Color(0xFF0D2E4D),
        onPrimaryContainer = Color(0xFFE0F2FE),
        secondary = Color(0xFF60A5FA),
        tertiary = Color(0xFF2563EB),
        overlayBubbleColorInt = 0xE60A192F.toInt(),
        overlayStrokeColorInt = 0xFF38BDF8.toInt()
    ),
    DUSTY_ROSE(
        id = "dusty_rose",
        titleRu = "Пыльная роза",
        isGradient = false,
        previewStart = Color(0xFF831843),
        previewEnd = Color(0xFFF472B6),
        backgroundHex = Color(0xFF100A0F),
        primary = Color(0xFFF472B6),
        onPrimary = Color(0xFF3D0722),
        primaryContainer = Color(0xFF42172F),
        onPrimaryContainer = Color(0xFFFCE7F3),
        secondary = Color(0xFFF9A8D4),
        tertiary = Color(0xFFDB2777),
        overlayBubbleColorInt = 0xE6220F1B.toInt(),
        overlayStrokeColorInt = 0xFFF472B6.toInt()
    ),
    AMBER_SUNSET(
        id = "amber_sunset",
        titleRu = "Янтарный закат",
        isGradient = false,
        previewStart = Color(0xFF7C2D12),
        previewEnd = Color(0xFFFB923C),
        backgroundHex = Color(0xFF100B07),
        primary = Color(0xFFFB923C),
        onPrimary = Color(0xFF331404),
        primaryContainer = Color(0xFF431E0C),
        onPrimaryContainer = Color(0xFFFFEDD5),
        secondary = Color(0xFFFDBA74),
        tertiary = Color(0xFFEA580C),
        overlayBubbleColorInt = 0xE621130A.toInt(),
        overlayStrokeColorInt = 0xFFFB923C.toInt()
    ),
    WARM_GRAPHITE(
        id = "warm_graphite",
        titleRu = "Тёплый графит",
        isGradient = false,
        previewStart = Color(0xFF292524),
        previewEnd = Color(0xFFD6D3D1),
        backgroundHex = Color(0xFF111110),
        primary = Color(0xFFD6D3D1),
        onPrimary = Color(0xFF1C1917),
        primaryContainer = Color(0xFF2C2927),
        onPrimaryContainer = Color(0xFFF5F5F4),
        secondary = Color(0xFFA8A29E),
        tertiary = Color(0xFF78716C),
        overlayBubbleColorInt = 0xE61C1B19.toInt(),
        overlayStrokeColorInt = 0xFFD6D3D1.toInt()
    ),
    EMERALD_NIGHT(
        id = "emerald_night",
        titleRu = "Изумрудная ночь",
        isGradient = false,
        previewStart = Color(0xFF064E3B),
        previewEnd = Color(0xFF34D399),
        backgroundHex = Color(0xFF06100C),
        primary = Color(0xFF34D399),
        onPrimary = Color(0xFF022C22),
        primaryContainer = Color(0xFF0B3B2E),
        onPrimaryContainer = Color(0xFFD1FAE5),
        secondary = Color(0xFF6EE7B7),
        tertiary = Color(0xFF10B981),
        overlayBubbleColorInt = 0xE6091F17.toInt(),
        overlayStrokeColorInt = 0xFF34D399.toInt()
    ),
    BURGUNDY_VELVET(
        id = "burgundy_velvet",
        titleRu = "Бордовый бархат",
        isGradient = false,
        previewStart = Color(0xFF881337),
        previewEnd = Color(0xFFFB7185),
        backgroundHex = Color(0xFF11070A),
        primary = Color(0xFFFB7185),
        onPrimary = Color(0xFF3E0513),
        primaryContainer = Color(0xFF47111F),
        onPrimaryContainer = Color(0xFFFFE4E6),
        secondary = Color(0xFFFDA4AF),
        tertiary = Color(0xFFE11D48),
        overlayBubbleColorInt = 0xE6220B12.toInt(),
        overlayStrokeColorInt = 0xFFFB7185.toInt()
    ),
    CYBER_TEAL(
        id = "cyber_teal",
        titleRu = "Кибер-бирюза",
        isGradient = false,
        previewStart = Color(0xFF134E4A),
        previewEnd = Color(0xFF2DD4BF),
        backgroundHex = Color(0xFF050F11),
        primary = Color(0xFF2DD4BF),
        onPrimary = Color(0xFF022624),
        primaryContainer = Color(0xFF0B3635),
        onPrimaryContainer = Color(0xFFCCFBF1),
        secondary = Color(0xFF5EEAD4),
        tertiary = Color(0xFF14B8A6),
        overlayBubbleColorInt = 0xE6081D20.toInt(),
        overlayStrokeColorInt = 0xFF2DD4BF.toInt()
    ),
    GRADIENT_CYBERPUNK(
        id = "grad_cyberpunk",
        titleRu = "Градиент: Киберпанк",
        isGradient = true,
        previewStart = Color(0xFFA855F7),
        previewEnd = Color(0xFF38BDF8),
        backgroundHex = Color(0xFF090714),
        primary = Color(0xFFC084FC),
        onPrimary = Color(0xFF1D0736),
        primaryContainer = Color(0xFF26174A),
        onPrimaryContainer = Color(0xFFF3E8FF),
        secondary = Color(0xFF38BDF8),
        tertiary = Color(0xFF06B6D4),
        overlayBubbleColorInt = 0xE6141029.toInt(),
        overlayStrokeColorInt = 0xFF38BDF8.toInt()
    ),
    GRADIENT_SUNSET(
        id = "grad_sunset",
        titleRu = "Градиент: Закат",
        isGradient = true,
        previewStart = Color(0xFFF97316),
        previewEnd = Color(0xFFEC4899),
        backgroundHex = Color(0xFF12080C),
        primary = Color(0xFFFB923C),
        onPrimary = Color(0xFF331105),
        primaryContainer = Color(0xFF421622),
        onPrimaryContainer = Color(0xFFFFEDD5),
        secondary = Color(0xFFF472B6),
        tertiary = Color(0xFFEC4899),
        overlayBubbleColorInt = 0xE6221017.toInt(),
        overlayStrokeColorInt = 0xFFF472B6.toInt()
    ),
    GRADIENT_AURORA(
        id = "grad_aurora",
        titleRu = "Градиент: Северное сияние",
        isGradient = true,
        previewStart = Color(0xFF10B981),
        previewEnd = Color(0xFF3B82F6),
        backgroundHex = Color(0xFF060F12),
        primary = Color(0xFF34D399),
        onPrimary = Color(0xFF03291E),
        primaryContainer = Color(0xFF0B323D),
        onPrimaryContainer = Color(0xFFD1FAE5),
        secondary = Color(0xFF60A5FA),
        tertiary = Color(0xFF3B82F6),
        overlayBubbleColorInt = 0xE6091B22.toInt(),
        overlayStrokeColorInt = 0xFF34D399.toInt()
    ),
    GRADIENT_COSMOS(
        id = "grad_cosmos",
        titleRu = "Градиент: Космос",
        isGradient = true,
        previewStart = Color(0xFF475569),
        previewEnd = Color(0xFF8B5CF6),
        backgroundHex = Color(0xFF090A12),
        primary = Color(0xFFA78BFA),
        onPrimary = Color(0xFF1B103D),
        primaryContainer = Color(0xFF1E2238),
        onPrimaryContainer = Color(0xFFEDE9FE),
        secondary = Color(0xFF94A3B8),
        tertiary = Color(0xFF8B5CF6),
        overlayBubbleColorInt = 0xE6111322.toInt(),
        overlayStrokeColorInt = 0xFFA78BFA.toInt()
    );

    companion object {
        fun fromId(id: String?): PalettePreset = entries.find { it.id == id } ?: NEON_SAPPHIRE
    }
}

@Immutable
data class JarvisPaletteExtras(
    val preset: PalettePreset = PalettePreset.NEON_SAPPHIRE,
    val accentGradient: Brush = Brush.linearGradient(listOf(PalettePreset.NEON_SAPPHIRE.previewStart, PalettePreset.NEON_SAPPHIRE.previewEnd)),
    val surfaceGradient: Brush = Brush.verticalGradient(listOf(DarkContainerHigh, DarkBackgroundObsidian))
)

val LocalJarvisPalette = staticCompositionLocalOf { JarvisPaletteExtras() }

val JarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private fun buildStrictDarkScheme(preset: PalettePreset): ColorScheme {
    val bg = preset.backgroundHex
    val containerLowest = if (bg == Color.Black) Color(0xFF040507) else bg
    val containerLow = Color(0xFF0D0F14)
    val container = Color(0xFF12141B)
    val containerHigh = Color(0xFF181A20)
    val containerHighest = Color(0xFF21242D)

    return darkColorScheme(
        primary = preset.primary,
        onPrimary = preset.onPrimary,
        primaryContainer = preset.primaryContainer,
        onPrimaryContainer = preset.onPrimaryContainer,
        secondary = preset.secondary,
        tertiary = preset.tertiary,
        background = bg,
        onBackground = DarkOnSurface,
        surface = bg,
        onSurface = DarkOnSurface,
        surfaceVariant = containerHigh,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceContainerLowest = containerLowest,
        surfaceContainerLow = containerLow,
        surfaceContainer = container,
        surfaceContainerHigh = containerHigh,
        surfaceContainerHighest = containerHighest,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        error = StatusCriticalRed,
        onError = Color.White
    )
}

@Composable
fun JarvisTheme(
    palettePreset: PalettePreset = PalettePreset.NEON_SAPPHIRE,
    content: @Composable () -> Unit
) {
    val colorScheme = buildStrictDarkScheme(palettePreset)
    val extras = JarvisPaletteExtras(
        preset = palettePreset,
        accentGradient = Brush.linearGradient(listOf(palettePreset.previewStart, palettePreset.previewEnd)),
        surfaceGradient = Brush.verticalGradient(
            colors = if (palettePreset.isGradient) {
                listOf(
                    palettePreset.primaryContainer.copy(alpha = 0.28f),
                    palettePreset.backgroundHex
                )
            } else {
                listOf(DarkContainerLow, palettePreset.backgroundHex)
            }
        )
    )

    CompositionLocalProvider(LocalJarvisPalette provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = JarvisShapes,
            content = content
        )
    }
}
