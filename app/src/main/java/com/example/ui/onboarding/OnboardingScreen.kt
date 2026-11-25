package com.example.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private data class OnboardingSlide(
    val title: String,
    val description: String,
    val badge: String,
    val icon: ImageVector
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val slides = listOf(
        OnboardingSlide(
            title = "Живой голос и глубокий разум",
            description = "Голосовой режим, мгновенный синтез речи (TTS) и 3 уровня глубины рассуждения: Низкий, Средний и Высокий.",
            badge = "Voice & Reasoning",
            icon = Icons.Outlined.GraphicEq
        ),
        OnboardingSlide(
            title = "Управление телефоном за 0 токенов",
            description = "Запуск любых приложений, системные таймеры и будильники, калькулятор и управление музыкой офлайн — без траты API-лимитов.",
            badge = "Zero-Token Router",
            icon = Icons.Outlined.Bolt
        ),
        OnboardingSlide(
            title = "Твой стиль, плагины и JARVIS Notebook",
            description = "Анимированные GIF-профили, редактор заметок в духе Samsung Notes, кастомные JAR-боты и реальные интеграции с GitHub и Google.",
            badge = "Local-First Ecosystem",
            icon = Icons.Outlined.AutoAwesome
        )
    )

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onFinish,
                modifier = Modifier.testTag("onboarding_skip_button")
            ) {
                Text("Пропустить")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Hero Animation: Live Blue Dancing Audio Equalizer
        BlueDancingAudioEqualizer(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )

        Spacer(Modifier.height(16.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val slide = slides[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = slide.icon,
                            contentDescription = null,
                            tint = Color(0xFF67E8F9),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = slide.badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = slide.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = slide.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // MD3 Dot Indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            repeat(slides.size) { idx ->
                val active = pagerState.currentPage == idx
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(if (active) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) Color(0xFF38BDF8)
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage < slides.lastIndex) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onFinish()
                }
            },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("onboarding_next_button")
        ) {
            Text(
                text = if (pagerState.currentPage < slides.lastIndex) "Далее" else "Начать работу",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun BlueDancingAudioEqualizer(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_equalizer")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eq_phase"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_glow"
    )

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // Ambient blue glow halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0EA5E9).copy(alpha = glowPulse * 0.32f),
                    Color(0xFF2563EB).copy(alpha = glowPulse * 0.14f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = size.minDimension * 0.65f
            ),
            radius = size.minDimension * 0.65f,
            center = Offset(centerX, centerY)
        )

        val barCount = 7
        val barWidth = 14.dp.toPx()
        val gap = 11.dp.toPx()
        val totalWidth = barCount * barWidth + (barCount - 1) * gap
        val startX = centerX - totalWidth / 2f
        val maxBarHeight = size.height * 0.76f
        val minBarHeight = 22.dp.toPx()

        val barBrush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF67E8F9),
                Color(0xFF38BDF8),
                Color(0xFF2563EB)
            )
        )

        for (i in 0 until barCount) {
            val wave1 = sin(phase * 1f + i * 0.85f)
            val wave2 = sin(phase * 2f - i * 0.55f)
            val centerWeight = 1f - (kotlin.math.abs(i - (barCount - 1) / 2f) / (barCount / 2f)) * 0.38f
            val normalized = ((wave1 * 0.6f + wave2 * 0.4f) + 1f) / 2f
            val barHeight = minBarHeight + (maxBarHeight - minBarHeight) * normalized * centerWeight

            val left = startX + i * (barWidth + gap)
            val top = centerY - barHeight / 2f

            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
