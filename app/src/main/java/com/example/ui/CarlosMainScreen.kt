package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ScreenTorchDialog
import com.example.ui.screens.CarlosActionsTab
import com.example.ui.screens.CarlosGrokTab
import com.example.ui.screens.CarlosHistoryTab
import com.example.ui.screens.CarlosPermissionsTab
import com.example.ui.screens.CarlosVoiceTab
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDark
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CarlosViewModel

enum class CarlosNavTab(val title: String, val icon: ImageVector) {
    VOICE("Voice", Icons.Default.Mic),
    CONTROLS("Controls", Icons.Default.Smartphone),
    SETUP("Setup", Icons.Default.Security),
    HISTORY("History", Icons.Default.History)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarlosMainScreen(
    viewModel: CarlosViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    val isBgActive by viewModel.isBackgroundServiceRunning.collectAsState()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsState()
    val wakeWord by viewModel.wakeWord.collectAsState()
    val isScreenTorchActive by viewModel.isScreenTorchActive.collectAsState()

    if (isScreenTorchActive) {
        ScreenTorchDialog(onDismiss = { viewModel.dismissScreenTorch() })
    }

    BackHandler(enabled = selectedTabIndex != 0) {
        selectedTabIndex = 0
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CyberBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(NeonCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Carlos",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Carlos AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isBgActive) "Listening: \"$wakeWord\"" else "Standby Mode",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isBgActive) CyberEmerald else TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    // TTS Toggle
                    IconButton(
                        onClick = { viewModel.toggleTts(!isTtsEnabled) },
                        modifier = Modifier.testTag("button_toggle_tts")
                    ) {
                        Icon(
                            imageVector = if (isTtsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Voice Feedback",
                            tint = if (isTtsEnabled) ElectricBlue else TextSecondary
                        )
                    }

                    // Background service toggle icon
                    IconButton(
                        onClick = { viewModel.toggleBackgroundWakeWord(!isBgActive, context) },
                        modifier = Modifier.testTag("button_quick_toggle_bg")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = "Background Listener",
                            tint = if (isBgActive) CyberEmerald else TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberDark,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberDark),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavigationBar(
                    containerColor = CyberDark,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("carlos_bottom_navigation")
                ) {
                    CarlosNavTab.values().forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = NeonCyan,
                                indicatorColor = NeonCyan,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }

                // Permanent Developer Signature at the bottom of the UI
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberBlack)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DEVELOPER - CYBER_ANXHU",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyan,
                        letterSpacing = 1.6.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabIndex) {
                0 -> CarlosVoiceTab(viewModel = viewModel)
                1 -> CarlosActionsTab(viewModel = viewModel)
                2 -> CarlosPermissionsTab(viewModel = viewModel)
                3 -> CarlosHistoryTab(viewModel = viewModel)
            }
        }
    }
}
