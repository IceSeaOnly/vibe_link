package com.vibelink.client

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.Executors

class MainActivity : Activity(), RemoteScreenView.Listener {
    private enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()
    private lateinit var prefs: SharedPreferences
    private lateinit var serverInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var configContainer: LinearLayout
    private lateinit var configEditButton: Button
    private lateinit var languageButton: Button
    private lateinit var mainConnectionButton: Button
    private lateinit var discoverButton: Button
    private lateinit var scanButton: Button
    private lateinit var rootScroll: ScrollView
    private lateinit var statusText: TextView
    private lateinit var frameText: TextView
    private lateinit var videoView: TextureView
    private lateinit var screenView: RemoteScreenView
    private lateinit var modeButton: ImageButton
    private lateinit var displayContainer: LinearLayout
    private lateinit var displayLabel: TextView
    private lateinit var brandText: TextView
    private lateinit var displaySpinner: Spinner
    private lateinit var textInput: EditText
    private lateinit var keyboardInput: EditText
    private lateinit var submitCheck: CheckBox
    private lateinit var scrollUpButton: Button
    private lateinit var scrollDownButton: Button
    private lateinit var controlsLayout: LinearLayout
    private lateinit var quickTextsTitle: TextView
    private lateinit var quickTextsLayout: LinearLayout
    private lateinit var commandsTitle: TextView
    private lateinit var commandsLayout: LinearLayout
    private lateinit var shortcutsTitle: TextView
    private lateinit var shortcutsLayout: LinearLayout
    private lateinit var outputText: TextView
    private lateinit var copyrightText: TextView
    private var apiClient: ApiClient? = null
    private var streamInput: Closeable? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var streamGeneration = 0
    private var healthGeneration = 0
    private var healthFailures = 0
    private var connectionState = ConnectionState.DISCONNECTED
    private var isConfigExpanded = true
    private var selectedDisplayId: String? = null
    private var remoteDisplays: List<RemoteDisplay> = emptyList()
    private var lastScreenWidth = 0
    private var lastScreenHeight = 0
    private var clientConfigGeneration = 0
    private var keyboardActive = false
    private var keyboardInputInternalChange = false
    private var appLanguage = AppLanguage.EN
    private var lastControlButtons: List<ControlButton> = emptyList()
    private var lastQuickTexts: List<QuickText> = emptyList()
    private var lastCommands: List<QuickCommand> = emptyList()
    private var lastShortcuts: List<ShortcutButton> = emptyList()
    private val keyboardSentinel = "\u200B"

    private val healthRunnable = object : Runnable {
        override fun run() {
            val client = apiClient
            if (connectionState != ConnectionState.CONNECTED || client == null) return
            val generation = healthGeneration
            executor.execute {
                try {
                    client.getHealth()
                    if (generation == healthGeneration) {
                        mainHandler.post { healthFailures = 0 }
                    }
                } catch (_: Exception) {
                    if (generation == healthGeneration) {
                        mainHandler.post {
                            healthFailures += 1
                            if (healthFailures >= 2) {
                                setDisconnected(t(AppText.Key.HEALTH_DISCONNECTED))
                            }
                        }
                    }
                }
            }
            mainHandler.postDelayed(this, 3000L)
        }
    }

    private val clientConfigRunnable = object : Runnable {
        override fun run() {
            val client = apiClient
            if (connectionState != ConnectionState.CONNECTED || client == null) return
            val generation = clientConfigGeneration
            executor.execute {
                try {
                    val config = client.getClientConfig()
                    if (generation == clientConfigGeneration) {
                        mainHandler.post {
                            renderControlButtons(config.controlButtons)
                            renderQuickTexts(config.quickTexts)
                            renderCommands(config.commands)
                            renderShortcuts(config.shortcutButtons)
                        }
                    }
                } catch (_: Exception) {
                    // Keep the last known layout; the next poll will retry.
                }
            }
            mainHandler.postDelayed(this, 7000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("vibelink", MODE_PRIVATE)
        appLanguage = AppLanguage.fromCode(prefs.getString("language", "en"))
        isConfigExpanded = prefs.getString("serverUrl", "").isNullOrBlank() ||
            prefs.getString("token", "").isNullOrBlank()
        buildUi()
        updateStaticText()
        updateConnectionUi()
        updateConfigVisibility()
        updateModeButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDiscovery()
        setDisconnected(t(AppText.Key.DISCONNECTED))
        executor.shutdownNow()
    }

    @Deprecated("Deprecated Android callback kept for the minimal Activity implementation.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == QR_SCAN_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.getStringExtra(QrScanActivity.EXTRA_PAIRING_URI).orEmpty()
            applyPairingUri(uri)
        }
    }

    private fun applyPairingUri(raw: String) {
        try {
            val uri = Uri.parse(raw)
            val url = uri.getQueryParameter("url").orEmpty()
            val token = uri.getQueryParameter("token").orEmpty()
            if (uri.scheme != "vibelink" || uri.host != "pair" || url.isBlank() || token.isBlank()) {
                showError(t(AppText.Key.PAIRING_INVALID))
                return
            }
            serverInput.setText(url)
            tokenInput.setText(token)
            prefs.edit().putString("serverUrl", url).putString("token", token).apply()
            isConfigExpanded = true
            updateConfigVisibility()
            showStatus(t(AppText.Key.PAIRING_APPLIED))
        } catch (_: Exception) {
            showError(t(AppText.Key.PAIRING_INVALID))
        }
    }

    private fun discoverServer() {
        stopDiscovery()
        showStatus(t(AppText.Key.DISCOVERING))
        val manager = getSystemService(NSD_SERVICE) as NsdManager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
                mainHandler.post { showError(t(AppText.Key.DISCOVERY_FAILED)) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.lowercase().contains("_vibelink._tcp")) return
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        val url = "http://$host:${resolved.port}"
                        mainHandler.post {
                            serverInput.setText(url)
                            prefs.edit().putString("serverUrl", url).apply()
                            showStatus("${t(AppText.Key.SERVER_FOUND)}: $url")
                            stopDiscovery()
                        }
                    }
                })
            }
        }
        discoveryListener = listener
        manager.discoverServices("_vibelink._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        mainHandler.postDelayed({
            if (discoveryListener === listener) {
                stopDiscovery()
                showStatus(t(AppText.Key.DISCOVERY_FAILED))
            }
        }, 8000L)
    }

    private fun stopDiscovery() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching {
            (getSystemService(NSD_SERVICE) as NsdManager).stopServiceDiscovery(listener)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF8FAFC.toInt())
        }

        val screenContainer = FrameLayout(this).apply {
            setBackgroundColor(0xFF121826.toInt())
        }
        videoView = TextureView(this).apply {
            visibility = View.GONE
        }
        screenContainer.addView(
            videoView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        screenView = RemoteScreenView(this).apply {
            listener = this@MainActivity
            minimumHeight = dp(300)
        }
        screenContainer.addView(
            screenView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        frameText = TextView(this).apply {
            text = "${t(AppText.Key.FRAME)}: -"
            textSize = 11f
            setTextColor(0xFF9CA3AF.toInt())
            setShadowLayer(2f, 0f, 1f, 0x99000000.toInt())
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        screenContainer.addView(
            frameText,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                topMargin = dp(6)
                leftMargin = dp(6)
            }
        )
        modeButton = ImageButton(this).apply {
            background = roundedDrawable(0xFFFFFFFF.toInt(), dp(20), 0xFFE0E7FF.toInt(), 1)
            setColorFilter(0xFF2563EB.toInt())
            scaleType = ImageView.ScaleType.CENTER
            elevation = dp(4).toFloat()
            contentDescription = t(AppText.Key.SWITCH_INTERACTION_MODE)
            setOnClickListener { toggleInteractionMode() }
        }
        screenContainer.addView(
            modeButton,
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(8)
                bottomMargin = dp(8)
            }
        )
        root.addView(screenContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(320)))

        rootScroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(24))
        }
        rootScroll.addView(content)
        root.addView(rootScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val headerRow = row().apply {
            setPadding(0, 0, 0, dp(8))
        }
        brandText = TextView(this).apply {
            text = t(AppText.Key.BRAND)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF111827.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(brandText, LinearLayout.LayoutParams(0, dp(34), 1f))
        languageButton = button(appLanguage.switchLabel(), ButtonStyle.GHOST) { toggleLanguage() }
        headerRow.addView(languageButton, LinearLayout.LayoutParams(dp(52), dp(34)).apply {
            leftMargin = dp(6)
        })
        mainConnectionButton = button(t(AppText.Key.CONNECT), ButtonStyle.PRIMARY) { onMainConnectionClick() }
        headerRow.addView(mainConnectionButton, LinearLayout.LayoutParams(dp(98), dp(34)).apply {
            leftMargin = dp(6)
        })
        statusText = TextView(this).apply {
            text = t(AppText.Key.NOT_CONNECTED)
            textSize = 11f
            setTextColor(0xFF64748B.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        configEditButton = button(t(AppText.Key.AUTH), ButtonStyle.GHOST) {
            isConfigExpanded = !isConfigExpanded
            updateConfigVisibility()
        }
        headerRow.addView(configEditButton, LinearLayout.LayoutParams(dp(58), dp(34)).apply {
            leftMargin = dp(6)
        })
        content.addView(headerRow, matchWrap())

        configContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        serverInput = EditText(this).apply {
            hint = "http://192.168.1.10:8765"
            setSingleLine()
            setText(prefs.getString("serverUrl", ""))
            background = roundedDrawable(0xFFFFFFFF.toInt(), dp(10), 0xFFE2E8F0.toInt(), 1)
            setPadding(dp(12), 0, dp(12), 0)
        }
        tokenInput = EditText(this).apply {
            hint = t(AppText.Key.TOKEN)
            setSingleLine()
            setText(prefs.getString("token", ""))
            background = roundedDrawable(0xFFFFFFFF.toInt(), dp(10), 0xFFE2E8F0.toInt(), 1)
            setPadding(dp(12), 0, dp(12), 0)
        }
        configContainer.addView(serverInput, matchWrap().apply { bottomMargin = dp(8) })
        configContainer.addView(tokenInput, matchWrap().apply { bottomMargin = dp(8) })
        val pairingRow = row()
        discoverButton = button(t(AppText.Key.FIND_SERVER), ButtonStyle.GHOST) { discoverServer() }
        scanButton = button(t(AppText.Key.SCAN_PAIRING), ButtonStyle.GHOST) { startActivityForResult(Intent(this, QrScanActivity::class.java), QR_SCAN_REQUEST) }
        pairingRow.addView(discoverButton, weightWrap(isFirst = true))
        pairingRow.addView(scanButton, weightWrap(isLast = true))
        configContainer.addView(pairingRow, rowWrap())
        content.addView(configContainer, matchWrap())

        displayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val displayHeaderRow = row()
        displayLabel = TextView(this).apply {
            text = t(AppText.Key.DISPLAY)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF111827.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        displayHeaderRow.addView(displayLabel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)))
        displaySpinner = Spinner(this).apply {
            setPadding(0, 0, 0, 0)
        }
        displayHeaderRow.addView(displaySpinner, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
            leftMargin = dp(8)
        })
        displayContainer.addView(displayHeaderRow, matchWrap())
        content.addView(displayContainer, matchWrap())

        val scrollRow = row()
        scrollUpButton = button(t(AppText.Key.SCROLL_UP)) { sendScroll(0, -360) }
        scrollDownButton = button(t(AppText.Key.SCROLL_DOWN)) { sendScroll(0, 360) }
        scrollRow.addView(scrollUpButton, weightWrap(isFirst = true))
        scrollRow.addView(scrollDownButton, weightWrap(isLast = true))
        content.addView(scrollRow, rowWrap())

        textInput = EditText(this).apply {
            hint = t(AppText.Key.TEXT_TO_SEND)
            minLines = 2
            maxLines = 5
            gravity = Gravity.TOP
            background = roundedDrawable(0xFFFFFFFF.toInt(), dp(10), 0xFFE2E8F0.toInt(), 1)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    this.text.isEmpty()
                ) {
                    sendBackspace()
                    true
                } else {
                    false
                }
            }
        }
        content.addView(textInput, matchWrap())

        submitCheck = CheckBox(this).apply { text = t(AppText.Key.SUBMIT_WITH_ENTER) }
        content.addView(submitCheck)

        keyboardInput = createKeyboardInput()
        content.addView(keyboardInput, LinearLayout.LayoutParams(1, 1))

        controlsLayout = verticalItems()
        content.addView(controlsLayout, matchWrap())
        renderControlButtons(defaultControlButtons())

        quickTextsTitle = section(t(AppText.Key.QUICK_TEXTS))
        content.addView(quickTextsTitle)
        quickTextsLayout = horizontalItems()
        content.addView(wrapHorizontal(quickTextsLayout))

        commandsTitle = section(t(AppText.Key.COMMANDS))
        content.addView(commandsTitle)
        commandsLayout = verticalItems()
        content.addView(commandsLayout)

        outputText = TextView(this).apply {
            text = t(AppText.Key.COMMAND_OUTPUT)
            setTextColor(0xFF1F2937.toInt())
            background = roundedDrawable(0xFFEFF6FF.toInt(), dp(10), 0xFFBFDBFE.toInt(), 1)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        content.addView(outputText, matchWrap())

        shortcutsTitle = section(t(AppText.Key.SHORTCUT_BUTTONS))
        content.addView(shortcutsTitle)
        shortcutsLayout = horizontalItems()
        content.addView(wrapHorizontal(shortcutsLayout))

        copyrightText = TextView(this).apply {
            text = t(AppText.Key.COPYRIGHT)
            textSize = 10f
            setTextColor(0xFF94A3B8.toInt())
            gravity = Gravity.CENTER
            maxLines = 2
        }
        content.addView(copyrightText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
        })

        setContentView(root)
    }

    private fun onMainConnectionClick() {
        when (connectionState) {
            ConnectionState.DISCONNECTED -> connect()
            ConnectionState.CONNECTING -> Unit
            ConnectionState.CONNECTED -> setDisconnected(t(AppText.Key.DISCONNECTED))
        }
    }

    private fun connect() {
        val serverUrl = serverInput.text.toString().trim()
        val token = tokenInput.text.toString().trim()
        if (serverUrl.isBlank() || token.isBlank()) {
            showError(t(AppText.Key.ENTER_SERVER_AND_TOKEN))
            isConfigExpanded = true
            updateConfigVisibility()
            return
        }
        setConnectionState(ConnectionState.CONNECTING, t(AppText.Key.CONNECTING_STATUS))
        executor.execute {
            try {
                val client = ApiClient(serverUrl, token)
                val health = client.getHealth()
                val displays = try {
                    client.getDisplays()
                } catch (_: Exception) {
                    health.displays
                }
                prefs.edit().putString("serverUrl", serverUrl).putString("token", token).apply()
                mainHandler.post {
                    apiClient = client
                    healthFailures = 0
                    lastScreenWidth = health.screenWidth
                    lastScreenHeight = health.screenHeight
                    remoteDisplays = displays
                    selectedDisplayId = displays.firstOrNull { it.isMain }?.id ?: displays.firstOrNull()?.id
                    isConfigExpanded = false
                    setConnectionState(
                        ConnectionState.CONNECTED,
                        "${t(AppText.Key.CONNECTED)}: ${health.name} ${health.version} ${health.screenWidth}x${health.screenHeight}"
                    )
                    updateConfigVisibility()
                    renderDisplays(displays)
                    startHealthChecks()
                    startStream(client)
                }
                loadQuickActions(client)
            } catch (error: Exception) {
                mainHandler.post {
                    setDisconnected("${t(AppText.Key.CONNECT_FAILED)}: ${error.shortMessage()}", showToast = true)
                }
            }
        }
    }

    private fun setDisconnected(reason: String, showToast: Boolean = false) {
        streamGeneration++
        healthGeneration++
        clientConfigGeneration++
        mainHandler.removeCallbacks(healthRunnable)
        mainHandler.removeCallbacks(clientConfigRunnable)
        streamInput?.close()
        streamInput = null
        apiClient = null
        selectedDisplayId = null
        remoteDisplays = emptyList()
        mainHandler.post {
            connectionState = ConnectionState.DISCONNECTED
            statusText.text = reason
            frameText.text = "${t(AppText.Key.FRAME)}: -"
            if (::videoView.isInitialized) videoView.visibility = View.GONE
            if (::screenView.isInitialized) screenView.videoUnderlayMode = false
            displayContainer.visibility = View.GONE
            updateConnectionUi()
            if (showToast) Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setConnectionState(state: ConnectionState, message: String? = null) {
        connectionState = state
        if (message != null) statusText.text = message
        updateConnectionUi()
    }

    private fun updateConnectionUi() {
        if (!::mainConnectionButton.isInitialized) return
        mainConnectionButton.text = when (connectionState) {
            ConnectionState.DISCONNECTED -> t(AppText.Key.CONNECT)
            ConnectionState.CONNECTING -> t(AppText.Key.CONNECTING)
            ConnectionState.CONNECTED -> t(AppText.Key.DISCONNECT)
        }
        mainConnectionButton.isEnabled = connectionState != ConnectionState.CONNECTING
        configEditButton.isEnabled = connectionState != ConnectionState.CONNECTING
        when (connectionState) {
            ConnectionState.DISCONNECTED -> styleButton(mainConnectionButton, ButtonStyle.PRIMARY)
            ConnectionState.CONNECTING -> styleButton(mainConnectionButton, ButtonStyle.DISABLED)
            ConnectionState.CONNECTED -> styleButton(mainConnectionButton, ButtonStyle.DANGER)
        }
        styleButton(configEditButton, ButtonStyle.GHOST)
    }

    private fun updateConfigVisibility() {
        if (!::configContainer.isInitialized) return
        configContainer.visibility = if (isConfigExpanded) View.VISIBLE else View.GONE
        configEditButton.text = t(AppText.Key.AUTH)
    }

    private fun startHealthChecks() {
        healthGeneration++
        healthFailures = 0
        mainHandler.removeCallbacks(healthRunnable)
        mainHandler.postDelayed(healthRunnable, 3000L)
    }

    private fun startClientConfigPolling() {
        clientConfigGeneration++
        mainHandler.removeCallbacks(clientConfigRunnable)
        clientConfigRunnable.run()
    }

    private fun renderDisplays(displays: List<RemoteDisplay>) {
        displayContainer.visibility = View.VISIBLE
        displaySpinner.onItemSelectedListener = null
        if (displays.isEmpty()) {
            displaySpinner.adapter = compactDisplayAdapter(listOf(t(AppText.Key.DEFAULT_STREAM)))
            displaySpinner.isEnabled = false
            return
        }
        val labels = displays.map {
            "${it.name} (${it.width}x${it.height})"
        }
        displaySpinner.isEnabled = displays.size > 1
        displaySpinner.adapter = compactDisplayAdapter(labels)
        val selectedIndex = displays.indexOfFirst { it.id == selectedDisplayId }.coerceAtLeast(0)
        displaySpinner.setSelection(selectedIndex, false)
        displaySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val display = remoteDisplays.getOrNull(position) ?: return
                if (display.id == selectedDisplayId) return
                selectedDisplayId = display.id
                lastScreenWidth = display.width
                lastScreenHeight = display.height
                apiClient?.let { startStream(it) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun startStream(client: ApiClient) {
        val generation = ++streamGeneration
        val displayId = selectedDisplayId
        streamInput?.close()
        streamInput = null
        executor.execute {
            var h264Rendered = false
            try {
                val width = evenDimension(lastScreenWidth.takeIf { it > 0 } ?: remoteDisplays.firstOrNull { it.id == displayId }?.width ?: client.getHealth().screenWidth)
                val height = evenDimension(lastScreenHeight.takeIf { it > 0 } ?: remoteDisplays.firstOrNull { it.id == displayId }?.height ?: client.getHealth().screenHeight)
                val h264Reader = H264StreamReader.connect(serverInput.text.toString().trim(), tokenInput.text.toString().trim(), displayId)
                val player = H264VideoStreamPlayer(videoView)
                streamInput = Closeable {
                    player.close()
                    h264Reader.close()
                }
                mainHandler.post { prepareVideoFrame(width, height) }
                player.play(h264Reader, width, height) {
                    if (generation != streamGeneration) return@play
                    h264Rendered = true
                    mainHandler.post { updateVideoFrame(width, height) }
                }
                if (generation == streamGeneration) {
                    mainHandler.post { setDisconnected(t(AppText.Key.STREAM_ENDED)) }
                }
            } catch (videoError: Exception) {
                streamInput?.close()
                streamInput = null
                if (h264Rendered) {
                    if (generation == streamGeneration) {
                        mainHandler.post { setDisconnected("${t(AppText.Key.STREAM_ERROR)} ${videoError.shortMessage()}") }
                    }
                    return@execute
                }
                try {
                    val reader = try {
                        WebSocketFrameReader.connect(serverInput.text.toString().trim(), tokenInput.text.toString().trim(), displayId)
                    } catch (_: Exception) {
                        null
                    }
                    if (reader != null) {
                        streamInput = reader
                        reader.readFrames { frame ->
                            if (generation != streamGeneration) return@readFrames
                            mainHandler.post { updateFrame(frame) }
                        }
                    } else {
                        val input = client.openStream(displayId)
                        streamInput = input
                        MjpegStreamReader(input).readFrames { frame ->
                            if (generation != streamGeneration) return@readFrames
                            mainHandler.post { updateFrame(frame) }
                        }
                    }
                    if (generation == streamGeneration) {
                        mainHandler.post { setDisconnected(t(AppText.Key.STREAM_ENDED)) }
                    }
                } catch (error: Exception) {
                    if (generation == streamGeneration) {
                        mainHandler.post { setDisconnected("${t(AppText.Key.STREAM_ERROR)} ${error.shortMessage()}") }
                    }
                }
            }
        }
    }

    private fun prepareVideoFrame(width: Int, height: Int) {
        lastScreenWidth = width
        lastScreenHeight = height
        videoView.visibility = View.VISIBLE
        screenView.videoUnderlayMode = true
        screenView.setFrame(Bitmap.createBitmap(width.coerceAtLeast(2), height.coerceAtLeast(2), Bitmap.Config.ARGB_8888))
    }

    private fun updateVideoFrame(width: Int, height: Int) {
        lastScreenWidth = width
        lastScreenHeight = height
        frameText.text = "${t(AppText.Key.FRAME)}: H.264 ${width}x${height} ${System.currentTimeMillis()}"
    }

    private fun updateFrame(frame: Bitmap) {
        lastScreenWidth = frame.width
        lastScreenHeight = frame.height
        if (::videoView.isInitialized) videoView.visibility = View.GONE
        screenView.videoUnderlayMode = false
        screenView.setFrame(frame)
        frameText.text = "${t(AppText.Key.FRAME)}: ${frame.width}x${frame.height} ${System.currentTimeMillis()}"
    }

    private fun applyVideoTransform(geometry: ImageGeometry) {
        videoView.post {
            if (videoView.width <= 0 || videoView.height <= 0 || geometry.imageWidth <= 0 || geometry.imageHeight <= 0) return@post
            val drawnWidth = geometry.imageWidth * geometry.scale
            val drawnHeight = geometry.imageHeight * geometry.scale
            val scaleX = drawnWidth / videoView.width.toFloat()
            val scaleY = drawnHeight / videoView.height.toFloat()
            val matrix = Matrix()
            matrix.setScale(scaleX, scaleY)
            matrix.postTranslate(geometry.offsetX, geometry.offsetY)
            videoView.setTransform(matrix)
        }
    }

    private fun evenDimension(value: Int): Int = value.coerceAtLeast(2).let { it - (it % 2) }

    private fun compactDisplayAdapter(labels: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return displaySpinnerText(getItem(position).orEmpty(), isDropdown = false)
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return displaySpinnerText(getItem(position).orEmpty(), isDropdown = true)
            }
        }
    }

    private fun displaySpinnerText(text: String, isDropdown: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = if (isDropdown) 14f else 12f
            setTextColor(if (isDropdown) 0xFF111827.toInt() else 0xFF64748B.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
    }

    private fun loadQuickActions(client: ApiClient) {
        executor.execute {
            try {
                val config = client.getClientConfig()
                mainHandler.post {
                    renderControlButtons(config.controlButtons)
                    renderQuickTexts(config.quickTexts)
                    renderCommands(config.commands)
                    renderShortcuts(config.shortcutButtons)
                    startClientConfigPolling()
                }
            } catch (error: Exception) {
                mainHandler.post { showError("${t(AppText.Key.ACTIONS_LOAD_FAILED)}: ${error.shortMessage()}") }
            }
        }
    }

    private fun renderControlButtons(items: List<ControlButton>) {
        lastControlButtons = items
        val buttons = items.ifEmpty { defaultControlButtons() }
        controlsLayout.removeAllViews()
        buttons.chunked(4).forEach { rowItems ->
            val controlsRow = row()
            rowItems.forEachIndexed { index, item ->
                controlsRow.addView(button(AppText.controlLabel(item.type, item.label, appLanguage)) { runControlButton(item.type) }, weightWrap(isFirst = index == 0, isLast = index == rowItems.lastIndex))
            }
            if (rowItems.size < 4) {
                repeat(4 - rowItems.size) {
                    controlsRow.addView(View(this), weightWrap())
                }
            }
            controlsLayout.addView(controlsRow, rowWrap())
        }
    }

    private fun renderQuickTexts(items: List<QuickText>) {
        lastQuickTexts = items
        quickTextsLayout.removeAllViews()
        items.forEach { item ->
            quickTextsLayout.addView(button(AppText.quickTextName(item.id, item.name, appLanguage)) {
                sendControl(JSONObject().put("type", "text").put("text", item.content).put("submit", submitCheck.isChecked))
            }, horizontalItemWrap())
        }
    }

    private fun renderCommands(items: List<QuickCommand>) {
        lastCommands = items
        commandsLayout.removeAllViews()
        items.chunked(4).forEach { rowItems ->
            val commandsRow = row()
            rowItems.forEachIndexed { index, item ->
                commandsRow.addView(button(AppText.commandName(item.id, item.name, appLanguage)) { runCommand(item.id) }, weightWrap(isFirst = index == 0, isLast = index == rowItems.lastIndex))
            }
            if (rowItems.size < 4) {
                repeat(4 - rowItems.size) {
                    commandsRow.addView(View(this), weightWrap())
                }
            }
            commandsLayout.addView(commandsRow, rowWrap())
        }
    }

    private fun renderShortcuts(items: List<ShortcutButton>) {
        lastShortcuts = items
        shortcutsLayout.removeAllViews()
        items.forEach { item ->
            shortcutsLayout.addView(button(AppText.shortcutName(item.id, item.name, appLanguage)) { runShortcut(item.id) }, horizontalItemWrap())
        }
        shortcutsLayout.addView(button(t(AppText.Key.ADD_SHORTCUT), ButtonStyle.GHOST) { startShortcutCapture() }, horizontalItemWrap())
    }

    private fun sendText() {
        val text = textInput.text.toString()
        if (text.isBlank()) {
            showError(t(AppText.Key.TEXT_EMPTY))
            return
        }
        textInput.text.clear()
        sendControl(JSONObject().put("type", "text").put("text", text).put("submit", submitCheck.isChecked))
    }

    private fun sendBackspace() {
        sendControl(JSONObject().put("type", "backspace"))
    }

    private fun sendKeyAction(type: String) {
        sendControl(JSONObject().put("type", type))
    }

    private fun runControlButton(type: String) {
        when (type) {
            "sendText" -> sendText()
            "backspace" -> sendBackspace()
            "keyboard", "voice" -> toggleKeyboardInput()
            else -> sendKeyAction(type)
        }
    }

    private fun defaultControlButtons(): List<ControlButton> = ControlButtonDefaults.defaultButtons()

    private fun runCommand(id: String) {
        val client = apiClient ?: return showError(t(AppText.Key.NOT_CONNECTED_ERROR))
        outputText.text = t(AppText.Key.STARTING_COMMAND)
        executor.execute {
            try {
                val runId = client.runCommand(id)
                if (runId.isBlank()) throw IllegalStateException("missing runId")
                pollCommand(client, runId)
            } catch (error: Exception) {
                mainHandler.post { showError("${t(AppText.Key.COMMAND_FAILED)}: ${error.shortMessage()}") }
            }
        }
    }

    private fun pollCommand(client: ApiClient, runId: String) {
        repeat(60) {
            val run = client.getCommandRun(runId)
            mainHandler.post {
                outputText.text = "${t(AppText.Key.COMMAND_STATUS)}: ${run.status} ${t(AppText.Key.EXIT)}: ${run.exitCode ?: "-"}\n${run.output}"
            }
            if (run.status != "queued" && run.status != "running") return
            Thread.sleep(1000)
        }
    }

    private fun runShortcut(id: String) {
        val client = apiClient ?: return showError(t(AppText.Key.NOT_CONNECTED_ERROR))
        executor.execute {
            try {
                client.runShortcutButton(id)
                mainHandler.post { showStatus(t(AppText.Key.SHORTCUT_SENT)) }
            } catch (error: Exception) {
                mainHandler.post { showError("${t(AppText.Key.SHORTCUT_FAILED)}: ${error.shortMessage()}") }
            }
        }
    }

    private fun startShortcutCapture() {
        if (connectionState != ConnectionState.CONNECTED) {
            showError(t(AppText.Key.CONNECT_BEFORE_SHORTCUT))
            return
        }
        if (!screenView.startShortcutCapture()) {
            showError(t(AppText.Key.NO_SCREEN_FRAME))
            return
        }
        showStatus(t(AppText.Key.DRAG_POINTER))
    }

    override fun onTap(x: Int, y: Int, imageWidth: Int, imageHeight: Int) {
        sendControl(JSONObject().put("type", "tap").put("x", x).put("y", y).putScreen(imageWidth, imageHeight))
    }

    override fun onDoubleTap(x: Int, y: Int, imageWidth: Int, imageHeight: Int) {
        sendControl(JSONObject().put("type", "doubleTap").put("x", x).put("y", y).putScreen(imageWidth, imageHeight))
    }

    override fun onRightClick(x: Int, y: Int, imageWidth: Int, imageHeight: Int) {
        sendControl(JSONObject().put("type", "rightClick").put("x", x).put("y", y).putScreen(imageWidth, imageHeight))
    }

    override fun onMove(x: Int, y: Int, imageWidth: Int, imageHeight: Int) {
        sendControl(JSONObject().put("type", "move").put("x", x).put("y", y).putScreen(imageWidth, imageHeight), showSentStatus = false)
    }

    override fun onDrag(fromX: Int, fromY: Int, toX: Int, toY: Int, imageWidth: Int, imageHeight: Int, durationMs: Long) {
        sendControl(
            JSONObject()
                .put("type", "drag")
                .put("fromX", fromX)
                .put("fromY", fromY)
                .put("toX", toX)
                .put("toY", toY)
                .put("durationMs", durationMs)
                .putScreen(imageWidth, imageHeight)
        )
    }

    override fun onRelativeMove(deltaX: Int, deltaY: Int) {
        sendControl(JSONObject().put("type", "relativeMove").put("deltaX", deltaX).put("deltaY", deltaY), showSentStatus = false)
    }

    override fun onCurrentTap() {
        sendControl(JSONObject().put("type", "clickCurrent"))
    }

    override fun onCurrentDoubleTap() {
        sendControl(JSONObject().put("type", "doubleClickCurrent"))
    }

    override fun onCurrentRightClick() {
        sendControl(JSONObject().put("type", "rightClickCurrent"))
    }

    override fun onCurrentDragStart() {
        sendControl(JSONObject().put("type", "mouseDownCurrent"), showSentStatus = false)
    }

    override fun onCurrentDragMove(deltaX: Int, deltaY: Int) {
        sendControl(JSONObject().put("type", "relativeDrag").put("deltaX", deltaX).put("deltaY", deltaY), showSentStatus = false)
    }

    override fun onCurrentDragEnd() {
        sendControl(JSONObject().put("type", "mouseUpCurrent"), showSentStatus = false)
    }

    override fun onScroll(deltaX: Int, deltaY: Int) {
        sendScroll(deltaX, deltaY)
    }

    override fun onShortcutPointSelected(x: Int, y: Int, imageWidth: Int, imageHeight: Int) {
        val input = EditText(this).apply {
            hint = t(AppText.Key.SHORTCUT_NAME)
            setSingleLine()
            setText(t(AppText.Key.SHORTCUT))
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(t(AppText.Key.NAME_SHORTCUT))
            .setView(input)
            .setNegativeButton(t(AppText.Key.CANCEL), null)
            .setPositiveButton(t(AppText.Key.SAVE)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    showError(t(AppText.Key.SHORTCUT_NAME_EMPTY))
                } else {
                    saveShortcutPoint(name, x, y, imageWidth, imageHeight)
                }
            }
            .show()
    }

    override fun onViewportChanged(geometry: ImageGeometry) {
        if (::videoView.isInitialized && videoView.visibility == View.VISIBLE) {
            applyVideoTransform(geometry)
        }
    }

    private fun saveShortcutPoint(name: String, x: Int, y: Int, imageWidth: Int, imageHeight: Int) {
        val client = apiClient ?: return showError(t(AppText.Key.NOT_CONNECTED_ERROR))
        val button = ShortcutButton(
            id = "shortcut_${System.currentTimeMillis()}",
            name = name,
            x = x.toDouble(),
            y = y.toDouble(),
            screenWidth = imageWidth.toDouble(),
            screenHeight = imageHeight.toDouble(),
            requiresConfirmation = false
        )
        executor.execute {
            try {
                val shortcuts = client.saveShortcutButton(button)
                mainHandler.post {
                    renderShortcuts(shortcuts)
                    showStatus(t(AppText.Key.SHORTCUT_SAVED))
                }
            } catch (error: Exception) {
                mainHandler.post { showError("${t(AppText.Key.SHORTCUT_SAVE_FAILED)}: ${error.shortMessage()}") }
            }
        }
    }

    private fun sendScroll(deltaX: Int, deltaY: Int) {
        sendControl(JSONObject().put("type", "scroll").put("deltaX", deltaX).put("deltaY", deltaY))
    }

    private fun sendControl(payload: JSONObject, showSentStatus: Boolean = true) {
        val client = apiClient ?: return showError(t(AppText.Key.NOT_CONNECTED_ERROR))
        val decorated = payload.withDisplayContext()
        executor.execute {
            try {
                client.sendControl(decorated)
                if (showSentStatus) {
                    mainHandler.post { showStatus("${t(AppText.Key.SENT)} ${decorated.optString("type")}") }
                }
            } catch (error: Exception) {
                mainHandler.post { showError("${t(AppText.Key.SEND_FAILED)}: ${error.shortMessage()}") }
            }
        }
    }

    private fun JSONObject.withDisplayContext(): JSONObject {
        val display = remoteDisplays.firstOrNull { it.id == selectedDisplayId }
        val width = optInt("screenWidth", lastScreenWidth.takeIf { it > 0 } ?: display?.width ?: 0)
        val height = optInt("screenHeight", lastScreenHeight.takeIf { it > 0 } ?: display?.height ?: 0)
        selectedDisplayId?.takeIf { it.isNotBlank() }?.let { put("displayId", it) }
        put("screenWidth", width)
        put("screenHeight", height)
        return this
    }

    private fun toggleInteractionMode() {
        screenView.interactionMode = when (screenView.interactionMode) {
            InteractionMode.SCREEN -> InteractionMode.POINTER
            InteractionMode.POINTER -> InteractionMode.TRACKPAD
            InteractionMode.TRACKPAD -> InteractionMode.SCREEN
        }
        updateModeButton()
    }

    private fun updateModeButton() {
        if (!::modeButton.isInitialized) return
        when (screenView.interactionMode) {
            InteractionMode.SCREEN -> {
                modeButton.setImageResource(R.drawable.ic_mode_view)
                modeButton.contentDescription = t(AppText.Key.SCREEN_VIEW_MODE)
            }
            InteractionMode.POINTER -> {
                modeButton.setImageResource(R.drawable.ic_mode_pointer)
                modeButton.contentDescription = t(AppText.Key.POINTER_CLICK_MODE)
            }
            InteractionMode.TRACKPAD -> {
                modeButton.setImageResource(R.drawable.ic_mode_trackpad)
                modeButton.contentDescription = t(AppText.Key.TRACKPAD_MODE)
            }
        }
    }

    private fun createKeyboardInput(): EditText = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setSingleLine(false)
        minLines = 1
        maxLines = 1
        alpha = 0f
        isCursorVisible = false
        includeFontPadding = false
        setTextColor(0x00000000)
        setBackgroundColor(0x00000000)
        isFocusable = true
        isFocusableInTouchMode = true
        setText(keyboardSentinel)
        setSelection(text.length)
        setOnKeyListener { _, keyCode, event ->
            if (!keyboardActive || event.action != KeyEvent.ACTION_DOWN) {
                false
            } else {
                when (keyCode) {
                    KeyEvent.KEYCODE_DEL -> {
                        if (text.toString() == keyboardSentinel) {
                            sendBackspace()
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_ENTER -> {
                        resetKeyboardInput()
                        sendKeyAction("enter")
                        true
                    }
                    else -> false
                }
            }
        }
        setOnEditorActionListener { _, _, _ ->
            if (keyboardActive) {
                resetKeyboardInput()
                sendKeyAction("enter")
                true
            } else {
                false
            }
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                handleKeyboardInputChanged(s ?: return)
            }
        })
    }

    private fun toggleKeyboardInput() {
        if (keyboardActive) {
            closeKeyboardInput()
        } else {
            openKeyboardInput()
        }
    }

    private fun openKeyboardInput() {
        keyboardActive = true
        resetKeyboardInput()
        keyboardInput.requestFocus()
        keyboardInput.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(keyboardInput, InputMethodManager.SHOW_IMPLICIT)
        }
        mainHandler.postDelayed({
            rootScroll.smoothScrollTo(0, controlsLayout.top)
        }, 250L)
        mainHandler.postDelayed({
            rootScroll.scrollTo(0, controlsLayout.top)
        }, 600L)
        showStatus(t(AppText.Key.KEYBOARD_OPEN))
    }

    private fun closeKeyboardInput() {
        keyboardActive = false
        resetKeyboardInput()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(keyboardInput.windowToken, 0)
        keyboardInput.clearFocus()
        showStatus(t(AppText.Key.KEYBOARD_CLOSED))
    }

    private fun handleKeyboardInputChanged(editable: Editable) {
        if (keyboardInputInternalChange || !keyboardActive) return
        val composingStart = BaseInputConnection.getComposingSpanStart(editable)
        val composingEnd = BaseInputConnection.getComposingSpanEnd(editable)
        if (composingStart >= 0 && composingEnd >= 0) return

        val value = editable.toString()
        when {
            value == keyboardSentinel -> Unit
            value.startsWith(keyboardSentinel) && value.length > keyboardSentinel.length -> {
                val typed = value.substring(keyboardSentinel.length)
                resetKeyboardInput()
                sendKeyboardText(typed)
            }
            else -> {
                resetKeyboardInput()
                sendBackspace()
            }
        }
    }

    private fun sendKeyboardText(text: String) {
        if (text.isEmpty()) return
        sendControl(JSONObject().put("type", "text").put("text", text).put("submit", false), showSentStatus = false)
    }

    private fun resetKeyboardInput() {
        if (!::keyboardInput.isInitialized) return
        keyboardInputInternalChange = true
        keyboardInput.setText(keyboardSentinel)
        keyboardInput.setSelection(keyboardInput.text.length)
        keyboardInputInternalChange = false
    }

    private fun toggleLanguage() {
        appLanguage = appLanguage.toggle()
        prefs.edit().putString("language", appLanguage.code()).apply()
        updateStaticText()
        renderControlButtons(lastControlButtons.ifEmpty { defaultControlButtons() })
        renderQuickTexts(lastQuickTexts)
        renderCommands(lastCommands)
        renderShortcuts(lastShortcuts)
        if (displayContainer.visibility == View.VISIBLE) renderDisplays(remoteDisplays)
        updateConnectionUi()
        updateModeButton()
    }

    private fun updateStaticText() {
        if (::languageButton.isInitialized) languageButton.text = appLanguage.switchLabel()
        if (::brandText.isInitialized) {
            brandText.text = t(AppText.Key.BRAND)
            title = t(AppText.Key.BRAND)
        }
        if (::configEditButton.isInitialized) configEditButton.text = t(AppText.Key.AUTH)
        if (::discoverButton.isInitialized) discoverButton.text = t(AppText.Key.FIND_SERVER)
        if (::scanButton.isInitialized) scanButton.text = t(AppText.Key.SCAN_PAIRING)
        if (::displayLabel.isInitialized) displayLabel.text = t(AppText.Key.DISPLAY)
        if (::scrollUpButton.isInitialized) scrollUpButton.text = t(AppText.Key.SCROLL_UP)
        if (::scrollDownButton.isInitialized) scrollDownButton.text = t(AppText.Key.SCROLL_DOWN)
        if (::textInput.isInitialized) textInput.hint = t(AppText.Key.TEXT_TO_SEND)
        if (::tokenInput.isInitialized) tokenInput.hint = t(AppText.Key.TOKEN)
        if (::submitCheck.isInitialized) submitCheck.text = t(AppText.Key.SUBMIT_WITH_ENTER)
        if (::quickTextsTitle.isInitialized) quickTextsTitle.text = t(AppText.Key.QUICK_TEXTS)
        if (::commandsTitle.isInitialized) commandsTitle.text = t(AppText.Key.COMMANDS)
        if (::shortcutsTitle.isInitialized) shortcutsTitle.text = t(AppText.Key.SHORTCUT_BUTTONS)
        if (::copyrightText.isInitialized) copyrightText.text = t(AppText.Key.COPYRIGHT)
        if (::outputText.isInitialized && outputText.text.toString() == AppText.text(AppText.Key.COMMAND_OUTPUT, appLanguage.toggle())) {
            outputText.text = t(AppText.Key.COMMAND_OUTPUT)
        }
        if (::frameText.isInitialized) {
            frameText.text = if (lastScreenWidth > 0 && lastScreenHeight > 0) {
                "${t(AppText.Key.FRAME)}: ${lastScreenWidth}x${lastScreenHeight} ${System.currentTimeMillis()}"
            } else {
                "${t(AppText.Key.FRAME)}: -"
            }
        }
        if (::screenView.isInitialized) screenView.noFrameText = t(AppText.Key.NO_FRAME)
        if (::statusText.isInitialized && connectionState == ConnectionState.DISCONNECTED) {
            statusText.text = t(AppText.Key.NOT_CONNECTED)
        }
    }

    private fun t(key: AppText.Key): String = AppText.text(key, appLanguage)

    private fun showStatus(message: String) {
        statusText.text = message
    }

    private fun showError(message: String) {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(16), 0, dp(6))
        setTextColor(0xFF111827.toInt())
    }

    private fun button(text: String, style: ButtonStyle = ButtonStyle.SECONDARY, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        minHeight = dp(34)
        minimumHeight = dp(34)
        minWidth = 0
        minimumWidth = 0
        includeFontPadding = false
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(10), 0, dp(10), 0)
        styleButton(this, style)
        setOnClickListener { onClick() }
    }

    private fun styleButton(button: Button, style: ButtonStyle) {
        val (fill, pressed, stroke, text) = when (style) {
            ButtonStyle.PRIMARY -> ButtonColors(0xFF2563EB.toInt(), 0xFF1D4ED8.toInt(), 0xFF2563EB.toInt(), 0xFFFFFFFF.toInt())
            ButtonStyle.SECONDARY -> ButtonColors(0xFFFFFFFF.toInt(), 0xFFEFF6FF.toInt(), 0xFFCBD5E1.toInt(), 0xFF1E293B.toInt())
            ButtonStyle.GHOST -> ButtonColors(0xFFEFF6FF.toInt(), 0xFFDBEAFE.toInt(), 0xFFBFDBFE.toInt(), 0xFF1D4ED8.toInt())
            ButtonStyle.DANGER -> ButtonColors(0xFFDC2626.toInt(), 0xFFB91C1C.toInt(), 0xFFDC2626.toInt(), 0xFFFFFFFF.toInt())
            ButtonStyle.DISABLED -> ButtonColors(0xFFE2E8F0.toInt(), 0xFFE2E8F0.toInt(), 0xFFCBD5E1.toInt(), 0xFF64748B.toInt())
        }
        button.background = stateDrawable(fill, pressed, dp(10), stroke, 1)
        button.setTextColor(text)
        button.elevation = if (style == ButtonStyle.PRIMARY || style == ButtonStyle.DANGER) dp(2).toFloat() else 0f
    }

    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun horizontalItems(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun verticalItems(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    private fun wrapHorizontal(child: View): HorizontalScrollView = HorizontalScrollView(this).apply {
        addView(child)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun rowWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        bottomMargin = dp(6)
    }

    private fun weightWrap(isFirst: Boolean = false, isLast: Boolean = false) = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
    ).apply {
        leftMargin = if (isFirst) 0 else dp(3)
        rightMargin = if (isLast) 0 else dp(3)
    }

    private fun horizontalItemWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        rightMargin = dp(8)
        bottomMargin = dp(4)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun roundedDrawable(fillColor: Int, radius: Int, strokeColor: Int = 0, strokeWidth: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = radius.toFloat()
            if (strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor)
        }
    }

    private fun stateDrawable(
        fillColor: Int,
        pressedColor: Int,
        radius: Int,
        strokeColor: Int,
        strokeWidth: Int
    ): StateListDrawable {
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), roundedDrawable(pressedColor, radius, strokeColor, strokeWidth))
            addState(intArrayOf(), roundedDrawable(fillColor, radius, strokeColor, strokeWidth))
        }
    }

    private enum class ButtonStyle {
        PRIMARY,
        SECONDARY,
        GHOST,
        DANGER,
        DISABLED
    }

    private data class ButtonColors(
        val fill: Int,
        val pressed: Int,
        val stroke: Int,
        val text: Int
    )

    companion object {
        private const val QR_SCAN_REQUEST = 2048
    }
}

private fun JSONObject.putScreen(width: Int, height: Int): JSONObject {
    put("screenWidth", width)
    put("screenHeight", height)
    return this
}

private fun Exception.shortMessage(): String = message?.take(140) ?: javaClass.simpleName
