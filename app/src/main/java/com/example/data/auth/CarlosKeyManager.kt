package com.example.data.auth

import android.content.Context
import android.util.Log
import com.example.data.pref.CarlosPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface KeyValidationResult {
    data class Success(val key: String) : KeyValidationResult
    data class InvalidKey(val message: String) : KeyValidationResult
    data class NetworkError(val message: String) : KeyValidationResult
}

class CarlosKeyManager(private val context: Context) {

    private val prefs = CarlosPreferences(context)

    companion object {
        const val PRIMARY_KEY_URL = "https://raw.githubusercontent.com/mranshurx/carlos-ai-v2/refs/heads/main/key.txt"
        const val FALLBACK_KEY_URL = "https://raw.githubusercontent.com/mranshurx/carlos-ai-v2/main/key.txt"
        private const val TAG = "CarlosKeyManager"
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    var savedKey: String
        get() = prefs.savedUserKey
        set(value) {
            prefs.savedUserKey = value
        }

    fun clearSavedKey() {
        prefs.clearSavedKey()
    }

    /**
     * Fetches the real-time active key from GitHub online.
     * Uses cache-busting headers and query timestamp so GitHub never returns stale cached keys.
     */
    suspend fun fetchActiveKeysFromCloud(): Result<List<String>> = withContext(Dispatchers.IO) {
        val urls = listOf(
            "$PRIMARY_KEY_URL?nocache=${System.currentTimeMillis()}",
            "$FALLBACK_KEY_URL?nocache=${System.currentTimeMillis()}"
        )

        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .cacheControl(CacheControl.Builder().noCache().noStore().build())
                    .header("Cache-Control", "no-cache, no-store, max-age=0")
                    .header("Pragma", "no-cache")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()?.trim().orEmpty()
                    if (bodyString.isNotBlank()) {
                        // Support single key or multi-line keys
                        val keys = bodyString.lines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }

                        if (keys.isNotEmpty()) {
                            Log.d(TAG, "Successfully fetched ${keys.size} active key(s) from cloud.")
                            return@withContext Result.success(keys)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch key from $url: ${e.message}")
            }
        }

        Result.failure(Exception("Could not connect to online key server. Please check your internet connection."))
    }

    /**
     * Validates a candidate key against the live cloud key file.
     */
    suspend fun validateKeyOnline(candidateKey: String): KeyValidationResult {
        val cleanCandidate = candidateKey.trim()
        if (cleanCandidate.isBlank()) {
            return KeyValidationResult.InvalidKey("Access key cannot be empty. Please enter your key.")
        }

        val cloudKeysResult = fetchActiveKeysFromCloud()
        if (cloudKeysResult.isFailure) {
            val errorMsg = cloudKeysResult.exceptionOrNull()?.message
                ?: "Network error. Unable to verify key online."
            return KeyValidationResult.NetworkError(errorMsg)
        }

        val validKeys = cloudKeysResult.getOrNull().orEmpty()

        // Check if candidate matches any valid key (exact match after trimming)
        val isMatched = validKeys.any { it.equals(cleanCandidate, ignoreCase = false) }

        return if (isMatched) {
            // Save the verified key locally
            savedKey = cleanCandidate
            KeyValidationResult.Success(cleanCandidate)
        } else {
            // Key mismatch
            clearSavedKey()
            KeyValidationResult.InvalidKey("Invalid Key! The key you entered is incorrect or was updated by the developer.")
        }
    }

    /**
     * Checks if the currently saved key is still valid against the online cloud key.
     * Called in background every time the app opens.
     */
    suspend fun verifyExistingSavedKey(): KeyValidationResult {
        val currentKey = savedKey
        if (currentKey.isBlank()) {
            return KeyValidationResult.InvalidKey("No access key found. Please enter your key to unlock Carlos AI.")
        }

        val cloudKeysResult = fetchActiveKeysFromCloud()
        if (cloudKeysResult.isFailure) {
            // If offline, check if network error
            val errorMsg = cloudKeysResult.exceptionOrNull()?.message ?: "Network error"
            return KeyValidationResult.NetworkError(errorMsg)
        }

        val validKeys = cloudKeysResult.getOrNull().orEmpty()
        val isMatched = validKeys.any { it.equals(currentKey, ignoreCase = false) }

        return if (isMatched) {
            KeyValidationResult.Success(currentKey)
        } else {
            // Key has been changed online! Clear saved key and demand re-login
            clearSavedKey()
            KeyValidationResult.InvalidKey("Invalid Key. The access key was changed or expired online. Please enter the new key.")
        }
    }
}
