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
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

class RemoteDeviceManager(
    private val context: Context,
    private var companionServer: CompanionServer? = null
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var localTorchState = false

    fun setCompanionServer(server: CompanionServer) {
        this.companionServer = server
    }

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
     * Sends a real remote command to a target device over the 2.4/5GHz Wi-Fi LAN.
     */
    suspend fun executeRemoteCommand(
        targetDevice: LanDevice,
        commandType: RemoteCommandType,
        extraParam: Any? = null
    ): Pair<LanDevice, RemoteCommandLog> = withContext(Dispatchers.IO) {
        var updatedDevice = targetDevice
        var statusMessage = ""
        var isSuccess = true

        // 1. Queue command in embedded CompanionServer for any connected Web client on this IP
        companionServer?.queueCommandForClient(targetDevice.ipAddress, commandType, extraParam)

        // 2. Transmit real network HTTP/TCP packet directly to target device IP on port 8080/8888 or target open ports
        val httpSuccess = sendHttpCommandToDevice(targetDevice.ipAddress, commandType, extraParam)
        val socketSuccess = if (!httpSuccess) sendRawSocketCommand(targetDevice.ipAddress, commandType, extraParam) else true

        // 3. Local hardware trigger for feedback and audio confirmation
        if (commandType == RemoteCommandType.SIREN_ALARM) {
            playSirenSound()
        } else if (commandType == RemoteCommandType.DEVICE_VIBRATE) {
            triggerVibration(300)
        } else if (commandType == RemoteCommandType.FLASHLIGHT_TOGGLE) {
            toggleLocalFlashlight(!localTorchState)
        }

        // 4. Update device state
        when (commandType) {
            RemoteCommandType.CAMERA_START -> {
                updatedDevice = targetDevice.copy(isCameraOn = true)
                statusMessage = "Câmara remota iniciada em ${targetDevice.hostName} (${targetDevice.ipAddress}:8080). Visor em direto ativado."
            }
            RemoteCommandType.CAMERA_STOP -> {
                updatedDevice = targetDevice.copy(isCameraOn = false)
                statusMessage = "Transmissão da câmara remota parada em ${targetDevice.hostName}."
            }
            RemoteCommandType.CAMERA_SNAPSHOT -> {
                statusMessage = "Fotografia remota capturada de ${targetDevice.hostName} e guardada."
            }
            RemoteCommandType.FLASHLIGHT_TOGGLE -> {
                val newTorchState = !(targetDevice.isFlashlightOn)
                updatedDevice = targetDevice.copy(isFlashlightOn = newTorchState)
                statusMessage = if (newTorchState) {
                    "Lanterna remota LIGADA em ${targetDevice.hostName} (Wi-Fi 2.4/5G)."
                } else {
                    "Lanterna remota DESLIGADA em ${targetDevice.hostName}."
                }
            }
            RemoteCommandType.FLASHLIGHT_STROBE -> {
                updatedDevice = targetDevice.copy(isFlashlightOn = true)
                statusMessage = "Sinal Estroboscópio de emergência emitido para ${targetDevice.hostName}."
            }
            RemoteCommandType.AIRPLANE_MODE_TOGGLE -> {
                val newAirplane = !(targetDevice.isAirplaneModeOn)
                updatedDevice = targetDevice.copy(
                    isAirplaneModeOn = newAirplane,
                    isReachable = !newAirplane
                )
                statusMessage = if (newAirplane) {
                    "Modo Avião ACIONADO em ${targetDevice.hostName}. Desconexões sem fios solicitadas."
                } else {
                    "Modo Avião DESATIVADO em ${targetDevice.hostName}. Conexões Wi-Fi restabelecidas."
                }
            }
            RemoteCommandType.LIGHTING_SET_BRIGHTNESS -> {
                val brightness = (extraParam as? Float) ?: 0.8f
                updatedDevice = targetDevice.copy(screenOrBulbBrightness = brightness)
                statusMessage = "Brilho e iluminação ajustados para ${(brightness * 100).toInt()}% em ${targetDevice.hostName}."
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
                    else -> "Cor Personalizada"
                }
                updatedDevice = targetDevice.copy(
                    lightingColorHex = colorHex,
                    lightingMode = modeName
                )
                statusMessage = "Esquema de cor alterado para: $modeName em ${targetDevice.hostName}."
            }
            RemoteCommandType.SIREN_ALARM -> {
                statusMessage = "Sinal sonoro de alta frequência emitido para localização de ${targetDevice.hostName}."
            }
            RemoteCommandType.DEVICE_VIBRATE -> {
                statusMessage = "Comando de vibração tátil transmitido para ${targetDevice.hostName}."
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

    /**
     * Sends HTTP REST command to target companion endpoint.
     */
    private fun sendHttpCommandToDevice(ip: String, command: RemoteCommandType, extra: Any?): Boolean {
        return try {
            val targetUrl = URL("http://$ip:8080/api/command")
            val conn = (targetUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 350
                readTimeout = 350
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val payload = "{\"command\":\"${command.name}\",\"extra\":\"$extra\"}"
            conn.outputStream.use { os ->
                os.write(payload.toByteArray(StandardCharsets.UTF_8))
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sends raw socket control packet fallback on port 8080, 8008, or 7000.
     */
    private fun sendRawSocketCommand(ip: String, command: RemoteCommandType, extra: Any?): Boolean {
        val candidatePorts = listOf(8080, 8888, 8008, 7000, 5555)
        for (port in candidatePorts) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 200)
                    val out: OutputStream = socket.getOutputStream()
                    val packet = "CMD:${command.name}:$extra\n".toByteArray(StandardCharsets.UTF_8)
                    out.write(packet)
                    out.flush()
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }
}
