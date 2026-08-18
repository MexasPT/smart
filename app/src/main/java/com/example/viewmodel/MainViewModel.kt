package com.example.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CompanionServer
import com.example.data.ContactsRepository
import com.example.data.ExportManager
import com.example.data.LanScannerManager
import com.example.data.NativeNfcManager
import com.example.data.PhotosRepository
import com.example.data.RemoteDeviceManager
import com.example.data.ScanBandMode
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class AppTab(val title: String) {
    NFC_RADAR("NFC Radar"),
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
    val isNfcGateInstalled: Boolean = true,
    val isCapturingData: Boolean = false,
    val lastCapturedTime: Long? = null,
    
    // Wi-Fi LAN & Remote Control
    val wifiSsid: String = "Rede Wi-Fi (2.4G / 5G)",
    val localIpAddress: String = "192.168.1.100",
    val subnetPrefix: String = "192.168.1",
    val scanBandMode: ScanBandMode = ScanBandMode.CURRENT_SUBNET,
    val isScanningLan: Boolean = false,
    val scanProgress: Float = 0f,
    val scannedCount: Int = 0,
    val totalToScan: Int = 254,
    val lanDevices: List<LanDevice> = emptyList(),
    val selectedLanDevice: LanDevice? = null,
    val remoteCommandLogs: List<RemoteCommandLog> = emptyList(),
    
    // Companion Web Server
    val isCompanionServerRunning: Boolean = true,
    val companionServerPort: Int = 8080,
    val companionServerUrl: String = "http://192.168.1.100:8080",
    
    // Proximity Event Dialog
    val proximityAlertVisible: Boolean = false,
    val proximityAlertTitle: String = "",
    val proximityAlertMessage: String = "",
    
    // Contacts
    val contacts: List<ContactItem> = emptyList(),
    val contactsSearchQuery: String = "",
    
    // Photos
    val photos: List<PhotoItem> = emptyList(),
    val photosSearchQuery: String = "",
    
    // Passwords Vault
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
    val nativeNfcManager = NativeNfcManager(application)
    private val exportManager = ExportManager(application)
    private val lanScannerManager = LanScannerManager(application)
    private val remoteDeviceManager = RemoteDeviceManager(application)
    private val db = AppDatabase.getDatabase(application)
    private val passwordDao = db.passwordDao()

    private var companionServer: CompanionServer? = null
    private var scanJob: Job? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        checkNfcState()
        observePasswords()
        initNetworkInfo()
        startCompanionServer()
        startLanScan(fullScan = false)
        loadContacts()
        loadPhotos()
    }

    private fun initNetworkInfo() {
        val ip = lanScannerManager.getLocalIpAddress()
        val subnet = lanScannerManager.getSubnetPrefix()
        val ssid = lanScannerManager.getWifiSsid()
        val port = companionServer?.serverPort ?: 8080
        _uiState.update {
            it.copy(
                wifiSsid = ssid,
                localIpAddress = ip,
                subnetPrefix = subnet,
                companionServerPort = port,
                companionServerUrl = "http://$ip:$port"
            )
        }
    }

    private fun startCompanionServer() {
        companionServer = CompanionServer(getApplication()) { registeredDevice ->
            _uiState.update { state ->
                val existing = state.lanDevices.filter { it.ipAddress != registeredDevice.ipAddress }
                val updated = listOf(registeredDevice) + existing
                state.copy(
                    lanDevices = updated,
                    toastMessage = "🟢 Dispositivo Remoto Pareado: ${registeredDevice.hostName}"
                )
            }
        }
        companionServer?.startServer(8080)
        remoteDeviceManager.setCompanionServer(companionServer!!)
    }

    fun checkNfcState() {
        _uiState.update {
            it.copy(
                isNfcSupported = nativeNfcManager.isNfcSupported,
                isNfcEnabled = nativeNfcManager.isNfcEnabled,
                isNfcGateInstalled = true
            )
        }
    }

    fun startNativeNfcReader(activity: Activity) {
        checkNfcState()
        nativeNfcManager.enableReaderMode(activity) { event ->
            handleDiscoveredNfcEvent(event)
        }
        nativeNfcManager.enableForegroundDispatch(activity)
    }

    fun stopNativeNfcReader(activity: Activity) {
        nativeNfcManager.disableReaderMode(activity)
        nativeNfcManager.disableForegroundDispatch(activity)
    }

    fun handleDiscoveredNfcEvent(event: NfcEvent) {
        nativeNfcManager.triggerHapticFeedback()
        _uiState.update { state ->
            state.copy(
                proximityAlertVisible = true,
                proximityAlertTitle = event.title,
                proximityAlertMessage = event.description,
                nfcEvents = listOf(event) + state.nfcEvents
            )
        }
    }

    fun onNfcDeviceApproached(message: String) {
        nativeNfcManager.triggerHapticFeedback()
        val event = NfcEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            eventType = NfcEventType.DEVICE_PROXIMITY,
            tagUid = "NFC-AIR-RADAR",
            techList = listOf("NfcA", "IsoDep"),
            payloadSummary = message,
            rawPayload = "0xNFC_RADAR_BEAM",
            title = "Aproximação NFC Intercetada",
            description = message
        )
        handleDiscoveredNfcEvent(event)
    }

    fun simulateNfcProximityTouch() {
        nativeNfcManager.triggerHapticFeedback()
        val simulatedEvent = NfcEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            eventType = NfcEventType.PROXIMITY_TOUCH,
            tagUid = "04:E2:89:1A:FC:6D:80",
            techList = listOf("IsoDep", "NfcA", "MifareUltralight", "NDEF"),
            payloadSummary = "Smartphone Remoto Emparelhado via Toque Físico NFC",
            rawPayload = "00A4040007A0000002471001",
            title = "Proximidade NFC Detectada",
            description = "Smartphone detectado a ≤ 3cm. Canal de sincronização ativado."
        )
        _uiState.update { state ->
            state.copy(
                proximityAlertVisible = true,
                proximityAlertTitle = "Proximidade NFC Intercetada",
                proximityAlertMessage = "Telemóvel remoto detectado via campo eletromagnético (NFC nativo). Sincronização pronta.",
                nfcEvents = listOf(simulatedEvent) + state.nfcEvents
            )
        }
    }

    fun dismissProximityAlert() {
        _uiState.update { it.copy(proximityAlertVisible = false) }
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == AppTab.CONTACTS) loadContacts()
        if (tab == AppTab.PHOTOS) loadPhotos()
    }

    // --- Wi-Fi LAN Handlers ---
    fun setScanBandMode(mode: ScanBandMode) {
        _uiState.update { it.copy(scanBandMode = mode) }
    }

    fun startLanScan(fullScan: Boolean = true) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            initNetworkInfo()
            val currentSubnet = _uiState.value.subnetPrefix
            val mode = _uiState.value.scanBandMode

            val targetSubnets = when (mode) {
                ScanBandMode.CURRENT_SUBNET -> listOf(currentSubnet)
                ScanBandMode.DUAL_BAND_ALL -> {
                    val list = mutableListOf(currentSubnet, "192.168.1", "192.168.0", "192.168.2", "10.0.0", "172.20.10", "192.168.43")
                    list.distinct()
                }
                ScanBandMode.CUSTOM_SUBNET -> listOf(currentSubnet)
            }

            val totalHosts = if (fullScan) 254 * targetSubnets.size else 60 * targetSubnets.size
            _uiState.update {
                it.copy(
                    isScanningLan = true,
                    scanProgress = 0f,
                    scannedCount = 0,
                    totalToScan = totalHosts
                )
            }

            val devices = lanScannerManager.scanNetwork(
                scanSubnets = targetSubnets,
                fullScan = fullScan
            ) { current, totalHostsCount, _ ->
                _uiState.update {
                    it.copy(
                        scanProgress = if (totalHostsCount > 0) current.toFloat() / totalHostsCount.toFloat() else 0f,
                        scannedCount = current,
                        totalToScan = totalHostsCount
                    )
                }
            }

            _uiState.update { state ->
                val companionClients = state.lanDevices.filter { it.isCompanionConnected }
                val merged = (companionClients + devices).distinctBy { it.ipAddress }

                state.copy(
                    lanDevices = merged,
                    isScanningLan = false,
                    scanProgress = 1f,
                    toastMessage = if (merged.isEmpty())
                        "Varredura concluída. Para controlar um telemóvel, abra ${state.companionServerUrl} no navegador dele."
                    else
                        "${merged.size} dispositivo(s) wireless (2.4/5G) detectado(s)!"
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
                val exists = it.lanDevices.any { d -> d.ipAddress == ip }
                val updated = if (exists) it.lanDevices else it.lanDevices + connectedDevice
                it.copy(
                    lanDevices = updated,
                    selectedLanDevice = connectedDevice,
                    toastMessage = "Dispositivo $ip adicionado."
                )
            }
        }
    }

    // --- Remote Control Commands ---
    fun sendRemoteCommand(commandType: RemoteCommandType, extraValue: Any? = null) {
        val target = _uiState.value.selectedLanDevice ?: return
        viewModelScope.launch {
            val (updatedDevice, log) = remoteDeviceManager.executeRemoteCommand(target, commandType, extraValue)
            _uiState.update { state ->
                val updatedDevices = state.lanDevices.map { dev ->
                    if (dev.id == target.id) updatedDevice else dev
                }

                state.copy(
                    selectedLanDevice = updatedDevice,
                    lanDevices = updatedDevices,
                    remoteCommandLogs = listOf(log) + state.remoteCommandLogs,
                    toastMessage = "${commandType.displayName}: ${log.status}"
                )
            }
        }
    }

    // --- Contacts Handlers ---
    fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = contactsRepo.fetchContacts()
            _uiState.update {
                it.copy(contacts = list)
            }
        }
    }

    fun updateContactsSearchQuery(query: String) {
        _uiState.update { it.copy(contactsSearchQuery = query) }
    }

    fun toggleContactSelection(contactId: String) {
        _uiState.update { state ->
            val updated = state.contacts.map {
                if (it.id == contactId) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(contacts = updated)
        }
    }

    fun selectAllContacts(selectAll: Boolean) {
        _uiState.update { state ->
            val updated = state.contacts.map { it.copy(isSelected = selectAll) }
            state.copy(contacts = updated)
        }
    }

    fun addManualContact(name: String, phone: String, email: String, note: String) {
        val newContact = ContactItem(
            id = UUID.randomUUID().toString(),
            displayName = name,
            phoneNumbers = if (phone.isNotBlank()) listOf(phone) else emptyList(),
            emails = if (email.isNotBlank()) listOf(email) else emptyList(),
            note = note,
            isSelected = true
        )
        _uiState.update { state ->
            state.copy(
                contacts = listOf(newContact) + state.contacts,
                toastMessage = "Contacto '$name' adicionado com sucesso."
            )
        }
    }

    fun clearSyncedContacts() {
        _uiState.update { it.copy(contacts = emptyList(), toastMessage = "Lista de contactos limpa.") }
    }

    // --- Photos Handlers ---
    fun loadPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = photosRepo.fetchPhotos()
            _uiState.update {
                it.copy(photos = list)
            }
        }
    }

    fun updatePhotosSearchQuery(query: String) {
        _uiState.update { it.copy(photosSearchQuery = query) }
    }

    fun togglePhotoSelection(photoId: Long) {
        _uiState.update { state ->
            val updated = state.photos.map {
                if (it.id == photoId) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(photos = updated)
        }
    }

    fun selectAllPhotos(selectAll: Boolean) {
        _uiState.update { state ->
            val updated = state.photos.map { it.copy(isSelected = selectAll) }
            state.copy(photos = updated)
        }
    }

    fun clearSyncedPhotos() {
        _uiState.update { it.copy(photos = emptyList(), toastMessage = "Galeria de fotos sincronizadas limpa.") }
    }

    // --- Passwords Handlers ---
    private fun observePasswords() {
        viewModelScope.launch {
            passwordDao.getAllPasswords().collect { list ->
                _uiState.update { it.copy(passwords = list) }
            }
        }
    }

    fun updatePasswordsSearchQuery(query: String) {
        _uiState.update { it.copy(passwordsSearchQuery = query) }
    }

    fun togglePasswordSelection(passwordId: Long) {
        _uiState.update { state ->
            val updated = state.passwords.map {
                if (it.id == passwordId) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(passwords = updated)
        }
    }

    fun selectAllPasswords(selectAll: Boolean) {
        _uiState.update { state ->
            val updated = state.passwords.map { it.copy(isSelected = selectAll) }
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
        passwordPlain: String,
        category: String,
        notes: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val editing = _uiState.value.editingPassword
            val credential = PasswordCredential(
                id = editing?.id ?: 0L,
                title = title,
                username = username,
                encryptedPassword = passwordPlain,
                category = category,
                notes = notes,
                updatedAt = System.currentTimeMillis(),
                isSelected = true
            )
            passwordDao.insertPassword(credential)
            dismissAddPasswordDialog()
            _uiState.update { it.copy(toastMessage = "Credencial '$title' guardada.") }
        }
    }

    fun deletePasswordCredential(credential: PasswordCredential) {
        viewModelScope.launch(Dispatchers.IO) {
            passwordDao.deletePassword(credential)
            _uiState.update { it.copy(toastMessage = "Credencial removida.") }
        }
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

    fun performExport(andShare: Boolean = false) {
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isExporting = true) }
            val (file, content) = exportManager.generateExportFile(
                contacts = if (state.includeContactsInExport) state.contacts.filter { it.isSelected } else emptyList(),
                photos = if (state.includePhotosInExport) state.photos.filter { it.isSelected } else emptyList(),
                passwords = if (state.includePasswordsInExport) state.passwords.filter { it.isSelected } else emptyList(),
                format = state.exportFormat,
                encryptionKey = state.exportEncryptionKey,
                includeContacts = state.includeContactsInExport,
                includePhotos = state.includePhotosInExport,
                includePasswords = state.includePasswordsInExport
            )

            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportedFile = file,
                    exportedPreviewContent = content,
                    toastMessage = "Exportação gerada com sucesso (${file.name})."
                )
            }

            if (andShare) {
                exportManager.shareExportFile(file)
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        companionServer?.stopServer()
    }
}
