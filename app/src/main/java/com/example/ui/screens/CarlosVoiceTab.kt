package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CarlosState
import com.example.ui.components.CarlosVoiceOrb
import com.example.ui.components.WakeWordSettingsCard
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDark
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CarlosViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarlosVoiceTab(
    viewModel: CarlosViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val carlosState by viewModel.carlosState.collectAsState()
    val liveTranscript by viewModel.liveTranscript.collectAsState()
    val lastResponse by viewModel.lastResponseText.collectAsState()
    val audioRms by viewModel.audioRmsLevel.collectAsState()
    val isBgActive by viewModel.isBackgroundServiceRunning.collectAsState()
    val currentWakeWord by viewModel.wakeWord.collectAsState()
    val grokApiKey by viewModel.grokApiKey.collectAsState()

    var manualText by remember { mutableStateOf("") }

    val quickCommands = listOf(
        "Open WhatsApp",
        "Open Camera",
        "Open Gallery",
        "Make a Call",
        "Turn on Flashlight",
        "Check Battery",
        "Open Settings",
        "Send WhatsApp"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBlack)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top status pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isBgActive) CyberEmerald else Color.Gray)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBgActive) "BG Listener: ACTIVE" else "BG Listener: PAUSED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isBgActive) CyberEmerald else TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (grokApiKey.isNotBlank()) NeonPurple.copy(alpha = 0.2f) else CyberSurface)
                    .border(
                        1.dp,
                        if (grokApiKey.isNotBlank()) NeonPurple.copy(alpha = 0.5f) else CyberBorder,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (grokApiKey.isNotBlank()) Icons.Default.SmartToy else Icons.Default.Bolt,
                        contentDescription = "AI Mode",
                        tint = if (grokApiKey.isNotBlank()) NeonPurple else ElectricBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (grokApiKey.isNotBlank()) "Grok 2 AI Powered" else "Smart Instant Engine",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Customizable Wake-Word Card
        WakeWordSettingsCard(
            currentWakeWord = currentWakeWord,
            isBackgroundActive = isBgActive,
            onToggleBackground = { viewModel.toggleBackgroundWakeWord(it, context) },
            onSaveWakeWord = { viewModel.updateWakeWord(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Holographic Voice Orb
        CarlosVoiceOrb(
            state = carlosState,
            rmsLevel = audioRms,
            onClick = {
                if (carlosState == CarlosState.LISTENING_COMMAND) {
                    viewModel.stopVoiceListening()
                } else {
                    viewModel.startVoiceListening()
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Current Voice State Label
        Text(
            text = when (carlosState) {
                CarlosState.IDLE -> "Tap orb or say \"$currentWakeWord\""
                CarlosState.LISTENING_WAKE_WORD, CarlosState.LISTENING_COMMAND -> "Listening to your voice..."
                CarlosState.PROCESSING -> "Carlos is executing command..."
                CarlosState.SPEAKING -> "Carlos is speaking..."
                CarlosState.ERROR -> "Voice error. Tap to retry."
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = when (carlosState) {
                CarlosState.IDLE -> TextSecondary
                CarlosState.LISTENING_COMMAND -> NeonCyan
                CarlosState.PROCESSING -> NeonPurple
                CarlosState.SPEAKING -> ElectricBlue
                CarlosState.ERROR -> MaterialTheme.colorScheme.error
                else -> TextSecondary
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Speech bubbles
        AnimatedVisibility(visible = liveTranscript.isNotBlank() || lastResponse.isNotBlank()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // User Transcript Bubble
                if (liveTranscript.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp))
                            .background(CyberSurface)
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "You said:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = liveTranscript,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Carlos Response Bubble
                if (lastResponse.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("carlos_response_card"),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberDark),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ElectricBlue.copy(alpha = 0.5f)))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Carlos",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Carlos:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lastResponse,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }

                            IconButton(
                                onClick = { viewModel.speakCurrentResponse() },
                                modifier = Modifier.testTag("button_repeat_speech")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Speak aloud",
                                    tint = ElectricBlue
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Quick command suggestions
        Text(
            text = "Voice Commands Carlos Can Execute:",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickCommands.forEach { cmd ->
                AssistChip(
                    onClick = {
                        viewModel.processUserVoiceInput(cmd)
                    },
                    label = { Text(cmd, color = TextPrimary) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = CyberSurface
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        borderColor = CyberBorder,
                        enabled = true
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Execute $cmd",
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    modifier = Modifier.testTag("chip_command_${cmd.lowercase().replace(" ", "_")}")
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Manual Command Text Bar
        OutlinedTextField(
            value = manualText,
            onValueChange = { manualText = it },
            placeholder = { Text("Or type any command to Carlos...", color = TextSecondary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (manualText.isNotBlank()) {
                        viewModel.processUserVoiceInput(manualText)
                        manualText = ""
                    }
                }
            ),
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (manualText.isNotBlank()) {
                            viewModel.processUserVoiceInput(manualText)
                            manualText = ""
                        }
                    },
                    modifier = Modifier.testTag("button_send_manual_command")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Command",
                        tint = NeonCyan
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = CyberBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = CyberSurface,
                unfocusedContainerColor = CyberSurface
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_manual_command")
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Developer Signature
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CyberSurface)
                .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "DEVELOPER - CYBER_ANXHU",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = NeonCyan,
                letterSpacing = 1.6.sp
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
