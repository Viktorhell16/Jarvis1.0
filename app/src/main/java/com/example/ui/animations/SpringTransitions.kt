package com.example.ui.animations

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Adds tactile spring scale-bounce feedback on press to interactive buttons, chips, and pills.
 */
fun Modifier.springBounce(
    pressedScale: Float = 0.94f,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "spring_bounce_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Applies real Android 12+ RenderEffect blur for frosted Liquid Glass backdrop layers.
 */
fun Modifier.liquidGlassBlurLayer(radiusPx: Float = 26f): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.graphicsLayer {
            renderEffect = RenderEffect.createBlurEffect(
                radiusPx,
                radiusPx,
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    } else {
        this
    }
}
