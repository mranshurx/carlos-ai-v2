package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CarlosAuthState
import com.example.ui.CarlosMainScreen
import com.example.ui.screens.CarlosKeyAuthScreen
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CarlosViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CarlosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CyberBlack
                ) {
                    val authState by viewModel.keyAuthState.collectAsState()

                    when (val state = authState) {
                        is CarlosAuthState.Authenticated -> {
                            CarlosMainScreen(viewModel = viewModel)
                        }
                        is CarlosAuthState.RequiresKey -> {
                            CarlosKeyAuthScreen(
                                errorMessage = state.errorMessage,
                                isVerifying = false,
                                onVerifyKey = { viewModel.verifyEnteredKey(it) }
                            )
                        }
                        is CarlosAuthState.Verifying -> {
                            CarlosKeyAuthScreen(
                                errorMessage = null,
                                isVerifying = true,
                                onVerifyKey = { viewModel.verifyEnteredKey(it) }
                            )
                        }
                        is CarlosAuthState.Loading -> {
                            // Loading splash while checking key status
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(CyberBlack),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = NeonCyan,
                                        modifier = Modifier.size(48.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Text(
                                        text = "Verifying Cloud Security Key...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NeonCyan
                                    )
                                    Text(
                                        text = "DEVELOPER - CYBER_ANXHU",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        letterSpacing = 1.2.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Every time the user opens the app or returns to it, verify the key online in the background
        viewModel.verifySavedKeyInBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val triggeredByWakeWord = intent.getBooleanExtra("TRIGGERED_BY_WAKE_WORD", false)
        val triggeredFromOverlay = intent.getBooleanExtra("TRIGGERED_FROM_OVERLAY", false)

        if (triggeredByWakeWord || triggeredFromOverlay) {
            // Immediately start listening for the follow-up command!
            viewModel.startVoiceListening()
        }
    }
}
