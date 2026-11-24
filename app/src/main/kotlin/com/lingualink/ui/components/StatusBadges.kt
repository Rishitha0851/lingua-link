package com.lingualink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingualink.nearby.NearbyConnectionState
import com.lingualink.translation.TranslationLanguage

@Composable
fun OfflineStatusBadge(
    isOfflineReady: Boolean,
    onClick: () -> Unit = {}
) {
    val bgColor = if (isOfflineReady) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    val contentColor = if (isOfflineReady) Color(0xFF2E7D32) else Color(0xFFE65100)
    val icon = if (isOfflineReady) Icons.Default.CheckCircle else Icons.Default.Warning
    val label = if (isOfflineReady) "Offline Ready" else "Packs Incomplete"

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
fun NearbyStatusBadge(
    connectionState: NearbyConnectionState,
    onClick: () -> Unit = {}
) {
    val (label, bgColor, contentColor, isConnected) = when (connectionState) {
        is NearbyConnectionState.Connected -> {
            val name = connectionState.endpointName.take(12)
            listOf("Nearby: $name", Color(0xFFE1F5FE), Color(0xFF0277BD), true)
        }
        is NearbyConnectionState.Advertising -> {
            listOf("Advertising...", Color(0xFFF3E5F5), Color(0xFF7B1FA2), false)
        }
        is NearbyConnectionState.Discovering -> {
            listOf("Discovering...", Color(0xFFEDE7F6), Color(0xFF512DA8), false)
        }
        is NearbyConnectionState.Connecting -> {
            listOf("Connecting...", Color(0xFFFFF8E1), Color(0xFFF57F17), false)
        }
        else -> {
            listOf("Nearby: Off", Color(0xFFEEEEEE), Color(0xFF757575), false)
        }
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor as Color)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isConnected as Boolean) Icons.Default.Wifi else Icons.Default.WifiOff,
            contentDescription = null,
            tint = contentColor as Color,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label as String,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
fun LanguageDropdownSelector(
    selectedLanguage: TranslationLanguage,
    onLanguageSelected: (TranslationLanguage) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedCard(
            modifier = Modifier.clickable(enabled = enabled) { expanded = true },
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedLanguage.displayName} (${selectedLanguage.nativeName})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TranslationLanguage.entries.forEach { lang ->
                DropdownMenuItem(
                    text = {
                        Text("${lang.displayName} - ${lang.nativeName}")
                    },
                    onClick = {
                        onLanguageSelected(lang)
                        expanded = false
                    }
                )
            }
        }
    }
}
