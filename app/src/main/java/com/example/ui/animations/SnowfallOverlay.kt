package com.example.ui.animations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sin
import kotlin.random.Random

private class Snowflake(
    var x: Float,
    var y: Float,
    val radius: Float,
    val speedY: Float,
    val windAmp: Float,
    val windFreq: Float,
    val phase: Float,
    val color: Color
)

/**
 * Lightweight multi-depth holiday snowfall particle overlay (`alpha = 0.15f..0.40f`)
 * with gentle sine-wave wind drift, running frame-synced while lifecycle is STARTED
 * without allocating Color objects per frame.
 */
@Composable
fun SnowfallOverlay(
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isStarted = true
                Lifecycle.Event.ON_STOP -> isStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx().coerceAtLeast(320f) }
        val heightPx = with(density) { maxHeight.toPx().coerceAtLeast(480f) }

        val flakes = remember(widthPx, heightPx) {
            val rnd = Random(2026)
            List(46) { i ->
                val depth = 0.4f + (i % 5) * 0.15f
                val radiusPx = with(density) { (1.4f + depth * 2.1f).dp.toPx() }
                val alpha = (0.15f + (depth - 0.4f) * 0.40f).coerceIn(0.15f, 0.40f)
                Snowflake(
                    x = rnd.nextFloat() * widthPx,
                    y = rnd.nextFloat() * heightPx,
                    radius = radiusPx,
                    speedY = (24f + depth * 44f),
                    windAmp = (8f + depth * 12f),
                    windFreq = 0.7f + rnd.nextFloat() * 0.8f,
                    phase = rnd.nextFloat() * 6.28f,
                    color = Color.White.copy(alpha = alpha)
                )
            }
        }

        var frameNanosState by remember { mutableLongStateOf(0L) }

        LaunchedEffect(enabled, isStarted, widthPx, heightPx) {
            if (!isStarted) return@LaunchedEffect
            var lastNanos = 0L
            var totalNs = 0L
            while (true) {
                withFrameNanos { nanos ->
                    if (lastNanos != 0L) {
                        val dtNs = (nanos - lastNanos).coerceIn(0L, 50_000_000L)
                        val dt = dtNs / 1_000_000_000f
                        totalNs += dtNs
                        val t = totalNs / 1_000_000_000f
                        flakes.forEach { f ->
                            f.y += f.speedY * dt
                            f.x += sin(t * f.windFreq + f.phase) * f.windAmp * dt
                            if (f.y - f.radius > heightPx) {
                                f.y = -f.radius
                            }
                            if (f.x < -f.radius) f.x = widthPx + f.radius
                            else if (f.x > widthPx + f.radius) f.x = -f.radius
                        }
                        frameNanosState = nanos
                    }
                    lastNanos = nanos
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val tick = frameNanosState
            flakes.forEach { flake ->
                drawCircle(
                    color = flake.color,
                    radius = flake.radius,
                    center = Offset(flake.x, flake.y)
                )
            }
        }
    }
}
