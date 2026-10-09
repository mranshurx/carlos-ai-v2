package com.example.device

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.CarlosActionType
import com.example.data.model.InstalledAppInfo

object DeviceActionExecutor {

    private var flashlightManager: FlashlightManager? = null

    fun getFlashlightManager(context: Context): FlashlightManager {
        if (flashlightManager == null) {
            flashlightManager = FlashlightManager(context.applicationContext)
        }
        return flashlightManager!!
    }

    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        val list = mutableListOf<InstalledAppInfo>()

        for (resolveInfo in resolveInfoList) {
            val label = resolveInfo.loadLabel(pm).toString()
            val packageName = resolveInfo.activityInfo.packageName
            val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            list.add(InstalledAppInfo(label = label, packageName = packageName, isSystemApp = isSystem))
        }

        return list.sortedBy { it.label.lowercase() }
    }

    fun openApp(context: Context, appQuery: String): Pair<Boolean, String> {
        val pm = context.packageManager
        val cleanQuery = appQuery.trim().lowercase()

        // 1. Direct package matches for popular apps
        val knownPackages = mapOf(
            "whatsapp" to "com.whatsapp",
            "whatsapp business" to "com.whatsapp.w4b",
            "camera" to "com.google.android.GoogleCamera",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "gmail" to "com.google.android.gm",
            "instagram" to "com.instagram.android",
            "telegram" to "org.telegram.messenger",
            "facebook" to "com.facebook.katana",
            "settings" to "com.android.settings",
            "clock" to "com.google.android.deskclock",
            "calculator" to "com.google.android.calculator"
        )

        // Try direct launch for camera & gallery first
        if (cleanQuery.contains("camera")) {
            return openCamera(context)
        }
        if (cleanQuery.contains("gallery") || cleanQuery == "photos") {
            return openGallery(context)
        }

        // Try known package
        for ((key, pkg) in knownPackages) {
            if (cleanQuery.contains(key)) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return Pair(true, "Opened ${key.replaceFirstChar { it.uppercase() }}")
                }
            }
        }

        // 2. Search installed apps by label
        val installedApps = getInstalledApps(context)
        val exactMatch = installedApps.firstOrNull { it.label.lowercase() == cleanQuery }
        val containsMatch = installedApps.firstOrNull {
            it.label.lowercase().contains(cleanQuery) || cleanQuery.contains(it.label.lowercase())
        }

        val targetApp = exactMatch ?: containsMatch
        if (targetApp != null) {
            val launchIntent = pm.getLaunchIntentForPackage(targetApp.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return Pair(true, "Opened ${targetApp.label}")
            }
        }

        // Fallback: If not found, search in Play Store or report
        return Pair(false, "Could not find an installed app matching '$appQuery'")
    }

    fun sendWhatsAppMessage(context: Context, recipientOrPhone: String, message: String): Pair<Boolean, String> {
        return try {
            var finalNumber: String? = null
            var contactName: String? = null

            if (recipientOrPhone.any { it.isLetter() }) {
                contactName = recipientOrPhone.trim()
                finalNumber = findContactPhoneNumber(context, contactName)
            } else {
                finalNumber = recipientOrPhone
            }

            val cleanPhone = finalNumber?.replace("[^0-9+]".toRegex(), "").orEmpty()
            val encodedMessage = Uri.encode(message.ifEmpty { "Hello from Carlos AI" })

            val uri = if (cleanPhone.isNotEmpty()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=$encodedMessage")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.whatsapp")
            }

            // Check if WhatsApp package is available
            val pm = context.packageManager
            val activities = pm.queryIntentActivities(intent, 0)
            if (activities.isNotEmpty()) {
                context.startActivity(intent)
                val status = if (!contactName.isNullOrEmpty() && cleanPhone.isNotEmpty()) {
                    "Opening WhatsApp for $contactName."
                } else if (!contactName.isNullOrEmpty()) {
                    "Opening WhatsApp for $contactName."
                } else {
                    "Opening WhatsApp."
                }
                Pair(true, status)
            } else {
                // Try WhatsApp Business or browser fallback
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                Pair(true, "Opening WhatsApp.")
            }
        } catch (e: Exception) {
            Pair(false, "Failed to send WhatsApp message: ${e.message}")
        }
    }

    fun setAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        message: String = "Carlos Alarm",
        skipUi: Boolean = false
    ): Pair<Boolean, String> {
        return try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            val displayMinute = String.format(java.util.Locale.US, "%02d", minute)
            Pair(true, "Alarm set for $displayHour:$displayMinute $amPm.")
        } catch (e: Exception) {
            try {
                val clockIntent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(clockIntent)
                Pair(true, "Opening clock alarms.")
            } catch (ex: Exception) {
                Pair(false, "Could not set alarm: ${ex.message}")
            }
        }
    }

    fun findContactPhoneNumber(context: Context, nameQuery: String): String? {
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasContactsPermission) {
            Log.w("DeviceActionExecutor", "READ_CONTACTS permission not granted. Cannot lookup $nameQuery")
            return null
        }

        val contentResolver = context.contentResolver
        val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )

        var bestMatchNumber: String? = null
        var bestMatchExact = false

        try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

                if (numberIndex != -1 && nameIndex != -1) {
                    val targetLower = nameQuery.trim().lowercase()

                    while (cursor.moveToNext()) {
                        val contactName = cursor.getString(nameIndex).orEmpty()
                        val contactNameLower = contactName.trim().lowercase()
                        val phoneNumber = cursor.getString(numberIndex)

                        if (contactNameLower == targetLower) {
                            bestMatchNumber = phoneNumber
                            bestMatchExact = true
                            break
                        } else if (!bestMatchExact && (contactNameLower.contains(targetLower) || targetLower.contains(contactNameLower))) {
                            bestMatchNumber = phoneNumber
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DeviceActionExecutor", "Error querying contacts: ${e.message}")
        }

        return bestMatchNumber
    }

    fun makePhoneCall(context: Context, recipient: String): Pair<Boolean, String> {
        var finalNumber: String? = null
        var contactNameMatched: String? = null

        // If recipient contains any letters, treat it as a contact name lookup!
        if (recipient.any { it.isLetter() }) {
            finalNumber = findContactPhoneNumber(context, recipient)
            if (finalNumber != null) {
                contactNameMatched = recipient
            }
        } else {
            finalNumber = recipient.replace("[^0-9+*#]".toRegex(), "")
        }

        return try {
            if (!finalNumber.isNullOrEmpty()) {
                val cleanNumber = finalNumber.replace("[^0-9+*#]".toRegex(), "")
                val hasCallPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED

                val intent = if (hasCallPermission) {
                    Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)

                val displayTarget = contactNameMatched ?: cleanNumber
                val status = if (hasCallPermission) "Calling $displayTarget" else "Opening dialer for $displayTarget"
                Pair(true, status)
            } else {
                if (recipient.any { it.isLetter() }) {
                    Pair(false, "Could not find a contact named '$recipient' in your contacts book.")
                } else {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Pair(true, "Opening phone dialer")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Could not place call: ${e.message}")
        }
    }

    fun openCamera(context: Context): Pair<Boolean, String> {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Camera opened.")
        } catch (e: Exception) {
            // Fallback
            try {
                val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                Pair(true, "Camera opened.")
            } catch (ex: Exception) {
                Pair(false, "Could not open camera: ${ex.message}")
            }
        }
    }

    fun openGallery(context: Context): Pair<Boolean, String> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Gallery opened.")
        } catch (e: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_GALLERY)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                Pair(true, "Gallery opened.")
            } catch (ex: Exception) {
                Pair(false, "Could not open gallery: ${ex.message}")
            }
        }
    }

    fun toggleFlashlight(context: Context, desiredState: Boolean? = null): Pair<Boolean, String> {
        return getFlashlightManager(context).toggleFlashlight(desiredState)
    }

    fun getBatteryStatus(context: Context): Pair<Boolean, String> {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = bm.isCharging
            val status = if (isCharging) {
                "Battery is at $level% and currently charging."
            } else {
                "Battery is at $level%."
            }
            Pair(true, status)
        } catch (e: Exception) {
            Pair(false, "Could not read battery: ${e.message}")
        }
    }

    fun openSettings(context: Context, category: String = "general"): Pair<Boolean, String> {
        return try {
            val action = when (category.lowercase()) {
                "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
                "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
                "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Opened $category settings.")
        } catch (e: Exception) {
            Pair(false, "Could not open settings: ${e.message}")
        }
    }

    fun executeParsedAction(
        context: Context,
        actionType: CarlosActionType,
        target: String,
        params: Map<String, String>
    ): Pair<Boolean, String> {
        return when (actionType) {
            CarlosActionType.OPEN_APP -> openApp(context, target.ifEmpty { params["app"] ?: "" })
            CarlosActionType.MAKE_CALL -> makePhoneCall(context, target.ifEmpty { params["number"] ?: params["recipient"] ?: "" })
            CarlosActionType.SEND_WHATSAPP -> {
                val phone = params["phone"] ?: params["number"] ?: target
                val message = params["message"] ?: params["text"] ?: "Hello from Carlos AI"
                sendWhatsAppMessage(context, phone, message)
            }
            CarlosActionType.OPEN_CAMERA -> openCamera(context)
            CarlosActionType.OPEN_GALLERY -> openGallery(context)
            CarlosActionType.TOGGLE_FLASHLIGHT -> {
                val state = when (target.lowercase()) {
                    "on", "enable" -> true
                    "off", "disable" -> false
                    else -> null
                }
                toggleFlashlight(context, state)
            }
            CarlosActionType.SET_ALARM -> {
                val hour = params["hour"]?.toIntOrNull() ?: 7
                val minute = params["minute"]?.toIntOrNull() ?: 0
                val message = params["message"] ?: target.ifEmpty { "Carlos Alarm" }
                setAlarm(context, hour, minute, message)
            }
            CarlosActionType.CHECK_BATTERY -> getBatteryStatus(context)
            CarlosActionType.DEVICE_SETTINGS -> openSettings(context, target.ifEmpty { "general" })
            CarlosActionType.CONVERSATIONAL, CarlosActionType.UNKNOWN -> Pair(true, "")
        }
    }
}
