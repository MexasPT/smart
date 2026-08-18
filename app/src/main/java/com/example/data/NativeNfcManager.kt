package com.example.data

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.model.NfcEvent
import com.example.model.NfcEventType
import java.nio.charset.StandardCharsets
import java.util.UUID

class NativeNfcManager(private val context: Context) {

    val nfcAdapter: NfcAdapter? by lazy {
        try {
            NfcAdapter.getDefaultAdapter(context)
        } catch (e: Exception) {
            null
        }
    }

    val isNfcSupported: Boolean
        get() = nfcAdapter != null

    val isNfcEnabled: Boolean
        get() = nfcAdapter?.isEnabled == true

    /**
     * Enables Native High-Performance Reader Mode for detecting any NFC-equipped smartphone,
     * smart card, tag, or HCE emulation in physical proximity (<= 4cm).
     */
    fun enableReaderMode(
        activity: Activity,
        onTagDiscovered: (NfcEvent) -> Unit
    ) {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        val options = Bundle()
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 100)

        try {
            adapter.enableReaderMode(activity, { tag ->
                triggerHapticFeedback()
                val event = processDiscoveredTag(tag)
                activity.runOnUiThread {
                    onTagDiscovered(event)
                }
            }, flags, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disableReaderMode(activity: Activity) {
        try {
            nfcAdapter?.disableReaderMode(activity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Enables Native Foreground Dispatch as a secondary receiver.
     */
    fun enableForegroundDispatch(activity: Activity) {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return

        val intent = Intent(activity, activity.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(activity, 0, intent, flags)

        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                try {
                    addDataType("*/*")
                } catch (_: Exception) {}
            },
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )

        val techList = arrayOf(
            arrayOf(IsoDep::class.java.name),
            arrayOf(NfcA::class.java.name),
            arrayOf(NfcB::class.java.name),
            arrayOf(NfcF::class.java.name),
            arrayOf(NfcV::class.java.name),
            arrayOf(Ndef::class.java.name)
        )

        try {
            adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disableForegroundDispatch(activity: Activity) {
        try {
            nfcAdapter?.disableForegroundDispatch(activity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Processes a discovered Tag into a rich NfcEvent model.
     */
    fun processDiscoveredTag(tag: Tag): NfcEvent {
        val rawUid = tag.id ?: byteArrayOf()
        val uidHex = rawUid.joinToString(":") { "%02X".format(it) }.ifEmpty { "TAG-${System.currentTimeMillis() % 10000}" }
        val techs = tag.techList?.map { it.substringAfterLast(".") } ?: listOf("NfcA")

        var payloadSummary = "Dispositivo / Tag NFC Detectado (${techs.joinToString(", ")})"
        var eventType = NfcEventType.PROXIMITY_TOUCH

        // Read NDEF message if present
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                val ndefMessage = ndef.ndefMessage
                if (ndefMessage != null) {
                    val records = ndefMessage.records
                    val recordsText = records.mapNotNull { rec ->
                        try {
                            String(rec.payload, StandardCharsets.UTF_8).filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,;:!?-/@" }
                        } catch (_: Exception) { null }
                    }.filter { it.isNotBlank() }

                    if (recordsText.isNotEmpty()) {
                        payloadSummary = "NDEF: " + recordsText.joinToString(" | ")
                        eventType = NfcEventType.SYNC_PAYLOAD_RECEIVED
                    }
                }
                ndef.close()
            }
        } catch (_: Exception) {}

        // Check if ISO-DEP (typical for other Smartphones running HCE, EMV cards, or smart badges)
        if (techs.contains("IsoDep")) {
            payloadSummary = "Smartphone / Dispositivo HCE (ISO-DEP 14443-4)"
            eventType = NfcEventType.DEVICE_CONNECTED
        }

        return NfcEvent(
            id = UUID.randomUUID().toString(),
            tagUid = uidHex,
            eventType = eventType,
            techList = techs,
            payloadSummary = payloadSummary,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Creates an NDEF Message for native peer-to-peer data beam.
     */
    fun createNdefPayload(summaryText: String): NdefMessage {
        val mimeRecord = NdefRecord.createMime(
            "application/vnd.com.example.nfc.sync",
            summaryText.toByteArray(StandardCharsets.UTF_8)
        )
        return NdefMessage(arrayOf(mimeRecord))
    }

    /**
     * Triggers device haptic vibration on proximity detection.
     */
    fun triggerHapticFeedback() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 150), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        } catch (_: Exception) {}
    }
}
