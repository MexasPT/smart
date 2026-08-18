package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ContactItem
import com.example.model.ExportFormat
import com.example.model.PasswordCredential
import com.example.model.PhotoItem
import com.example.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    uiState: MainUiState,
    onFormatChange: (ExportFormat) -> Unit,
    onKeyChange: (String) -> Unit,
    onToggleIncludeContacts: (Boolean) -> Unit,
    onToggleIncludePhotos: (Boolean) -> Unit,
    onToggleIncludePasswords: (Boolean) -> Unit,
    onExportAndShare: () -> Unit,
    onExportSaveLocal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedContacts = remember(uiState.contacts, uiState.includeContactsInExport) {
        if (uiState.includeContactsInExport) uiState.contacts.filter { it.isSelected } else emptyList()
    }
    val selectedPasswords = remember(uiState.passwords, uiState.includePasswordsInExport) {
        if (uiState.includePasswordsInExport) uiState.passwords.filter { it.isSelected } else emptyList()
    }
    val selectedPhotos = remember(uiState.photos, uiState.includePhotosInExport) {
        if (uiState.includePhotosInExport) uiState.photos.filter { it.isSelected } else emptyList()
    }

    var selectedPreviewTab by remember { mutableIntStateOf(0) }
    var previewSearchQuery by remember { mutableStateOf("") }
    var isEncryptionKeyVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            ExportSummaryCard(
                contactsCount = uiState.contacts.count { it.isSelected },
                photosCount = uiState.photos.count { it.isSelected },
                passwordsCount = uiState.passwords.count { it.isSelected },
                includeContacts = uiState.includeContactsInExport,
                includePhotos = uiState.includePhotosInExport,
                includePasswords = uiState.includePasswordsInExport,
                onToggleIncludeContacts = onToggleIncludeContacts,
                onToggleIncludePhotos = onToggleIncludePhotos,
                onToggleIncludePasswords = onToggleIncludePasswords
            )
        }

        // Live Pre-Export Preview Section
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_live_pre_export_preview"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Pré-visualização dos Dados",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${selectedContacts.size} contactos • ${selectedPasswords.size} credenciais • ${selectedPhotos.size} fotos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Pronto a Exportar",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Tab selector inside Preview
                    ScrollableTabRow(
                        selectedTabIndex = selectedPreviewTab,
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = selectedPreviewTab == 0,
                            onClick = { selectedPreviewTab = 0 },
                            text = { Text("Contactos (${selectedContacts.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedPreviewTab == 1,
                            onClick = { selectedPreviewTab = 1 },
                            text = { Text("Credenciais (${selectedPasswords.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedPreviewTab == 2,
                            onClick = { selectedPreviewTab = 2 },
                            text = { Text("Fotografias (${selectedPhotos.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedPreviewTab == 3,
                            onClick = { selectedPreviewTab = 3 },
                            text = { Text("Código JSON/VCard", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search field inside Preview
                    if (selectedPreviewTab in 0..2) {
                        OutlinedTextField(
                            value = previewSearchQuery,
                            onValueChange = { previewSearchQuery = it },
                            placeholder = { Text("Filtrar itens na pré-visualização…", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (previewSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { previewSearchQuery = "" }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Limpar", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_preview_search"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Content based on selected tab
                    when (selectedPreviewTab) {
                        0 -> {
                            val filteredContacts = selectedContacts.filter {
                                previewSearchQuery.isEmpty() ||
                                        it.displayName.contains(previewSearchQuery, ignoreCase = true) ||
                                        it.phoneNumbers.any { p -> p.contains(previewSearchQuery) } ||
                                        it.emails.any { e -> e.contains(previewSearchQuery, ignoreCase = true) }
                            }

                            if (filteredContacts.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (selectedContacts.isEmpty()) "Nenhum contacto incluído na seleção de exportação." else "Nenhum contacto corresponde à pesquisa.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filteredContacts.forEach { contact ->
                                        PreExportContactCard(contact = contact)
                                    }
                                }
                            }
                        }
                        1 -> {
                            val filteredPasswords = selectedPasswords.filter {
                                previewSearchQuery.isEmpty() ||
                                        it.title.contains(previewSearchQuery, ignoreCase = true) ||
                                        it.username.contains(previewSearchQuery, ignoreCase = true) ||
                                        it.category.contains(previewSearchQuery, ignoreCase = true)
                            }

                            if (filteredPasswords.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (selectedPasswords.isEmpty()) "Nenhuma credencial incluída na seleção de exportação." else "Nenhuma credencial encontrada.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filteredPasswords.forEach { credential ->
                                        PreExportPasswordCard(credential = credential)
                                    }
                                }
                            }
                        }
                        2 -> {
                            val filteredPhotos = selectedPhotos.filter {
                                previewSearchQuery.isEmpty() || it.displayName.contains(previewSearchQuery, ignoreCase = true)
                            }

                            if (filteredPhotos.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (selectedPhotos.isEmpty()) "Nenhuma fotografia selecionada para exportação." else "Nenhuma fotografia encontrada.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filteredPhotos.take(10).forEach { photo ->
                                        PreExportPhotoCard(photo = photo)
                                    }
                                    if (filteredPhotos.size > 10) {
                                        Text(
                                            text = "+ ${filteredPhotos.size - 10} outras fotografias prontas para o pacote",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        3 -> {
                            val generatedCodePreview = remember(
                                selectedContacts,
                                selectedPasswords,
                                selectedPhotos,
                                uiState.exportFormat
                            ) {
                                buildLiveCodePreview(
                                    contacts = selectedContacts,
                                    passwords = selectedPasswords,
                                    photos = selectedPhotos,
                                    format = uiState.exportFormat
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Payload Estruturado (${uiState.exportFormat.title})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Em tempo real",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = generatedCodePreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Formato de Exportação & Segurança",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            FormatSelectionCard(
                selectedFormat = uiState.exportFormat,
                onFormatChange = onFormatChange
            )
        }

        if (uiState.exportFormat == ExportFormat.ENCRYPTED_JSON) {
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Palavra-chave de Cifra AES-256",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "O ficheiro será encriptado com esta chave. Apenas quem tiver a chave poderá descodificar no outro smartphone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = uiState.exportEncryptionKey,
                            onValueChange = onKeyChange,
                            placeholder = { Text("Insira uma chave ou deixe em branco para chave padrão") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_key_input"),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { isEncryptionKeyVisible = !isEncryptionKeyVisible }) {
                                    Icon(
                                        imageVector = if (isEncryptionKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // Export Action Buttons
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onExportAndShare,
                    enabled = !uiState.isExporting && (selectedContacts.isNotEmpty() || selectedPhotos.isNotEmpty() || selectedPasswords.isNotEmpty()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("btn_export_and_share"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("A gerar pacote seguro…")
                    } else {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar e Partilhar com Smartphone", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                FilledTonalButton(
                    onClick = onExportSaveLocal,
                    enabled = !uiState.isExporting && (selectedContacts.isNotEmpty() || selectedPhotos.isNotEmpty() || selectedPasswords.isNotEmpty()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_save_export_local"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gerar e Guardar Ficheiro Localmente")
                }
            }
        }

        // Persistent Output file summary if already generated
        if (uiState.exportedFile != null) {
            item {
                Text(
                    text = "Último Ficheiro Guardado (${uiState.exportedFile.name})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tamanho: ${uiState.exportedFile.length() / 1024} KB",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Guardado em cache segura",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.exportedPreviewContent?.take(400) ?: "Ficheiro pronto para transmissão.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 8
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PreExportContactCard(contact: ContactItem) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.displayName.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (contact.phoneNumbers.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = contact.phoneNumbers.first(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (contact.emails.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = contact.emails.first(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "Incluído",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun PreExportPasswordCard(credential: PasswordCredential) {
    var isRevealed by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = credential.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = credential.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "AES-256",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Utilizador: ${credential.username}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isRevealed) "Password: ${credential.encryptedPassword}" else "Password: ••••••••••••",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = if (isRevealed) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(
                    onClick = { isRevealed = !isRevealed },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Mostrar password",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PreExportPhotoCard(photo: PhotoItem) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = photo.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${photo.sizeFormatted} • ${photo.bucketName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun buildLiveCodePreview(
    contacts: List<ContactItem>,
    passwords: List<PasswordCredential>,
    photos: List<PhotoItem>,
    format: ExportFormat
): String {
    return when (format) {
        ExportFormat.ENCRYPTED_JSON -> {
            """
            {
              "type": "AES_256_GCM_ENCRYPTED",
              "algorithm": "AES/GCM/NoPadding",
              "keyDerivation": "PBKDF2WithHmacSHA256 (65536 it)",
              "iv": "e4a78b91c2d3...",
              "salt": "a1f89c02...",
              "ciphertext": "U2FsdGVkX1+vUZ2WL3... [${contacts.size} contactos + ${passwords.size} credenciais cifradas]",
              "tag": "4f9a0c21b3e8..."
            }
            """.trimIndent()
        }
        ExportFormat.VCARD_ZIP -> {
            val sb = java.lang.StringBuilder()
            sb.append("// VCARD 3.0 Standard Contacts Stream\n")
            contacts.take(3).forEach { c ->
                sb.append("BEGIN:VCARD\n")
                sb.append("VERSION:3.0\n")
                sb.append("FN:${c.displayName}\n")
                if (c.phoneNumbers.isNotEmpty()) sb.append("TEL;TYPE=CELL:${c.phoneNumbers.first()}\n")
                if (c.emails.isNotEmpty()) sb.append("EMAIL;TYPE=INTERNET:${c.emails.first()}\n")
                sb.append("END:VCARD\n\n")
            }
            if (contacts.size > 3) sb.append("... [mais ${contacts.size - 3} contactos em vCard]")
            sb.toString()
        }
        ExportFormat.PLAIN_JSON, ExportFormat.MIGRATION_BUNDLE -> {
            """
            {
              "schema": "nfcgate.vault.migration.v1",
              "generatedAt": "2026-08-18T13:20:00Z",
              "summary": {
                "contactsCount": ${contacts.size},
                "passwordsCount": ${passwords.size},
                "photosCount": ${photos.size}
              },
              "contacts": [
                ${contacts.take(2).joinToString(",\n    ") { """{"name": "${it.displayName}", "phones": ${it.phoneNumbers.size}, "emails": ${it.emails.size}}""" }}${if (contacts.size > 2) ",\n    ... [${contacts.size - 2} outros]" else ""}
              ],
              "passwords": [
                ${passwords.take(2).joinToString(",\n    ") { """{"title": "${it.title}", "username": "${it.username}", "category": "${it.category}"}""" }}${if (passwords.size > 2) ",\n    ... [${passwords.size - 2} outras]" else ""}
              ]
            }
            """.trimIndent()
        }
    }
}

@Composable
fun ExportSummaryCard(
    contactsCount: Int,
    photosCount: Int,
    passwordsCount: Int,
    includeContacts: Boolean,
    includePhotos: Boolean,
    includePasswords: Boolean,
    onToggleIncludeContacts: (Boolean) -> Unit,
    onToggleIncludePhotos: (Boolean) -> Unit,
    onToggleIncludePasswords: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Resumo dos Dados Selecionados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Escolha quais as categorias que pretende incluir no pacote seguro:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            ExportCategoryCheckboxRow(
                icon = Icons.Default.ContactPage,
                title = "Contactos",
                count = contactsCount,
                checked = includeContacts,
                onCheckedChange = onToggleIncludeContacts
            )

            ExportCategoryCheckboxRow(
                icon = Icons.Default.PhotoLibrary,
                title = "Fotografias",
                count = photosCount,
                checked = includePhotos,
                onCheckedChange = onToggleIncludePhotos
            )

            ExportCategoryCheckboxRow(
                icon = Icons.Default.Key,
                title = "Passwords & Credenciais",
                count = passwordsCount,
                checked = includePasswords,
                onCheckedChange = onToggleIncludePasswords
            )
        }
    }
}

@Composable
fun ExportCategoryCheckboxRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "$count selecionados",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun FormatSelectionCard(
    selectedFormat: ExportFormat,
    onFormatChange: (ExportFormat) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ExportFormat.values().forEach { format ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFormatChange(format) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedFormat == format,
                        onClick = { onFormatChange(format) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = format.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedFormat == format) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = when (format) {
                                ExportFormat.ENCRYPTED_JSON -> "Cifra forte com salt SHA-256 e IV de 128-bits. Máxima segurança."
                                ExportFormat.PLAIN_JSON -> "Estrutura JSON clara legível por computadores e outras apps."
                                ExportFormat.VCARD_ZIP -> "Compatível com a aplicação de Contactos padrão do Android e iOS."
                                ExportFormat.MIGRATION_BUNDLE -> "Ficheiro organizado para migração rápida entre telemóveis."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
