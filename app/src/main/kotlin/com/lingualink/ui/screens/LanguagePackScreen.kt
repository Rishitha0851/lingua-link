package com.lingualink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingualink.packmanager.LanguagePackItem
import com.lingualink.packmanager.PackStatus
import com.lingualink.packmanager.PackType
import com.lingualink.ui.MainViewModel

@Composable
fun LanguagePackScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val packItems by viewModel.languagePackManager.packItems.collectAsState()

    val sttPacks = packItems.filter { it.type == PackType.STT_VOSK }
    val translationPacks = packItems.filter { it.type == PackType.TRANSLATION_MLKIT }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Offline Language Pack Manager",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Download speech recognition and translation models to use Lingua Link completely offline in airplane mode. Models are stored locally on the device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Section 1: Vosk Speech Recognition Models
        item {
            Text(
                text = "Vosk Speech Recognition Models (STT)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(sttPacks) { pack ->
            LanguagePackCard(
                pack = pack,
                onDownload = { viewModel.languagePackManager.downloadPack(pack) },
                onDelete = { viewModel.languagePackManager.deletePack(pack) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Google ML Kit On-Device Translation Models",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(translationPacks) { pack ->
            LanguagePackCard(
                pack = pack,
                onDownload = { viewModel.languagePackManager.downloadPack(pack) },
                onDelete = { viewModel.languagePackManager.deletePack(pack) }
            )
        }
    }
}

@Composable
private fun LanguagePackCard(
    pack: LanguagePackItem,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
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
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pack.languageName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Native: ${pack.nativeName} • Size: ${pack.sizeText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                StatusBadge(status = pack.status)
            }

            // Progress bar if downloading
            if (pack.status == PackStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { pack.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            pack.statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (pack.status) {
                        PackStatus.UNSUPPORTED -> Color(0xFFE65100)
                        PackStatus.ERROR -> MaterialTheme.colorScheme.error
                        PackStatus.READY -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (pack.status) {
                    PackStatus.READY -> {
                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete")
                        }
                    }
                    PackStatus.DOWNLOADING -> {
                        Text(
                            text = "Downloading ${pack.progressPercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    PackStatus.UNSUPPORTED -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFF3E0)
                        ) {
                            Text(
                                text = "Speech Input Unsupported",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    else -> {
                        Button(
                            onClick = onDownload,
                            enabled = pack.isActionEnabled,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: PackStatus) {
    val (label, bgColor, fgColor) = when (status) {
        PackStatus.READY -> Triple("Ready", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        PackStatus.DOWNLOADING -> Triple("Downloading", Color(0xFFE3F2FD), Color(0xFF1565C0))
        PackStatus.UNSUPPORTED -> Triple("Unsupported", Color(0xFFFBE9E7), Color(0xFFD84315))
        PackStatus.ERROR -> Triple("Error", Color(0xFFFFEBEE), Color(0xFFC62828))
        PackStatus.NOT_DOWNLOADED -> Triple("Not Downloaded", Color(0xFFF5F5F5), Color(0xFF757575))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = fgColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
