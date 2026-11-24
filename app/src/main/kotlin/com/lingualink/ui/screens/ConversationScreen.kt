package com.lingualink.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingualink.nearby.DeviceRole
import com.lingualink.nearby.NearbyConnectionState
import com.lingualink.translation.TranslationLanguage
import com.lingualink.ui.ConversationMode
import com.lingualink.ui.MainViewModel
import com.lingualink.ui.SpeakerPanelState
import com.lingualink.ui.components.AudioMetricsCard
import com.lingualink.ui.components.LanguageDropdownSelector
import com.lingualink.ui.components.LiveWaveformView
import com.lingualink.ui.components.NearbyStatusBadge
import com.lingualink.ui.components.OfflineStatusBadge

@Composable
fun ConversationScreen(
    viewModel: MainViewModel,
    onNavigateToPacks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mode by viewModel.conversationMode.collectAsState()
    val isOfflineReady by viewModel.isOfflineReady.collectAsState()
    val nearbyState by viewModel.nearbyManager.connectionState.collectAsState()
    val metrics by viewModel.audioManager.metrics.collectAsState()
    val isFilterEnabled by viewModel.audioManager.isFilterEnabled.collectAsState()
    val isRecording by viewModel.audioManager.isRecording.collectAsState()

    val touristState by viewModel.touristState.collectAsState()
    val providerState by viewModel.providerState.collectAsState()

    var showTypedInputDialog by remember { mutableStateOf<DeviceRole?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Mode Selector Tab Row (Split-Screen Face-to-Face vs Nearby Paired)
        TabRow(
            selectedTabIndex = if (mode == ConversationMode.SPLIT_SCREEN) 0 else 1,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Tab(
                selected = mode == ConversationMode.SPLIT_SCREEN,
                onClick = { viewModel.setConversationMode(ConversationMode.SPLIT_SCREEN) },
                text = { Text("Split-Screen (Single Phone)") }
            )
            Tab(
                selected = mode == ConversationMode.NEARBY_PAIRED,
                onClick = { viewModel.setConversationMode(ConversationMode.NEARBY_PAIRED) },
                text = { Text("Nearby (Two Phones)") }
            )
        }

        // Top Header Row with Status Badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OfflineStatusBadge(
                isOfflineReady = isOfflineReady,
                onClick = onNavigateToPacks
            )

            NearbyStatusBadge(
                connectionState = nearbyState,
                onClick = {
                    if (nearbyState is NearbyConnectionState.Disconnected) {
                        viewModel.nearbyManager.startAdvertising()
                    } else {
                        viewModel.nearbyManager.disconnect()
                    }
                }
            )
        }

        if (mode == ConversationMode.SPLIT_SCREEN) {
            // === SPLIT-SCREEN MODE ===
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Half: Provider (Flipped 180 degrees)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .rotate(180f)
                ) {
                    SpeakerFacePanel(
                        panelState = providerState,
                        onLanguageChange = { viewModel.setProviderLanguage(it) },
                        onTapToSpeak = {
                            if (providerState.isListening) viewModel.stopSpeaking()
                            else viewModel.startSpeaking(DeviceRole.PROVIDER)
                        },
                        onTypedInputClick = { showTypedInputDialog = DeviceRole.PROVIDER },
                        onReplayClick = { viewModel.replayLastUtterance() },
                        accentColor = Color(0xFFE91E63),
                        title = "Service Provider"
                    )
                }

                // Center Live Audio & DSP Monitor Panel
                Divider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    LiveWaveformView(
                        waveformSamples = metrics.waveformSamples,
                        isRecording = isRecording
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AudioMetricsCard(
                        metrics = metrics,
                        isFilterEnabled = isFilterEnabled,
                        onFilterToggle = { viewModel.toggleNoiseFilter(it) }
                    )
                }

                Divider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // Bottom Half: Tourist (Right-side up)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    SpeakerFacePanel(
                        panelState = touristState,
                        onLanguageChange = { viewModel.setTouristLanguage(it) },
                        onTapToSpeak = {
                            if (touristState.isListening) viewModel.stopSpeaking()
                            else viewModel.startSpeaking(DeviceRole.TOURIST)
                        },
                        onTypedInputClick = { showTypedInputDialog = DeviceRole.TOURIST },
                        onReplayClick = { viewModel.replayLastUtterance() },
                        accentColor = Color(0xFF1976D2),
                        title = "Tourist"
                    )
                }
            }
        } else {
            // === NEARBY TWO-DEVICE PAIRED MODE ===
            NearbyModeView(
                viewModel = viewModel,
                touristState = touristState,
                providerState = providerState,
                nearbyState = nearbyState,
                metrics = metrics,
                isRecording = isRecording,
                isFilterEnabled = isFilterEnabled,
                onTypedInput = { showTypedInputDialog = DeviceRole.TOURIST }
            )
        }
    }

    // Typed input fallback dialog
    showTypedInputDialog?.let { role ->
        TypedInputDialog(
            role = role,
            onDismiss = { showTypedInputDialog = null },
            onSend = { text ->
                viewModel.sendTypedInput(text, role)
                showTypedInputDialog = null
            }
        )
    }
}

/**
 * Speaker panel used in Split-Screen mode.
 */
@Composable
private fun SpeakerFacePanel(
    panelState: SpeakerPanelState,
    onLanguageChange: (TranslationLanguage) -> Unit,
    onTapToSpeak: () -> Unit,
    onTypedInputClick: () -> Unit,
    onReplayClick: () -> Unit,
    accentColor: Color,
    title: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Panel Header: Title + Language selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }

                LanguageDropdownSelector(
                    selectedLanguage = panelState.selectedLanguage,
                    onLanguageSelected = onLanguageChange
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Original and Translated Speech Display
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Original transcript card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Original (${panelState.selectedLanguage.displayName}):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (panelState.originalText.isNotBlank()) panelState.originalText else "Tap microphone and speak...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (panelState.originalText.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                            color = if (panelState.originalText.isNotBlank()) MaterialTheme.colorScheme.onSurface else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Translated ML Kit output card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = accentColor.copy(alpha = 0.08f)
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Translated:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                            if (panelState.translatedText.isNotBlank()) {
                                IconButton(
                                    onClick = onReplayClick,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay,
                                        contentDescription = "Replay TTS",
                                        tint = accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (panelState.translatedText.isNotBlank()) panelState.translatedText else "Translation appears here...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (panelState.translatedText.isNotBlank()) MaterialTheme.colorScheme.onSurface else Color.Gray
                        )
                    }
                }

                if (panelState.statusMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = panelState.statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (panelState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Bottom action row: Mic Button + Typed input fallback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onTypedInputClick) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Typed input fallback",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Big Mic Button with Pulsing Animation when listening
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = if (panelState.isListening) 1.25f else 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                FilledTonalButton(
                    onClick = onTapToSpeak,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(54.dp)
                        .scale(pulseScale),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (panelState.isListening) Color.Red else accentColor,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (panelState.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Tap to speak",
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    }
}

/**
 * Nearby Two-Device Paired View.
 */
@Composable
private fun NearbyModeView(
    viewModel: MainViewModel,
    touristState: SpeakerPanelState,
    providerState: SpeakerPanelState,
    nearbyState: NearbyConnectionState,
    metrics: com.lingualink.audio.AudioMetrics,
    isRecording: Boolean,
    isFilterEnabled: Boolean,
    onTypedInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Pairing Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Nearby P2P Pairing (No Internet Required)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Connect two phones over Bluetooth and Wi-Fi Direct to talk naturally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.nearbyManager.startAdvertising() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Advertise")
                    }
                    Button(
                        onClick = { viewModel.nearbyManager.startDiscovery() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Discover")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Live Audio & DSP Monitor
        LiveWaveformView(
            waveformSamples = metrics.waveformSamples,
            isRecording = isRecording
        )
        Spacer(modifier = Modifier.height(6.dp))
        AudioMetricsCard(
            metrics = metrics,
            isFilterEnabled = isFilterEnabled,
            onFilterToggle = { viewModel.toggleNoiseFilter(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Utterance Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Source Transcript:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (touristState.originalText.isNotBlank()) touristState.originalText else "Spoken transcript will appear here...",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Translated (Sent to Peer Phone):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (touristState.translatedText.isNotBlank()) touristState.translatedText else "Translation will appear here...",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Tap to Speak bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onTypedInput) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Type text"
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {
                    if (touristState.isListening) viewModel.stopSpeaking()
                    else viewModel.startSpeaking(DeviceRole.TOURIST)
                },
                modifier = Modifier.height(56.dp)
            ) {
                Icon(
                    imageVector = if (touristState.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (touristState.isListening) "Stop Listening" else "Tap to Speak")
            }
        }
    }
}

@Composable
private fun TypedInputDialog(
    role: DeviceRole,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Typed Input (${role.label})") },
        text = {
            Column {
                Text(
                    text = "Type a sentence to translate and speak offline:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Enter text here...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Translate & Speak")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
