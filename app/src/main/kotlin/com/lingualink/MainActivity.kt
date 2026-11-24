package com.lingualink

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.lingualink.ui.AppScreen
import com.lingualink.ui.MainViewModel
import com.lingualink.ui.screens.ConversationScreen
import com.lingualink.ui.screens.LanguagePackScreen
import com.lingualink.ui.screens.LogHistoryScreen
import com.lingualink.ui.screens.PhrasebookScreen
import com.lingualink.ui.screens.SettingsAndSelfTestScreen
import com.lingualink.ui.theme.LinguaLinkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LinguaLinkTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()

                // Permission Request Handling
                var hasAudioPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
                }

                LaunchedEffect(Unit) {
                    val neededPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        neededPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        neededPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
                        neededPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                        neededPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }

                    val ungranted = neededPermissions.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (ungranted.isNotEmpty()) {
                        permissionLauncher.launch(ungranted.toTypedArray())
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Lingua Link",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentScreen == AppScreen.CONVERSATION,
                                onClick = { viewModel.navigateTo(AppScreen.CONVERSATION) },
                                icon = { Icon(Icons.Default.Chat, contentDescription = "Conversation") },
                                label = { Text("Chat") }
                            )
                            NavigationBarItem(
                                selected = currentScreen == AppScreen.PHRASEBOOK,
                                onClick = { viewModel.navigateTo(AppScreen.PHRASEBOOK) },
                                icon = { Icon(Icons.Default.Bookmark, contentDescription = "Phrasebook") },
                                label = { Text("Phrases") }
                            )
                            NavigationBarItem(
                                selected = currentScreen == AppScreen.LANGUAGE_PACKS,
                                onClick = { viewModel.navigateTo(AppScreen.LANGUAGE_PACKS) },
                                icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Language Packs") },
                                label = { Text("Packs") }
                            )
                            NavigationBarItem(
                                selected = currentScreen == AppScreen.LOG_HISTORY,
                                onClick = { viewModel.navigateTo(AppScreen.LOG_HISTORY) },
                                icon = { Icon(Icons.Default.History, contentDescription = "Log History") },
                                label = { Text("Log") }
                            )
                            NavigationBarItem(
                                selected = currentScreen == AppScreen.SETTINGS_SELF_TEST,
                                onClick = { viewModel.navigateTo(AppScreen.SETTINGS_SELF_TEST) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings & Test") },
                                label = { Text("Settings") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            AppScreen.CONVERSATION -> ConversationScreen(
                                viewModel = viewModel,
                                onNavigateToPacks = { viewModel.navigateTo(AppScreen.LANGUAGE_PACKS) }
                            )
                            AppScreen.PHRASEBOOK -> PhrasebookScreen(viewModel = viewModel)
                            AppScreen.LANGUAGE_PACKS -> LanguagePackScreen(viewModel = viewModel)
                            AppScreen.LOG_HISTORY -> LogHistoryScreen(viewModel = viewModel)
                            AppScreen.SETTINGS_SELF_TEST -> SettingsAndSelfTestScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
