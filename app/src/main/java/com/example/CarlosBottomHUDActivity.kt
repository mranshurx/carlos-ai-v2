package com.example

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CarlosActionType
import com.example.data.model.CarlosCommandLog
import com.example.data.model.CarlosParsedAction
import com.example.data.pref.CarlosPreferences
import com.example.device.DeviceActionExecutor
import com.example.engine.CarlosBrain
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDark
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.voice.CarlosInAppListener
import com.example.voice.CarlosTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CarlosBottomHUDActivity : ComponentActivity() {

    private lateinit var prefs: CarlosPreferences
    private lateinit var brain: CarlosBrain
    private lateinit var ttsEngine: CarlosTtsEngine
    private lateinit var inAppListener: CarlosInAppListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Position window at the bottom of the screen
        window.setGravity(Gravity.BOTTOM)
        val params = window.attributes
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.dimAmount = 0.25f
        window.attributes = params

        prefs = CarlosPreferences(this)
        brain = CarlosBrain(this)
        ttsEngine = CarlosTtsEngine(this)
        inAppListener = CarlosInAppListener(this)

        setContent {
            MyApplicationTheme {
                CarlosBottomHUDContent(
                    inAppListener = inAppListener,
                    brain = brain,
                    prefs = prefs,
                    ttsEngine = ttsEngine,
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        inAppListener.stopListening()
        ttsEngine.shutdown()
        super.onDestroy()
    }
}

@Composable
fun CarlosBottomHUDContent(
    inAppListener: CarlosInAppListener,
    brain: CarlosBrain,
    prefs: CarlosPreferences,
    ttsEngine: CarlosTtsEngine,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isListening by inAppListener.isListening.collectAsState()
    val audioRms by inAppListener.rmsLevel.collectAsState()
    val partialText by inAppListener.partialText.collectAsState()

    var statusText by remember { mutableStateOf("Listening to your command...") }
    var responseSpeech by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "hud_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hud_pulse"
    )

    fun executeSpokenCommand(commandText: String) {
        if (commandText.isBlank()) return
        isExecuting = true
        statusText = "\"$commandText\""

        coroutineScope.launch {
            val parsed = brain.processCommand(commandText)
            responseSpeech = parsed.speech
            statusText = parsed.speech

            if (prefs.isTtsEnabled) {
                ttsEngine.speak(parsed.speech)
            }

            val (success, detail) = withContext(Dispatchers.Main) {
                DeviceActionExecutor.executeParsedAction(
                    com.example.CarlosApp.instance,
                    parsed.actionType,
                    parsed.target,
                    parsed.params
                )
            }

            prefs.saveCommandLog(
                CarlosCommandLog(
                    userInput = commandText,
                    actionType = parsed.actionType,
                    target = parsed.target,
                    details = detail,
                    responseSpeech = parsed.speech,
                    isSuccess = success,
                    source = "Bottom HUD"
                )
            )

            // Auto-dismiss HUD after execution so user returns directly to their phone
            Handler(Looper.getMainLooper()).postDelayed({
                onDismiss()
            }, 1400)
        }
    }

    LaunchedEffect(Unit) {
        inAppListener.onFinalResult = { text ->
            executeSpokenCommand(text)
        }
        inAppListener.onErrorOccurred = { error ->
            statusText = error
        }
        inAppListener.startListening()
    }

    // Outer container: tapping outside dismisses
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 14.dp, vertical = 18.dp)
            .testTag("carlos_bottom_hud"),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* stop propagation */ }
                )
                .border(
                    width = 1.5.dp,
                    color = NeonCyan.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(22.dp)
                ),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = CyberDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Mini animated Voice Orb
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .scale(if (isListening) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(
                                    if (isExecuting) NeonPurple.copy(alpha = 0.25f)
                                    else NeonCyan.copy(alpha = 0.25f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = "Carlos Orb",
                                tint = if (isExecuting) NeonPurple else NeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "CARLOS AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonCyan,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isListening) CyberEmerald else NeonPurple)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = if (partialText.isNotBlank()) "\"$partialText\"" else statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 2
                            )
                        }
                    }

                    // Right: Close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("button_close_hud")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Developer Signature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Say any command: \"open camera\", \"turn off flashlight\", etc.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )

                    Text(
                        text = "DEVELOPER - CYBER_ANXHU",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = ElectricBlue,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}
