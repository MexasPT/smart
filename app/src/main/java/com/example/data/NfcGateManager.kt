package com.example.data

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.model.NfcEvent
import com.example.model.NfcEventType
import java.nio.charset.StandardCharsets
import java.util.UUID

class NfcGateManager(private val context: Context) {

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

    companion object {
        const val NFCGATE_PACKAGE = "de.julianostarek.nfcgate"
        const val NFCGATE_GITHUB_URL = "https://github.com/nfcgate/nfcgate/releases"
    }

    /**
     * Checks if NFCGate application is installed on the smartphone.
     */
    fun isNfcGateInstalled(): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(NFCGATE_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(NFCGATE_PACKAGE, 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Creates an Intent to launch the NFCGate app.
     */
    fun getLaunchNfcGateIntent(): Intent? {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(NFCGATE_PACKAGE)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchIntent
    }

    /**
     * Creates an Intent to open the download/GitHub page for NFCGate.
     */
    fun getDownloadNfcGateIntent(): Intent {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(NFCGATE_GITHUB_URL))
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return browserIntent
    }

    /**
     * Creates an NDEF Message for P2P NFC communication.
     */
    fun createNdefPayload(summaryText: String): NdefMessage {
        val mimeRecord = NdefRecord.createMime(
            "application/vnd.com.example.nfcgate.sync",
            summaryText.toByteArray(StandardCharsets.UTF_8)
        )
        val appRecord = NdefRecord.createApplicationRecord(NFCGATE_PACKAGE)
        return NdefMessage(arrayOf(mimeRecord, appRecord))
    }

    /**
     * Enables foreground dispatch when activity is in focus.
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
                } catch (e: Exception) {}
            },
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )

        try {
            adapter.enableForegroundDispatch(activity, pendingIntent, filters, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Disables foreground dispatch.
     */
    fun disableForegroundDispatch(activity: Activity) {
        try {
            nfcAdapter?.disableForegroundDispatch(activity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Trigger device haptic vibration on proximity detection.
     */
    fun triggerHapticFeedback() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 120), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        } catch (e: Exception) {}
    }
}
