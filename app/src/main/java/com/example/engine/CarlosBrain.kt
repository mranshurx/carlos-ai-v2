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

        // WhatsApp messaging: "send message to [phone/name] saying [text]" or "whatsapp [text]"
        if (lower.contains("whatsapp")) {
            if (lower == "open whatsapp" || lower == "launch whatsapp") {
                return CarlosParsedAction(
                    actionType = CarlosActionType.OPEN_APP,
                    target = "whatsapp",
                    speech = "Opening WhatsApp."
                )
            }
            // Parse message
            val sendMatch = Regex("(?:send|give)\\s+(?:a\\s+)?message\\s+(?:to\\s+)?([^\\s]+)?\\s*(?:saying|that)?\\s*(.*)").find(lower)
            if (sendMatch != null) {
                val target = sendMatch.groupValues[1].orEmpty()
                val message = sendMatch.groupValues[2].ifEmpty { "Hello from Carlos AI" }
                return CarlosParsedAction(
                    actionType = CarlosActionType.SEND_WHATSAPP,
                    target = target,
                    params = mapOf("phone" to target, "message" to message),
                    speech = "Sending WhatsApp message."
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

        // Calls: "make a call", "call [number/name]"
        val callMatch = Regex("(?:make a call to|call)\\s+([0-9+]+|[a-zA-Z]+)?").find(lower)
        if (callMatch != null) {
            val recipient = callMatch.groupValues[1].orEmpty().trim()
            return CarlosParsedAction(
                actionType = CarlosActionType.MAKE_CALL,
                target = recipient,
                params = mapOf("recipient" to recipient),
                speech = if (recipient.isNotEmpty()) "Calling $recipient." else "Opening phone dialer."
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
                speech = "Checking your battery status."
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
        val lower = input.lowercase()
        return when {
            lower.contains("who are you") || lower.contains("what is your name") -> {
                CarlosParsedAction(
                    actionType = CarlosActionType.CONVERSATIONAL,
                    speech = "I am Carlos, your voice AI assistant. I can open apps, make calls, send WhatsApp messages, and control your phone!"
                )
            }
            lower.contains("hello") || lower.contains("hi carlos") || lower == "carlos" -> {
                CarlosParsedAction(
                    actionType = CarlosActionType.CONVERSATIONAL,
                    speech = "Hello! Carlos at your service. What should I do for you?"
                )
            }
            lower.contains("what can you do") || lower.contains("help") -> {
                CarlosParsedAction(
                    actionType = CarlosActionType.CONVERSATIONAL,
                    speech = "You can tell me to open WhatsApp, make a call, open camera, turn on flashlight, check battery, or launch any installed app!"
                )
            }
            else -> {
                CarlosParsedAction(
                    actionType = CarlosActionType.CONVERSATIONAL,
                    speech = "I heard '$input'. Say 'Help' to see everything I can control on your phone.",
                    thought = "Unrecognized command"
                )
            }
        }
    }
}
