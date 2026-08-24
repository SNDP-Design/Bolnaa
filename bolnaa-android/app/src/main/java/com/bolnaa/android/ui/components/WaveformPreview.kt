package com.bolnaa.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bolnaa.android.ui.theme.FlowAccent
import com.bolnaa.android.ui.theme.FlowPrimary
import kotlin.math.sin

@Composable
fun WaveformPreview(
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val barCount = 28
        val barWidth = size.width / (barCount * 1.8f)
        val spacing = barWidth * 0.8f
        val centerY = size.height / 2f

        val gradient = Brush.verticalGradient(
            colors = listOf(FlowAccent, FlowPrimary),
            startY = 0f,
            endY = size.height
        )

        for (i in 0 until barCount) {
            val progress = i.toFloat() / barCount
            val sine = if (isListening) {
                ((sin(phase + (progress * 4 * Math.PI)) + 1f) / 2f).toFloat()
            } else {
                0.2f + (0.1f * sin(progress * Math.PI).toFloat())
            }

            val barHeight = (size.height * 0.8f * sine).coerceAtLeast(6.dp.toPx())
            val x = i * (barWidth + spacing) + spacing

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
