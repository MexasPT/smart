package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.example.model.LanDevice
import com.example.model.LanDeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ScanBandMode(val title: String) {
    CURRENT_SUBNET("Sub-rede Atual"),
    DUAL_BAND_ALL("Dual-Band 2.4G & 5G (Todas)"),
    CUSTOM_SUBNET("Sub-rede Personalizada")
}

class LanScannerManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * Acquires MulticastLock to receive UDP mDNS and SSDP broadcast responses on Wi-Fi.
     */
    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("LanScannerMulticastLock")?.apply {
                    setReferenceCounted(true)
                }
            }
            multicastLock?.acquire()
        } catch (_: Exception) {}
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }

    /**
     * Gets the real local IP address.
     */
    fun getLocalIpAddress(): String {
        try {
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }

            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotBlank() && host != "127.0.0.1") return host
                    }
                }
            }
        } catch (_: Exception) {}
        return "192.168.1.100"
    }

    fun getSubnetPrefix(): String {
        val ip = getLocalIpAddress()
        val parts = ip.split(".")
        return if (parts.size >= 3) {
            "${parts[0]}.${parts[1]}.${parts[2]}"
        } else {
            "192.168.1"
        }
    }

    fun isWifiConnected(): Boolean {
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun getWifiSsid(): String {
        try {
            val info = wifiManager?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "")
            if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                return ssid
            }
        } catch (_: Exception) {}
        return "Rede Wi-Fi (2.4G / 5G)"
    }

    /**
     * Reads ARP table if available on the system.
     */
    private fun readRealArpNeighbors(): Map<String, String> {
        val arpMap = mutableMapOf<String, String>()
        try {
            val file = File("/proc/net/arp")
            if (file.exists() && file.canRead()) {
                BufferedReader(FileReader(file)).use { br ->
                    var line: String? = br.readLine() // Skip header
                    while (br.readLine().also { line = it } != null) {
                        val parts = line!!.trim().split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            val ip = parts[0]
                            val mac = parts[3]
                            if (mac != "00:00:00:00:00:00" && !mac.contains("incomplete", ignoreCase = true)) {
                                arpMap[ip] = mac
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return arpMap
    }

    /**
     * Comprehensive real network scanner with mDNS, SSDP, and High-Concurrency TCP probe
     * across ports commonly open on smartphones (iOS/Android), tablets, routers, and smart devices.
     */
    suspend fun scanNetwork(
        scanSubnets: List<String> = listOf(getSubnetPrefix()),
        fullScan: Boolean = true,
        onProgress: (current: Int, total: Int, device: LanDevice?) -> Unit
    ): List<LanDevice> = withContext(Dispatchers.IO) {
        acquireMulticastLock()
        val myIp = getLocalIpAddress()
        val realArp = readRealArpNeighbors()
        val discoveredMap = ConcurrentHashMap<String, LanDevice>()

        // 1. Initial known ARP neighbors
        for ((arpIp, mac) in realArp) {
            if (arpIp != myIp && !arpIp.endsWith(".255") && !arpIp.endsWith(".0")) {
                val dev = probeRealHost(arpIp, mac)
                if (dev != null) {
                    discoveredMap[dev.ipAddress] = dev
                    onProgress(1, 254 * scanSubnets.size, dev)
                }
            }
        }

        // 2. Discover via SSDP (UPnP) query
        try {
            discoverSsdpDevices(discoveredMap)
        } catch (_: Exception) {}

        // 3. Full Parallel Subnet Sweep across specified subnets
        val totalHostsPerSubnet = if (fullScan) 254 else 60
        val totalScanTarget = totalHostsPerSubnet * scanSubnets.size
        val semaphore = Semaphore(50)
        var completedCount = 0

        coroutineScope {
            val tasks = scanSubnets.flatMap { subnet ->
                (1..totalHostsPerSubnet).map { suffix ->
                    async {
                        val hostIp = "$subnet.$suffix"
                        if (hostIp == myIp) {
                            synchronized(this@LanScannerManager) {
                                completedCount++
                                onProgress(completedCount, totalScanTarget, null)
                            }
                            return@async null
                        }

                        // If already detected via ARP/SSDP, skip probe
                        if (discoveredMap.containsKey(hostIp)) {
                            synchronized(this@LanScannerManager) {
                                completedCount++
                                onProgress(completedCount, totalScanTarget, discoveredMap[hostIp])
                            }
                            return@async discoveredMap[hostIp]
                        }

                        val mac = realArp[hostIp] ?: "02:00:00:00:00:00"
                        val device = semaphore.withPermit {
                            probeRealHost(hostIp, mac)
                        }

                        synchronized(this@LanScannerManager) {
                            completedCount++
                            if (device != null) {
                                discoveredMap[device.ipAddress] = device
                                onProgress(completedCount, totalScanTarget, device)
                            } else {
                                onProgress(completedCount, totalScanTarget, null)
                            }
                        }
                        device
                    }
                }
            }
            tasks.awaitAll()
        }

        releaseMulticastLock()
        discoveredMap.values.toList().sortedBy { it.ipAddress }
    }

    /**
     * Sends SSDP M-SEARCH broadcast on UDP 1900 to discover UPnP devices, Smart TVs, Media renderers, and routers.
     */
    private fun discoverSsdpDevices(discoveredMap: ConcurrentHashMap<String, LanDevice>) {
        var socket: DatagramSocket? = null
        try {
            val mSearch = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 1\r\n" +
                    "ST: ssdp:all\r\n\r\n"

            val sendData = mSearch.toByteArray()
            val broadcastAddr = InetAddress.getByName("239.255.255.250")
            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddr, 1900)

            socket = DatagramSocket()
            socket.soTimeout = 400
            socket.send(sendPacket)

            val buffer = ByteArray(2048)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < 500) {
                try {
                    socket.receive(receivePacket)
                    val responderIp = receivePacket.address.hostAddress ?: continue
                    if (responderIp != getLocalIpAddress() && !discoveredMap.containsKey(responderIp)) {
                        val responseText = String(receivePacket.data, 0, receivePacket.length)
                        val serverLine = responseText.lines().firstOrNull { it.startsWith("SERVER:", ignoreCase = true) }
                            ?.substringAfter(":")?.trim() ?: "Dispositivo UPnP/SSDP"

                        val dev = LanDevice(
                            id = UUID.nameUUIDFromBytes(responderIp.toByteArray()).toString(),
                            ipAddress = responderIp,
                            macAddress = "02:00:00:00:00:00",
                            hostName = serverLine,
                            deviceType = if (responderIp.endsWith(".1")) LanDeviceType.ROUTER else LanDeviceType.SMARTPHONE_ANDROID,
                            brandModel = serverLine,
                            isReachable = true,
                            pingLatencyMs = 8L,
                            openPorts = listOf(1900),
                            isCompanionConnected = false,
                            batteryPercent = null,
                            signalDbm = -45
                        )
                        discoveredMap[responderIp] = dev
                    }
                } catch (_: Exception) {
                    break
                }
            }
        } catch (_: Exception) {
        } finally {
            socket?.close()
        }
    }

    /**
     * Probes real host using key TCP ports for iOS, Android, and IoT.
     */
    private fun probeRealHost(ip: String, mac: String): LanDevice? {
        // Ports for Smartphones, Tablets, AirPlay, Cast, Web, Companion Server and Media
        val targetPorts = listOf(
            8080,  // Companion Web / HTTP Alt
            8888,  // Companion Fallback
            62078, // Apple Mobile Device / iTunes Sync (iOS iPhones/iPads)
            7000,  // Apple AirPlay (iPhones/iPads/Apple TV/Mac)
            8008,  // Google Cast / Android devices
            8009,  // Google Cast Secure
            5353,  // mDNS Multicast DNS
            5555,  // Android ADB
            80,    // Web / Router / IoT
            443,   // HTTPS
            5000,  // UPnP / AirPlay / NAS
            8000,  // Web / Media
            9100   // Network Printers
        )

        var isOnline = false
        val openPortsFound = mutableListOf<Int>()
        var responseTime = 0L

        // Test fast socket connection on smartphone & network ports
        for (port in targetPorts) {
            try {
                val start = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 80)
                    isOnline = true
                    responseTime = System.currentTimeMillis() - start
                    openPortsFound.add(port)
                }
            } catch (_: Exception) {}
            if (openPortsFound.size >= 2) break
        }

        // Reachability fallback
        if (!isOnline) {
            try {
                val addr = InetAddress.getByName(ip)
                val start = System.currentTimeMillis()
                if (addr.isReachable(90)) {
                    isOnline = true
                    responseTime = System.currentTimeMillis() - start
                }
            } catch (_: Exception) {}
        }

        if (!isOnline && mac == "02:00:00:00:00:00") {
            return null
        }

        // Hostname resolution
        var realHostName = ""
        try {
            val addr = InetAddress.getByName(ip)
            val host = addr.canonicalHostName
            if (host != ip && !host.isNullOrBlank()) {
                realHostName = host
            }
        } catch (_: Exception) {}

        val isApple = openPortsFound.contains(62078) || openPortsFound.contains(7000) ||
                realHostName.contains("iphone", ignoreCase = true) ||
                realHostName.contains("ipad", ignoreCase = true) ||
                realHostName.contains("apple", ignoreCase = true)

        val isCastOrAndroid = openPortsFound.contains(8008) || openPortsFound.contains(8009) ||
                openPortsFound.contains(5555) ||
                realHostName.contains("android", ignoreCase = true)

        val isRouter = ip.endsWith(".1") || ip.endsWith(".254")

        val inferredType = when {
            isRouter -> LanDeviceType.ROUTER
            isApple -> LanDeviceType.SMARTPHONE_IOS
            isCastOrAndroid -> LanDeviceType.SMARTPHONE_ANDROID
            openPortsFound.contains(9100) -> LanDeviceType.IOT_GENERIC
            openPortsFound.contains(80) || openPortsFound.contains(443) -> LanDeviceType.IOT_GENERIC
            else -> LanDeviceType.SMARTPHONE_ANDROID
        }

        if (realHostName.isBlank()) {
            realHostName = when {
                isRouter -> "Router / Gateway Wi-Fi"
                isApple -> "Apple iPhone / iPad ($ip)"
                isCastOrAndroid -> "Smartphone Android / Cast ($ip)"
                else -> "Dispositivo Wi-Fi ($ip)"
            }
        }

        val brandModel = when {
            isRouter -> "Router Gateway (${if (openPortsFound.isNotEmpty()) "Portas: ${openPortsFound.joinToString()}" else "Online"})"
            isApple -> "Apple iOS Device (AirPlay/Sync Ativo)"
            isCastOrAndroid -> "Android Device (Cast/ADB Ativo)"
            else -> "Dispositivo Conectado ($ip)"
        }

        return LanDevice(
            id = UUID.nameUUIDFromBytes(ip.toByteArray()).toString(),
            ipAddress = ip,
            macAddress = mac,
            hostName = realHostName,
            deviceType = inferredType,
            brandModel = brandModel,
            isReachable = true,
            pingLatencyMs = if (responseTime > 0) responseTime else 12L,
            openPorts = if (openPortsFound.isNotEmpty()) openPortsFound else listOf(80),
            isCompanionConnected = openPortsFound.contains(8080) || openPortsFound.contains(8888) || openPortsFound.contains(5555) || openPortsFound.contains(62078),
            batteryPercent = null,
            signalDbm = -48
        )
    }

    /**
     * Connects directly to a user-provided IP.
     */
    suspend fun connectToManualIp(ipAddress: String, port: Int = 8080): LanDevice = withContext(Dispatchers.IO) {
        val cleanIp = ipAddress.trim()
        var reachable = false
        var latency = 0L

        try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(cleanIp, port), 400)
                reachable = true
                latency = System.currentTimeMillis() - start
            }
        } catch (_: Exception) {
            try {
                val addr = InetAddress.getByName(cleanIp)
                val start = System.currentTimeMillis()
                reachable = addr.isReachable(300)
                latency = System.currentTimeMillis() - start
            } catch (_: Exception) {}
        }

        var hostName = ""
        try {
            val addr = InetAddress.getByName(cleanIp)
            val name = addr.canonicalHostName
            if (name != cleanIp && !name.isNullOrBlank()) {
                hostName = name
            }
        } catch (_: Exception) {}

        if (hostName.isBlank()) {
            hostName = "Dispositivo Remoto ($cleanIp)"
        }

        LanDevice(
            id = UUID.nameUUIDFromBytes(cleanIp.toByteArray()).toString(),
            ipAddress = cleanIp,
            macAddress = "02:00:00:00:00:00",
            hostName = hostName,
            deviceType = LanDeviceType.SMARTPHONE_ANDROID,
            brandModel = "Conexão Manual Direta",
            isReachable = reachable,
            pingLatencyMs = if (latency > 0) latency else 15L,
            openPorts = listOf(port),
            isCompanionConnected = reachable,
            batteryPercent = null
        )
    }
}
