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
 * 24/7 Background Foreground Service with continuous microphone capture.
 * Features:
 * - PARTIAL_WAKE_LOCK to prevent CPU sleep when phone is locked.
 * - Active Keep-Alive Watchdog to resurrect speech recognition automatically if it ever stalls.
 * - Instant re-arm (50ms) on speech timeouts or silence.
 * - Auto-recreation on errors (ERROR_RECOGNIZER_BUSY, ERROR_CLIENT, etc.).
 * - Safe mic handoff when bottom HUD or in-app speech is active.
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
        private var isExternalMicActive = false

        fun pauseForExternalSpeech() {
            isExternalMicActive = true
            activeServiceInstance?.pauseBackgroundListening()
        }

        fun resumeFromExternalSpeech() {
            isExternalMicActive = false
            activeServiceInstance?.resumeBackgroundListening()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: CarlosPreferences
    private lateinit var brain: CarlosBrain
    private lateinit var ttsEngine: CarlosTtsEngine

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastAudioActivityTimestamp = System.currentTimeMillis()
    private var consecutiveErrors = 0

    // Watchdog to guarantee 24/7 uninterrupted listening
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (isServiceRunning && !isExternalMicActive) {
                val elapsed = System.currentTimeMillis() - lastAudioActivityTimestamp
                // If recognizer is not listening or received no callbacks for over 6 seconds, revive it immediately!
                if (!isListening || elapsed > 6500L) {
                    Log.d(TAG, "Watchdog detected inactive mic (isListening=$isListening, elapsed=${elapsed}ms). Reviving 24/7 listener...")
                    recreateRecognizerAndListen()
                }
            }
            mainHandler.postDelayed(this, 3000L)
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
        mainHandler.postDelayed(watchdogRunnable, 3000L)
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CarlosAI:ContinuousVoiceWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "Partial WakeLock acquired for 24/7 background voice listening")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        }
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
            .setContentTitle("Carlos AI Active (Mic Always On 24/7)")
            .setContentText("Listening for '$wakeWordDisplay' - Ready for commands anytime")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingTapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
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
            Log.e(TAG, "Speech recognition not available on this device")
            return
        }

        recreateRecognizerAndListen()
    }

    fun pauseBackgroundListening() {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed(watchdogRunnable, 3000L)
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        isListening = false
    }

    fun resumeBackgroundListening() {
        lastAudioActivityTimestamp = System.currentTimeMillis()
        recreateRecognizerAndListen()
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            // Keep continuous dictation active without premature silence cuts
            putExtra("android.speech.extra.DICTATION_MODE", true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
    }

    private fun recreateRecognizerAndListen() {
        if (!isServiceRunning || isExternalMicActive) return

        destroyRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(createRecognitionListener())
            }
            speechRecognizer?.startListening(buildRecognizerIntent())
            isListening = true
            lastAudioActivityTimestamp = System.currentTimeMillis()
            consecutiveErrors = 0
            Log.d(TAG, "SpeechRecognizer created and actively listening 24/7")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer: ${e.message}")
            isListening = false
            scheduleQuickRestart(400L)
        }
    }

    private fun scheduleQuickRestart(delayMs: Long) {
        if (!isServiceRunning || isExternalMicActive) return
        mainHandler.removeCallbacks(restartListeningRunnable)
        mainHandler.postDelayed(restartListeningRunnable, delayMs)
    }

    private val restartListeningRunnable = Runnable {
        if (isServiceRunning && !isExternalMicActive) {
            try {
                if (speechRecognizer == null) {
                    recreateRecognizerAndListen()
                } else {
                    speechRecognizer?.cancel()
                    speechRecognizer?.startListening(buildRecognizerIntent())
                    isListening = true
                    lastAudioActivityTimestamp = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Quick restart failed, recreating recognizer: ${e.message}")
                recreateRecognizerAndListen()
            }
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            lastAudioActivityTimestamp = System.currentTimeMillis()
            consecutiveErrors = 0
        }

        override fun onBeginningOfSpeech() {
            lastAudioActivityTimestamp = System.currentTimeMillis()
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (rmsdB > 1.5f) {
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
            consecutiveErrors++

            Log.d(TAG, "Recognition onError code=$error (consecutive=$consecutiveErrors)")

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Normal silence timeout: re-arm mic immediately (50ms)
                    scheduleQuickRestart(50L)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                    // Critical speech engine reset required
                    mainHandler.postDelayed({ recreateRecognizerAndListen() }, 150L)
                }
                else -> {
                    val delay = if (consecutiveErrors > 3) 1000L else 200L
                    mainHandler.postDelayed({ recreateRecognizerAndListen() }, delay)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            lastAudioActivityTimestamp = System.currentTimeMillis()
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            if (!matches.isNullOrEmpty()) {
                handleSpeechResults(matches)
            } else {
                scheduleQuickRestart(50L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            lastAudioActivityTimestamp = System.currentTimeMillis()
            val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            if (!partials.isNullOrEmpty()) {
                for (phrase in partials) {
                    val (matched, command) = brain.matchWakeWord(phrase, prefs.wakeWord)
                    if (matched) {
                        try {
                            speechRecognizer?.cancel()
                        } catch (_: Exception) {}
                        isListening = false
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
            scheduleQuickRestart(50L)
        }
    }

    private fun onWakeWordDetected(fullSpeech: String, immediateCommand: String) {
        triggerHaptic()

        serviceScope.launch {
            if (immediateCommand.isNotBlank()) {
                // User said "Hey Carlos turn off my flashlight" in one breath!
                val parsed = brain.processCommand(immediateCommand)
                if (prefs.isTtsEnabled) {
                    ttsEngine.speak(parsed.speech)
                }

                val (success, detail) = DeviceActionExecutor.executeParsedAction(
                    applicationContext,
                    parsed.actionType,
                    parsed.target,
                    parsed.params
                )

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

                sendBroadcast(Intent("com.example.carlos.COMMAND_EXECUTED"))

                // Resume continuous 24/7 listening after brief command execution pause
                mainHandler.postDelayed({
                    recreateRecognizerAndListen()
                }, 1200L)
            } else {
                // User said only "Hey Carlos": launch bottom HUD silently
                val bottomHudIntent = Intent(applicationContext, com.example.CarlosBottomHUDActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                applicationContext.startActivity(bottomHudIntent)

                // The bottom HUD manages its own mic. As soon as HUD dismisses, resumeFromExternalSpeech() takes over!
            }
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
