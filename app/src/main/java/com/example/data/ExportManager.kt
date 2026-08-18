package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.ContactItem
import com.example.model.ExportFormat
import com.example.model.PasswordCredential
import com.example.model.PhotoItem
import com.example.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportManager(private val context: Context) {

    /**
     * Builds the JSON representation of the selected data package.
     */
    fun buildJsonPackage(
        contacts: List<ContactItem>,
        photos: List<PhotoItem>,
        passwords: List<PasswordCredential>,
        includeContacts: Boolean = true,
        includePhotos: Boolean = true,
        includePasswords: Boolean = true
    ): JSONObject {
        val root = JSONObject()
        root.put("app", "NFCGate Sync & Data Vault")
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(Date()))
        root.put("deviceModel", android.os.Build.MODEL)
        root.put("deviceManufacturer", android.os.Build.MANUFACTURER)
        root.put("androidVersion", android.os.Build.VERSION.RELEASE)

        val stats = JSONObject()
        stats.put("contactsCount", if (includeContacts) contacts.size else 0)
        stats.put("photosCount", if (includePhotos) photos.size else 0)
        stats.put("passwordsCount", if (includePasswords) passwords.size else 0)
        root.put("summary", stats)

        if (includeContacts) {
            val contactsArray = JSONArray()
            for (c in contacts) {
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("displayName", c.displayName)
                cObj.put("phoneNumbers", JSONArray(c.phoneNumbers))
                cObj.put("emails", JSONArray(c.emails))
                cObj.put("note", c.note)
                contactsArray.put(cObj)
            }
            root.put("contacts", contactsArray)
        }

        if (includePhotos) {
            val photosArray = JSONArray()
            for (p in photos) {
                val pObj = JSONObject()
                pObj.put("id", p.id)
                pObj.put("displayName", p.displayName)
                pObj.put("dateTaken", p.dateTaken)
                pObj.put("sizeFormatted", p.sizeFormatted)
                pObj.put("bucketName", p.bucketName)
                pObj.put("uri", p.uri)
                photosArray.put(pObj)
            }
            root.put("photos", photosArray)
        }

        if (includePasswords) {
            val passArray = JSONArray()
            for (pw in passwords) {
                val pwObj = JSONObject()
                pwObj.put("id", pw.id)
                pwObj.put("title", pw.title)
                pwObj.put("username", pw.username)
                pwObj.put("password", pw.encryptedPassword)
                pwObj.put("category", pw.category)
                pwObj.put("notes", pw.notes)
                passArray.put(pwObj)
            }
            root.put("passwords", passArray)
        }

        return root
    }

    /**
     * Generates the export file and creates a share intent.
     */
    suspend fun generateExportFile(
        contacts: List<ContactItem>,
        photos: List<PhotoItem>,
        passwords: List<PasswordCredential>,
        format: ExportFormat,
        encryptionKey: String,
        includeContacts: Boolean = true,
        includePhotos: Boolean = true,
        includePasswords: Boolean = true
    ): Pair<File, String> = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val jsonPayload = buildJsonPackage(
            contacts = contacts,
            photos = photos,
            passwords = passwords,
            includeContacts = includeContacts,
            includePhotos = includePhotos,
            includePasswords = includePasswords
        ).toString(2)

        val targetFile: File
        val contentToWrite: String

        when (format) {
            ExportFormat.ENCRYPTED_JSON -> {
                targetFile = File(exportDir, "NFCGate_Vault_Encrypted_${timeStamp}.json.enc")
                val keyToUse = if (encryptionKey.isNotBlank()) encryptionKey else "NFCGATE_SECURE_TRANSFER_2026"
                contentToWrite = SecurityUtils.encrypt(jsonPayload, keyToUse)
            }
            ExportFormat.PLAIN_JSON -> {
                targetFile = File(exportDir, "NFCGate_Vault_${timeStamp}.json")
                contentToWrite = jsonPayload
            }
            ExportFormat.VCARD_ZIP -> {
                targetFile = File(exportDir, "NFCGate_Contacts_${timeStamp}.vcf")
                contentToWrite = ContactsRepository(context).exportToVCard(contacts)
            }
            ExportFormat.MIGRATION_BUNDLE -> {
                targetFile = File(exportDir, "NFCGate_Migration_Package_${timeStamp}.json")
                contentToWrite = jsonPayload
            }
        }

        FileOutputStream(targetFile).use { fos ->
            fos.write(contentToWrite.toByteArray())
        }

        Pair(targetFile, contentToWrite)
    }

    /**
     * Shares the exported file using Android Share Sheet.
     */
    fun shareExportFile(file: File, mimeType: String = "text/plain") {
        val uri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "NFCGate Sync Export: ${file.name}")
            putExtra(Intent.EXTRA_TEXT, "Dados exportados em segurança via NFCGate Sync & Data Vault.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(shareIntent, "Partilhar ficheiro exportado")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
