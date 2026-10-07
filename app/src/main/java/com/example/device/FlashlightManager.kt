package com.example.device

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FlashlightManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isScreenTorchActive = MutableStateFlow(false)
    val isScreenTorchActive: StateFlow<Boolean> = _isScreenTorchActive.asStateFlow()

    private var verifiedFlashCameraId: String? = null
    private var isTorchCallbackRegistered = false

    init {
        findVerifiedFlashCamera()
        registerTorchCallback()
    }

    private fun findVerifiedFlashCamera(): String? {
        if (cameraManager == null) return null

        try {
            var bestBackCameraId: String? = null
            var anyFlashCameraId: String? = null

            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

                if (hasFlash) {
                    val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                        bestBackCameraId = id
                        break
                    }
                    if (anyFlashCameraId == null) {
                        anyFlashCameraId = id
                    }
                }
            }

            verifiedFlashCameraId = bestBackCameraId ?: anyFlashCameraId
            Log.d("FlashlightManager", "Verified flash camera ID: $verifiedFlashCameraId")
        } catch (e: Exception) {
            Log.e("FlashlightManager", "Error querying camera characteristics: ${e.message}")
        }

        return verifiedFlashCameraId
    }

    private fun registerTorchCallback() {
        if (cameraManager == null || isTorchCallbackRegistered) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        super.onTorchModeChanged(cameraId, enabled)
                        if (cameraId == verifiedFlashCameraId || verifiedFlashCameraId == null) {
                            _isTorchOn.value = enabled
                        }
                    }

                    override fun onTorchModeUnavailable(cameraId: String) {
                        super.onTorchModeUnavailable(cameraId)
                        if (cameraId == verifiedFlashCameraId) {
                            _isTorchOn.value = false
                        }
                    }
                }, null)
                isTorchCallbackRegistered = true
            }
        } catch (e: Exception) {
            Log.e("FlashlightManager", "Error registering torch callback: ${e.message}")
        }
    }

    fun hasHardwareFlash(): Boolean {
        if (verifiedFlashCameraId == null) {
            findVerifiedFlashCamera()
        }
        return verifiedFlashCameraId != null
    }

    /**
     * Toggles or sets the flashlight.
     * If desiredState is null, inverts current state.
     * Returns Pair(success, message).
     */
    fun toggleFlashlight(desiredState: Boolean? = null): Pair<Boolean, String> {
        val targetState = desiredState ?: (!_isTorchOn.value && !_isScreenTorchActive.value)

        // If turning off, guarantee all flashlights (hardware and screen) are disabled immediately
        if (!targetState) {
            _isScreenTorchActive.value = false
            _isTorchOn.value = false

            if (cameraManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val camId = verifiedFlashCameraId ?: findVerifiedFlashCamera()
                    if (camId != null) {
                        cameraManager.setTorchMode(camId, false)
                    }
                } catch (e: Exception) {
                    try {
                        for (id in cameraManager.cameraIdList) {
                            try { cameraManager.setTorchMode(id, false) } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }
            }
            return Pair(true, "Flashlight turned off")
        }

        // 1. Try hardware flash first
        val camId = verifiedFlashCameraId ?: findVerifiedFlashCamera()

        if (cameraManager != null && camId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                cameraManager.setTorchMode(camId, targetState)
                _isTorchOn.value = targetState
                _isScreenTorchActive.value = false
                val msg = if (targetState) "Flashlight turned on" else "Flashlight turned off"
                return Pair(true, msg)
            } catch (e: CameraAccessException) {
                Log.w("FlashlightManager", "Camera access exception: ${e.message}. Trying screen torch fallback.")
            } catch (e: IllegalArgumentException) {
                Log.w("FlashlightManager", "Camera ID doesn't support flash: ${e.message}. Re-scanning cameras.")
                // Try scanning again
                verifiedFlashCameraId = null
                val recheckedId = findVerifiedFlashCamera()
                if (recheckedId != null && recheckedId != camId) {
                    try {
                        cameraManager.setTorchMode(recheckedId, targetState)
                        _isTorchOn.value = targetState
                        _isScreenTorchActive.value = false
                        val msg = if (targetState) "Flashlight turned on" else "Flashlight turned off"
                        return Pair(true, msg)
                    } catch (ex: Exception) {
                        // fallback to screen torch
                    }
                }
            } catch (e: Exception) {
                Log.e("FlashlightManager", "Unexpected torch error: ${e.message}")
            }
        }

        // 2. Hardware flash not available or failed (e.g. on emulator or device without back flash)
        // Fallback to on-screen bright white torch!
        _isScreenTorchActive.value = targetState
        _isTorchOn.value = targetState
        val msg = if (targetState) "Hardware flash not detected. Screen flashlight turned on." else "Flashlight turned off."
        return Pair(true, msg)
    }

    fun setScreenTorch(active: Boolean) {
        _isScreenTorchActive.value = active
        if (!active && !_isTorchOn.value) {
            // turned off
        }
    }
}
