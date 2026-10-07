package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.CarlosState
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.OrbListeningGlow
import com.example.ui.theme.OrbProcessingGlow
import com.example.ui.theme.OrbSpeakingGlow

@Composable
fun CarlosVoiceOrb(
    state: CarlosState,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    // Breathing pulse
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    // Rotation shimmer
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val normalizedRms = (rmsLevel / 10f).coerceIn(0f, 1f)
    val rmsScale = remember { Animatable(1f) }

    LaunchedEffect(normalizedRms, state) {
        if (state == CarlosState.LISTENING_COMMAND || state == CarlosState.LISTENING_WAKE_WORD) {
            rmsScale.animateTo(
                targetValue = 1f + (normalizedRms * 0.35f),
                animationSpec = tween(80, easing = LinearEasing)
            )
        } else {
            rmsScale.animateTo(1f, animationSpec = tween(300))
        }
    }

    val primaryColor = when (state) {
        CarlosState.IDLE -> NeonCyan
        CarlosState.LISTENING_WAKE_WORD, CarlosState.LISTENING_COMMAND -> OrbListeningGlow
        CarlosState.PROCESSING -> OrbProcessingGlow
        CarlosState.SPEAKING -> OrbSpeakingGlow
        CarlosState.ERROR -> MaterialTheme.colorScheme.error
    }

    val secondaryColor = when (state) {
        CarlosState.PROCESSING -> ElectricBlue
        CarlosState.SPEAKING -> NeonCyan
        else -> NeonPurple
    }

    val finalScale = if (state == CarlosState.IDLE) idlePulse else rmsScale.value

    Box(
        modifier = modifier
            .size(190.dp)
            .scale(finalScale)
            .testTag("carlos_voice_orb")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Concentric acoustic rings canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2.6f

            // Outer acoustic aura ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                    center = center,
                    radius = baseRadius * 1.55f
                ),
                radius = baseRadius * 1.55f,
                center = center
            )

            // Dynamic acoustic ripple 1
            if (state == CarlosState.LISTENING_COMMAND || state == CarlosState.SPEAKING) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.5f),
                    radius = baseRadius * (1.2f + (normalizedRms * 0.25f)),
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
                // Dynamic acoustic ripple 2
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.35f),
                    radius = baseRadius * (1.38f + (normalizedRms * 0.35f)),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Core holographic sphere gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.9f),
                        secondaryColor.copy(alpha = 0.6f),
                        Color(0xFF0B1120)
                    ),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )

            // Inner glowing border
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(primaryColor, secondaryColor, primaryColor),
                    center = center
                ),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 3.5.dp.toPx())
            )
        }

        // Center state icon
        val icon = when (state) {
            CarlosState.IDLE -> Icons.Default.Mic
            CarlosState.LISTENING_WAKE_WORD, CarlosState.LISTENING_COMMAND -> Icons.Default.GraphicEq
            CarlosState.PROCESSING -> Icons.Default.GraphicEq
            CarlosState.SPEAKING -> Icons.Default.VolumeUp
            CarlosState.ERROR -> Icons.Default.Warning
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F172A).copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Carlos Voice State: $state",
                tint = primaryColor,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
