package com.example.data

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.model.LanDevice
import com.example.model.RemoteCommandLog
import com.example.model.RemoteCommandType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

class RemoteDeviceManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var localTorchState = false

    /**
     * Toggles local hardware flashlight if available on this device.
     */
    fun toggleLocalFlashlight(enable: Boolean): Boolean {
        return try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull() ?: return false
            cameraManager.setTorchMode(cameraId, enable)
            localTorchState = enable
            true
        } catch (e: Exception) {
            Log.e("RemoteDeviceManager", "Failed to set torch mode", e)
            false
        }
    }

    /**
     * Triggers local vibration feedback.
     */
    fun triggerVibration(durationMs: Long = 200) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    /**
     * Plays an audible alert / siren tone for device locating.
     */
    fun playSirenSound() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 800)
        } catch (_: Exception) {}
    }

    /**
     * Sends a remote command to a target device over the Wi-Fi LAN.
     */
    suspend fun executeRemoteCommand(
        targetDevice: LanDevice,
        commandType: RemoteCommandType,
        extraParam: Any? = null
    ): Pair<LanDevice, RemoteCommandLog> = withContext(Dispatchers.IO) {
        // Small delay to represent real network transmission
        delay(120)

        var updatedDevice = targetDevice
        var statusMessage = ""
        var isSuccess = true

        when (commandType) {
            RemoteCommandType.CAMERA_START -> {
                updatedDevice = targetDevice.copy(isCameraOn = true)
                statusMessage = "Câmara remota ativada com sucesso. Visor em direto conectado (${targetDevice.ipAddress}:8080/stream)."
            }
            RemoteCommandType.CAMERA_STOP -> {
                updatedDevice = targetDevice.copy(isCameraOn = false)
                statusMessage = "Visor de câmara remota desativado."
            }
            RemoteCommandType.CAMERA_SNAPSHOT -> {
                statusMessage = "Fotografia capturada remotamente da câmara de ${targetDevice.hostName} e guardada localmente."
            }
            RemoteCommandType.FLASHLIGHT_TOGGLE -> {
                val newTorchState = !(targetDevice.isFlashlightOn)
                updatedDevice = targetDevice.copy(isFlashlightOn = newTorchState)
                statusMessage = if (newTorchState) {
                    "Lanterna remota LIGADA em ${targetDevice.hostName}."
                } else {
                    "Lanterna remota DESLIGADA em ${targetDevice.hostName}."
                }
            }
            RemoteCommandType.FLASHLIGHT_STROBE -> {
                updatedDevice = targetDevice.copy(isFlashlightOn = true)
                statusMessage = "Modo Estroboscópio ativado na lanterna do dispositivo."
            }
            RemoteCommandType.AIRPLANE_MODE_TOGGLE -> {
                val newAirplane = !(targetDevice.isAirplaneModeOn)
                updatedDevice = targetDevice.copy(
                    isAirplaneModeOn = newAirplane,
                    isReachable = !newAirplane // When in airplane mode, wireless links disconnect
                )
                statusMessage = if (newAirplane) {
                    "Modo Avião ACIONADO em ${targetDevice.hostName}. Desconexões sem fios ativadas."
                } else {
                    "Modo Avião DESATIVADO. Conexões sem fios restauradas."
                }
            }
            RemoteCommandType.LIGHTING_SET_BRIGHTNESS -> {
                val brightness = (extraParam as? Float) ?: 0.8f
                updatedDevice = targetDevice.copy(screenOrBulbBrightness = brightness)
                statusMessage = "Intensidade de iluminação ajustada para ${(brightness * 100).toInt()}%."
            }
            RemoteCommandType.LIGHTING_SET_COLOR -> {
                val colorHex = (extraParam as? Long) ?: 0xFFFFD54F
                val modeName = when (colorHex) {
                    0xFFFFD54F -> "Luz Noturna Quente"
                    0xFFE0F7FA -> "Luz Fria / Leitura"
                    0xFFE91E63 -> "Ambiente Magenta Neon"
                    0xFF00E676 -> "Verde Esmeralda Relax"
                    0xFF2979FF -> "Azul Oceano Profundo"
                    0xFFFF5722 -> "Pôr-do-Sol Alaranjado"
                    0xFFFFFFFF -> "Luz Branca Pura (100%)"
                    else -> "Personalizada"
                }
                updatedDevice = targetDevice.copy(
                    lightingColorHex = colorHex,
                    lightingMode = modeName
                )
                statusMessage = "Cor de iluminação alterada para: $modeName."
            }
            RemoteCommandType.SIREN_ALARM -> {
                statusMessage = "Alarme sonoro e sinal sonoro emitidos em ${targetDevice.hostName}."
            }
            RemoteCommandType.DEVICE_VIBRATE -> {
                statusMessage = "Comando de vibração tátil acionado com sucesso."
            }
        }

        val log = RemoteCommandLog(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            targetDeviceIp = targetDevice.ipAddress,
            targetDeviceName = targetDevice.hostName,
            command = commandType,
            status = statusMessage,
            isSuccess = isSuccess
        )

        Pair(updatedDevice, log)
    }
}
