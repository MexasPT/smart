package com.example

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AddOrEditPasswordDialog
import com.example.ui.ContactsScreen
import com.example.ui.ExportScreen
import com.example.ui.LanDevicesScreen
import com.example.ui.NfcRadarScreen
import com.example.ui.PasswordVaultScreen
import com.example.ui.PhotosScreen
import com.example.ui.ProximityAlertDialog
import com.example.ui.RemoteControlScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppTab
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingNfcIntent(intent)

        setContent {
            MyApplicationTheme {
                MainAppContent(
                    viewModel = viewModel,
                    onTriggerNfcScan = { viewModel.simulateNfcProximityTouch() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startNativeNfcReader(this)
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopNativeNfcReader(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingNfcIntent(intent)
    }

    private fun handleIncomingNfcIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            if (tag != null) {
                val event = viewModel.nativeNfcManager.processDiscoveredTag(tag)
                viewModel.handleDiscoveredNfcEvent(event)
            } else {
                viewModel.onNfcDeviceApproached("Dispositivo NFC detectado no sensor.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: MainViewModel,
    onTriggerNfcScan: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    BackHandler(enabled = uiState.selectedLanDevice != null) {
        viewModel.selectLanDevice(null)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (uiState.currentTab) {
                            AppTab.NFC_RADAR -> "Radar NFC & Proximidade"
                            AppTab.LAN_DEVICES -> uiState.selectedLanDevice?.let { "Controlo: ${it.hostName}" } ?: "Rede Wi-Fi & Dispositivos"
                            AppTab.CONTACTS -> "Contactos Sincronizados"
                            AppTab.PHOTOS -> "Galeria de Fotos Remotas"
                            AppTab.VAULT -> "Cofre de Passwords"
                            AppTab.EXPORT -> "Exportação Segura"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (uiState.currentTab == AppTab.NFC_RADAR) {
                        IconButton(
                            onClick = onTriggerNfcScan,
                            modifier = Modifier.testTag("topbar_btn_nfc_pulse")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Nfc,
                                contentDescription = "Testar Proximidade NFC",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.NFC_RADAR,
                    onClick = { viewModel.setTab(AppTab.NFC_RADAR) },
                    icon = {
                        Icon(imageVector = Icons.Default.Nfc, contentDescription = "NFC")
                    },
                    label = { Text("NFC", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_nfc_radar")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.LAN_DEVICES,
                    onClick = { viewModel.setTab(AppTab.LAN_DEVICES) },
                    icon = {
                        BadgedBox(badge = {
                            if (uiState.lanDevices.isNotEmpty()) {
                                Badge { Text(uiState.lanDevices.size.toString()) }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Wifi, contentDescription = "Wi-Fi & LAN")
                        }
                    },
                    label = { Text("Wi-Fi", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_lan_devices")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.CONTACTS,
                    onClick = { viewModel.setTab(AppTab.CONTACTS) },
                    icon = {
                        BadgedBox(badge = {
                            if (uiState.contacts.isNotEmpty()) {
                                Badge { Text(uiState.contacts.size.toString()) }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.ContactPage, contentDescription = "Contactos")
                        }
                    },
                    label = { Text("Contactos", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_contacts")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.PHOTOS,
                    onClick = { viewModel.setTab(AppTab.PHOTOS) },
                    icon = {
                        BadgedBox(badge = {
                            if (uiState.photos.isNotEmpty()) {
                                Badge { Text(uiState.photos.size.toString()) }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Fotos")
                        }
                    },
                    label = { Text("Fotos", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_photos")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.VAULT,
                    onClick = { viewModel.setTab(AppTab.VAULT) },
                    icon = {
                        BadgedBox(badge = {
                            if (uiState.passwords.isNotEmpty()) {
                                Badge { Text(uiState.passwords.size.toString()) }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = "Passwords")
                        }
                    },
                    label = { Text("Vault", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_vault")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.EXPORT,
                    onClick = { viewModel.setTab(AppTab.EXPORT) },
                    icon = {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Exportar")
                    },
                    label = { Text("Exportar", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_export")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                AppTab.NFC_RADAR -> {
                    NfcRadarScreen(
                        uiState = uiState,
                        onSimulateTouch = { viewModel.simulateNfcProximityTouch() },
                        onNavigateTab = { viewModel.setTab(it) }
                    )
                }
                AppTab.LAN_DEVICES -> {
                    val selectedDevice = uiState.selectedLanDevice
                    if (selectedDevice != null) {
                        RemoteControlScreen(
                            device = selectedDevice,
                            commandLogs = uiState.remoteCommandLogs.filter { it.targetDeviceIp == selectedDevice.ipAddress },
                            onBack = { viewModel.selectLanDevice(null) },
                            onSendCommand = { cmd, extra -> viewModel.sendRemoteCommand(cmd, extra) }
                        )
                    } else {
                        LanDevicesScreen(
                            uiState = uiState,
                            onStartScan = { viewModel.startLanScan(fullScan = true) },
                            onStopScan = { viewModel.stopLanScan() },
                            onSetScanMode = { viewModel.setScanBandMode(it) },
                            onSelectDevice = { viewModel.selectLanDevice(it) },
                            onAddManualDevice = { ip, name, type -> viewModel.addManualLanDevice(ip, name, type) }
                        )
                    }
                }
                AppTab.CONTACTS -> {
                    ContactsScreen(
                        contacts = uiState.contacts,
                        searchQuery = uiState.contactsSearchQuery,
                        onSearchQueryChange = { viewModel.updateContactsSearchQuery(it) },
                        onToggleSelect = { viewModel.toggleContactSelection(it) },
                        onSelectAll = { viewModel.selectAllContacts(it) },
                        onAddContact = { name, phone, email, note ->
                            viewModel.addManualContact(name, phone, email, note)
                        },
                        onClearContacts = { viewModel.clearSyncedContacts() }
                    )
                }
                AppTab.PHOTOS -> {
                    PhotosScreen(
                        photos = uiState.photos,
                        searchQuery = uiState.photosSearchQuery,
                        onSearchQueryChange = { viewModel.updatePhotosSearchQuery(it) },
                        onToggleSelect = { viewModel.togglePhotoSelection(it) },
                        onSelectAll = { viewModel.selectAllPhotos(it) },
                        onClearPhotos = { viewModel.clearSyncedPhotos() }
                    )
                }
                AppTab.VAULT -> {
                    PasswordVaultScreen(
                        passwords = uiState.passwords,
                        searchQuery = uiState.passwordsSearchQuery,
                        onSearchQueryChange = { viewModel.updatePasswordsSearchQuery(it) },
                        onToggleSelect = { viewModel.togglePasswordSelection(it) },
                        onSelectAll = { viewModel.selectAllPasswords(it) },
                        onOpenAddDialog = { viewModel.openAddPasswordDialog(it) },
                        onDeletePassword = { viewModel.deletePasswordCredential(it) }
                    )
                }
                AppTab.EXPORT -> {
                    ExportScreen(
                        uiState = uiState,
                        onFormatChange = { viewModel.setExportFormat(it) },
                        onKeyChange = { viewModel.setExportEncryptionKey(it) },
                        onToggleIncludeContacts = { viewModel.toggleIncludeContacts(it) },
                        onToggleIncludePhotos = { viewModel.toggleIncludePhotos(it) },
                        onToggleIncludePasswords = { viewModel.toggleIncludePasswords(it) },
                        onExportAndShare = { viewModel.performExport(andShare = true) },
                        onExportSaveLocal = { viewModel.performExport(andShare = false) }
                    )
                }
            }
        }

        // Proximity Alert Popup when NFC device approaches
        if (uiState.proximityAlertVisible) {
            ProximityAlertDialog(
                title = uiState.proximityAlertTitle,
                message = uiState.proximityAlertMessage,
                onExportNow = {
                    viewModel.setTab(AppTab.EXPORT)
                    viewModel.performExport(andShare = true)
                },
                onDismiss = { viewModel.dismissProximityAlert() }
            )
        }

        // Add/Edit Password Dialog
        if (uiState.showAddPasswordDialog) {
            AddOrEditPasswordDialog(
                initialCredential = uiState.editingPassword,
                onDismiss = { viewModel.dismissAddPasswordDialog() },
                onSave = { title, user, pass, cat, note ->
                    viewModel.savePasswordCredential(title, user, pass, cat, note)
                }
            )
        }
    }
}
