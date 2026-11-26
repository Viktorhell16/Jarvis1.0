package com.example.ui.animations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.LocalJarvisPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private enum class ShapeKind {
    ROUNDED_SQUARE, STADIUM_PILL, CLOVER, SOFT_POLYGON
}

private class BouncingShapeBody(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    var angle: Float,
    val omega: Float,
    val kind: ShapeKind,
    val alpha: Float,
    val useSecondary: Boolean
)

/**
 * 60/120fps frame-synced DVD-style bouncing MD3 geometric shapes background
 * with subtle opacity (0.08f..0.13f), acrylic frosted glass blur (48.dp),
 * lifecycle pause/resume on ON_STOP/ON_START, and pre-allocated drawing objects.
 */
@Composable
fun BouncingShapesBackground(
    enabled: Boolean = true,
    showNothingDotMatrix: Boolean = false,
    modifier: Modifier = Modifier
) {
    val paletteExtras = LocalJarvisPalette.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val dotTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
    val ringTint = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)
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

    val startBase = remember(paletteExtras, primary) {
        paletteExtras.preset.previewStart.takeIf { it != Color.Transparent } ?: primary
    }
    val endBase = remember(paletteExtras, secondary) {
        paletteExtras.preset.previewEnd.takeIf { it != Color.Transparent } ?: secondary
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx().coerceAtLeast(320f) }
        val heightPx = with(density) { maxHeight.toPx().coerceAtLeast(480f) }

        if (enabled) {
            val bodies = remember(widthPx, heightPx) {
                listOf(
                    BouncingShapeBody(widthPx * 0.24f, heightPx * 0.20f, 52f, 44f, 88f, 12f, 8f, ShapeKind.CLOVER, 0.12f, false),
                    BouncingShapeBody(widthPx * 0.76f, heightPx * 0.28f, -46f, 54f, 76f, 40f, -10f, ShapeKind.STADIUM_PILL, 0.11f, true),
                    BouncingShapeBody(widthPx * 0.32f, heightPx * 0.68f, 60f, -40f, 94f, 20f, 6f, ShapeKind.ROUNDED_SQUARE, 0.10f, false),
                    BouncingShapeBody(widthPx * 0.72f, heightPx * 0.76f, -54f, -48f, 72f, 75f, 12f, ShapeKind.SOFT_POLYGON, 0.13f, true),
                    BouncingShapeBody(widthPx * 0.50f, heightPx * 0.48f, 38f, -58f, 82f, 0f, -7f, ShapeKind.CLOVER, 0.085f, false),
                    BouncingShapeBody(widthPx * 0.16f, heightPx * 0.85f, 48f, -34f, 68f, 30f, 9f, ShapeKind.STADIUM_PILL, 0.10f, true)
                )
            }

            // Pre-allocate Path & per-body fill colors outside draw block
            val reusablePath = remember { Path() }
            val bodyColors = remember(bodies, startBase, endBase) {
                bodies.map { b -> (if (b.useSecondary) endBase else startBase).copy(alpha = b.alpha) }
            }

            var frameTick by remember { mutableLongStateOf(0L) }

            LaunchedEffect(enabled, isStarted, widthPx, heightPx) {
                if (!isStarted) return@LaunchedEffect
                var lastNanos = 0L
                while (true) {
                    withFrameNanos { nanos ->
                        if (lastNanos != 0L) {
                            val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                            bodies.forEach { b ->
                                b.x += b.vx * dt
                                b.y += b.vy * dt
                                b.angle = (b.angle + b.omega * dt) % 360f

                                if (b.x - b.radius < 0f) {
                                    b.x = b.radius
                                    b.vx = abs(b.vx)
                                } else if (b.x + b.radius > widthPx) {
                                    b.x = widthPx - b.radius
                                    b.vx = -abs(b.vx)
                                }

                                if (b.y - b.radius < 0f) {
                                    b.y = b.radius
                                    b.vy = abs(b.vy)
                                } else if (b.y + b.radius > heightPx) {
                                    b.y = heightPx - b.radius
                                    b.vy = -abs(b.vy)
                                }
                            }
                            frameTick = nanos
                        }
                        lastNanos = nanos
                    }
                }
            }

            // Acrylic frosted glass blurred canvas for bouncing shapes
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp)
            ) {
                @Suppress("UNUSED_VARIABLE")
                val tick = frameTick

                bodies.forEachIndexed { idx, body ->
                    val fillColor = bodyColors[idx]
                    val center = Offset(body.x, body.y)

                    rotate(degrees = body.angle, pivot = center) {
                        when (body.kind) {
                            ShapeKind.ROUNDED_SQUARE -> {
                                val side = body.radius * 1.75f
                                drawRoundRect(
                                    color = fillColor,
                                    topLeft = Offset(center.x - side / 2f, center.y - side / 2f),
                                    size = Size(side, side),
                                    cornerRadius = CornerRadius(side * 0.32f, side * 0.32f)
                                )
                            }
                            ShapeKind.STADIUM_PILL -> {
                                val w = body.radius * 2.2f
                                val h = body.radius * 1.05f
                                drawRoundRect(
                                    color = fillColor,
                                    topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
                                    size = Size(w, h),
                                    cornerRadius = CornerRadius(h / 2f, h / 2f)
                                )
                            }
                            ShapeKind.CLOVER -> {
                                buildCloverPathInto(reusablePath, center, body.radius, lobes = 6)
                                drawPath(path = reusablePath, color = fillColor)
                            }
                            ShapeKind.SOFT_POLYGON -> {
                                buildCloverPathInto(reusablePath, center, body.radius, lobes = 4)
                                drawPath(path = reusablePath, color = fillColor)
                            }
                        }
                    }
                }
            }
        }

        if (showNothingDotMatrix) {
            val dotStepPx = with(density) { 28.dp.toPx() }
            val dotRadiusPx = with(density) { 1.3.dp.toPx() }
            val ringRadii = remember(density) {
                with(density) { listOf(72.dp.toPx(), 118.dp.toPx(), 164.dp.toPx()) }
            }
            val ringStroke = remember(density) { Stroke(width = with(density) { 1.dp.toPx() }) }

            Canvas(modifier = Modifier.fillMaxSize()) {
                var x = dotStepPx * 0.5f
                while (x < size.width) {
                    var y = dotStepPx * 0.5f
                    while (y < size.height) {
                        drawCircle(color = dotTint, radius = dotRadiusPx, center = Offset(x, y))
                        y += dotStepPx
                    }
                    x += dotStepPx
                }

                val ringCenter = Offset(size.width * 0.82f, size.height * 0.18f)
                ringRadii.forEach { r ->
                    drawCircle(
                        color = ringTint,
                        radius = r,
                        center = ringCenter,
                        style = ringStroke
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

private fun buildCloverPathInto(path: Path, center: Offset, radius: Float, lobes: Int) {
    path.reset()
    val steps = 80
    for (i in 0..steps) {
        val angle = (i.toFloat() / steps) * (2f * PI.toFloat())
        val r = radius * (0.80f + 0.20f * cos(lobes * angle))
        val px = center.x + r * cos(angle)
        val py = center.y + r * sin(angle)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
}
