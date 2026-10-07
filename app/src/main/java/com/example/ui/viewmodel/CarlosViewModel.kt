package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GrokApiClient
import com.example.data.model.CarlosActionType
import com.example.data.model.CarlosCommandLog
import com.example.data.model.CarlosParsedAction
import com.example.data.model.CarlosState
import com.example.data.model.GrokMessage
import com.example.data.model.GrokRequest
import com.example.data.model.InstalledAppInfo
import com.example.data.pref.CarlosPreferences
import com.example.device.DeviceActionExecutor
import com.example.engine.CarlosBrain
import com.example.service.CarlosOverlayService
import com.example.service.CarlosWakeWordService
import com.example.voice.CarlosInAppListener
import com.example.voice.CarlosTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CarlosViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = CarlosPreferences(application)
    private val brain = CarlosBrain(application)
    private val ttsEngine = CarlosTtsEngine(application)
    private val inAppListener = CarlosInAppListener(application)

    private val _carlosState = MutableStateFlow(CarlosState.IDLE)
    val carlosState: StateFlow<CarlosState> = _carlosState.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _lastResponseText = MutableStateFlow("Hi! I'm Carlos. Say 'Hey Carlos' or tap the orb to control your phone.")
    val lastResponseText: StateFlow<String> = _lastResponseText.asStateFlow()

    private val _lastAction = MutableStateFlow<CarlosParsedAction?>(null)
    val lastAction: StateFlow<CarlosParsedAction?> = _lastAction.asStateFlow()

    val audioRmsLevel: StateFlow<Float> = inAppListener.rmsLevel

    val isTorchOn: StateFlow<Boolean> = DeviceActionExecutor.getFlashlightManager(application).isTorchOn
    val isScreenTorchActive: StateFlow<Boolean> = DeviceActionExecutor.getFlashlightManager(application).isScreenTorchActive

    private val _isBackgroundServiceRunning = MutableStateFlow(CarlosWakeWordService.isServiceRunning)
    val isBackgroundServiceRunning: StateFlow<Boolean> = _isBackgroundServiceRunning.asStateFlow()

    private val _wakeWord = MutableStateFlow(prefs.wakeWord)
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    private val _grokApiKey = MutableStateFlow(prefs.grokApiKey)
    val grokApiKey: StateFlow<String> = _grokApiKey.asStateFlow()

    private val _grokModel = MutableStateFlow(prefs.grokModel)
    val grokModel: StateFlow<String> = _grokModel.asStateFlow()

    private val _grokPersona = MutableStateFlow(prefs.grokPersona)
    val grokPersona: StateFlow<String> = _grokPersona.asStateFlow()

    private val _isGrokTesting = MutableStateFlow(false)
    val isGrokTesting: StateFlow<Boolean> = _isGrokTesting.asStateFlow()

    private val _grokTestStatus = MutableStateFlow<String?>(null)
    val grokTestStatus: StateFlow<String?> = _grokTestStatus.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _commandLogs = MutableStateFlow<List<CarlosCommandLog>>(prefs.getCommandLogs())
    val commandLogs: StateFlow<List<CarlosCommandLog>> = _commandLogs.asStateFlow()

    private val _isTtsEnabled = MutableStateFlow(prefs.isTtsEnabled)
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    private val _isOverlayRunning = MutableStateFlow(CarlosOverlayService.isOverlayShowing)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    init {
        // Collect in-app speech results
        inAppListener.onFinalResult = { text ->
            processUserVoiceInput(text)
        }
        inAppListener.onErrorOccurred = { error ->
            _carlosState.value = CarlosState.ERROR
            _lastResponseText.value = error
        }

        // Monitor TTS speaking state
        viewModelScope.launch {
            ttsEngine.isSpeaking.collect { speaking ->
                if (speaking) {
                    _carlosState.value = CarlosState.SPEAKING
                } else if (_carlosState.value == CarlosState.SPEAKING) {
                    _carlosState.value = CarlosState.IDLE
                }
            }
        }

        // Load installed apps in background
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = DeviceActionExecutor.getInstalledApps(getApplication())
            _installedApps.value = apps
        }
    }

    fun startVoiceListening() {
        ttsEngine.stop()
        _carlosState.value = CarlosState.LISTENING_COMMAND
        _liveTranscript.value = "Listening..."
        inAppListener.startListening()
    }

    fun stopVoiceListening() {
        inAppListener.stopListening()
        if (_carlosState.value == CarlosState.LISTENING_COMMAND) {
            _carlosState.value = CarlosState.IDLE
        }
    }

    fun processUserVoiceInput(input: String) {
        _liveTranscript.value = input
        _carlosState.value = CarlosState.PROCESSING

        viewModelScope.launch {
            // Strip any leading wake word if present in direct voice mode
            val (_, commandPart) = brain.matchWakeWord(input, prefs.wakeWord)
            val effectiveCommand = if (commandPart.isNotBlank()) commandPart else input

            val parsed = brain.processCommand(effectiveCommand)
            _lastAction.value = parsed
            _lastResponseText.value = parsed.speech

            // Speak response if TTS is enabled
            if (prefs.isTtsEnabled) {
                ttsEngine.speak(parsed.speech)
            }

            // Execute device action
            val (success, detail) = withContext(Dispatchers.Main) {
                DeviceActionExecutor.executeParsedAction(
                    getApplication(),
                    parsed.actionType,
                    parsed.target,
                    parsed.params
                )
            }

            // Save log
            val log = CarlosCommandLog(
                userInput = input,
                actionType = parsed.actionType,
                target = parsed.target,
                details = detail,
                responseSpeech = parsed.speech,
                isSuccess = success,
                source = "App Direct"
            )
            prefs.saveCommandLog(log)
            _commandLogs.value = prefs.getCommandLogs()
        }
    }

    fun executeQuickAction(actionType: CarlosActionType, target: String, params: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            val (success, detail) = withContext(Dispatchers.Main) {
                DeviceActionExecutor.executeParsedAction(
                    getApplication(),
                    actionType,
                    target,
                    params
                )
            }
            val speech = when (actionType) {
                CarlosActionType.OPEN_APP -> "Opening $target"
                CarlosActionType.MAKE_CALL -> "Calling $target"
                CarlosActionType.SEND_WHATSAPP -> "Opening WhatsApp"
                CarlosActionType.OPEN_CAMERA -> "Opening Camera"
                CarlosActionType.OPEN_GALLERY -> "Opening Gallery"
                CarlosActionType.TOGGLE_FLASHLIGHT -> detail
                CarlosActionType.CHECK_BATTERY -> detail
                CarlosActionType.DEVICE_SETTINGS -> "Opening $target settings"
                else -> detail
            }

            _lastResponseText.value = speech
            if (prefs.isTtsEnabled) {
                ttsEngine.speak(speech)
            }

            val log = CarlosCommandLog(
                userInput = "Quick Action: ${actionType.displayName}",
                actionType = actionType,
                target = target,
                details = detail,
                responseSpeech = speech,
                isSuccess = success,
                source = "Quick Action"
            )
            prefs.saveCommandLog(log)
            _commandLogs.value = prefs.getCommandLogs()
        }
    }

    fun dismissScreenTorch() {
        DeviceActionExecutor.getFlashlightManager(getApplication()).setScreenTorch(false)
    }

    fun toggleBackgroundWakeWord(enable: Boolean, context: Context) {
        prefs.isBackgroundListeningEnabled = enable
        _isBackgroundServiceRunning.value = enable

        val intent = Intent(context, CarlosWakeWordService::class.java).apply {
            action = if (enable) CarlosWakeWordService.ACTION_START else CarlosWakeWordService.ACTION_STOP
        }

        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            val msg = "Carlos is now actively listening for '${prefs.wakeWord}' in the background!"
            _lastResponseText.value = msg
            if (prefs.isTtsEnabled) ttsEngine.speak(msg)
        } else {
            context.startService(intent)
            _lastResponseText.value = "Background listening stopped."
        }
    }

    fun toggleOverlayService(enable: Boolean, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            _lastResponseText.value = "Overlay permission required to display Carlos over other apps."
            return
        }

        val intent = Intent(context, CarlosOverlayService::class.java)
        if (enable) {
            context.startService(intent)
            _isOverlayRunning.value = true
        } else {
            context.stopService(intent)
            _isOverlayRunning.value = false
        }
    }

    fun updateWakeWord(newWake: String) {
        val trimmed = newWake.trim()
        if (trimmed.isNotEmpty()) {
            prefs.wakeWord = trimmed
            _wakeWord.value = trimmed
            // If service is running, restart it to pick up new wake word
            if (_isBackgroundServiceRunning.value) {
                toggleBackgroundWakeWord(true, getApplication())
            }
        }
    }

    fun updateGrokApiKey(key: String) {
        prefs.grokApiKey = key.trim()
        _grokApiKey.value = key.trim()
        _grokTestStatus.value = null
    }

    fun updateGrokModel(model: String) {
        prefs.grokModel = model.trim()
        _grokModel.value = model.trim()
    }

    fun updateGrokPersona(persona: String) {
        prefs.grokPersona = persona
        _grokPersona.value = persona
    }

    fun toggleTts(enable: Boolean) {
        prefs.isTtsEnabled = enable
        _isTtsEnabled.value = enable
        if (!enable) ttsEngine.stop()
    }

    fun testGrokConnection() {
        val key = _grokApiKey.value.trim()
        if (key.isEmpty()) {
            _grokTestStatus.value = "Error: Please enter an xAI Grok API key first."
            return
        }

        _isGrokTesting.value = true
        _grokTestStatus.value = "Testing xAI Grok API connection..."

        viewModelScope.launch {
            try {
                val service = GrokApiClient.getService(prefs.grokBaseUrl)
                val testReq = GrokRequest(
                    model = _grokModel.value,
                    messages = listOf(
                        GrokMessage(role = "system", content = "You are Carlos AI."),
                        GrokMessage(role = "user", content = "Say 'Carlos AI online and ready!' in 6 words.")
                    ),
                    temperature = 0.3f
                )

                val auth = if (key.startsWith("Bearer ")) key else "Bearer $key"
                val response = withContext(Dispatchers.IO) {
                    service.createChatCompletion(auth, testReq)
                }

                if (response.isSuccessful) {
                    val reply = response.body()?.choices?.firstOrNull()?.message?.content?.trim()
                    _grokTestStatus.value = "Success! Grok response: \"$reply\""
                    if (prefs.isTtsEnabled) {
                        ttsEngine.speak("Grok AI is connected and ready to assist you!")
                    }
                } else {
                    _grokTestStatus.value = "API Error ${response.code()}: ${response.errorBody()?.string() ?: response.message()}"
                }
            } catch (e: Exception) {
                _grokTestStatus.value = "Connection failed: ${e.message}"
            } finally {
                _isGrokTesting.value = false
            }
        }
    }

    fun speakCurrentResponse() {
        if (_lastResponseText.value.isNotBlank()) {
            ttsEngine.speak(_lastResponseText.value)
        }
    }

    fun clearCommandLogs() {
        prefs.clearLogs()
        _commandLogs.value = emptyList()
    }

    fun refreshLogs() {
        _commandLogs.value = prefs.getCommandLogs()
    }

    override fun onCleared() {
        inAppListener.stopListening()
        ttsEngine.shutdown()
        super.onCleared()
    }
}
