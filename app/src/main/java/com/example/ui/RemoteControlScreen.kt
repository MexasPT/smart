package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AirplanemodeInactive
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LanDevice
import com.example.model.LanDeviceType
import com.example.model.RemoteCommandLog
import com.example.model.RemoteCommandType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    device: LanDevice,
    commandLogs: List<RemoteCommandLog>,
    onBack: () -> Unit,
    onSendCommand: (RemoteCommandType, Any?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAirplaneConfirmDialog by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var snapshotTakenNotification by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            DeviceHeaderCard(device = device, onBack = onBack)
        }

        // --- 1. CONTROLO DA CÂMARA REMOTA ---
        item {
            CameraControlCard(
                device = device,
                isFrontCamera = isFrontCamera,
                snapshotNotification = snapshotTakenNotification,
                onToggleCamera = {
                    if (device.isCameraOn) {
                        onSendCommand(RemoteCommandType.CAMERA_STOP, null)
                    } else {
                        onSendCommand(RemoteCommandType.CAMERA_START, null)
                    }
                },
                onSwitchLens = { isFrontCamera = !isFrontCamera },
                onTakeSnapshot = {
                    snapshotTakenNotification = true
                    onSendCommand(RemoteCommandType.CAMERA_SNAPSHOT, null)
                }
            )
        }

        // --- 2. CONTROLO DA LANTERNA REMOTA ---
        item {
            FlashlightControlCard(
                device = device,
                onToggleTorch = {
                    onSendCommand(RemoteCommandType.FLASHLIGHT_TOGGLE, null)
                },
                onStrobeMode = {
                    onSendCommand(RemoteCommandType.FLASHLIGHT_STROBE, null)
                }
            )
        }

        // --- 3. CONTROLO DE ILUMINAÇÃO & BRILHO ---
        item {
            LightingControlCard(
                device = device,
                onBrightnessChange = { newBrightness ->
                    onSendCommand(RemoteCommandType.LIGHTING_SET_BRIGHTNESS, newBrightness)
                },
                onColorChange = { colorHex ->
                    onSendCommand(RemoteCommandType.LIGHTING_SET_COLOR, colorHex)
                }
            )
        }

        // --- 4. MODO AVIÃO & GESTÃO DE CONEXÕES ---
        item {
            AirplaneModeCard(
                device = device,
                onRequestToggleAirplane = {
                    showAirplaneConfirmDialog = true
                }
            )
        }

        // --- 5. AÇÕES RÁPIDAS & ALERTA SONORO ---
        item {
            QuickActionsCard(
                onPlaySiren = { onSendCommand(RemoteCommandType.SIREN_ALARM, null) },
                onVibrate = { onSendCommand(RemoteCommandType.DEVICE_VIBRATE, null) }
            )
        }

        // --- 6. REGISTO DE COMANDOS TRANSMITIDOS EM TEMPO REAL ---
        item {
            CommandAuditCard(commandLogs = commandLogs)
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (showAirplaneConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showAirplaneConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.AirplanemodeActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (device.isAirplaneModeOn) "Desativar Modo Avião?" else "Colocar em Modo Avião?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (device.isAirplaneModeOn) {
                        "Restaurar as conexões de rádio (Wi-Fi, Bluetooth e Dados Móveis) em ${device.hostName}."
                    } else {
                        "Atenção: Ao colocar ${device.hostName} em Modo Avião, as antenas de Wi-Fi e rede serão desligadas remotamente."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAirplaneConfirmDialog = false
                        onSendCommand(RemoteCommandType.AIRPLANE_MODE_TOGGLE, null)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (device.isAirplaneModeOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("btn_confirm_airplane_mode")
                ) {
                    Text(if (device.isAirplaneModeOn) "Desativar Modo Avião" else "Ativar Modo Avião")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAirplaneConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun DeviceHeaderCard(
    device: LanDevice,
    onBack: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("btn_back_to_lan")
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
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

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (device.isReachable) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (device.isReachable) Color(0xFF2E7D32) else Color(0xFFC62828))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (device.isReachable) "Conectado" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (device.isReachable) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }
        }
    }
}

// --- CAMERA CARD ---
@Composable
fun CameraControlCard(
    device: LanDevice,
    isFrontCamera: Boolean,
    snapshotNotification: Boolean,
    onToggleCamera: () -> Unit,
    onSwitchLens: () -> Unit,
    onTakeSnapshot: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Câmara Remota",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = device.isCameraOn,
                    onCheckedChange = { onToggleCamera() },
                    modifier = Modifier.testTag("switch_remote_camera")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (device.isCameraOn) {
                // Live Viewfinder Frame Simulation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Simulated Night/Camera stream backdrop
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1A237E).copy(alpha = 0.6f), Color.Black)
                                )
                            )
                    )

                    // HUD elements
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "STREAM AO VIVO • 1080p @ 30fps",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = if (isFrontCamera) "CÂMARA FRONTAL" else "CÂMARA TRASEIRA 50MP",
                                color = Color.Yellow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Center Viewfinder target crosshair
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(60.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ISO 400 • F/1.8 • AF ATIVO",
                                color = Color.LightGray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            IconButton(
                                onClick = onSwitchLens,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlipCameraAndroid,
                                    contentDescription = "Alternar Lente",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onTakeSnapshot,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_take_remote_photo"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tirar Fotografia", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onToggleCamera,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VideocamOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Desligar Visor")
                    }
                }
            } else {
                Text(
                    text = "A câmara remota está desligada. Toque no interruptor para abrir o visor e transmitir o vídeo do smartphone em direto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onToggleCamera,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_turn_on_camera"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ligar Câmara Remota")
                }
            }
        }
    }
}

// --- FLASHLIGHT CARD ---
@Composable
fun FlashlightControlCard(
    device: LanDevice,
    onToggleTorch: () -> Unit,
    onStrobeMode: () -> Unit
) {
    val torchGlowColor by animateColorAsState(
        targetValue = if (device.isFlashlightOn) Color(0xFFFFD54F) else Color.Gray.copy(alpha = 0.3f),
        label = "torchGlow"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (device.isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = null,
                        tint = if (device.isFlashlightOn) Color(0xFFF57F17) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lanterna do Smartphone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = device.isFlashlightOn,
                    onCheckedChange = { onToggleTorch() },
                    modifier = Modifier.testTag("switch_remote_flashlight")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visual Torch Light simulation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                torchGlowColor.copy(alpha = if (device.isFlashlightOn) 0.9f else 0.1f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (device.isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = null,
                        tint = if (device.isFlashlightOn) Color(0xFFE65100) else Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (device.isFlashlightOn) "Lanterna Acesa no Telemóvel Remoto" else "Lanterna Apagada",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (device.isFlashlightOn) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onToggleTorch,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_toggle_torch"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (device.isFlashlightOn) Color(0xFFF57F17) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (device.isFlashlightOn) "Desligar Lanterna" else "Ligar Lanterna")
                }

                FilledTonalButton(
                    onClick = onStrobeMode,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_torch_strobe"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Estroboscópio SOS")
                }
            }
        }
    }
}

// --- LIGHTING CONTROL CARD ---
@Composable
fun LightingControlCard(
    device: LanDevice,
    onBrightnessChange: (Float) -> Unit,
    onColorChange: (Long) -> Unit
) {
    val currentColor = Color(device.lightingColorHex)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = currentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Controlo de Iluminação & Brilho",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${(device.screenOrBulbBrightness * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Brightness Slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.BrightnessLow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = device.screenOrBulbBrightness,
                    onValueChange = onBrightnessChange,
                    valueRange = 0.05f..1.0f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("slider_lighting_brightness"),
                    colors = SliderDefaults.colors(
                        thumbColor = currentColor,
                        activeTrackColor = currentColor
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(imageVector = Icons.Default.BrightnessHigh, contentDescription = null, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Modo Atual: ${device.lightingMode}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Color Swatches
            val colorPresets = listOf(
                Pair(0xFFFFD54F, "Luz Noturna Quente"),
                Pair(0xFFE0F7FA, "Luz Fria / Leitura"),
                Pair(0xFFFFFFFF, "Branco Puro 100%"),
                Pair(0xFFE91E63, "Neon Magenta"),
                Pair(0xFF00E676, "Verde Esmeralda"),
                Pair(0xFF2979FF, "Azul Oceano"),
                Pair(0xFFFF5722, "Pôr-do-Sol Alaranjado")
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(colorPresets) { (hex, name) ->
                    val isSelected = device.lightingColorHex == hex
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.LightGray,
                                shape = CircleShape
                            )
                            .clickable { onColorChange(hex) }
                            .testTag("color_preset_${name.replace(" ", "_")}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (hex == 0xFFFFFFFF) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- AIRPLANE MODE CARD ---
@Composable
fun AirplaneModeCard(
    device: LanDevice,
    onRequestToggleAirplane: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (device.isAirplaneModeOn) Icons.Default.AirplanemodeActive else Icons.Default.AirplanemodeInactive,
                        contentDescription = null,
                        tint = if (device.isAirplaneModeOn) Color(0xFF0288D1) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Modo Avião & Conexões",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (device.isAirplaneModeOn) Color(0xFFE1F5FE) else MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = if (device.isAirplaneModeOn) "ATIVADO" else "DESATIVADO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (device.isAirplaneModeOn) Color(0xFF0288D1) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (device.isAirplaneModeOn) {
                    "O smartphone está em Modo Avião com antenas de transmissão cortadas."
                } else {
                    "Permite acionar remotamente o corte de conexões de rádio (Wi-Fi, Bluetooth, Dados Móveis) no smartphone selecionado."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRequestToggleAirplane,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_toggle_airplane_mode"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (device.isAirplaneModeOn) MaterialTheme.colorScheme.primary else Color(0xFF0288D1)
                )
            ) {
                Icon(
                    imageVector = if (device.isAirplaneModeOn) Icons.Default.AirplanemodeInactive else Icons.Default.AirplanemodeActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (device.isAirplaneModeOn) "Desativar Modo Avião Remoto" else "Colocar em Modo Avião",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- QUICK ACTIONS CARD ---
@Composable
fun QuickActionsCard(
    onPlaySiren: () -> Unit,
    onVibrate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Ações Rápidas de Localização",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPlaySiren,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_remote_siren"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Alarme Sonoro")
                }

                OutlinedButton(
                    onClick = onVibrate,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_remote_vibrate"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Vibrar")
                }
            }
        }
    }
}

// --- COMMAND AUDIT LOG CARD ---
@Composable
fun CommandAuditCard(commandLogs: List<RemoteCommandLog>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Consola de Comandos LAN",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (commandLogs.isEmpty()) {
                Text(
                    text = "Aguardando disparo de comandos para o nó de rede...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                commandLogs.take(6).forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = timeFormat.format(Date(log.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = log.status,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
