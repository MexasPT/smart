package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.LanDevice
import com.example.model.LanDeviceType
import com.example.model.RemoteCommandType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ConnectedClient(
    val id: String,
    val ip: String,
    val userAgent: String,
    val deviceName: String,
    var lastSeen: Long = System.currentTimeMillis()
)

data class PendingCommand(
    val commandType: RemoteCommandType,
    val extraValue: Any?,
    val timestamp: Long = System.currentTimeMillis()
)

class CompanionServer(
    private val context: Context,
    private val onDeviceRegistered: (LanDevice) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val connectedClients = ConcurrentHashMap<String, ConnectedClient>()
    private val pendingCommands = ConcurrentHashMap<String, MutableList<PendingCommand>>()

    private val _incomingLogFlow = MutableSharedFlow<String>()
    val incomingLogFlow = _incomingLogFlow.asSharedFlow()

    var serverPort: Int = 8080
        private set

    var isRunning: Boolean = false
        private set

    fun startServer(port: Int = 8080) {
        if (isRunning) return
        serverPort = port
        serverJob = scope.launch {
            try {
                serverSocket = try {
                    ServerSocket(port)
                } catch (_: Exception) {
                    ServerSocket(8888).also { serverPort = 8888 }
                }
                isRunning = true
                Log.d("CompanionServer", "Companion HTTP Server started on port $serverPort")

                while (isActive && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.e("CompanionServer", "Server error", e)
            } finally {
                isRunning = false
            }
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
    }

    fun queueCommandForClient(targetIp: String, commandType: RemoteCommandType, extraValue: Any?) {
        val list = pendingCommands.getOrPut(targetIp) { mutableListOf() }
        synchronized(list) {
            list.add(PendingCommand(commandType, extraValue))
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val output = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0]
            val path = parts[1]

            // Read headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                val headerParts = line!!.split(":", limit = 2)
                if (headerParts.size == 2) {
                    headers[headerParts[0].trim().lowercase()] = headerParts[1].trim()
                }
            }

            val clientIp = socket.inetAddress.hostAddress ?: "unknown"
            val userAgent = headers["user-agent"] ?: "Navegador Web / Dispositivo"

            // Read body for POST
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                reader.read(buf, 0, contentLength)
                String(buf)
            } else ""

            when {
                path == "/" || path == "/index.html" -> {
                    // Register client as active
                    registerClient(clientIp, userAgent)
                    sendHtmlResponse(output, getCompanionWebPageHtml(clientIp))
                }
                path.startsWith("/api/poll") -> {
                    registerClient(clientIp, userAgent)
                    handlePollCommands(clientIp, output)
                }
                path.startsWith("/api/command") -> {
                    sendJsonResponse(output, "{\"status\":\"ok\",\"received\":true}")
                }
                path.startsWith("/api/status") -> {
                    sendJsonResponse(output, "{\"server\":\"NFC-LAN-Controller\",\"clients\":${connectedClients.size}}")
                }
                else -> {
                    send404Response(output)
                }
            }
        } catch (e: Exception) {
            Log.e("CompanionServer", "Error handling HTTP client", e)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun registerClient(clientIp: String, userAgent: String) {
        val isApple = userAgent.contains("iPhone", ignoreCase = true) ||
                userAgent.contains("iPad", ignoreCase = true) ||
                userAgent.contains("Macintosh", ignoreCase = true)

        val isAndroid = userAgent.contains("Android", ignoreCase = true)

        val deviceName = when {
            isApple -> "Apple iPhone / iPad ($clientIp)"
            isAndroid -> "Smartphone Android ($clientIp)"
            else -> "Dispositivo Web Remoto ($clientIp)"
        }

        val client = connectedClients.getOrPut(clientIp) {
            ConnectedClient(
                id = UUID.nameUUIDFromBytes(clientIp.toByteArray()).toString(),
                ip = clientIp,
                userAgent = userAgent,
                deviceName = deviceName
            )
        }
        client.lastSeen = System.currentTimeMillis()

        val lanDevice = LanDevice(
            id = UUID.nameUUIDFromBytes(clientIp.toByteArray()).toString(),
            ipAddress = clientIp,
            macAddress = "02:00:00:00:00:00",
            hostName = deviceName,
            deviceType = if (isApple) LanDeviceType.SMARTPHONE_IOS else LanDeviceType.SMARTPHONE_ANDROID,
            brandModel = "Web Companion Conectado (Porta $serverPort)",
            isReachable = true,
            pingLatencyMs = 4L,
            openPorts = listOf(serverPort),
            isCompanionConnected = true,
            batteryPercent = 88,
            signalDbm = -38
        )
        onDeviceRegistered(lanDevice)
    }

    private fun handlePollCommands(clientIp: String, output: OutputStream) {
        val list = pendingCommands[clientIp]
        val cmds = mutableListOf<PendingCommand>()
        if (list != null) {
            synchronized(list) {
                cmds.addAll(list)
                list.clear()
            }
        }

        val jsonArray = StringBuilder("[")
        cmds.forEachIndexed { index, cmd ->
            val extraJson = when (val extra = cmd.extraValue) {
                is Number -> "$extra"
                is String -> "\"$extra\""
                null -> "null"
                else -> "\"$extra\""
            }
            jsonArray.append("{\"type\":\"${cmd.commandType.name}\",\"extra\":$extraJson}")
            if (index < cmds.size - 1) jsonArray.append(",")
        }
        jsonArray.append("]")

        sendJsonResponse(output, "{\"commands\":$jsonArray}")
    }

    private fun sendJsonResponse(output: OutputStream, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendHtmlResponse(output: OutputStream, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun send404Response(output: OutputStream) {
        val msg = "404 Not Found"
        val header = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${msg.length}\r\n" +
                "Connection: close\r\n\r\n$msg"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    /**
     * Interactive Web Console loaded by any iPhone, iPad, Android phone, tablet or PC.
     */
    private fun getCompanionWebPageHtml(clientIp: String): String {
        return """
<!DOCTYPE html>
<html lang="pt">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Dispositivo Remoto Conectado | NFC & Wi-Fi Controller</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background: #0f172a;
            color: #f8fafc;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 20px;
            text-align: center;
            transition: background 0.3s ease;
        }
        .container {
            max-width: 480px;
            width: 100%;
            background: #1e293b;
            border-radius: 24px;
            padding: 24px;
            box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.4);
            border: 1px solid #334155;
        }
        .badge {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 14px;
            border-radius: 999px;
            background: rgba(16, 185, 129, 0.2);
            color: #34d399;
            font-size: 13px;
            font-weight: 600;
            margin-bottom: 16px;
        }
        .pulse-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: #10b981;
            box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
            animation: pulse 1.5s infinite;
        }
        @keyframes pulse {
            0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
            70% { transform: scale(1); box-shadow: 0 0 0 8px rgba(16, 185, 129, 0); }
            100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
        }
        h1 { font-size: 20px; font-weight: 700; margin-bottom: 8px; }
        p { color: #94a3b8; font-size: 14px; line-height: 1.5; margin-bottom: 20px; }
        .card {
            background: #0f172a;
            border-radius: 16px;
            padding: 16px;
            margin-bottom: 16px;
            text-align: left;
            border: 1px solid #334155;
        }
        .card-title { font-size: 13px; font-weight: 700; color: #38bdf8; text-transform: uppercase; margin-bottom: 8px; }
        .video-box {
            width: 100%;
            height: 200px;
            background: #000;
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
            position: relative;
        }
        video { width: 100%; height: 100%; object-fit: cover; }
        .btn {
            background: #2563eb;
            color: #fff;
            border: none;
            border-radius: 12px;
            padding: 12px 18px;
            font-size: 14px;
            font-weight: 600;
            width: 100%;
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            margin-top: 10px;
        }
        .btn:active { transform: scale(0.98); }
        .btn-success { background: #059669; }
        .btn-warning { background: #d97706; }
        .btn-danger { background: #dc2626; }
        .log-box {
            font-family: monospace;
            font-size: 12px;
            background: #050b14;
            padding: 10px;
            border-radius: 8px;
            max-height: 100px;
            overflow-y: auto;
            color: #a5f3fc;
            text-align: left;
        }
        .strobe { animation: strobe 0.1s infinite alternate; }
        @keyframes strobe { from { background: #ffffff; } to { background: #000000; } }
    </style>
</head>
<body id="bodyRoot">
    <div class="container">
        <div class="badge">
            <span class="pulse-dot"></span>
            Ligação Ativa com App Controladora
        </div>
        <h1>📱 Dispositivo Remoto Pareado</h1>
        <p>Este telemóvel está sincronizado via Wi-Fi ($clientIp) e pronto para receber comandos em tempo real.</p>

        <div class="card">
            <div class="card-title">📹 Câmara em Direto</div>
            <div class="video-box">
                <video id="videoElement" autoplay playsinline muted></video>
                <div id="videoPlaceholder" style="color: #64748b; font-size: 13px;">Câmara em Espera de Comando</div>
            </div>
            <button class="btn btn-success" id="btnStartCam" onclick="toggleCamera()">Ativar Câmara Local</button>
        </div>

        <div class="card">
            <div class="card-title">💡 Iluminação & Sons</div>
            <button class="btn" onclick="testFlashlight()">⚡ Testar Lanterna / Ecrã</button>
            <button class="btn btn-warning" onclick="playAudioAlarm()">🔔 Testar Alarme / Sirene</button>
        </div>

        <div class="card">
            <div class="card-title">📡 Registo de Comandos Recebidos</div>
            <div class="log-box" id="logConsole">Pronto para receber ordens do controlador...</div>
        </div>
    </div>

    <script>
        let stream = null;
        let isTorchOn = false;
        const video = document.getElementById('videoElement');
        const placeholder = document.getElementById('videoPlaceholder');
        const logConsole = document.getElementById('logConsole');
        const bodyRoot = document.getElementById('bodyRoot');

        function addLog(msg) {
            const time = new Date().toLocaleTimeString();
            logConsole.innerHTML = "[" + time + "] " + msg + "<br>" + logConsole.innerHTML;
        }

        async function toggleCamera() {
            if (stream) {
                stream.getTracks().forEach(t => t.stop());
                stream = null;
                video.style.display = 'none';
                placeholder.style.display = 'block';
                document.getElementById('btnStartCam').innerText = 'Ativar Câmara Local';
                addLog("Câmara desativada");
            } else {
                try {
                    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
                    video.srcObject = stream;
                    video.style.display = 'block';
                    placeholder.style.display = 'none';
                    document.getElementById('btnStartCam').innerText = 'Desativar Câmara';
                    addLog("Câmara ligada e a transmitir");
                } catch(e) {
                    addLog("Erro ao aceder à câmara: " + e.message);
                }
            }
        }

        function playAudioAlarm() {
            try {
                const ctx = new (window.AudioContext || window.webkitAudioContext)();
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(800, ctx.currentTime);
                osc.frequency.exponentialRampToValueAtTime(1400, ctx.currentTime + 0.3);
                gain.gain.setValueAtTime(1, ctx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 1.2);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start();
                osc.stop(ctx.currentTime + 1.2);
                if (navigator.vibrate) navigator.vibrate([200, 100, 200, 100, 400]);
                addLog("🚨 Alarme sonoro reproduzido!");
            } catch(e) {
                addLog("Erro de áudio: " + e.message);
            }
        }

        function testFlashlight() {
            isTorchOn = !isTorchOn;
            if (isTorchOn) {
                bodyRoot.style.background = '#ffffff';
                bodyRoot.style.color = '#000000';
                addLog("💡 Lanterna de ecrã LIGADA");
            } else {
                bodyRoot.style.background = '#0f172a';
                bodyRoot.style.color = '#f8fafc';
                addLog("💡 Lanterna de ecrã DESLIGADA");
            }
        }

        // Long Poll commands from App Controller
        async function pollCommands() {
            try {
                const res = await fetch('/api/poll');
                if (res.ok) {
                    const data = await res.json();
                    if (data.commands && data.commands.length > 0) {
                        data.commands.forEach(cmd => {
                            executeCommand(cmd.type, cmd.extra);
                        });
                    }
                }
            } catch(e) {}
            setTimeout(pollCommands, 800);
        }

        function executeCommand(type, extra) {
            addLog("Comando recebido da app: <b>" + type + "</b>");
            if (type === 'CAMERA_START') {
                if (!stream) toggleCamera();
            } else if (type === 'CAMERA_STOP') {
                if (stream) toggleCamera();
            } else if (type === 'FLASHLIGHT_TOGGLE') {
                testFlashlight();
            } else if (type === 'FLASHLIGHT_STROBE') {
                bodyRoot.classList.add('strobe');
                setTimeout(() => bodyRoot.classList.remove('strobe'), 4000);
                addLog("⚡ Estroboscópio ativado!");
            } else if (type === 'SIREN_ALARM') {
                playAudioAlarm();
            } else if (type === 'DEVICE_VIBRATE') {
                if (navigator.vibrate) navigator.vibrate(300);
                addLog("📳 Vibração disparada");
            } else if (type === 'LIGHTING_SET_COLOR') {
                const hex = extra ? '#' + Number(extra).toString(16).slice(-6) : '#ffd54f';
                bodyRoot.style.background = hex;
                addLog("🎨 Cor alterada para " + hex);
            }
        }

        window.onload = function() {
            pollCommands();
        };
    </script>
</body>
</html>
        """.trimIndent()
    }
}
