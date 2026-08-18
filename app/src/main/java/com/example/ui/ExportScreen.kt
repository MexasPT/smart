package com.example.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val selectedContactsCount = uiState.contacts.count { it.isSelected }
    val selectedPhotosCount = uiState.photos.count { it.isSelected }
    val selectedPasswordsCount = uiState.passwords.count { it.isSelected }

    var isPasswordVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            ExportSummaryCard(
                contactsCount = selectedContactsCount,
                photosCount = selectedPhotosCount,
                passwordsCount = selectedPasswordsCount,
                includeContacts = uiState.includeContactsInExport,
                includePhotos = uiState.includePhotosInExport,
                includePasswords = uiState.includePasswordsInExport,
                onToggleIncludeContacts = onToggleIncludeContacts,
                onToggleIncludePhotos = onToggleIncludePhotos,
                onToggleIncludePasswords = onToggleIncludePasswords
            )
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
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
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
                    enabled = !uiState.isExporting && (selectedContactsCount > 0 || selectedPhotosCount > 0 || selectedPasswordsCount > 0),
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
                    enabled = !uiState.isExporting,
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

        // Preview inspector card if exported
        if (uiState.exportedPreviewContent != null) {
            item {
                Text(
                    text = "Pré-visualização do Ficheiro Gerado (${uiState.exportedFile?.name})",
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
                                text = "Tamanho: ${uiState.exportedFile?.length()?.div(1024) ?: 0} KB",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Pronto para transferência",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.exportedPreviewContent.take(800) + if (uiState.exportedPreviewContent.length > 800) "\n\n... [restante conteúdo preservado em segurança]" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 15
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
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
