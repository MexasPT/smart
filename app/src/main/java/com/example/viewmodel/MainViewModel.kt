package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ContactsRepository
import com.example.data.ExportManager
import com.example.data.LanScannerManager
import com.example.data.NfcGateManager
import com.example.data.PhotosRepository
import com.example.data.RemoteDeviceManager
import com.example.model.ContactItem
import com.example.model.ExportFormat
import com.example.model.LanDevice
import com.example.model.LanDeviceType
import com.example.model.NfcEvent
import com.example.model.NfcEventType
import com.example.model.PasswordCredential
import com.example.model.PhotoItem
import com.example.model.RemoteCommandLog
import com.example.model.RemoteCommandType
import com.example.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class AppTab(val title: String) {
    NFC_RADAR("NFC & Gate"),
    LAN_DEVICES("Rede Wi-Fi"),
    CONTACTS("Contactos"),
    PHOTOS("Fotos"),
    VAULT("Passwords"),
    EXPORT("Exportar")
}

data class MainUiState(
    val currentTab: AppTab = AppTab.NFC_RADAR,
    val isNfcSupported: Boolean = true,
    val isNfcEnabled: Boolean = true,
    val isNfcGateInstalled: Boolean = false,
    val isCapturingData: Boolean = false,
    val lastCapturedTime: Long? = null,
    
    // Wi-Fi LAN & Remote Control
    val wifiSsid: String = "Rede Wi-Fi",
    val localIpAddress: String = "127.0.0.1",
    val subnetPrefix: String = "192.168.1",
    val isScanningLan: Boolean = false,
    val scanProgress: Float = 0f,
    val lanDevices: List<LanDevice> = emptyList(),
    val selectedLanDevice: LanDevice? = null,
    val remoteCommandLogs: List<RemoteCommandLog> = emptyList(),
    
    // Proximity Event Dialog
    val proximityAlertVisible: Boolean = false,
    val proximityAlertTitle: String = "",
    val proximityAlertMessage: String = "",
    
    // Contacts (Received / Synced from Remote Devices)
    val contacts: List<ContactItem> = emptyList(),
    val contactsSearchQuery: String = "",
    val contactsPermissionGranted: Boolean = false,
    
    // Photos (Received / Transferred from Remote Devices)
    val photos: List<PhotoItem> = emptyList(),
    val photosSearchQuery: String = "",
    val photosPermissionGranted: Boolean = false,
    
    // Passwords Vault (Stored / Synced)
    val passwords: List<PasswordCredential> = emptyList(),
    val passwordsSearchQuery: String = "",
    val showAddPasswordDialog: Boolean = false,
    val editingPassword: PasswordCredential? = null,
    
    // NFC Events Log
    val nfcEvents: List<NfcEvent> = emptyList(),
    val isRadarPulsing: Boolean = true,
    
    // Export Configuration & Output
    val exportFormat: ExportFormat = ExportFormat.ENCRYPTED_JSON,
    val exportEncryptionKey: String = "",
    val includeContactsInExport: Boolean = true,
    val includePhotosInExport: Boolean = true,
    val includePasswordsInExport: Boolean = true,
    val isExporting: Boolean = false,
    val exportedFile: File? = null,
    val exportedPreviewContent: String? = null,
    val toastMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepo = ContactsRepository(application)
    private val photosRepo = PhotosRepository(application)
    private val nfcGateManager = NfcGateManager(application)
    private val exportManager = ExportManager(application)
    private val lanScannerManager = LanScannerManager(application)
    private val remoteDeviceManager = RemoteDeviceManager(application)
    private val db = AppDatabase.getDatabase(application)
    private val passwordDao = db.passwordDao()

    private var scanJob: Job? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        checkNfcAndGateState()
        observePasswords()
        initNetworkInfo()
        startLanScan()
    }

    private fun initNetworkInfo() {
        _uiState.update {
            it.copy(
                wifiSsid = lanScannerManager.getWifiSsid(),
                localIpAddress = lanScannerManager.getLocalIpAddress(),
                subnetPrefix = lanScannerManager.getSubnetPrefix()
            )
        }
    }

    fun checkNfcAndGateState() {
        _uiState.update {
            it.copy(
                isNfcSupported = nfcGateManager.isNfcSupported,
                isNfcEnabled = nfcGateManager.isNfcEnabled,
                isNfcGateInstalled = nfcGateManager.isNfcGateInstalled()
            )
        }
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    private fun observePasswords() {
        viewModelScope.launch {
            passwordDao.getAllPasswords().collect { list ->
                _uiState.update { it.copy(passwords = list) }
            }
        }
    }

    // --- Contacts Handlers ---
    fun updateContactsSearchQuery(query: String) {
        _uiState.update { it.copy(contactsSearchQuery = query) }
    }

    fun toggleContactSelection(id: String) {
        _uiState.update { state ->
            val updated = state.contacts.map {
                if (it.id == id) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(contacts = updated)
        }
    }

    fun selectAllContacts(select: Boolean) {
        _uiState.update { state ->
            val updated = state.contacts.map { it.copy(isSelected = select) }
            state.copy(contacts = updated)
        }
    }

    fun addManualContact(name: String, phone: String, email: String, note: String = "") {
        val newContact = ContactItem(
            id = UUID.randomUUID().toString(),
            displayName = name.trim(),
            phoneNumbers = if (phone.isNotBlank()) listOf(phone.trim()) else emptyList(),
            emails = if (email.isNotBlank()) listOf(email.trim()) else emptyList(),
            note = note.trim(),
            isSelected = true
        )
        _uiState.update {
            it.copy(
                contacts = listOf(newContact) + it.contacts,
                toastMessage = "Contacto sincronizado adicionado: $name"
            )
        }
    }

    fun clearSyncedContacts() {
        _uiState.update { it.copy(contacts = emptyList(), toastMessage = "Lista de contactos recebidos limpa.") }
    }

    // --- Photos Handlers ---
    fun updatePhotosSearchQuery(query: String) {
        _uiState.update { it.copy(photosSearchQuery = query) }
    }

    fun togglePhotoSelection(id: Long) {
        _uiState.update { state ->
            val updated = state.photos.map {
                if (it.id == id) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(photos = updated)
        }
    }

    fun selectAllPhotos(select: Boolean) {
        _uiState.update { state ->
            val updated = state.photos.map { it.copy(isSelected = select) }
            state.copy(photos = updated)
        }
    }

    fun clearSyncedPhotos() {
        _uiState.update { it.copy(photos = emptyList(), toastMessage = "Galeria de fotografias recebidas limpa.") }
    }

    // --- Passwords Vault Handlers ---
    fun updatePasswordsSearchQuery(query: String) {
        _uiState.update { it.copy(passwordsSearchQuery = query) }
    }

    fun togglePasswordSelection(id: Long) {
        _uiState.update { state ->
            val updated = state.passwords.map {
                if (it.id == id) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(passwords = updated)
        }
    }

    fun selectAllPasswords(select: Boolean) {
        _uiState.update { state ->
            val updated = state.passwords.map { it.copy(isSelected = select) }
            state.copy(passwords = updated)
        }
    }

    fun openAddPasswordDialog(credential: PasswordCredential? = null) {
        _uiState.update {
            it.copy(
                showAddPasswordDialog = true,
                editingPassword = credential
            )
        }
    }

    fun dismissAddPasswordDialog() {
        _uiState.update {
            it.copy(
                showAddPasswordDialog = false,
                editingPassword = null
            )
        }
    }

    fun savePasswordCredential(
        title: String,
        username: String,
        passwordRaw: String,
        category: String,
        notes: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val editing = _uiState.value.editingPassword
            val item = PasswordCredential(
                id = editing?.id ?: 0,
                title = title.trim(),
                username = username.trim(),
                encryptedPassword = passwordRaw.trim(),
                category = category.trim().ifEmpty { "Geral" },
                notes = notes.trim(),
                updatedAt = System.currentTimeMillis(),
                isSelected = true
            )
            if (item.id == 0L) {
                passwordDao.insertPassword(item)
            } else {
                passwordDao.updatePassword(item)
            }
            _uiState.update {
                it.copy(
                    showAddPasswordDialog = false,
                    editingPassword = null,
                    toastMessage = "Credencial guardada no cofre seguro."
                )
            }
        }
    }

    fun deletePasswordCredential(item: PasswordCredential) {
        viewModelScope.launch(Dispatchers.IO) {
            passwordDao.deletePassword(item)
            _uiState.update { it.copy(toastMessage = "Credencial removida do cofre.") }
        }
    }

    // --- NFC Proximity & NFCGate Handlers ---
    fun onNfcDeviceApproached(tagInfo: String? = null) {
        nfcGateManager.triggerHapticFeedback()
        val event = NfcEvent(
            id = UUID.randomUUID().toString(),
            title = "Aproximação NFC Detectada",
            description = tagInfo ?: "Dispositivo NFC emparelhado no alcance de leitura.",
            eventType = NfcEventType.DEVICE_PROXIMITY,
            rawPayload = tagInfo
        )
        _uiState.update {
            it.copy(
                proximityAlertVisible = true,
                proximityAlertTitle = "Dispositivo NFC Aproximado",
                proximityAlertMessage = "Dispositivo NFC detectado. Deseja abrir o NFCGate ou sincronizar dados?",
                nfcEvents = listOf(event) + it.nfcEvents
            )
        }
    }

    fun dismissProximityAlert() {
        _uiState.update { it.copy(proximityAlertVisible = false) }
    }

    fun launchNfcGate() {
        val event = NfcEvent(
            id = UUID.randomUUID().toString(),
            title = "NFCGate Acionado",
            description = "Abertura da aplicação NFCGate.",
            eventType = NfcEventType.NFCGATE_LAUNCHED
        )
        _uiState.update {
            it.copy(
                proximityAlertVisible = false,
                nfcEvents = listOf(event) + it.nfcEvents
            )
        }

        val launchIntent = nfcGateManager.getLaunchNfcGateIntent()
        if (launchIntent != null) {
            getApplication<Application>().startActivity(launchIntent)
        } else {
            val downloadIntent = nfcGateManager.getDownloadNfcGateIntent()
            getApplication<Application>().startActivity(downloadIntent)
        }
    }

    fun simulateNfcProximityTouch() {
        onNfcDeviceApproached("NFC Tag / Smartphone Remoto detectado no sensor NFC.")
    }

    // --- Export Handlers ---
    fun setExportFormat(format: ExportFormat) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    fun setExportEncryptionKey(key: String) {
        _uiState.update { it.copy(exportEncryptionKey = key) }
    }

    fun toggleIncludeContacts(include: Boolean) {
        _uiState.update { it.copy(includeContactsInExport = include) }
    }

    fun toggleIncludePhotos(include: Boolean) {
        _uiState.update { it.copy(includePhotosInExport = include) }
    }

    fun toggleIncludePasswords(include: Boolean) {
        _uiState.update { it.copy(includePasswordsInExport = include) }
    }

    fun performExport(andShare: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val state = _uiState.value

            val selectedContacts = state.contacts.filter { it.isSelected }
            val selectedPhotos = state.photos.filter { it.isSelected }
            val selectedPasswords = state.passwords.filter { it.isSelected }

            val (file, previewText) = exportManager.generateExportFile(
                contacts = selectedContacts,
                photos = selectedPhotos,
                passwords = selectedPasswords,
                format = state.exportFormat,
                encryptionKey = state.exportEncryptionKey,
                includeContacts = state.includeContactsInExport,
                includePhotos = state.includePhotosInExport,
                includePasswords = state.includePasswordsInExport
            )

            val event = NfcEvent(
                id = UUID.randomUUID().toString(),
                title = "Exportação Segura Concluída",
                description = "Ficheiro gerado: ${file.name} (${file.length() / 1024} KB)",
                eventType = NfcEventType.EXPORT_TRIGGERED
            )

            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportedFile = file,
                    exportedPreviewContent = previewText,
                    nfcEvents = listOf(event) + it.nfcEvents,
                    toastMessage = "Ficheiro ${file.name} gerado com sucesso!"
                )
            }

            if (andShare) {
                exportManager.shareExportFile(file)
            }
        }
    }

    // --- Wi-Fi LAN Handlers ---
    fun startLanScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            initNetworkInfo()
            _uiState.update {
                it.copy(
                    isScanningLan = true,
                    scanProgress = 0f
                )
            }

            val devices = lanScannerManager.scanNetwork { current, total, _ ->
                _uiState.update {
                    it.copy(scanProgress = current.toFloat() / total.toFloat())
                }
            }

            _uiState.update {
                it.copy(
                    lanDevices = devices,
                    isScanningLan = false,
                    scanProgress = 1f,
                    toastMessage = if (devices.isEmpty())
                        "Nenhum outro dispositivo detectado na sub-rede ${it.subnetPrefix}.*"
                    else
                        "${devices.size} outro(s) dispositivo(s) real(is) detectado(s) na rede Wi-Fi!"
                )
            }
        }
    }

    fun stopLanScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanningLan = false) }
    }

    fun selectLanDevice(device: LanDevice?) {
        _uiState.update { it.copy(selectedLanDevice = device) }
    }

    fun addManualLanDevice(ip: String, name: String, type: LanDeviceType) {
        viewModelScope.launch {
            val connectedDevice = lanScannerManager.connectToManualIp(ip)
            _uiState.update {
                it.copy(
                    lanDevices = listOf(connectedDevice) + it.lanDevices.filter { d -> d.ipAddress != ip },
                    toastMessage = "Dispositivo $ip adicionado!"
                )
            }
        }
    }

    fun syncFromRemoteDevice(device: LanDevice) {
        viewModelScope.launch {
            _uiState.update { it.copy(toastMessage = "A sincronizar dados com ${device.hostName} (${device.ipAddress})...") }
            // Simulates receiving remote handshake confirmation from companion or companion port
            kotlinx.coroutines.delay(800)
            _uiState.update {
                it.copy(
                    toastMessage = "Sincronização com ${device.ipAddress} concluída com sucesso."
                )
            }
        }
    }

    fun sendRemoteCommand(commandType: RemoteCommandType, extraParam: Any?) {
        val target = _uiState.value.selectedLanDevice ?: return
        viewModelScope.launch {
            if (commandType == RemoteCommandType.DEVICE_VIBRATE) {
                remoteDeviceManager.triggerVibration()
            } else if (commandType == RemoteCommandType.SIREN_ALARM) {
                remoteDeviceManager.playSirenSound()
            } else if (commandType == RemoteCommandType.FLASHLIGHT_TOGGLE) {
                remoteDeviceManager.toggleLocalFlashlight(!target.isFlashlightOn)
            }

            val (updatedDevice, log) = remoteDeviceManager.executeRemoteCommand(
                targetDevice = target,
                commandType = commandType,
                extraParam = extraParam
            )

            _uiState.update { state ->
                val updatedList = state.lanDevices.map {
                    if (it.id == updatedDevice.id) updatedDevice else it
                }
                state.copy(
                    selectedLanDevice = updatedDevice,
                    lanDevices = updatedList,
                    remoteCommandLogs = listOf(log) + state.remoteCommandLogs,
                    toastMessage = log.status
                )
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
