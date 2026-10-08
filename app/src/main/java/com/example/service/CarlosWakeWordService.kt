package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.CarlosApp
import com.example.MainActivity
import com.example.R
import com.example.data.model.CarlosActionType
import com.example.data.model.CarlosCommandLog
import com.example.data.pref.CarlosPreferences
import com.example.device.DeviceActionExecutor
import com.example.engine.CarlosBrain
import com.example.voice.CarlosTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class CarlosWakeWordService : Service() {

    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.example.carlos.ACTION_START"
        const val ACTION_STOP = "com.example.carlos.ACTION_STOP"
        const val ACTION_WAKE_DETECTED = "com.example.carlos.ACTION_WAKE_DETECTED"
        const val EXTRA_DETECTED_COMMAND = "extra_detected_command"

        var isServiceRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: CarlosPreferences
    private lateinit var brain: CarlosBrain
    private lateinit var ttsEngine: CarlosTtsEngine

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        prefs = CarlosPreferences(this)
        brain = CarlosBrain(this)
        ttsEngine = CarlosTtsEngine(this)
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListening()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        startContinuousWakeWordListening()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingTapIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CarlosWakeWordService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wakeWordDisplay = prefs.wakeWord.ifEmpty { "Hey Carlos" }

        val notification: Notification = NotificationCompat.Builder(this, CarlosApp.CHANNEL_ID_WAKE_WORD)
            .setContentTitle("Carlos AI Assistant Active")
            .setContentText("Listening for '$wakeWordDisplay' (Phone Automation Ready)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingTapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "Stop Listening", pendingStopIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Talk to Carlos", pendingTapIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startContinuousWakeWordListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("CarlosWakeWord", "Speech recognition not available")
            return
        }

        initRecognizer()
        restartListeningWithDelay(200)
    }

    private fun initRecognizer() {
        destroyRecognizer()
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(createRecognitionListener())
            }
        } catch (e: Exception) {
            Log.e("CarlosWakeWord", "Error creating speech recognizer: ${e.message}")
        }
    }

    private fun startListeningInternal() {
        if (speechRecognizer == null) {
            initRecognizer()
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        try {
            speechRecognizer?.startListening(recognizerIntent)
            isListening = true
        } catch (e: Exception) {
            isListening = false
            restartListeningWithDelay(1500)
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            // Brief pause before restarting listener to avoid tight retry loop
            val delayMs = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> 400L
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 400L
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1000L
                else -> 1200L
            }
            restartListeningWithDelay(delayMs)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                handleSpeechResults(matches)
            } else {
                restartListeningWithDelay(400)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!partials.isNullOrEmpty()) {
                for (phrase in partials) {
                    val (matched, command) = brain.matchWakeWord(phrase, prefs.wakeWord)
                    if (matched) {
                        // Quick trigger on partial if command is already present
                        speechRecognizer?.stopListening()
                        onWakeWordDetected(phrase, command)
                        return
                    }
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleSpeechResults(matches: List<String>) {
        var wakeMatched = false
        var commandFound = ""
        var fullSpeech = ""

        for (phrase in matches) {
            val (matched, command) = brain.matchWakeWord(phrase, prefs.wakeWord)
            if (matched) {
                wakeMatched = true
                commandFound = command
                fullSpeech = phrase
                break
            }
        }

        if (wakeMatched) {
            onWakeWordDetected(fullSpeech, commandFound)
        } else {
            // No wake word matched, keep listening
            restartListeningWithDelay(300)
        }
    }

    private fun onWakeWordDetected(fullSpeech: String, immediateCommand: String) {
        triggerHaptic()

        serviceScope.launch {
            if (immediateCommand.isNotBlank()) {
                // User said "Hey Carlos open WhatsApp" in one go!
                val parsed = brain.processCommand(immediateCommand)
                if (prefs.isTtsEnabled) {
                    ttsEngine.speak(parsed.speech)
                }

                // Execute device action right away!
                val (success, detail) = DeviceActionExecutor.executeParsedAction(
                    applicationContext,
                    parsed.actionType,
                    parsed.target,
                    parsed.params
                )

                // Save log
                prefs.saveCommandLog(
                    CarlosCommandLog(
                        userInput = immediateCommand,
                        actionType = parsed.actionType,
                        target = parsed.target,
                        details = detail,
                        responseSpeech = parsed.speech,
                        isSuccess = success,
                        source = "Background Voice ('${prefs.wakeWord}')"
                    )
                )

                // Broadcast update so open UI refreshes
                sendBroadcast(Intent("com.example.carlos.COMMAND_EXECUTED"))
            } else {
                // Launch compact Bottom HUD box at the bottom instead of opening the full app!
                val bottomHudIntent = Intent(applicationContext, com.example.CarlosBottomHUDActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                applicationContext.startActivity(bottomHudIntent)
            }

            // Wait 2.5 seconds before resuming background wake-word listening loop
            mainHandler.postDelayed({
                restartListeningWithDelay(500)
            }, 2500)
        }
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(120)
            }
        } catch (e: Exception) {
            // vibration fallback
        }
    }

    private fun restartListeningWithDelay(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (isServiceRunning) {
                startListeningInternal()
            }
        }, delayMs)
    }

    private fun stopListening() {
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        isListening = false
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        speechRecognizer = null
    }

    override fun onDestroy() {
        isServiceRunning = false
        stopListening()
        ttsEngine.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
