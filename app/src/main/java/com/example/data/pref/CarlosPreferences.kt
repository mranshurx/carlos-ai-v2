package com.example.data.pref

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.CarlosActionType
import com.example.data.model.CarlosCommandLog
import org.json.JSONArray
import org.json.JSONObject

class CarlosPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("carlos_ai_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WAKE_WORD = "wake_word"
        private const val KEY_BG_LISTENING = "bg_listening_enabled"
        private const val KEY_GROK_API_KEY = "grok_api_key"
        private const val KEY_GROK_MODEL = "grok_model"
        private const val KEY_GROK_BASE_URL = "grok_base_url"
        private const val KEY_GROK_PERSONA = "grok_persona"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_TTS_RATE = "tts_rate"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_COMMAND_LOGS = "command_logs"

        const val DEFAULT_WAKE_WORD = "Hey Carlos"
        const val DEFAULT_GROK_MODEL = "grok-2-latest"
        const val DEFAULT_GROK_BASE_URL = "https://api.x.ai/v1/"
        const val DEFAULT_GROK_PERSONA = "You are Carlos, a hyper-intelligent, helpful, and loyal AI phone assistant. You control the user's Android phone with precision, executing commands like opening apps, WhatsApp messaging, calls, camera, and device settings. Keep spoken responses short, punchy, and confident."
    }

    var wakeWord: String
        get() = prefs.getString(KEY_WAKE_WORD, DEFAULT_WAKE_WORD) ?: DEFAULT_WAKE_WORD
        set(value) = prefs.edit().putString(KEY_WAKE_WORD, value.trim()).apply()

    var isBackgroundListeningEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_LISTENING, false)
        set(value) = prefs.edit().putBoolean(KEY_BG_LISTENING, value).apply()

    var grokApiKey: String
        get() = prefs.getString(KEY_GROK_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GROK_API_KEY, value.trim()).apply()

    var grokModel: String
        get() = prefs.getString(KEY_GROK_MODEL, DEFAULT_GROK_MODEL) ?: DEFAULT_GROK_MODEL
        set(value) = prefs.edit().putString(KEY_GROK_MODEL, value.trim()).apply()

    var grokBaseUrl: String
        get() = prefs.getString(KEY_GROK_BASE_URL, DEFAULT_GROK_BASE_URL) ?: DEFAULT_GROK_BASE_URL
        set(value) = prefs.edit().putString(KEY_GROK_BASE_URL, value.trim()).apply()

    var grokPersona: String
        get() = prefs.getString(KEY_GROK_PERSONA, DEFAULT_GROK_PERSONA) ?: DEFAULT_GROK_PERSONA
        set(value) = prefs.edit().putString(KEY_GROK_PERSONA, value).apply()

    var isTtsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()

    var ttsSpeechRate: Float
        get() = prefs.getFloat(KEY_TTS_RATE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_TTS_RATE, value).apply()

    var ttsPitch: Float
        get() = prefs.getFloat(KEY_TTS_PITCH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_TTS_PITCH, value).apply()

    fun saveCommandLog(log: CarlosCommandLog) {
        val currentLogs = getCommandLogs().toMutableList()
        currentLogs.add(0, log)
        // Keep last 40 logs
        val trimmed = if (currentLogs.size > 40) currentLogs.take(40) else currentLogs

        val jsonArray = JSONArray()
        for (item in trimmed) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("userInput", item.userInput)
                put("actionType", item.actionType.name)
                put("target", item.target)
                put("details", item.details)
                put("responseSpeech", item.responseSpeech)
                put("timestamp", item.timestamp)
                put("isSuccess", item.isSuccess)
                put("source", item.source)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_COMMAND_LOGS, jsonArray.toString()).apply()
    }

    fun getCommandLogs(): List<CarlosCommandLog> {
        val jsonString = prefs.getString(KEY_COMMAND_LOGS, null) ?: return emptyList()
        val list = mutableListOf<CarlosCommandLog>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val actionType = try {
                    CarlosActionType.valueOf(obj.getString("actionType"))
                } catch (e: Exception) {
                    CarlosActionType.UNKNOWN
                }
                list.add(
                    CarlosCommandLog(
                        id = obj.optString("id"),
                        userInput = obj.optString("userInput"),
                        actionType = actionType,
                        target = obj.optString("target"),
                        details = obj.optString("details"),
                        responseSpeech = obj.optString("responseSpeech"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isSuccess = obj.optBoolean("isSuccess", true),
                        source = obj.optString("source", "App")
                    )
                )
            }
        } catch (e: Exception) {
            // parsing error fallback
        }
        return list
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_COMMAND_LOGS).apply()
    }
}
