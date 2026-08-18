package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Model representing a phone contact.
 */
data class ContactItem(
    val id: String,
    val lookupKey: String = "",
    val displayName: String,
    val phoneNumbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val photoUri: String? = null,
    val note: String = "",
    val isSelected: Boolean = true
)

/**
 * Model representing a photo in the device gallery.
 */
data class PhotoItem(
    val id: Long,
    val uri: String,
    val displayName: String,
    val dateTaken: Long = 0L,
    val sizeBytes: Long = 0L,
    val sizeFormatted: String = "",
    val bucketName: String = "Galeria",
    val mimeType: String = "image/jpeg",
    val isSelected: Boolean = true
)

/**
 * Model for passwords stored securely in the app vault.
 */
@Entity(tableName = "passwords_vault")
data class PasswordCredential(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val username: String,
    val encryptedPassword: String,
    val category: String = "Geral", // e.g. "Email", "Social", "Bancos", "Wi-Fi", "Servidores"
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isSelected: Boolean = true
)

/**
 * Model representing an NFC proximity / communication log.
 */
data class NfcEvent(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val description: String,
    val eventType: NfcEventType,
    val rawPayload: String? = null
)

enum class NfcEventType {
    TAG_DISCOVERED,
    DEVICE_PROXIMITY,
    NFCGATE_LAUNCHED,
    P2P_BEAM,
    EXPORT_TRIGGERED
}

/**
 * Export format options.
 */
enum class ExportFormat(val title: String, val extension: String) {
    ENCRYPTED_JSON("Ficheiro JSON Cifrado (AES-256)", ".json.enc"),
    PLAIN_JSON("JSON Estruturado Padrão", ".json"),
    VCARD_ZIP("Cartões de Contacto VCard (.vcf)", ".vcf"),
    MIGRATION_BUNDLE("Pacote Completo de Migração", ".zip")
}

/**
 * Types of devices detectable on the Local Wi-Fi Network.
 */
enum class LanDeviceType(val label: String) {
    SMARTPHONE_ANDROID("Smartphone Android"),
    SMARTPHONE_IOS("Apple iPhone"),
    SMART_LIGHT("Iluminação Inteligente / Smart Bulb"),
    TABLET("Tablet"),
    PC_LAPTOP("Computador / Portátil"),
    SMART_TV("Smart TV / Monitor"),
    ROUTER("Router / Gateway Wi-Fi"),
    IOT_GENERIC("Dispositivo IoT")
}

/**
 * Model representing a connected device on the local Wi-Fi subnet.
 */
data class LanDevice(
    val id: String,
    val ipAddress: String,
    val macAddress: String = "02:00:00:00:00:00",
    val hostName: String = "",
    val deviceType: LanDeviceType = LanDeviceType.SMARTPHONE_ANDROID,
    val brandModel: String = "",
    val isReachable: Boolean = true,
    val pingLatencyMs: Long = 12L,
    val openPorts: List<Int> = listOf(8080, 5555),
    val isCompanionConnected: Boolean = true,
    val batteryPercent: Int? = 85,
    val signalDbm: Int = -52,
    
    // Remote states controlled via companion protocol
    val isCameraOn: Boolean = false,
    val isFlashlightOn: Boolean = false,
    val isAirplaneModeOn: Boolean = false,
    val screenOrBulbBrightness: Float = 0.8f, // 0.0f to 1.0f
    val lightingColorHex: Long = 0xFFFFD54F, // Warm yellow by default
    val lightingMode: String = "Luz Noturna Quente"
)

/**
 * Command types that can be sent remotely to connected devices.
 */
enum class RemoteCommandType(val displayName: String) {
    CAMERA_START("Ativar Câmara Remota"),
    CAMERA_STOP("Desligar Câmara"),
    CAMERA_SNAPSHOT("Capturar Fotografia Remota"),
    FLASHLIGHT_TOGGLE("Alternar Lanterna"),
    FLASHLIGHT_STROBE("Modo Estroboscópio"),
    AIRPLANE_MODE_TOGGLE("Ativar/Desativar Modo Avião"),
    LIGHTING_SET_BRIGHTNESS("Ajustar Intensidade de Iluminação"),
    LIGHTING_SET_COLOR("Alterar Cor da Iluminação"),
    SIREN_ALARM("Alarme Sonoro de Procura"),
    DEVICE_VIBRATE("Vibrar Dispositivo")
}

/**
 * Log entry for remote hardware commands executed.
 */
data class RemoteCommandLog(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val targetDeviceIp: String,
    val targetDeviceName: String,
    val command: RemoteCommandType,
    val status: String,
    val isSuccess: Boolean = true
)

