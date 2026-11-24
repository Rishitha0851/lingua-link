package com.lingualink.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Live Audio Waveform Canvas rendering real normalized PCM amplitude samples.
 */
@Composable
fun LiveWaveformView(
    waveformSamples: FloatArray,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // Center zero line
        drawLine(
            color = Color.Gray.copy(alpha = 0.25f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        if (!isRecording || waveformSamples.isEmpty()) {
            return@Canvas
        }

        val pointCount = waveformSamples.size
        val sliceWidth = width / pointCount.toFloat()

        for (i in 0 until pointCount) {
            val amp = abs(waveformSamples[i])
            val barHeight = (amp * (height * 0.9f)).coerceIn(2.dp.toPx(), height * 0.95f)
            val x = i * sliceWidth + (sliceWidth / 2f)

            drawLine(
                color = waveColor,
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = (sliceWidth * 0.7f).coerceIn(1.dp.toPx(), 4.dp.toPx()),
                cap = StrokeCap.Round
            )
        }
    }
}
