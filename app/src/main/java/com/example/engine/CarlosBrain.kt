package com.example.engine

import android.content.Context
import com.example.data.api.GrokApiClient
import com.example.data.model.CarlosActionType
import com.example.data.model.CarlosParsedAction
import com.example.data.model.GrokMessage
import com.example.data.model.GrokRequest
import com.example.data.pref.CarlosPreferences
import org.json.JSONObject

class CarlosBrain(private val context: Context) {

    private val prefs = CarlosPreferences(context)

    /**
     * Determines if a spoken phrase contains the wake word.
     * Also returns the command portion if the user uttered it in the same sentence
     * (e.g., "Hey Carlos, open WhatsApp" -> wakeWordMatched=true, command="open WhatsApp").
     */
    fun matchWakeWord(spokenText: String, configuredWakeWord: String): Pair<Boolean, String> {
        val lowerText = spokenText.trim().lowercase()
        val lowerWake = configuredWakeWord.trim().lowercase()

        // List of accepted wake word variations based on configured word
        val wakeVariations = mutableListOf(
            lowerWake,
            "carlos",
            "hey carlos",
            "ok carlos",
            "hi carlos",
            "hello carlos",
            "yo carlos"
        )
        if (!wakeVariations.contains(lowerWake) && lowerWake.isNotEmpty()) {
            wakeVariations.add(0, lowerWake)
        }

        for (wake in wakeVariations) {
            val idx = lowerText.indexOf(wake)
            if (idx != -1) {
                // Wake word matched! Extract any command that followed it
                val commandPart = lowerText.substring(idx + wake.length)
                    .trim()
                    .removePrefix(",")
                    .removePrefix(".")
                    .trim()
                return Pair(true, commandPart)
            }
        }

        return Pair(false, "")
    }

    /**
     * Parses and interprets the command using Grok AI if configured,
     * or instant local NLU rule matching if no key is present or offline.
     */
    suspend fun processCommand(userInput: String): CarlosParsedAction {
        val cleanInput = userInput.trim()
        if (cleanInput.isEmpty()) {
            return CarlosParsedAction(
                actionType = CarlosActionType.CONVERSATIONAL,
                speech = "I didn't catch that. How can I help you?",
                thought = "Empty input"
            )
        }

        // 1. Try instant high-confidence local regex match
        val instantAction = matchInstantLocalAction(cleanInput)
        if (instantAction != null) {
            return instantAction
        }

        // 2. If Grok API key is provided, use Grok AI reasoning
        val apiKey = prefs.grokApiKey
        if (apiKey.isNotBlank()) {
            try {
                val grokResult = queryGrokAi(cleanInput, apiKey)
                if (grokResult != null) {
                    return grokResult
                }
            } catch (e: Exception) {
                // Fallback to local heuristic
            }
        }

        // 3. Fallback to advanced local semantic heuristic
        return fallbackLocalSemanticParser(cleanInput)
    }

    private fun matchInstantLocalAction(input: String): CarlosParsedAction? {
        val lower = input.lowercase().trim()

        // 1. Alarm: "set a alarm at 7:30 am", "set an alarm at 6 am", "set alarm for 7:00", "wake me up at 6 am", "alarm at 8"
        val isAlarmQuery = lower.contains("alarm") || lower.contains("wake me up")
        if (isAlarmQuery) {
            val alarmMatch = Regex("(?:set\\s+(?:a|an)?\\s*alarm|alarm|wake\\s+me\\s+up)\\s*(?:at|for)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower)
            if (alarmMatch != null) {
                var hour = alarmMatch.groupValues[1].toIntOrNull() ?: 7
                val minute = alarmMatch.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
                val amPm = alarmMatch.groupValues[3].lowercase()

                if (amPm == "pm" && hour < 12) {
                    hour += 12
                } else if (amPm == "am" && hour == 12) {
                    hour = 0
                }

                val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                val displayAmPm = if (hour < 12) "AM" else "PM"
                val displayMinute = String.format(java.util.Locale.US, "%02d", minute)
                val timeStr = "$displayHour:$displayMinute $displayAmPm"

                return CarlosParsedAction(
                    actionType = CarlosActionType.SET_ALARM,
                    target = timeStr,
                    params = mapOf(
                        "hour" to hour.toString(),
                        "minute" to minute.toString(),
                        "time" to timeStr
                    ),
                    speech = "Alarm set for $timeStr."
                )
            } else {
                return CarlosParsedAction(
                    actionType = CarlosActionType.SET_ALARM,
                    target = "7:00 AM",
                    params = mapOf("hour" to "7", "minute" to "0"),
                    speech = "Setting alarm."
                )
            }
        }

        // 2. WhatsApp messaging: "send a whatsapp message to [name]", "send whatsapp to [name] saying [text]", "whatsapp [name] [text]", "open whatsapp"
        if (lower.contains("whatsapp")) {
            if (lower == "open whatsapp" || lower == "launch whatsapp" || lower == "whatsapp") {
                return CarlosParsedAction(
                    actionType = CarlosActionType.OPEN_APP,
                    target = "whatsapp",
                    speech = "Opening WhatsApp."
                )
            }

            val whatsappDetailMatch = Regex("(?:send\\s+(?:a\\s+)?(?:whatsapp\\s+)?message\\s+(?:to\\s+)?|send\\s+whatsapp\\s+(?:to\\s+)?|whatsapp\\s+(?:message\\s+to\\s+)?|message\\s+)([^\\s]+)\\s*(?:saying|that)?\\s*(.*)").find(lower)

            if (whatsappDetailMatch != null) {
                val target = whatsappDetailMatch.groupValues[1].trim()
                val message = whatsappDetailMatch.groupValues[2].trim().ifEmpty { "Hello from Carlos AI" }
                val targetDisplay = target.replaceFirstChar { it.uppercase() }
                return CarlosParsedAction(
                    actionType = CarlosActionType.SEND_WHATSAPP,
                    target = target,
                    params = mapOf("phone" to target, "recipient" to target, "message" to message),
                    speech = "Opening WhatsApp for $targetDisplay."
                )
            }

            return CarlosParsedAction(
                actionType = CarlosActionType.OPEN_APP,
                target = "whatsapp",
                speech = "Opening WhatsApp."
            )
        }

        // Camera: "open camera", "take photo", "take a picture"
        if (lower.contains("camera") || lower.contains("take photo") || lower.contains("take a picture") || lower.contains("take selfie")) {
            return CarlosParsedAction(
                actionType = CarlosActionType.OPEN_CAMERA,
                speech = "Opening camera."
            )
        }

        // Gallery: "open gallery", "open photos", "my gallery", "show pictures"
        if (lower.contains("gallery") || lower.contains("photos") || lower.contains("my photos")) {
            return CarlosParsedAction(
                actionType = CarlosActionType.OPEN_GALLERY,
                speech = "Opening gallery."
            )
        }

        // Calls: "make a call to [name/number]", "call [number/name]"
        val callMatch = Regex("(?:make a call to|call)\\s+([0-9+*#]+|[a-zA-Z\\s]+)?").find(lower)
        if (callMatch != null) {
            val recipient = callMatch.groupValues[1].orEmpty().trim()
            val recipientDisplay = recipient.replaceFirstChar { it.uppercase() }
            return CarlosParsedAction(
                actionType = CarlosActionType.MAKE_CALL,
                target = recipient,
                params = mapOf("recipient" to recipient),
                speech = if (recipient.isNotEmpty()) "Calling $recipientDisplay." else "Opening phone dialer."
            )
        }

        // Flashlight / Torch: "turn on flashlight", "turn off my flashlight", "turn off my flashligjt", etc.
        val isFlashQuery = lower.contains("flashlight") ||
                lower.contains("flashligjt") ||
                lower.contains("torch") ||
                lower.contains("flash light") ||
                lower.contains("torchlight") ||
                (lower.contains("light") && (lower.contains("turn") || lower.contains("switch") || lower.contains("off") || lower.contains("on")))

        if (isFlashQuery) {
            val state = if (lower.contains("off") || lower.contains("disable") || lower.contains("stop") || lower.contains("kill") || lower.contains("close") || lower.contains("shut")) "off" else "on"
            return CarlosParsedAction(
                actionType = CarlosActionType.TOGGLE_FLASHLIGHT,
                target = state,
                speech = if (state == "on") "Turning flashlight on." else "Turning flashlight off."
            )
        }

        // Battery: "check battery", "battery status", "how much battery"
        if (lower.contains("battery")) {
            return CarlosParsedAction(
                actionType = CarlosActionType.CHECK_BATTERY,
                speech = "Checking battery."
            )
        }

        // Specific app: "open [app]", "launch [app]"
        val openAppMatch = Regex("(?:open|launch)\\s+(?:my\\s+)?([a-zA-Z0-9\\s]+)").find(lower)
        if (openAppMatch != null) {
            val appName = openAppMatch.groupValues[1].trim()
            if (appName.isNotEmpty() && appName != "settings") {
                return CarlosParsedAction(
                    actionType = CarlosActionType.OPEN_APP,
                    target = appName,
                    speech = "Opening $appName."
                )
            }
        }

        // Settings: "open settings", "wifi", "bluetooth"
        if (lower.contains("settings") || lower.contains("wifi") || lower.contains("bluetooth") || lower.contains("volume")) {
            val category = when {
                lower.contains("wifi") -> "wifi"
                lower.contains("bluetooth") -> "bluetooth"
                lower.contains("volume") || lower.contains("sound") -> "sound"
                lower.contains("display") || lower.contains("brightness") -> "display"
                else -> "general"
            }
            return CarlosParsedAction(
                actionType = CarlosActionType.DEVICE_SETTINGS,
                target = category,
                speech = "Opening $category settings."
            )
        }

        return null
    }

    private suspend fun queryGrokAi(userInput: String, apiKey: String): CarlosParsedAction? {
        val service = GrokApiClient.getService(prefs.grokBaseUrl)
        val systemPrompt = """
            ${prefs.grokPersona}
            You are Carlos, controlling the user's Android phone.
            Analyze the user's command and output STRICT JSON in this exact structure:
            {
              "thought": "brief reason",
              "action": "OPEN_APP" | "MAKE_CALL" | "SEND_WHATSAPP" | "OPEN_CAMERA" | "OPEN_GALLERY" | "TOGGLE_FLASHLIGHT" | "CHECK_BATTERY" | "DEVICE_SETTINGS" | "CONVERSATIONAL",
              "target": "app name, phone number, or target",
              "params": {"message": "...", "phone": "..."},
              "speech": "What Carlos should say to user aloud (short, natural, max 15 words)"
            }
            Do NOT include markdown formatting or backticks around the JSON.
        """.trimIndent()

        val request = GrokRequest(
            model = prefs.grokModel,
            messages = listOf(
                GrokMessage(role = "system", content = systemPrompt),
                GrokMessage(role = "user", content = userInput)
            ),
            temperature = 0.2f
        )

        val authHeader = if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey"
        val response = service.createChatCompletion(authHeader, request)

        if (response.isSuccessful) {
            val content = response.body()?.choices?.firstOrNull()?.message?.content?.trim()
            if (!content.isNullOrEmpty()) {
                val cleanedJson = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                return try {
                    val json = JSONObject(cleanedJson)
                    val actionStr = json.optString("action", "CONVERSATIONAL")
                    val actionType = try {
                        CarlosActionType.valueOf(actionStr)
                    } catch (e: Exception) {
                        CarlosActionType.CONVERSATIONAL
                    }
                    val target = json.optString("target", "")
                    val speech = json.optString("speech", "Right away.")
                    val thought = json.optString("thought", "")

                    val paramsMap = mutableMapOf<String, String>()
                    val paramsObj = json.optJSONObject("params")
                    if (paramsObj != null) {
                        val keys = paramsObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            paramsMap[k] = paramsObj.getString(k)
                        }
                    }

                    CarlosParsedAction(
                        actionType = actionType,
                        target = target,
                        params = paramsMap,
                        speech = speech,
                        thought = thought
                    )
                } catch (e: Exception) {
                    CarlosParsedAction(
                        actionType = CarlosActionType.CONVERSATIONAL,
                        speech = content.take(120),
                        thought = "Raw text from Grok"
                    )
                }
            }
        }
        return null
    }

    private fun fallbackLocalSemanticParser(input: String): CarlosParsedAction {
        val lower = input.lowercase().trim()
        return when {
            lower.isEmpty() || lower == "hey carlos" || lower == "carlos" || lower == "ok carlos" -> {
                CarlosParsedAction(
                    actionType = CarlosActionType.CONVERSATIONAL,
                    speech = ""
                )
            }
            lower.contains("hello") || lower.contains("hi carlos") -> {
                CarlosParsedAction(
                    actionType = CarlosActionType.CONVERSATIONAL,
                    speech = "Hello."
                )
            }
            else -> {
                CarlosParsedAction(
                    actionType = CarlosActionType.CONVERSATIONAL,
                    speech = "Command not recognized.",
                    thought = "Unrecognized command"
                )
            }
        }
    }
}
