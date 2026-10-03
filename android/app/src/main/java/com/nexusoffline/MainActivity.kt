package com.nexusoffline

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ComponentCallbacks2
import android.bluetooth.BluetoothManager
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.Base64
import android.net.wifi.WifiManager
import android.view.ViewGroup
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.annotation.RequiresApi
import androidx.webkit.WebViewAssetLoader
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var connections: ConnectionsClient
    private lateinit var secureStateStore: EncryptedStateStore
    private lateinit var encryptedAttachmentStore: EncryptedAttachmentStore
    private lateinit var localAIEngine: LocalAIEngine
    private lateinit var localAIModelStore: LocalAIModelStore
    private val discovered = ConcurrentHashMap<String, String>()
    private val connected = ConcurrentHashMap<String, String>()
    private val outgoingFiles = ConcurrentHashMap<String, OutgoingFile>()
    private val outgoingPayloads = ConcurrentHashMap<Long, String>()
    private val incomingExpectations = ConcurrentHashMap<String, IncomingExpectation>()
    private val incomingPayloads = ConcurrentHashMap<Long, IncomingPayload>()
    private val pendingPermissionActions = linkedSetOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val localAIMemoryTrimInProgress = AtomicBoolean(false)
    private val externalAttachmentFiles = ConcurrentHashMap<File, Uri>()
    @Volatile private var pendingAttachmentExport: File? = null
    @Volatile private var pendingLocalAIImport = false
    @Volatile private var localAIImportInProgress = false
    @Volatile private var attachmentActionInProgress = false
    @Volatile private var clearInProgress = false
    @Volatile private var pendingFileTarget: String? = null
    @Volatile private var permissionRequestInProgress = false
    @Volatile private var advertising = false
    @Volatile private var discovering = false
    @Volatile private var pendingAttachmentMigrationWarning: Int? = null
    private var webPageReady = false
    private val localEndpointName: String
        get() = "NX-" + getSharedPreferences("nexus", MODE_PRIVATE).let { prefs ->
            prefs.getString("device_id", null) ?: ("${System.currentTimeMillis()}-${(1000..9999).random()}").also {
                prefs.edit().putString("device_id", it).apply()
            }
        }.take(20)

    private data class OutgoingFile(
        val transferId: String,
        val endpointId: String,
        val filename: String,
        val plaintextSize: Long,
        val plaintextFile: File,
        var encryptedFile: File? = null,
        var plaintextSha256: String? = null,
        var payloadId: Long? = null,
        var payload: Payload? = null
    )

    private data class IncomingExpectation(
        val transferId: String,
        val filename: String,
        val plaintextSize: Long,
        val sha256: String
    )

    private data class IncomingPayload(
        val endpointId: String,
        val transferId: String,
        val file: File,
        val totalBytes: Long,
        val payload: Payload
    )

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 21, 34)
        window.navigationBarColor = android.graphics.Color.rgb(16, 21, 34)
        secureStateStore = EncryptedStateStore(this)
        encryptedAttachmentStore = EncryptedAttachmentStore(this)
        localAIModelStore = LocalAIModelStore(File(filesDir, "local-ai-models")) {
            runCatching { StatFs(filesDir.absolutePath).availableBytes }.getOrDefault(0L)
        }
        localAIModelStore.cleanupIncompleteImports()
        localAIEngine = LiteRtLocalAIEngine(
            modelStore = localAIModelStore,
            cacheDirectory = File(cacheDir, "litertlm-cache"),
            deviceAbis = Build.SUPPORTED_ABIS.toList(),
            availableStorageBytes = { runCatching { StatFs(filesDir.absolutePath).availableBytes }.getOrDefault(0L) },
            availableRamBytes = { runCatching { ActivityManager.MemoryInfo().also { (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }.availMem }.getOrNull() },
            totalRamBytes = { runCatching { ActivityManager.MemoryInfo().also { (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }.totalMem }.getOrNull() }
        ) { type, data -> emit(type, data) }
        localAIEngine.initializeRuntime()
        encryptedAttachmentStore.cleanupIncompleteWrites()
        File(cacheDir, "nexus-outgoing").deleteRecursively()
        File(cacheDir, "nexus-incoming").deleteRecursively()
        File(cacheDir, "nexus-receive-plaintext").deleteRecursively()
        File(cacheDir, "nexus-attachment-share").deleteRecursively()
        Thread({
            val migration = encryptedAttachmentStore.migrateLegacyPlaintextFiles()
            if (migration.remainingPlaintext > 0) {
                pendingAttachmentMigrationWarning = migration.remainingPlaintext
                runOnUiThread {
                    if (!isFinishing) deliverPendingAttachmentMigrationWarning()
                }
            }
            publishAttachmentList()
        }, "nexus-attachment-migration").start()
        connections = Nearby.getConnectionsClient(this)
        initializeWebView()
    }

    @Deprecated("Framework activity-result callback is retained for the app's minimum SDK-compatible picker flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOCAL_AI_MODEL_PICK_REQUEST) {
            handlePickedLocalAIModel(if (resultCode == RESULT_OK) data?.data else null)
            return
        }
        if (requestCode == FILE_PICK_REQUEST) {
            handlePickedFile(if (resultCode == RESULT_OK) data?.data else null)
            return
        }
        if (requestCode == ATTACHMENT_EXPORT_REQUEST) {
            completeAttachmentExport(if (resultCode == RESULT_OK) data?.data else null)
            return
        }
        val scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (scan != null) {
            val content = scan.contents
            if (content == null) emit("qrPairing", JSONObject().put("ok", false).put("error", "QR_SCAN_CANCELLED"))
            else processScannedPairingQr(content)
        }
    }

    override fun onResume() {
        super.onResume()
        mainHandler.postDelayed({ cleanupReturnedAttachmentFiles() }, RETURNED_ATTACHMENT_CLEANUP_DELAY_MS)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) releaseLocalAIForMemoryPressure()
    }

    private fun releaseLocalAIForMemoryPressure() {
        if (!::localAIEngine.isInitialized || !localAIMemoryTrimInProgress.compareAndSet(false, true)) return
        Thread({
            try {
                val cancelled = runCatching { localAIEngine.cancelGeneration() }.getOrDefault(false)
                if (!cancelled) runCatching { localAIEngine.unloadModel() }
            } finally {
                localAIMemoryTrimInProgress.set(false)
            }
        }, "nexus-local-ai-memory-trim").start()
    }

    @SuppressLint("SetJavaScriptEnabled", "MissingOnRenderProcessGone")
    private fun initializeWebView() {
        webPageReady = false
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (view === webView) {
                    webPageReady = true
                    deliverPendingAttachmentMigrationWarning()
                }
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                if (!isAppAssetUrl(request.url)) {
                    return android.webkit.WebResourceResponse(
                        "text/plain", "UTF-8", 404, "Blocked",
                        emptyMap(), java.io.ByteArrayInputStream(ByteArray(0))
                    )
                }
                return assetLoader.shouldInterceptRequest(request.url)
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return !isAppAssetUrl(request.url)
            }
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                if (view === webView && !isFinishing) {
                    Toast.makeText(this@MainActivity, "WebView ပြန်စတင်နေသည်", Toast.LENGTH_SHORT).show()
                    initializeWebView()
                }
                return true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("NEXUS OFFLINE · လုံခြုံရေးအတည်ပြုချက်")
                    .setMessage(message)
                    .setPositiveButton("အတည်ပြုမည်") { _, _ -> result.confirm() }
                    .setNegativeButton("ပယ်ဖျက်မည်") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
                val input = android.widget.EditText(this@MainActivity).apply {
                    setSingleLine(true)
                    setText(defaultValue.orEmpty())
                    setSelection(text.length)
                    contentDescription = message
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("NEXUS OFFLINE")
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("ဆက်လုပ်မည်") { _, _ -> result.confirm(input.text.toString()) }
                    .setNegativeButton("ပယ်ဖျက်မည်") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
        }
        webView.addJavascriptInterface(NativeBridge(), "NexusNativeNearby")
        setContentView(webView)
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    private fun isAppAssetUrl(url: Uri): Boolean =
        url.scheme == "https" && url.host == "appassets.androidplatform.net" && url.encodedPath?.startsWith("/assets/") == true

    @Deprecated("Framework callback retained for minSdk-compatible WebView back handling")
    override fun onBackPressed() {
        if (!::webView.isInitialized || isFinishing) {
            super.onBackPressed()
            return
        }
        webView.evaluateJavascript("window.NexusHandleSystemBack ? window.NexusHandleSystemBack() : false") { handled ->
            if (handled != "true" && !isFinishing) super@MainActivity.onBackPressed()
        }
    }

    private fun deliverPendingAttachmentMigrationWarning() {
        if (!webPageReady) return
        val remaining = pendingAttachmentMigrationWarning ?: return
        pendingAttachmentMigrationWarning = null
        emit("attachmentMigrationWarning", JSONObject().put("remaining", remaining))
    }

    private fun showPairingQr(): String {
        return try {
            val payload = PairingQr.create(localEndpointName)
            val encoded = PairingQr.encode(payload)
            val matrix = QRCodeWriter().encode(encoded, BarcodeFormat.QR_CODE, 640, 640)
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
            val image = ImageView(this).apply {
                setImageBitmap(bitmap)
                setPadding(18, 18, 18, 18)
                adjustViewBounds = true
            }
            runOnUiThread {
                if (!isFinishing) AlertDialog.Builder(this)
                    .setTitle("NEXUS OFFLINE · ခဏတာ QR pairing")
                    .setMessage("Public ID: ${payload.identityRef}\nသက်တမ်း ၂ မိနစ်သာရှိသည်။ Scan ပြီးလျှင် Nearby မှ အမည်တူစက်ကိုရွေးပြီး fingerprint ကို နှစ်ဖက်တိုက်စစ်ပါ။")
                    .setView(image)
                    .setPositiveButton("ပိတ်ရန်", null)
                    .setOnDismissListener { bitmap.recycle() }
                    .show()
            }
            JSONObject().put("ok", true).put("expiresAt", payload.expiresAtMillis).toString()
        } catch (_: Exception) {
            JSONObject().put("ok", false).put("error", "QR_GENERATION_FAILED").toString()
        }
    }

    private fun requestPairingQrScan(): String {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startPairingQrScanner()
            return JSONObject().put("ok", true).put("pending", true).toString()
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), QR_CAMERA_PERMISSION_REQUEST)
        return JSONObject().put("ok", true).put("pendingPermission", true).toString()
    }

    private fun startPairingQrScanner() {
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            runCatching {
                IntentIntegrator(this).apply {
                    setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                    setPrompt("NEXUS OFFLINE · public pairing QR ကို scan လုပ်ပါ")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                }.initiateScan()
            }.onFailure {
                emit("qrPairing", JSONObject().put("ok", false).put("error", "QR_SCANNER_UNAVAILABLE"))
            }
        }
    }

    private fun processScannedPairingQr(raw: String) {
        val payload = PairingQr.decode(raw)
        if (payload == null) {
            emit("qrPairing", JSONObject().put("ok", false).put("error", "QR_FORMAT_INVALID"))
            return
        }
        val prefs = getSharedPreferences("nexus", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val storedEntries = prefs.getStringSet(QR_USED_NONCES_KEY, emptySet()).orEmpty()
        val retained = linkedMapOf<String, Long>()
        storedEntries.forEach { entry ->
            val split = entry.split('|', limit = 2)
            val expiry = split.getOrNull(1)?.toLongOrNull()
            if (split.size == 2 && expiry != null && expiry > now) retained[split[0]] = expiry
        }
        val issue = PairingQr.consume(payload, now, retained.keys)
        if (issue != null) {
            emit("qrPairing", JSONObject().put("ok", false).put("error", issue))
            return
        }
        retained[payload.nonce] = payload.expiresAtMillis + PairingQr.TTL_MILLIS
        val cappedEntries = retained.entries.sortedByDescending { it.value }.take(MAX_REMEMBERED_QR_NONCES)
            .map { "${it.key}|${it.value}" }.toSet()
        prefs.edit().putStringSet(QR_USED_NONCES_KEY, cappedEntries).apply()
        emit("qrPairing", JSONObject()
            .put("ok", true)
            .put("identityRef", payload.identityRef)
            .put("expiresAt", payload.expiresAtMillis)
            .put("endpointData", payload.endpointData))
    }

    private fun beginAttachmentMigrationRetry() {
        Thread({
            val result = runCatching { encryptedAttachmentStore.migrateLegacyPlaintextFiles() }
            val migration = result.getOrElse { EncryptedAttachmentStore.MigrationResult(0, encryptedAttachmentStore.migrationPendingCount()) }
            pendingAttachmentMigrationWarning = migration.remainingPlaintext.takeIf { it > 0 }
            emit("attachmentMigrationStatus", JSONObject()
                .put("migrated", migration.migrated)
                .put("remaining", migration.remainingPlaintext)
                .put("ok", result.isSuccess))
            if (migration.remainingPlaintext > 0) deliverPendingAttachmentMigrationWarning()
            publishAttachmentList()
        }, "nexus-attachment-migration-retry").start()
    }

    private fun requiredPermissions(): Array<String> {
        val result = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            result += Manifest.permission.BLUETOOTH_SCAN
            result += Manifest.permission.BLUETOOTH_CONNECT
            result += Manifest.permission.BLUETOOTH_ADVERTISE
        }
        if (Build.VERSION.SDK_INT <= 28) result += Manifest.permission.ACCESS_COARSE_LOCATION
        else if (Build.VERSION.SDK_INT in 29..31) {
            result += Manifest.permission.ACCESS_COARSE_LOCATION
            result += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33) result += Manifest.permission.NEARBY_WIFI_DEVICES
        return result.distinct().toTypedArray()
    }

    private fun permissionsGranted() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionsFor(action: String? = null): String {
        if (action != null && !googlePlayServicesAvailable()) {
            emit("transportError", JSONObject().put("code", "PLAY_SERVICES_UNAVAILABLE"))
            return JSONObject().put("ok", false).put("error", "PLAY_SERVICES_UNAVAILABLE").toString()
        }
        if (permissionsGranted()) {
            if (action != null) runAction(action)
            emit("permissionChanged", JSONObject().put("granted", true))
            return JSONObject().put("ok", true).put("granted", true).toString()
        }
        val requestNow = synchronized(pendingPermissionActions) {
            if (action != null) pendingPermissionActions.add(action)
            if (permissionRequestInProgress) false else { permissionRequestInProgress = true; true }
        }
        if (requestNow) runOnUiThread {
            if (!isFinishing) ActivityCompat.requestPermissions(this, requiredPermissions(), PERMISSION_REQUEST)
        }
        return JSONObject().put("ok", true).put("pendingPermissions", true).toString()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == QR_CAMERA_PERMISSION_REQUEST) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startPairingQrScanner()
            else emit("qrPairing", JSONObject().put("ok", false).put("error", "CAMERA_PERMISSION_DENIED"))
            return
        }
        if (requestCode != PERMISSION_REQUEST) return
        synchronized(pendingPermissionActions) { permissionRequestInProgress = false }
        val granted = permissionsGranted()
        emit("permissionChanged", JSONObject().put("granted", granted))
        val actions = synchronized(pendingPermissionActions) {
            pendingPermissionActions.toList().also { pendingPermissionActions.clear() }
        }
        if (granted) actions.forEach(::runAction) else {
            emit("transportError", JSONObject().put("code", "PERMISSION_DENIED").put("message", "Nearby အသုံးပြုခွင့် မပေးထားပါ"))
        }
    }

    private fun runAction(action: String) {
        if (!googlePlayServicesAvailable()) {
            advertising = false
            discovering = false
            emit("transportError", JSONObject().put("code", "PLAY_SERVICES_UNAVAILABLE").put("message", "Google Play services is unavailable"))
            emitStatus()
            return
        }
        if (!radiosEnabled()) {
            emit("radioDisabled", JSONObject().put("message", "Wi-Fi သို့မဟုတ် Bluetooth ကို ဖုန်း၏ Settings မှ ဖွင့်ပါ"))
            return
        }
        when (action) {
            "advertise" -> startAdvertisingNow()
            "discover" -> startDiscoveryNow()
        }
    }

    private fun googlePlayServicesAvailable(): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun radiosEnabled(): Boolean {
        val bluetoothOn = runCatching { getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)
        val wifiOn = runCatching { (getSystemService(WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true }.getOrDefault(false)
        return bluetoothOn || wifiOn
    }

    private fun startAdvertisingNow() {
        if (advertising) return
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connections.startAdvertising(localEndpointName, SERVICE_ID, lifecycleCallback, options)
            .addOnSuccessListener { advertising = true; emitStatus() }
            .addOnFailureListener { error -> emitFailure("ADVERTISE_FAILED", error) }
    }

    private fun startDiscoveryNow() {
        if (discovering) return
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connections.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnSuccessListener { discovering = true; emitStatus() }
            .addOnFailureListener { error -> emitFailure("DISCOVERY_FAILED", error) }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            discovered[endpointId] = info.endpointName
            emit("deviceFound", JSONObject().put("endpointId", endpointId).put("endpointName", info.endpointName))
        }
        override fun onEndpointLost(endpointId: String) {
            discovered.remove(endpointId)
            emit("deviceLost", JSONObject().put("endpointId", endpointId))
        }
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            discovered[endpointId] = info.endpointName
            emit("connectionInitiated", JSONObject()
                .put("endpointId", endpointId)
                .put("endpointName", info.endpointName)
                .put("authenticationDigits", info.authenticationDigits)
                .put("incoming", info.isIncomingConnection))
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val name = discovered[endpointId] ?: "Nearby device"
                connected[endpointId] = name
                emit("connectionEstablished", JSONObject().put("endpointId", endpointId).put("endpointName", name))
            } else if (result.status.statusCode == ConnectionsStatusCodes.STATUS_RADIO_ERROR) {
                emit("radioDisabled")
            } else {
                emit("connectionFailed", JSONObject().put("endpointId", endpointId).put("statusCode", result.status.statusCode))
            }
            emitStatus()
        }

        override fun onDisconnected(endpointId: String) {
            val name = connected.remove(endpointId)
            discovered.remove(endpointId)
            incomingExpectations.remove(endpointId)
            outgoingFiles.values.filter { it.endpointId == endpointId }.forEach { entry ->
                entry.payloadId?.let { connections.cancelPayload(it); outgoingPayloads.remove(it) }
                cleanupOutgoing(entry.transferId)
                emit("fileTransferError", JSONObject().put("endpointId", endpointId).put("transferId", entry.transferId).put("code", "CONNECTION_LOST"))
            }
            incomingPayloads.entries.filter { it.value.endpointId == endpointId }.map { it.key }.forEach { id ->
                connections.cancelPayload(id)
                emit("fileTransferError", JSONObject().put("endpointId", endpointId).put("transferId", incomingPayloads[id]?.transferId ?: "").put("code", "CONNECTION_LOST"))
                cleanupIncoming(id)
            }
            emit("disconnected", JSONObject().put("endpointId", endpointId).put("endpointName", name ?: ""))
            emitStatus()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (!connected.containsKey(endpointId)) {
                connections.cancelPayload(payload.id)
                return
            }
            if (payload.type == Payload.Type.FILE) {
                val file = payload.asFile()
                val expected = incomingExpectations[endpointId]
                val size = file?.size ?: 0L
                if (expected == null || !NativeProtocol.isTransferId(expected.transferId) ||
                    !NativeProtocol.acceptsFileSize(expected.plaintextSize) || size != expected.plaintextSize + 16L ||
                    !NativeProtocol.acceptsEncryptedFileSize(size)) {
                    connections.cancelPayload(payload.id)
                    payload.close()
                    emit("fileTransferError", JSONObject().put("endpointId", endpointId).put("code", "FILE_NOT_EXPECTED"))
                    return
                }
                incomingExpectations.remove(endpointId, expected)
                val source = file?.asJavaFile()
                if (source == null) {
                    connections.cancelPayload(payload.id)
                    payload.close()
                    emit("fileTransferError", JSONObject().put("transferId", expected.transferId).put("code", "FILE_UNAVAILABLE"))
                    return
                }
                incomingPayloads[payload.id] = IncomingPayload(endpointId, expected.transferId, source, size, payload)
                emit("filePayloadReceived", JSONObject()
                    .put("endpointId", endpointId)
                    .put("transferId", expected.transferId)
                    .put("payloadId", payload.id.toString())
                    .put("totalBytes", size))
                return
            }
            if (payload.type != Payload.Type.BYTES) {
                emit("transportError", JSONObject().put("code", "UNSUPPORTED_PAYLOAD").put("endpointId", endpointId))
                return
            }
            val bytes = payload.asBytes() ?: return
            if (!NativeProtocol.acceptsPayloadSize(bytes.size)) {
                emit("transportError", JSONObject().put("code", "PAYLOAD_TOO_LARGE").put("endpointId", endpointId))
                return
            }
            emit("payload", JSONObject()
                .put("endpointId", endpointId)
                .put("data", String(bytes, StandardCharsets.UTF_8)))
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            val outgoingId = outgoingPayloads[payloadId]
            if (outgoingId != null) {
                val outgoing = outgoingFiles[outgoingId]
                emit("fileTransferUpdate", JSONObject()
                    .put("transferId", outgoingId)
                    .put("endpointId", endpointId)
                    .put("direction", "send")
                    .put("filename", outgoing?.filename ?: "file")
                    .put("bytesTransferred", update.bytesTransferred)
                    .put("totalBytes", update.totalBytes)
                    .put("status", transferStatus(update.status)))
                if (update.status != PayloadTransferUpdate.Status.IN_PROGRESS) {
                    outgoingPayloads.remove(payloadId)
                    cleanupOutgoing(outgoingId)
                }
                return
            }

            val incoming = incomingPayloads[payloadId] ?: return
            emit("fileTransferUpdate", JSONObject()
                .put("transferId", incoming.transferId)
                .put("endpointId", endpointId)
                .put("direction", "receive")
                .put("bytesTransferred", update.bytesTransferred)
                .put("totalBytes", update.totalBytes)
                .put("status", transferStatus(update.status)))
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                try {
                    if (update.totalBytes != incoming.totalBytes || !incoming.file.isFile || incoming.file.length() != incoming.totalBytes) {
                        throw IllegalStateException("FILE_SIZE_MISMATCH")
                    }
                    val directory = File(cacheDir, "nexus-incoming").apply { mkdirs() }
                    val staged = File(directory, "${incoming.transferId}.enc")
                    incoming.file.inputStream().use { input -> FileOutputStream(staged).use { output ->
                        input.copyTo(output, 64 * 1024)
                        output.fd.sync()
                    } }
                    incoming.payload.close()
                    incomingPayloads[payloadId] = incoming.copy(file = staged)
                    emit("filePayloadReady", JSONObject()
                        .put("transferId", incoming.transferId)
                        .put("endpointId", endpointId)
                        .put("payloadId", payloadId.toString())
                        .put("totalBytes", incoming.totalBytes))
                } catch (_: Exception) {
                    cleanupIncoming(payloadId)
                    emit("fileTransferError", JSONObject().put("transferId", incoming.transferId).put("code", "FILE_RECEIVE_FAILED"))
                }
            } else if (update.status != PayloadTransferUpdate.Status.IN_PROGRESS) {
                cleanupIncoming(payloadId)
                emit("fileTransferError", JSONObject().put("transferId", incoming.transferId).put("code", "FILE_TRANSFER_FAILED"))
            }
        }
    }

    private fun transferStatus(status: Int): String = when (status) {
        PayloadTransferUpdate.Status.IN_PROGRESS -> "IN_PROGRESS"
        PayloadTransferUpdate.Status.SUCCESS -> "SUCCESS"
        PayloadTransferUpdate.Status.FAILURE -> "FAILURE"
        PayloadTransferUpdate.Status.CANCELED -> "CANCELED"
        else -> "UNKNOWN"
    }

    private fun parseEndpointIds(raw: String): List<String> {
        if (raw.length > 8192) return emptyList()
        return try {
            val array = JSONArray(raw)
            if (array.length() > 32) return emptyList()
            val values = (0 until array.length()).map { array.getString(it) }
            if (values.any { it.isBlank() || it.length > 128 }) emptyList()
            else NativeProtocol.routeTargets(values, "")
        } catch (_: Exception) { emptyList() }
    }

    private fun handlePickedFile(uri: Uri?) {
        val endpointId = pendingFileTarget.also { pendingFileTarget = null } ?: return
        if (uri == null) {
            emit("filePickCancelled", JSONObject().put("endpointId", endpointId))
            return
        }
        var staged: File? = null
        try {
            val suppliedName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            } ?: "file"
            val safeName = NativeProtocol.sanitizeFilename(suppliedName)
            val transferId = UUID.randomUUID().toString().replace("-", "")
            val directory = File(cacheDir, "nexus-outgoing").apply { mkdirs() }
            val stagedFile = File(directory, "$transferId.plain")
            staged = stagedFile
            var total = 0L
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(stagedFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > NativeProtocol.MAX_FILE_BYTES) throw IllegalArgumentException("FILE_TOO_LARGE")
                        output.write(buffer, 0, count)
                    }
                    buffer.fill(0)
                    output.fd.sync()
                }
            } ?: throw IllegalArgumentException("FILE_UNAVAILABLE")
            if (!NativeProtocol.acceptsFileSize(total)) throw IllegalArgumentException("EMPTY_OR_INVALID_FILE")
            outgoingFiles[transferId] = OutgoingFile(transferId, endpointId, safeName, total, stagedFile)
            emit("filePicked", JSONObject()
                .put("transferId", transferId)
                .put("endpointId", endpointId)
                .put("filename", safeName)
                .put("size", total))
        } catch (error: Exception) {
            staged?.delete()
            val code = if (error.message == "FILE_TOO_LARGE") "FILE_TOO_LARGE" else "FILE_READ_FAILED"
            emit("fileTransferError", JSONObject().put("endpointId", endpointId).put("code", code))
        }
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            buffer.fill(0)
        }
        return digest.digest()
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun encryptFile(source: File, target: File, keyBytes: ByteArray, iv: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    val encrypted = cipher.update(buffer, 0, count)
                    if (encrypted != null) output.write(encrypted)
                }
                val finalBytes = cipher.doFinal()
                output.write(finalBytes)
                buffer.fill(0)
                keyBytes.fill(0)
                iv.fill(0)
                output.fd.sync()
            }
        }
    }

    private fun decryptFile(source: File, target: File, keyBytes: ByteArray, iv: ByteArray): Pair<Long, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    val clear = cipher.update(buffer, 0, count)
                    if (clear != null && clear.isNotEmpty()) {
                        total += clear.size
                        digest.update(clear)
                        output.write(clear)
                        clear.fill(0)
                    }
                }
                val finalBytes = cipher.doFinal() // Authenticates the GCM tag before finalizing the file.
                if (finalBytes.isNotEmpty()) {
                    total += finalBytes.size
                    digest.update(finalBytes)
                    output.write(finalBytes)
                    finalBytes.fill(0)
                }
                buffer.fill(0)
                keyBytes.fill(0)
                iv.fill(0)
                output.fd.sync()
            }
        }
        return total to toHex(digest.digest())
    }

    private fun decodeBase64(value: String, expectedBytes: Int): ByteArray? {
        if (value.length > 256) return null
        return runCatching { Base64.decode(value, Base64.DEFAULT).takeIf { it.size == expectedBytes } }.getOrNull()
    }

    private fun cleanupOutgoing(transferId: String) {
        outgoingFiles.remove(transferId)?.let { file ->
            file.plaintextFile.delete()
            file.encryptedFile?.delete()
            file.payload?.close()
        }
    }

    private fun cleanupIncoming(payloadId: Long) {
        incomingPayloads.remove(payloadId)?.let { incoming ->
            incoming.file.delete()
            incoming.payload.close()
        }
    }

    private fun emitStatus() {
        emit("status", JSONObject()
            .put("transport", "Nearby Connections / P2P_CLUSTER")
            .put("advertising", advertising)
            .put("discovering", discovering)
            .put("permissionGranted", permissionsGranted())
            .put("connected", JSONArray().also { a -> connected.forEach { (id, name) -> a.put(JSONObject().put("endpointId", id).put("endpointName", name)) } })
            .put("discoveredCount", discovered.size))
    }

    private fun emitFailure(code: String, error: Exception) {
        if ((error as? ApiException)?.statusCode == ConnectionsStatusCodes.STATUS_RADIO_ERROR) {
            emit("radioDisabled", JSONObject().put("source", code))
        } else {
            emit("transportError", JSONObject().put("code", code).put("message", error.javaClass.simpleName))
        }
    }

    private fun emit(type: String, data: JSONObject = JSONObject()) {
        val event = JSONObject(data.toString()).put("type", type)
        if (!::webView.isInitialized) return
        val quoted = JSONObject.quote(event.toString())
        runOnUiThread { if (!isFinishing) webView.evaluateJavascript("window.NexusNativeNearbyEvent && window.NexusNativeNearbyEvent($quoted);", null) }
    }

    private fun publishAttachmentList() {
        Thread({
            val entries = JSONArray()
            runCatching { encryptedAttachmentStore.listAttachments() }.onSuccess { records ->
                records.forEach { record -> entries.put(JSONObject()
                    .put("transferId", record.transferId)
                    .put("filename", record.filename)
                    .put("mimeType", mimeTypeFor(record.filename))
                    .put("sizeBytes", record.sizeBytes)
                    .put("receivedAtMillis", record.receivedAtMillis)
                    .put("sha256", record.sha256 ?: JSONObject.NULL)
                    .put("integrityStatus", record.integrityStatus))
                }
                emit("attachmentList", JSONObject()
                    .put("items", entries)
                    .put("encryptedBytes", encryptedAttachmentStore.encryptedBytes())
                    .put("legacyPlaintextCount", encryptedAttachmentStore.migrationPendingCount()))
            }.onFailure {
                emit("attachmentList", JSONObject().put("items", entries).put("error", "ATTACHMENT_LIST_FAILED"))
            }
        }, "nexus-attachment-list").start()
    }

    private fun beginAttachmentAction(transferId: String, action: String): String {
        if (action !in setOf("open", "share", "export")) return JSONObject().put("ok", false).put("error", "BAD_ATTACHMENT_ACTION").toString()
        if (!NativeProtocol.isTransferId(transferId)) return JSONObject().put("ok", false).put("error", "BAD_TRANSFER_ID").toString()
        synchronized(this) {
            if (clearInProgress || attachmentActionInProgress || pendingAttachmentExport != null) return JSONObject().put("ok", false).put("error", "ATTACHMENT_ACTION_BUSY").toString()
            attachmentActionInProgress = true
        }
        Thread({
            var plaintext: File? = null
            try {
                val metadata = encryptedAttachmentStore.metadataForUserAction(transferId)
                val shareDirectory = encryptedAttachmentStore.shareDirectory()
                if (!shareDirectory.exists() && !shareDirectory.mkdirs()) throw IllegalStateException("TEMP_STORAGE_UNAVAILABLE")
                plaintext = AttachmentTempFiles.safeOutput(shareDirectory, transferId, metadata.filename)
                val verified = encryptedAttachmentStore.preparePlaintextForUserAction(transferId, plaintext)
                runOnUiThread {
                    if (isFinishing) {
                        plaintext.delete()
                        emit("attachmentAction", JSONObject().put("ok", false).put("error", "ACTIVITY_CLOSED"))
                    } else launchAttachmentOutput(verified, plaintext, action)
                }
            } catch (error: Exception) {
                plaintext?.delete()
                val code = error.message?.takeIf { it.matches(Regex("^[A-Z0-9_]{2,64}$")) } ?: "ATTACHMENT_ACTION_FAILED"
                emit("attachmentAction", JSONObject().put("ok", false).put("error", code))
            } finally {
                attachmentActionInProgress = false
            }
        }, "nexus-attachment-action").start()
        return JSONObject().put("ok", true).put("pending", true).toString()
    }

    private fun beginAttachmentDelete(transferId: String): String {
        if (!NativeProtocol.isTransferId(transferId)) return JSONObject().put("ok", false).put("error", "BAD_TRANSFER_ID").toString()
        synchronized(this) {
            if (clearInProgress || attachmentActionInProgress || pendingAttachmentExport != null) {
                return JSONObject().put("ok", false).put("error", "ATTACHMENT_ACTION_BUSY").toString()
            }
            attachmentActionInProgress = true
        }
        Thread({
            var error: String? = null
            var tempCleanupOk = true
            try {
                if (!encryptedAttachmentStore.contains(transferId)) {
                    error = "ATTACHMENT_NOT_FOUND"
                } else if (!encryptedAttachmentStore.delete(transferId)) {
                    error = "ATTACHMENT_DELETE_FAILED"
                } else {
                    externalAttachmentFiles.keys.filter { it.name.startsWith("$transferId-") }.forEach(::revokeAndDeleteAttachment)
                    val shareDirectory = encryptedAttachmentStore.shareDirectory()
                    if (shareDirectory.exists()) tempCleanupOk = AttachmentFileCleanup.deleteTransferTemps(shareDirectory, transferId)
                    if (!tempCleanupOk) error = "TEMP_CLEANUP_FAILED"
                }
            } catch (_: Exception) {
                error = "ATTACHMENT_DELETE_FAILED"
            } finally {
                attachmentActionInProgress = false
                publishAttachmentList()
            }
            emit("attachmentAction", JSONObject()
                .put("ok", error == null)
                .put("action", "delete")
                .put("transferId", transferId)
                .put("temporaryCleanupOk", tempCleanupOk)
                .put("error", error ?: JSONObject.NULL))
        }, "nexus-attachment-delete").start()
        return JSONObject().put("ok", true).put("pending", true).toString()
    }

    private fun launchAttachmentOutput(record: EncryptedAttachmentStore.AttachmentRecord, plaintext: File, action: String) {
        val mime = mimeTypeFor(record.filename)
        if (action == "export") {
            if (pendingAttachmentExport != null) {
                plaintext.delete()
                emit("attachmentAction", JSONObject().put("ok", false).put("error", "ATTACHMENT_ACTION_BUSY"))
                return
            }
            pendingAttachmentExport = plaintext
            try {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mime
                    putExtra(Intent.EXTRA_TITLE, record.filename)
                }
                startActivityForResult(intent, ATTACHMENT_EXPORT_REQUEST)
            } catch (_: Exception) {
                pendingAttachmentExport = null
                plaintext.delete()
                emit("attachmentAction", JSONObject().put("ok", false).put("error", "EXPORT_PICKER_UNAVAILABLE"))
            }
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.attachments", plaintext)
            val intent = if (action == "open") Intent(Intent.ACTION_VIEW) else Intent(Intent.ACTION_SEND)
            intent.setDataAndType(uri, mime)
            if (action == "share") intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.clipData = android.content.ClipData.newUri(contentResolver, record.filename, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            externalAttachmentFiles[plaintext] = uri
            if (action == "share") startActivity(Intent.createChooser(intent, "Attachment share").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            else startActivity(intent)
            mainHandler.postDelayed({ revokeAndDeleteAttachment(plaintext) }, EXTERNAL_ATTACHMENT_MAX_AGE_MS)
            emit("attachmentAction", JSONObject().put("ok", true).put("action", action).put("filename", record.filename))
        } catch (_: Exception) {
            externalAttachmentFiles.remove(plaintext)
            plaintext.delete()
            emit("attachmentAction", JSONObject().put("ok", false).put("error", "NO_COMPATIBLE_APP"))
        }
    }

    private fun completeAttachmentExport(destination: Uri?) {
        val plaintext = pendingAttachmentExport
        pendingAttachmentExport = null
        if (plaintext == null) return
        if (destination == null) {
            plaintext.delete()
            emit("attachmentAction", JSONObject().put("ok", false).put("error", "EXPORT_CANCELLED"))
            return
        }
        try {
            contentResolver.openOutputStream(destination, "w")?.use { output ->
                AttachmentExportCopy.copyAndDeleteTemporary(plaintext, output)
            } ?: throw IllegalStateException("EXPORT_DESTINATION_UNAVAILABLE")
            emit("attachmentAction", JSONObject().put("ok", true).put("action", "export"))
        } catch (_: Exception) {
            runCatching { android.provider.DocumentsContract.deleteDocument(contentResolver, destination) }
            emit("attachmentAction", JSONObject().put("ok", false).put("error", "EXPORT_FAILED"))
        } finally {
            plaintext.delete()
        }
    }

    private fun cleanupReturnedAttachmentFiles() {
        externalAttachmentFiles.keys.toList().forEach(::revokeAndDeleteAttachment)
        File(cacheDir, "nexus-attachment-share").listFiles()?.filter { it.isFile && it != pendingAttachmentExport }?.forEach { it.delete() }
    }

    private fun revokeAndDeleteAttachment(file: File) {
        externalAttachmentFiles.remove(file)?.let { uri -> revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (file != pendingAttachmentExport) file.delete()
    }

    private fun mimeTypeFor(filename: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun localAIStatusJson(): JSONObject {
        val status = localAIEngine.getRuntimeStatus()
        return JSONObject()
            .put("runtimeInstalled", status.runtimeInstalled)
            .put("runtimeName", status.runtimeName ?: JSONObject.NULL)
            .put("runtimeStatus", if (status.runtimeInstalled) "RUNTIME_PRESENT_LOAD_ON_DEMAND" else "UNSUPPORTED_DEVICE_ABI_OR_RUNTIME")
            .put("modelStatus", status.modelStatus)
            .put("loadedModelId", status.loadedModelId ?: JSONObject.NULL)
            .put("state", status.state)
            .put("errorCode", status.errorCode ?: JSONObject.NULL)
            .put("supportedAbis", JSONArray(status.supportedAbis))
            .put("availableStorageBytes", status.availableStorageBytes ?: JSONObject.NULL)
            .put("availableRamBytes", status.availableRamBytes ?: JSONObject.NULL)
            .put("totalRamBytes", status.totalRamBytes ?: JSONObject.NULL)
            .put("installedModels", JSONArray().also { models -> status.installedModels.forEach { model ->
                models.put(JSONObject().put("id", model.id).put("displayName", model.displayName)
                    .put("license", model.license ?: JSONObject.NULL)
                    .put("requiredRamBytes", model.requiredRamBytes ?: JSONObject.NULL)
                    .put("requiredStorageBytes", model.requiredStorageBytes ?: JSONObject.NULL)
                    .put("availableRamBytes", model.availableRamBytes ?: JSONObject.NULL)
                    .put("format", model.format)
                    .put("formatVersion", model.formatVersion ?: JSONObject.NULL)
                    .put("architecture", model.architecture ?: JSONObject.NULL)
                    .put("contextLength", model.contextLength ?: JSONObject.NULL)
                    .put("compatibility", model.compatibility)
                    .put("supportedAbis", JSONArray(model.supportedAbis)))
            } })
            .put("status", status.message)
    }

    private fun localAICompatibilityJson(modelId: String): JSONObject {
        val status = localAIEngine.getRuntimeStatus()
        val model = localAIEngine.inspectModel(modelId)
            ?: return JSONObject().put("ok", false).put("error", "MODEL_NOT_INSTALLED")
        val formatValidated = model.format == "LiteRT-LM container" && model.formatVersion != null
        val abiSupported = status.supportedAbis.any { it == "arm64-v8a" || it == "x86_64" }
        val requiredRam = model.requiredRamBytes
        val availableRam = status.availableRamBytes
        val ramEstimateFits = if (requiredRam == null || availableRam == null) null else availableRam >= requiredRam
        val requiredStorage = model.requiredStorageBytes
        val availableStorage = status.availableStorageBytes
        val storageSufficientForCopy = if (requiredStorage == null || availableStorage == null) null else availableStorage >= requiredStorage
        val issues = JSONArray()
        if (!status.runtimeInstalled) issues.put("RUNTIME_UNAVAILABLE")
        if (!abiSupported) issues.put("UNSUPPORTED_DEVICE_ABI_OR_RUNTIME")
        if (!formatValidated) issues.put("MODEL_INSPECTION_FAILED")
        if (ramEstimateFits == false) issues.put("LOW_AVAILABLE_RAM_ESTIMATE")
        if (storageSufficientForCopy == false) issues.put("INSUFFICIENT_MODEL_STORAGE_FOR_ANOTHER_COPY")
        val loadGate = status.runtimeInstalled && abiSupported && formatValidated
        val result = JSONObject()
            .put("ok", true)
            .put("compatibilityOK", loadGate && ramEstimateFits != false)
            .put("runtimeAvailable", status.runtimeInstalled)
            .put("abiSupported", abiSupported)
            .put("formatValidated", formatValidated)
            .put("runtimeLoadRequired", true)
            .put("modelName", model.displayName)
            .put("format", model.format)
            .put("formatVersion", model.formatVersion ?: JSONObject.NULL)
            .put("architecture", model.architecture ?: JSONObject.NULL)
            .put("contextLength", model.contextLength ?: JSONObject.NULL)
            .put("license", model.license ?: "UNVERIFIED — confirm the upstream model license")
            .put("compatibility", model.compatibility)
            .put("requiredStorageBytes", requiredStorage ?: JSONObject.NULL)
            .put("availableStorageBytes", availableStorage ?: JSONObject.NULL)
            .put("storageSufficientForCopy", storageSufficientForCopy ?: JSONObject.NULL)
            .put("estimatedRamBytes", requiredRam ?: JSONObject.NULL)
            .put("availableRamBytes", availableRam ?: JSONObject.NULL)
            .put("ramEstimateFits", ramEstimateFits ?: JSONObject.NULL)
            .put("issues", issues)
        return result
    }

    private fun requestLocalAIModelImport(sourceDescription: String): String {
        sourceDescription.take(256) // Bound legacy bridge input; never interpret it as a filesystem path.
        val pickerStatus = localAIEngine.importModel(sourceDescription)
        if (pickerStatus != "MODEL_IMPORT_REQUIRES_DOCUMENT_PICKER") return JSONObject().put("ok", false).put("error", pickerStatus).toString()
        synchronized(this) {
            if (pendingLocalAIImport || localAIImportInProgress || clearInProgress) {
                return JSONObject().put("ok", false).put("error", "LOCAL_AI_BUSY").toString()
            }
            pendingLocalAIImport = true
        }
        runOnUiThread {
            if (isFinishing) {
                pendingLocalAIImport = false
                emit("localAIImportResult", JSONObject().put("ok", false).put("error", "ACTIVITY_CLOSED"))
                return@runOnUiThread
            }
            runCatching {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/x-litertlm"))
                }
                startActivityForResult(intent, LOCAL_AI_MODEL_PICK_REQUEST)
            }.onFailure {
                pendingLocalAIImport = false
                emit("localAIImportResult", JSONObject().put("ok", false).put("error", "MODEL_PICKER_UNAVAILABLE"))
            }
        }
        return JSONObject().put("ok", true).put("pending", true).toString()
    }

    private fun handlePickedLocalAIModel(uri: Uri?) {
        pendingLocalAIImport = false
        if (uri == null) {
            localAIModelStore.importPickedModel(null, null)
            emit("localAIImportResult", JSONObject().put("ok", false).put("cancelled", true).put("error", "MODEL_IMPORT_CANCELLED"))
            return
        }
        synchronized(this) {
            if (clearInProgress || localAIImportInProgress) {
                emit("localAIImportResult", JSONObject().put("ok", false).put("error", "LOCAL_AI_BUSY"))
                return
            }
            localAIImportInProgress = true
        }
        Thread({
            try {
                val metadata = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) null else {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex).takeIf { it >= 0L } else null
                        displayName to size
                    }
                }
                val displayName = metadata?.first ?: "model.litertlm"
                val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("MODEL_FILE_UNAVAILABLE")
                val model = localAIModelStore.importPickedModel(displayName, input, metadata?.second)
                    ?: throw IllegalStateException("MODEL_IMPORT_CANCELLED")
                val result = localAIEngine.registerImportedModel(model)
                if (result != "MODEL_IMPORTED") throw IllegalStateException(result)
                emit("localAIImportResult", JSONObject().put("ok", true).put("modelId", model.id)
                    .put("displayName", model.displayName).put("sizeBytes", model.sizeBytes))
            } catch (error: Exception) {
                val code = error.message?.takeIf { it.matches(Regex("^[A-Z0-9_]{2,64}$")) } ?: "MODEL_IMPORT_FAILED"
                emit("localAIImportResult", JSONObject().put("ok", false).put("error", code))
            } finally {
                localAIImportInProgress = false
            }
        }, "nexus-local-ai-model-import").start()
    }

    private inner class NativeBridge {
        @JavascriptInterface fun isAvailable(): Boolean = true
        @JavascriptInterface fun startAdvertising(): String = requestPermissionsFor("advertise")
        @JavascriptInterface fun startDiscovery(): String = requestPermissionsFor("discover")
        @JavascriptInterface fun requestPermissions(): String = requestPermissionsFor()

        @JavascriptInterface fun loadSecureState(): String = secureStateStore.load().toString()
        @JavascriptInterface fun saveSecureState(rawState: String): String = secureStateStore.save(rawState).toString()
        @JavascriptInterface fun clearSecureState(): String {
            synchronized(this@MainActivity) {
                if (clearInProgress || attachmentActionInProgress || pendingLocalAIImport || localAIImportInProgress) return JSONObject().put("ok", false).put("error", "APP_DATA_ACTION_BUSY").toString()
                clearInProgress = true
            }
            try {
                val result = secureStateStore.clear()
                if (result.optBoolean("ok")) {
                    localAIEngine.unloadModel()
                    val modelsCleared = localAIModelStore.clear()
                    File(cacheDir, "litertlm-cache").deleteRecursively()
                    val attachmentsCleared = encryptedAttachmentStore.clear()
                    pendingAttachmentExport?.delete()
                    pendingAttachmentExport = null
                    externalAttachmentFiles.keys.toList().forEach(::revokeAndDeleteAttachment)
                    outgoingFiles.keys.toList().forEach(::cleanupOutgoing)
                    incomingPayloads.keys.toList().forEach { id -> connections.cancelPayload(id); cleanupIncoming(id) }
                    incomingExpectations.clear()
                    File(cacheDir, "nexus-outgoing").deleteRecursively()
                    File(cacheDir, "nexus-incoming").deleteRecursively()
                    File(cacheDir, "nexus-receive-plaintext").deleteRecursively()
                    if (!attachmentsCleared) return JSONObject().put("ok", false).put("error", "ATTACHMENT_CLEAR_FAILED").toString()
                    if (!modelsCleared) return JSONObject().put("ok", false).put("error", "LOCAL_AI_MODEL_CLEAR_FAILED").toString()
                }
                return result.toString()
            } finally {
                clearInProgress = false
            }
        }
        @JavascriptInterface fun getSecureStorageBytes(): String = JSONObject()
            .put("ok", true)
            .put("bytes", secureStateStore.storedBytes())
            .toString()

        @JavascriptInterface fun listAttachments(): String {
            publishAttachmentList()
            return JSONObject().put("ok", true).put("pending", true).toString()
        }

        @JavascriptInterface fun retryAttachmentMigration(): String {
            beginAttachmentMigrationRetry()
            return JSONObject().put("ok", true).put("pending", true).toString()
        }

        @JavascriptInterface fun useAttachment(transferId: String, action: String): String = beginAttachmentAction(transferId, action)
        @JavascriptInterface fun deleteAttachment(transferId: String): String = beginAttachmentDelete(transferId)

        @JavascriptInterface fun createPairingQr(): String = showPairingQr()
        @JavascriptInterface fun scanPairingQr(): String = requestPairingQrScan()

        @JavascriptInterface fun getLocalAIStatus(): String = localAIStatusJson().toString()
        @JavascriptInterface fun importLocalAIModel(sourceDescription: String): String = requestLocalAIModelImport(sourceDescription)
        @JavascriptInterface fun loadLocalAIModel(modelId: String): String {
            val result = localAIEngine.loadModel(modelId.take(128))
            val pending = result == "MODEL_LOADING"
            val ok = pending || result == "MODEL_ALREADY_LOADED"
            return JSONObject().put("ok", ok).put("pending", pending).put("error", if (ok) JSONObject.NULL else result).toString()
        }
        @JavascriptInterface fun inspectLocalAIModel(modelId: String): String = localAICompatibilityJson(modelId.take(128)).toString()
        @JavascriptInterface fun checkLocalAIModelCompatibility(modelId: String): String = localAICompatibilityJson(modelId.take(128)).toString()
        @JavascriptInterface fun deleteLocalAIModel(modelId: String): String {
            val result = localAIEngine.deleteModel(modelId.take(128))
            val ok = result == "MODEL_DELETED"
            return JSONObject().put("ok", ok).put("error", if (ok) JSONObject.NULL else result).toString()
        }
        @JavascriptInterface fun generateLocalAI(prompt: String): String {
            val result = localAIEngine.generate(prompt.take(LiteRtLocalAIEngine.MAX_PROMPT_CHARS))
            val pending = result == "GENERATION_STARTED"
            return JSONObject().put("ok", pending).put("pending", pending).put("error", if (pending) JSONObject.NULL else result).toString()
        }
        @JavascriptInterface fun cancelLocalAIGeneration(): String = JSONObject()
            .put("ok", localAIEngine.cancelGeneration()).toString()
        @JavascriptInterface fun unloadLocalAIModel(): String = JSONObject()
            .put("ok", localAIEngine.unloadModel()).toString()

        @JavascriptInterface fun pickAndSendFile(endpointId: String): String {
            if (!connected.containsKey(endpointId)) return JSONObject().put("ok", false).put("error", "NOT_CONNECTED").toString()
            synchronized(this@MainActivity) {
                if (pendingFileTarget != null || outgoingFiles.isNotEmpty()) {
                    return JSONObject().put("ok", false).put("error", "FILE_TRANSFER_BUSY").toString()
                }
                pendingFileTarget = endpointId
            }
            runOnUiThread {
                if (isFinishing) {
                    pendingFileTarget = null
                    emit("fileTransferError", JSONObject().put("endpointId", endpointId).put("code", "ACTIVITY_CLOSED"))
                } else {
                    runCatching {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        startActivityForResult(intent, FILE_PICK_REQUEST)
                    }.onFailure {
                        pendingFileTarget = null
                        emit("fileTransferError", JSONObject().put("endpointId", endpointId).put("code", "FILE_PICKER_UNAVAILABLE"))
                    }
                }
            }
            return JSONObject().put("ok", true).put("pending", true).toString()
        }

        @JavascriptInterface fun prepareOutgoingFile(transferId: String, keyBase64: String, ivBase64: String): String {
            if (!NativeProtocol.isTransferId(transferId)) return JSONObject().put("ok", false).put("error", "BAD_TRANSFER_ID").toString()
            val entry = outgoingFiles[transferId] ?: return JSONObject().put("ok", false).put("error", "FILE_NOT_FOUND").toString()
            if (!connected.containsKey(entry.endpointId) || !entry.plaintextFile.isFile || entry.plaintextFile.length() != entry.plaintextSize) {
                cleanupOutgoing(transferId)
                return JSONObject().put("ok", false).put("error", "FILE_CHANGED_OR_DISCONNECTED").toString()
            }
            val key = decodeBase64(keyBase64, 32) ?: return JSONObject().put("ok", false).put("error", "BAD_FILE_KEY").toString()
            val iv = decodeBase64(ivBase64, 12) ?: run { key.fill(0); return JSONObject().put("ok", false).put("error", "BAD_FILE_IV").toString() }
            return try {
                val hash = toHex(sha256(entry.plaintextFile))
                val encryptedFile = File(entry.plaintextFile.parentFile, "$transferId.enc")
                encryptFile(entry.plaintextFile, encryptedFile, key, iv)
                entry.plaintextSha256 = hash
                entry.encryptedFile = encryptedFile
                entry.plaintextFile.delete()
                JSONObject()
                    .put("ok", true)
                    .put("transferId", transferId)
                    .put("filename", entry.filename)
                    .put("size", entry.plaintextSize)
                    .put("sha256", hash)
                    .toString()
            } catch (_: Exception) {
                key.fill(0)
                iv.fill(0)
                cleanupOutgoing(transferId)
                JSONObject().put("ok", false).put("error", "FILE_ENCRYPTION_FAILED").toString()
            }
        }

        @JavascriptInterface fun sendPreparedFile(transferId: String): String {
            if (!NativeProtocol.isTransferId(transferId)) return JSONObject().put("ok", false).put("error", "BAD_TRANSFER_ID").toString()
            val entry = outgoingFiles[transferId] ?: return JSONObject().put("ok", false).put("error", "FILE_NOT_FOUND").toString()
            val encryptedFile = entry.encryptedFile ?: return JSONObject().put("ok", false).put("error", "FILE_NOT_PREPARED").toString()
            if (!connected.containsKey(entry.endpointId) || !NativeProtocol.acceptsEncryptedFileSize(encryptedFile.length())) {
                cleanupOutgoing(transferId)
                return JSONObject().put("ok", false).put("error", "FILE_SIZE_OR_CONNECTION_INVALID").toString()
            }
            return try {
                val payload = Payload.fromFile(encryptedFile)
                payload.setSensitive(true)
                payload.setFileName("nexus-$transferId.nxe")
                outgoingPayloads[payload.id] = transferId
                entry.payloadId = payload.id
                entry.payload = payload
                connections.sendPayload(entry.endpointId, payload).addOnFailureListener {
                    outgoingPayloads.remove(payload.id)
                    cleanupOutgoing(transferId)
                    emit("fileTransferError", JSONObject().put("transferId", transferId).put("code", "FILE_SEND_FAILED"))
                }
                JSONObject().put("ok", true).put("transferId", transferId).put("payloadId", payload.id.toString()).toString()
            } catch (_: Exception) {
                cleanupOutgoing(transferId)
                JSONObject().put("ok", false).put("error", "FILE_SEND_FAILED").toString()
            }
        }

        @JavascriptInterface fun expectIncomingFile(endpointId: String, transferId: String, filename: String, size: Long, sha256Hex: String): String {
            val safeName = NativeProtocol.sanitizeFilename(filename)
            if (!connected.containsKey(endpointId) || !NativeProtocol.isTransferId(transferId) ||
                safeName != filename || !NativeProtocol.acceptsFileSize(size) || !sha256Hex.matches(Regex("^[a-fA-F0-9]{64}$"))) {
                return JSONObject().put("ok", false).put("error", "BAD_FILE_MANIFEST").toString()
            }
            val expectation = IncomingExpectation(transferId, safeName, size, sha256Hex.lowercase())
            if (incomingExpectations.putIfAbsent(endpointId, expectation) != null) {
                return JSONObject().put("ok", false).put("error", "INCOMING_FILE_BUSY").toString()
            }
            return JSONObject().put("ok", true).toString()
        }

        @JavascriptInterface fun decryptIncomingFile(
            payloadIdText: String,
            transferId: String,
            keyBase64: String,
            ivBase64: String,
            filename: String,
            expectedSize: Long,
            expectedSha256: String
        ): String {
            val payloadId = payloadIdText.toLongOrNull() ?: return JSONObject().put("ok", false).put("error", "BAD_PAYLOAD_ID").toString()
            val incoming = incomingPayloads[payloadId] ?: return JSONObject().put("ok", false).put("error", "PAYLOAD_NOT_READY").toString()
            val safeName = NativeProtocol.sanitizeFilename(filename)
            if (incoming.transferId != transferId || !NativeProtocol.isTransferId(transferId) || safeName != filename ||
                !NativeProtocol.acceptsFileSize(expectedSize) || incoming.totalBytes != expectedSize + 16L ||
                !expectedSha256.matches(Regex("^[a-fA-F0-9]{64}$")) || !incoming.file.isFile || incoming.file.length() != incoming.totalBytes) {
                cleanupIncoming(payloadId)
                return JSONObject().put("ok", false).put("error", "FILE_MANIFEST_MISMATCH").toString()
            }
            val key = decodeBase64(keyBase64, 32) ?: return JSONObject().put("ok", false).put("error", "BAD_FILE_KEY").toString()
            val iv = decodeBase64(ivBase64, 12) ?: run { key.fill(0); return JSONObject().put("ok", false).put("error", "BAD_FILE_IV").toString() }
            val temporaryDirectory = File(cacheDir, "nexus-receive-plaintext").apply { mkdirs() }
            val temporary = File(temporaryDirectory, "${transferId}.part")
            if (encryptedAttachmentStore.contains(transferId)) {
                key.fill(0); iv.fill(0); cleanupIncoming(payloadId)
                return JSONObject().put("ok", false).put("error", "DUPLICATE_FILE").toString()
            }
            var encryptedCommitted = false
            return try {
                val (written, actualHash) = decryptFile(incoming.file, temporary, key, iv)
                if (written != expectedSize || !MessageDigest.isEqual(actualHash.toByteArray(), expectedSha256.lowercase().toByteArray())) {
                    throw IllegalStateException("FILE_INTEGRITY_FAILED")
                }
                encryptedAttachmentStore.storeVerifiedFile(temporary, transferId, safeName)
                encryptedCommitted = true
                if (!temporary.delete()) throw IllegalStateException("PLAINTEXT_TEMP_CLEANUP_FAILED")
                cleanupIncoming(payloadId)
                emit("fileReceived", JSONObject().put("transferId", transferId).put("filename", safeName).put("size", written).put("sha256", actualHash).put("atRestEncrypted", true))
                JSONObject().put("ok", true).put("filename", safeName).put("size", written).put("sha256", actualHash).toString()
            } catch (_: Exception) {
                key.fill(0); iv.fill(0)
                temporary.delete()
                if (encryptedCommitted) encryptedAttachmentStore.delete(transferId)
                cleanupIncoming(payloadId)
                JSONObject().put("ok", false).put("error", "FILE_INTEGRITY_OR_DECRYPT_FAILED").toString()
            }
        }

        @JavascriptInterface fun cancelFileTransfer(transferOrPayloadId: String): String {
            if (NativeProtocol.isTransferId(transferOrPayloadId)) {
                val outgoing = outgoingFiles[transferOrPayloadId]
                outgoing?.payloadId?.let { connections.cancelPayload(it) }
                cleanupOutgoing(transferOrPayloadId)
                val incomingId = incomingPayloads.entries.firstOrNull { it.value.transferId == transferOrPayloadId }?.key
                if (incomingId != null) {
                    connections.cancelPayload(incomingId)
                    cleanupIncoming(incomingId)
                }
                incomingExpectations.entries.removeIf { it.value.transferId == transferOrPayloadId }
                return JSONObject().put("ok", true).toString()
            }
            return JSONObject().put("ok", false).put("error", "BAD_TRANSFER_ID").toString()
        }

        @JavascriptInterface fun stopAdvertising(): String {
            connections.stopAdvertising()
            advertising = false
            emitStatus()
            return JSONObject().put("ok", true).toString()
        }

        @JavascriptInterface fun stopDiscovery(): String {
            connections.stopDiscovery()
            discovering = false
            emitStatus()
            return JSONObject().put("ok", true).toString()
        }

        @JavascriptInterface fun connect(endpointId: String): String {
            if (!permissionsGranted()) return requestPermissionsFor()
            if (endpointId.isBlank() || endpointId.length > 128 || !discovered.containsKey(endpointId)) return JSONObject().put("ok", false).put("error", "BAD_ENDPOINT").toString()
            connections.requestConnection(localEndpointName, endpointId, lifecycleCallback)
                .addOnFailureListener { error -> emitFailure("CONNECT_FAILED", error) }
            return JSONObject().put("ok", true).put("pending", true).toString()
        }

        @JavascriptInterface fun acceptConnection(endpointId: String): String {
            if (!permissionsGranted()) return requestPermissionsFor()
            if (endpointId.isBlank() || endpointId.length > 128 || !discovered.containsKey(endpointId)) return JSONObject().put("ok", false).put("error", "BAD_ENDPOINT").toString()
            connections.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { error -> emitFailure("ACCEPT_FAILED", error) }
            return JSONObject().put("ok", true).toString()
        }

        @JavascriptInterface fun rejectConnection(endpointId: String): String {
            if (endpointId.isBlank() || endpointId.length > 128) return JSONObject().put("ok", false).put("error", "BAD_ENDPOINT").toString()
            connections.rejectConnection(endpointId).addOnFailureListener { error -> emitFailure("REJECT_FAILED", error) }
            return JSONObject().put("ok", true).toString()
        }

        @JavascriptInterface fun disconnect(endpointId: String): String {
            if (endpointId.isBlank() || endpointId.length > 128) return JSONObject().put("ok", false).put("error", "BAD_ENDPOINT").toString()
            connections.disconnectFromEndpoint(endpointId)
            connected.remove(endpointId)
            emit("disconnected", JSONObject().put("endpointId", endpointId))
            emitStatus()
            return JSONObject().put("ok", true).toString()
        }

        @JavascriptInterface fun send(endpointId: String, payload: String): String {
            if (!connected.containsKey(endpointId)) return JSONObject().put("ok", false).put("error", "NOT_CONNECTED").toString()
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            if (!NativeProtocol.acceptsPayloadSize(bytes.size)) return JSONObject().put("ok", false).put("error", "PAYLOAD_TOO_LARGE").toString()
            val type = runCatching { JSONObject(payload).optString("type") }.getOrNull()
            if (!NativeProtocol.acceptsWireType(type)) return JSONObject().put("ok", false).put("error", "UNSUPPORTED_WIRE_TYPE").toString()
            connections.sendPayload(endpointId, Payload.fromBytes(bytes))
                .addOnFailureListener { error -> emit("sendFailed", JSONObject().put("endpointId", endpointId).put("code", error.javaClass.simpleName)) }
            return JSONObject().put("ok", true).toString()
        }

        @JavascriptInterface fun sendGroup(endpointIdsJson: String, payload: String): String {
            if (!NativeProtocol.acceptsPayloadSize(payload.toByteArray(StandardCharsets.UTF_8).size)) return JSONObject().put("ok", false).put("error", "PAYLOAD_TOO_LARGE").toString()
            val type = runCatching { JSONObject(payload).optString("type") }.getOrNull()
            if (type != "ciphertext-v1") return JSONObject().put("ok", false).put("error", "UNSUPPORTED_WIRE_TYPE").toString()
            val ids = parseEndpointIds(endpointIdsJson).filter { connected.containsKey(it) }
            if (ids.isEmpty()) return JSONObject().put("ok", false).put("error", "NO_CONNECTED_PEERS").toString()
            connections.sendPayload(ids, Payload.fromBytes(payload.toByteArray(StandardCharsets.UTF_8)))
                .addOnFailureListener { error -> emit("sendFailed", JSONObject().put("code", error.javaClass.simpleName)) }
            return JSONObject().put("ok", true).put("queuedPeers", ids.size).toString()
        }

        @JavascriptInterface fun getStatus(): String {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
            val attachmentHealth = runCatching { encryptedAttachmentStore.listAttachments() }.getOrNull()
            val value = JSONObject()
                .put("available", true)
                .put("transport", "Nearby Connections / P2P_CLUSTER")
                .put("buildType", if (debuggable) "debug" else "release")
                .put("advertising", advertising)
                .put("discovering", discovering)
                .put("isMock", false)
                .put("googlePlayServicesAvailable", googlePlayServicesAvailable())
                .put("nearbyAvailable", googlePlayServicesAvailable())
                .put("qrScannerAvailable", runCatching { Class.forName("com.journeyapps.barcodescanner.CaptureActivity") }.isSuccess)
                .put("pairingQrVersion", PairingQr.PROTOCOL_VERSION)
                .put("permissionGranted", permissionsGranted())
                .put("connectedCount", connected.size)
                .put("connected", JSONArray().also { a -> connected.forEach { (id, name) -> a.put(JSONObject().put("endpointId", id).put("endpointName", name)) } })
                .put("realConnectedEndpoints", JSONArray().also { a -> connected.forEach { (id, name) -> a.put(JSONObject().put("endpointId", id).put("endpointName", name)) } })
                .put("secureSessionCount", JSONObject.NULL)
                .put("attachmentCount", encryptedAttachmentStore.attachmentCount())
                .put("attachmentEncryptedBytes", encryptedAttachmentStore.encryptedBytes())
                .put("attachmentVerifiedCount", attachmentHealth?.count { it.integrityStatus == "verified" } ?: JSONObject.NULL)
                .put("attachmentHealth", when { attachmentHealth == null -> "UNAVAILABLE"; attachmentHealth.any { it.integrityStatus == "integrity-failed" } -> "INTEGRITY_FAILURE"; else -> "LISTABLE" })
                .put("legacyPlaintextAttachmentCount", encryptedAttachmentStore.migrationPendingCount())
                .put("secureStateHealth", if (secureStateStore.storedBytes() > 0L) "ENCRYPTED_FILE_PRESENT" else "NOT_INITIALIZED")
                .put("localAI", localAIStatusJson())
                .put("applicationId", packageName)
                .put("versionCode", versionCode)
                .put("versionName", packageInfo.versionName ?: "unknown")
                .put("physicalVerification", "NOT_PERFORMED")
            return value.toString()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        pendingAttachmentExport?.delete()
        pendingAttachmentExport = null
        externalAttachmentFiles.keys.toList().forEach(::revokeAndDeleteAttachment)
        File(cacheDir, "nexus-attachment-share").deleteRecursively()
        outgoingFiles.keys.toList().forEach(::cleanupOutgoing)
        incomingPayloads.keys.toList().forEach(::cleanupIncoming)
        incomingExpectations.clear()
        if (::localAIEngine.isInitialized) localAIEngine.close()
        if (::connections.isInitialized) connections.stopAllEndpoints()
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("NexusNativeNearby")
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val SERVICE_ID = PairingQr.BOOTSTRAP_SERVICE_ID
        private const val PERMISSION_REQUEST = 7041
        private const val FILE_PICK_REQUEST = 7042
        private const val QR_CAMERA_PERMISSION_REQUEST = 7043
        private const val ATTACHMENT_EXPORT_REQUEST = 7044
        private const val LOCAL_AI_MODEL_PICK_REQUEST = 7045
        private const val QR_USED_NONCES_KEY = "consumed_pairing_qr_nonces"
        private const val MAX_REMEMBERED_QR_NONCES = 64
        private const val RETURNED_ATTACHMENT_CLEANUP_DELAY_MS = 2_000L
        private const val EXTERNAL_ATTACHMENT_MAX_AGE_MS = 15 * 60 * 1000L
        private val STRATEGY = Strategy.P2P_CLUSTER
    }
}
