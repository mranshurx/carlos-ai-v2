package com.example.service

import android.app.Notification
import android.app.NotificationManager
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
import android.os.PowerManager
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

/**
 * 24/7 Background Voice Assistant Service.
 * Runs continuously in foreground with microphone access.
 * Directly executes phone commands on voice in the background without needing activity launches.
 */
class CarlosWakeWordService : Service() {

    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.example.carlos.ACTION_START"
        const val ACTION_STOP = "com.example.carlos.ACTION_STOP"
        private const val TAG = "CarlosWakeWord"

        var isServiceRunning = false
            private set

        private var activeServiceInstance: CarlosWakeWordService? = null

        fun pauseForExternalSpeech() {
            activeServiceInstance?.pauseBackgroundListening()
        }

        fun resumeFromExternalSpeech() {
            activeServiceInstance?.resumeBackgroundListening()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: CarlosPreferences
    private lateinit var brain: CarlosBrain
    private lateinit var ttsEngine: CarlosTtsEngine

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isPaused = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastAudioActivityTimestamp = System.currentTimeMillis()
    private var isAwaitingCommand = false
    private var awaitingCommandExpiry = 0L

    // Watchdog to guarantee 24/7 uninterrupted listening
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (isServiceRunning && !isPaused) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastAudioActivityTimestamp

                // Check command awaiting timeout
                if (isAwaitingCommand && now > awaitingCommandExpiry) {
                    isAwaitingCommand = false
                    updateNotificationContent("Listening for '${prefs.wakeWord}'")
                }

                // If recognizer is not listening or received no callbacks for over 7 seconds, revive it immediately!
                if (!isListening || elapsed > 7000L) {
                    Log.d(TAG, "Watchdog reviving listener (isListening=$isListening, elapsed=${elapsed}ms)...")
                    destroyRecognizer()
                    startListeningInternal()
                }
            }
            mainHandler.postDelayed(this, 3500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = CarlosPreferences(this)
        brain = CarlosBrain(this)
        ttsEngine = CarlosTtsEngine(this)
        isServiceRunning = true
        activeServiceInstance = this

        acquireWakeLock()
        mainHandler.postDelayed(watchdogRunnable, 3500L)
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CarlosAI:ContinuousVoiceWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L /* 10 minutes, refreshed continuously */)
            }
            Log.d(TAG, "WakeLock acquired for background voice processing")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
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
        val notification = buildForegroundNotification("Listening for '${prefs.wakeWord}'")

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

    private fun buildForegroundNotification(contentText: String): Notification {
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

        return NotificationCompat.Builder(this, CarlosApp.CHANNEL_ID_WAKE_WORD)
            .setContentTitle("Carlos AI Assistant Active")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingTapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "Stop Listening", pendingStopIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Open Carlos", pendingTapIntent)
            .build()
    }

    private fun updateNotificationContent(text: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID, buildForegroundNotification(text))
        } catch (_: Exception) {}
    }

    private fun startContinuousWakeWordListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available on this device")
            return
        }

        startListeningInternal()
    }

    fun pauseBackgroundListening() {
        isPaused = true
        mainHandler.removeCallbacks(restartListeningRunnable)
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        isListening = false
    }

    fun resumeBackgroundListening() {
        isPaused = false
        lastAudioActivityTimestamp = System.currentTimeMillis()
        scheduleQuickRestart(100L)
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
    }

    private fun startListeningInternal() {
        if (!isServiceRunning || isPaused) return

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(createRecognitionListener())
                }
            }

            speechRecognizer?.cancel()
            speechRecognizer?.startListening(buildRecognizerIntent())
            isListening = true
            lastAudioActivityTimestamp = System.currentTimeMillis()
            Log.d(TAG, "SpeechRecognizer listening actively")
        } catch (e: Exception) {
            Log.e(TAG, "Error in startListeningInternal: ${e.message}")
            isListening = false
            destroyRecognizer()
            scheduleQuickRestart(400L)
        }
    }

    private fun scheduleQuickRestart(delayMs: Long) {
        if (!isServiceRunning || isPaused) return
        mainHandler.removeCallbacks(restartListeningRunnable)
        mainHandler.postDelayed(restartListeningRunnable, delayMs)
    }

    private val restartListeningRunnable = Runnable {
        if (isServiceRunning && !isPaused) {
            startListeningInternal()
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            lastAudioActivityTimestamp = System.currentTimeMillis()
        }

        override fun onBeginningOfSpeech() {
            lastAudioActivityTimestamp = System.currentTimeMillis()
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (rmsdB > 1.2f) {
                lastAudioActivityTimestamp = System.currentTimeMillis()
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            lastAudioActivityTimestamp = System.currentTimeMillis()
        }

        override fun onEndOfSpeech() {
            isListening = false
            lastAudioActivityTimestamp = System.currentTimeMillis()
        }

        override fun onError(error: Int) {
            isListening = false
            lastAudioActivityTimestamp = System.currentTimeMillis()

            Log.d(TAG, "RecognitionListener onError: $error")

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Normal silence timeout: restart listening immediately (50ms)
                    scheduleQuickRestart(50L)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                    // Reset recognizer cleanly
                    destroyRecognizer()
                    scheduleQuickRestart(200L)
                }
                else -> {
                    scheduleQuickRestart(200L)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            lastAudioActivityTimestamp = System.currentTimeMillis()
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            if (!matches.isNullOrEmpty()) {
                handleSpeechMatches(matches)
            } else {
                scheduleQuickRestart(50L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            lastAudioActivityTimestamp = System.currentTimeMillis()
            val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            if (!partials.isNullOrEmpty()) {
                for (phrase in partials) {
                    val (wakeMatched, command) = brain.matchWakeWord(phrase, prefs.wakeWord)
                    if (wakeMatched && command.isNotBlank()) {
                        // User said wake word + command in real-time partial speech! Execute immediately
                        try {
                            speechRecognizer?.cancel()
                        } catch (_: Exception) {}
                        isListening = false
                        executeDetectedCommand(command, phrase)
                        return
                    }
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleSpeechMatches(matches: List<String>) {
        serviceScope.launch {
            for (phrase in matches) {
                val cleanPhrase = phrase.trim()
                if (cleanPhrase.isBlank()) continue

                // 1. Check if wake word is present
                val (wakeMatched, commandPart) = brain.matchWakeWord(cleanPhrase, prefs.wakeWord)

                if (wakeMatched) {
                    if (commandPart.isNotBlank()) {
                        // User said: "Hey Carlos turn on flashlight" in one go!
                        executeDetectedCommand(commandPart, cleanPhrase)
                        return@launch
                    } else {
                        // User said: "Hey Carlos" alone!
                        triggerHaptic()
                        isAwaitingCommand = true
                        awaitingCommandExpiry = System.currentTimeMillis() + 8000L
                        updateNotificationContent("Carlos: Listening for your command...")
                        scheduleQuickRestart(50L)
                        return@launch
                    }
                }

                // 2. If in AWAITING_COMMAND mode (user said "Hey Carlos" previously):
                if (isAwaitingCommand && System.currentTimeMillis() <= awaitingCommandExpiry) {
                    isAwaitingCommand = false
                    updateNotificationContent("Listening for '${prefs.wakeWord}'")
                    executeDetectedCommand(cleanPhrase, cleanPhrase)
                    return@launch
                }

                // 3. Or check if the phrase is a direct phone automation command:
                // (e.g. "turn on flashlight", "turn off flashlight", "call Mom", "open WhatsApp", "open camera")
                val directAction = brain.processCommand(cleanPhrase)
                if (directAction.actionType != CarlosActionType.CONVERSATIONAL &&
                    directAction.actionType != CarlosActionType.UNKNOWN) {
                    executeDetectedCommand(cleanPhrase, cleanPhrase)
                    return@launch
                }
            }

            // No command recognized, re-arm listening immediately
            scheduleQuickRestart(50L)
        }
    }

    private fun executeDetectedCommand(command: String, rawSpeech: String) {
        triggerHaptic()
        isAwaitingCommand = false
        updateNotificationContent("Executing: $command")

        serviceScope.launch {
            val parsed = brain.processCommand(command)

            // Speak brief confirmation if enabled
            if (prefs.isTtsEnabled && parsed.speech.isNotBlank()) {
                ttsEngine.speak(parsed.speech)
            }

            // Execute the device action (flashlight, call, WhatsApp, camera, app, etc.)
            val (success, detail) = DeviceActionExecutor.executeParsedAction(
                applicationContext,
                parsed.actionType,
                parsed.target,
                parsed.params
            )

            // Save log
            prefs.saveCommandLog(
                CarlosCommandLog(
                    userInput = command,
                    actionType = parsed.actionType,
                    target = parsed.target,
                    details = detail,
                    responseSpeech = parsed.speech,
                    isSuccess = success,
                    source = "Background Voice ('$rawSpeech')"
                )
            )

            sendBroadcast(Intent("com.example.carlos.COMMAND_EXECUTED"))

            // Reset notification and resume listening after command completes
            mainHandler.postDelayed({
                updateNotificationContent("Listening for '${prefs.wakeWord}'")
                startListeningInternal()
            }, 1200L)
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
        } catch (_: Exception) {}
    }

    private fun stopListening() {
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        isListening = false
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isListening = false
    }

    override fun onDestroy() {
        isServiceRunning = false
        activeServiceInstance = null
        mainHandler.removeCallbacksAndMessages(null)
        stopListening()
        releaseWakeLock()
        ttsEngine.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
