package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LanDevice
import com.example.model.LanDeviceType
import com.example.viewmodel.MainUiState

@Composable
fun LanDevicesScreen(
    uiState: MainUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (LanDevice) -> Unit,
    onAddManualDevice: (String, String, LanDeviceType) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("Todos") }
    var showManualDialog by remember { mutableStateOf(false) }

    val filteredDevices = remember(uiState.lanDevices, selectedFilter) {
        when (selectedFilter) {
            "Smartphones" -> uiState.lanDevices.filter {
                it.deviceType == LanDeviceType.SMARTPHONE_ANDROID || it.deviceType == LanDeviceType.SMARTPHONE_IOS
            }
            "Iluminação" -> uiState.lanDevices.filter { it.deviceType == LanDeviceType.SMART_LIGHT }
            "Outros" -> uiState.lanDevices.filter {
                it.deviceType != LanDeviceType.SMARTPHONE_ANDROID &&
                it.deviceType != LanDeviceType.SMARTPHONE_IOS &&
                it.deviceType != LanDeviceType.SMART_LIGHT
            }
            else -> uiState.lanDevices
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                WifiNetworkStatusCard(
                    wifiSsid = uiState.wifiSsid,
                    localIp = uiState.localIpAddress,
                    subnet = uiState.subnetPrefix,
                    isScanning = uiState.isScanningLan,
                    scanProgress = uiState.scanProgress,
                    scannedCount = uiState.scannedCount,
                    totalToScan = uiState.totalToScan,
                    onStartScan = onStartScan,
                    onStopScan = onStopScan
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Dispositivos na Rede Wireless",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${filteredDevices.size} encontrados na sub-rede ${uiState.subnetPrefix}.*",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = { showManualDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_manual_device")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("IP Manual", fontSize = 12.sp)
                    }
                }
            }

            // Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filters = listOf("Todos", "Smartphones", "Iluminação", "Outros")
                    items(filters) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) },
                            modifier = Modifier.testTag("filter_chip_$filter")
                        )
                    }
                }
            }

            if (filteredDevices.isEmpty() && !uiState.isScanningLan) {
                item {
                    EmptyLanDevicesCard(onStartScan = onStartScan)
                }
            } else {
                items(filteredDevices, key = { it.id }) { device ->
                    LanDeviceCard(
                        device = device,
                        onEnterDevice = { onSelectDevice(device) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        if (showManualDialog) {
            AddManualDeviceDialog(
                onDismiss = { showManualDialog = false },
                onAdd = { ip, name, type ->
                    onAddManualDevice(ip, name, type)
                    showManualDialog = false
                }
            )
        }
    }
}

@Composable
fun WifiNetworkStatusCard(
    wifiSsid: String,
    localIp: String,
    subnet: String,
    isScanning: Boolean,
    scanProgress: Float,
    scannedCount: Int = 0,
    totalToScan: Int = 254,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .scale(if (isScanning) pulseScale else 1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = wifiSsid,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "IP deste aparelho: $localIp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Sub-rede: $subnet.*",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isScanning) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "A escanear $subnet.1..$totalToScan ($scannedCount de $totalToScan)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${(scanProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { scanProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onStopScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_stop_scan"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Parar Varredura")
                    }
                }
            } else {
                Button(
                    onClick = onStartScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_start_scan"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Escanear Sub-rede Completa (1..254)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LanDeviceCard(
    device: LanDevice,
    onEnterDevice: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEnterDevice() }
            .testTag("lan_device_card_${device.ipAddress.replace(".", "_")}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when (device.deviceType) {
                                    LanDeviceType.SMARTPHONE_ANDROID -> MaterialTheme.colorScheme.primaryContainer
                                    LanDeviceType.SMARTPHONE_IOS -> MaterialTheme.colorScheme.secondaryContainer
                                    LanDeviceType.SMART_LIGHT -> Color(0xFFFFF9C4)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getDeviceIcon(device.deviceType),
                            contentDescription = null,
                            tint = when (device.deviceType) {
                                LanDeviceType.SMARTPHONE_ANDROID -> MaterialTheme.colorScheme.primary
                                LanDeviceType.SMARTPHONE_IOS -> MaterialTheme.colorScheme.secondary
                                LanDeviceType.SMART_LIGHT -> Color(0xFFF57F17)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = device.hostName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${device.ipAddress} • ${device.brandModel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Battery / Status Indicator
                if (device.batteryPercent != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (device.batteryPercent > 20) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${device.batteryPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hardware capability tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CapabilityBadge(
                    icon = Icons.Default.CameraAlt,
                    label = if (device.isCameraOn) "Câmara ON" else "Câmara",
                    isActive = device.isCameraOn,
                    activeColor = Color(0xFFE53935)
                )
                CapabilityBadge(
                    icon = Icons.Default.FlashlightOn,
                    label = if (device.isFlashlightOn) "Lanterna ON" else "Lanterna",
                    isActive = device.isFlashlightOn,
                    activeColor = Color(0xFFFBC02D)
                )
                CapabilityBadge(
                    icon = Icons.Default.Lightbulb,
                    label = "Iluminação ${(device.screenOrBulbBrightness * 100).toInt()}%",
                    isActive = true,
                    activeColor = Color(device.lightingColorHex)
                )
                if (device.isAirplaneModeOn) {
                    CapabilityBadge(
                        icon = Icons.Default.AirplanemodeActive,
                        label = "Modo Avião",
                        isActive = true,
                        activeColor = Color(0xFF0288D1)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button to Enter Device Control
            Button(
                onClick = onEnterDevice,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_enter_device_${device.ipAddress.replace(".", "_")}"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.SettingsRemote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Entrar e Controlar Dispositivo",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CapabilityBadge(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) activeColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(26.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyLanDevicesCard(onStartScan: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Nenhum dispositivo encontrado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Toque em Escanear para varrer a rede Wi-Fi e detetar outros smartphones, lâmpadas e dispositivos conectados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onStartScan, shape = RoundedCornerShape(12.dp)) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Escanear Agora")
            }
        }
    }
}

@Composable
fun AddManualDeviceDialog(
    onDismiss: () -> Unit,
    onAdd: (ip: String, name: String, type: LanDeviceType) -> Unit
) {
    var ipInput by remember { mutableStateOf("192.168.1.") }
    var nameInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(LanDeviceType.SMARTPHONE_ANDROID) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Adicionar Dispositivo por IP", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Insira o endereço IP do smartphone ou dispositivo na rede Wi-Fi:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    label = { Text("Endereço IP (ex: 192.168.1.102)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_manual_ip")
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nome do Dispositivo (opcional)") },
                    placeholder = { Text("Ex: Smartphone Sala") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_manual_name")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (ipInput.isNotBlank()) {
                        onAdd(ipInput, nameInput, selectedType)
                    }
                },
                modifier = Modifier.testTag("btn_confirm_manual_ip")
            ) {
                Text("Adicionar & Ligar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

fun getDeviceIcon(type: LanDeviceType): ImageVector {
    return when (type) {
        LanDeviceType.SMARTPHONE_ANDROID -> Icons.Default.PhoneAndroid
        LanDeviceType.SMARTPHONE_IOS -> Icons.Default.PhoneIphone
        LanDeviceType.SMART_LIGHT -> Icons.Default.Lightbulb
        LanDeviceType.TABLET -> Icons.Default.Tablet
        LanDeviceType.PC_LAPTOP -> Icons.Default.Computer
        LanDeviceType.SMART_TV -> Icons.Default.Tv
        LanDeviceType.ROUTER -> Icons.Default.Router
        LanDeviceType.IOT_GENERIC -> Icons.Default.Devices
    }
}
