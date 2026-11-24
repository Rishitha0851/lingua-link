package com.lingualink.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingualink.audio.AudioMetrics
import java.util.Locale

@Composable
fun AudioMetricsCard(
    metrics: AudioMetrics,
    isFilterEnabled: Boolean,
    onFilterToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header with Noise Filter Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DSP Noise Suppression",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isFilterEnabled) "80Hz High-Pass + Adaptive Gate Active" else "Bypassed (Raw PCM Passthrough)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFilterEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isFilterEnabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isFilterEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Switch(
                        checked = isFilterEnabled,
                        onCheckedChange = onFilterToggle
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Level Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Level:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(42.dp)
                )

                val levelColor by animateColorAsState(
                    targetValue = when {
                        metrics.normalizedLevel > 0.85f -> Color.Red
                        metrics.normalizedLevel > 0.65f -> Color(0xFFFF9800) // Orange
                        else -> Color(0xFF4CAF50) // Green
                    },
                    label = "levelColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(metrics.normalizedLevel)
                            .background(levelColor)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = String.format(Locale.US, "%.1f dB", metrics.outputDb),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Computed Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "Input RMS",
                    value = String.format(Locale.US, "%.0f", metrics.inputRms)
                )
                MetricItem(
                    label = "Output RMS",
                    value = String.format(Locale.US, "%.0f", metrics.outputRms)
                )
                MetricItem(
                    label = "SNR Gain",
                    value = String.format(Locale.US, "+%.1f dB", metrics.snrGainDb),
                    highlight = metrics.snrGainDb > 0.5
                )
                MetricItem(
                    label = "DSP Latency",
                    value = String.format(Locale.US, "%.1f µs", metrics.dspLatencyUs)
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
