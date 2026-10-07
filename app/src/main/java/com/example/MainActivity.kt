package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.CarlosMainScreen
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.MyApplicationTheme
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
                    CarlosMainScreen(viewModel = viewModel)
                }
            }
        }
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
