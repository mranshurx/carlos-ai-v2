package com.example.data.model

enum class CarlosActionType(val displayName: String, val iconDescription: String) {
    OPEN_APP("Open App", "Launches installed phone apps"),
    MAKE_CALL("Phone Call", "Dials or calls a phone number"),
    SEND_WHATSAPP("WhatsApp Message", "Sends a WhatsApp chat message"),
    OPEN_CAMERA("Open Camera", "Opens device camera"),
    OPEN_GALLERY("Open Gallery", "Opens device photo gallery"),
    TOGGLE_FLASHLIGHT("Flashlight", "Turns device torch on or off"),
    CHECK_BATTERY("Battery Status", "Checks battery level and charging"),
    DEVICE_SETTINGS("System Settings", "Opens device settings (Wi-Fi, Bluetooth, etc.)"),
    CONVERSATIONAL("Carlos AI Thought", "Natural language response via Grok AI"),
    UNKNOWN("Command", "General phone command")
}

enum class CarlosState {
    IDLE,
    LISTENING_WAKE_WORD,
    LISTENING_COMMAND,
    PROCESSING,
    SPEAKING,
    ERROR
}

data class CarlosCommandLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val userInput: String,
    val actionType: CarlosActionType,
    val target: String = "",
    val details: String = "",
    val responseSpeech: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true,
    val source: String = "App" // "App" or "Background Wake Word"
)

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val isSystemApp: Boolean = false
)

// Grok API Models (xAI compatible)
data class GrokMessage(
    val role: String,
    val content: String
)

data class GrokRequest(
    val model: String = "grok-2-latest",
    val messages: List<GrokMessage>,
    val temperature: Float = 0.3f
)

data class GrokResponse(
    val id: String?,
    val model: String?,
    val choices: List<GrokChoice>?
)

data class GrokChoice(
    val index: Int?,
    val message: GrokMessage?
)

data class CarlosParsedAction(
    val actionType: CarlosActionType,
    val target: String = "",
    val params: Map<String, String> = emptyMap(),
    val speech: String,
    val thought: String = ""
)
