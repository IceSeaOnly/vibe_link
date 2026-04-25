package com.vibelink.client

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
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
import java.io.InputStream
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
    private lateinit var mainConnectionButton: Button
    private lateinit var rootScroll: ScrollView
    private lateinit var statusText: TextView
    private lateinit var frameText: TextView
    private lateinit var screenView: RemoteScreenView
    private lateinit var modeButton: ImageButton
    private lateinit var displayContainer: LinearLayout
    private lateinit var displaySpinner: Spinner
    private lateinit var textInput: EditText
    private lateinit var keyboardInput: EditText
    private lateinit var submitCheck: CheckBox
    private lateinit var controlsLayout: LinearLayout
    private lateinit var quickTextsLayout: LinearLayout
    private lateinit var commandsLayout: LinearLayout
    private lateinit var shortcutsLayout: LinearLayout
    private lateinit var outputText: TextView
    private var apiClient: ApiClient? = null
    private var streamInput: InputStream? = null
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
                                setDisconnected("Disconnected: health check failed")
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
        isConfigExpanded = prefs.getString("serverUrl", "").isNullOrBlank() ||
            prefs.getString("token", "").isNullOrBlank()
        buildUi()
        updateConnectionUi()
        updateConfigVisibility()
        updateModeButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        setDisconnected("Disconnected")
        executor.shutdownNow()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF8FAFC.toInt())
        }

        val screenContainer = FrameLayout(this).apply {
            setBackgroundColor(0xFF121826.toInt())
        }
        screenView = RemoteScreenView(this).apply {
            listener = this@MainActivity
            minimumHeight = dp(300)
        }
        screenContainer.addView(
            screenView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        frameText = TextView(this).apply {
            text = "Frame: -"
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
            contentDescription = "Switch interaction mode"
            setOnClickListener { toggleInteractionMode() }
        }
        screenContainer.addView(
            modeButton,
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                rightMargin = dp(8)
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
        headerRow.addView(TextView(this).apply {
            text = "VibeLink"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF111827.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(34), 1f))
        mainConnectionButton = button("Connect", ButtonStyle.PRIMARY) { onMainConnectionClick() }
        headerRow.addView(mainConnectionButton, LinearLayout.LayoutParams(dp(98), dp(34)).apply {
            leftMargin = dp(6)
        })
        statusText = TextView(this).apply {
            text = "Not connected"
            textSize = 11f
            setTextColor(0xFF64748B.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        configEditButton = button("Auth", ButtonStyle.GHOST) {
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
            hint = "token"
            setSingleLine()
            setText(prefs.getString("token", ""))
            background = roundedDrawable(0xFFFFFFFF.toInt(), dp(10), 0xFFE2E8F0.toInt(), 1)
            setPadding(dp(12), 0, dp(12), 0)
        }
        configContainer.addView(serverInput, matchWrap().apply { bottomMargin = dp(8) })
        configContainer.addView(tokenInput, matchWrap().apply { bottomMargin = dp(8) })
        content.addView(configContainer, matchWrap())

        displayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val displayHeaderRow = row()
        displayHeaderRow.addView(TextView(this).apply {
            text = "Display"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF111827.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)))
        displaySpinner = Spinner(this).apply {
            setPadding(0, 0, 0, 0)
        }
        displayHeaderRow.addView(displaySpinner, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
            leftMargin = dp(8)
        })
        displayContainer.addView(displayHeaderRow, matchWrap())
        content.addView(displayContainer, matchWrap())

        val scrollRow = row()
        scrollRow.addView(button("Scroll Up") { sendScroll(0, -360) }, weightWrap(isFirst = true))
        scrollRow.addView(button("Scroll Down") { sendScroll(0, 360) }, weightWrap(isLast = true))
        content.addView(scrollRow, rowWrap())

        textInput = EditText(this).apply {
            hint = "Text to send"
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

        submitCheck = CheckBox(this).apply { text = "Submit with Enter" }
        content.addView(submitCheck)

        keyboardInput = createKeyboardInput()
        content.addView(keyboardInput, LinearLayout.LayoutParams(1, 1))

        controlsLayout = verticalItems()
        content.addView(controlsLayout, matchWrap())
        renderControlButtons(defaultControlButtons())

        content.addView(section("Quick Texts"))
        quickTextsLayout = horizontalItems()
        content.addView(wrapHorizontal(quickTextsLayout))

        content.addView(section("Commands"))
        commandsLayout = verticalItems()
        content.addView(commandsLayout)

        outputText = TextView(this).apply {
            text = "Command output"
            setTextColor(0xFF1F2937.toInt())
            background = roundedDrawable(0xFFEFF6FF.toInt(), dp(10), 0xFFBFDBFE.toInt(), 1)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        content.addView(outputText, matchWrap())

        content.addView(section("Shortcut Buttons"))
        shortcutsLayout = horizontalItems()
        content.addView(wrapHorizontal(shortcutsLayout))

        setContentView(root)
    }

    private fun onMainConnectionClick() {
        when (connectionState) {
            ConnectionState.DISCONNECTED -> connect()
            ConnectionState.CONNECTING -> Unit
            ConnectionState.CONNECTED -> setDisconnected("Disconnected")
        }
    }

    private fun connect() {
        val serverUrl = serverInput.text.toString().trim()
        val token = tokenInput.text.toString().trim()
        if (serverUrl.isBlank() || token.isBlank()) {
            showError("Enter server address and token")
            isConfigExpanded = true
            updateConfigVisibility()
            return
        }
        setConnectionState(ConnectionState.CONNECTING, "Connecting...")
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
                        "Connected: ${health.name} ${health.version} ${health.screenWidth}x${health.screenHeight}"
                    )
                    updateConfigVisibility()
                    renderDisplays(displays)
                    startHealthChecks()
                    startStream(client)
                }
                loadQuickActions(client)
            } catch (error: Exception) {
                mainHandler.post {
                    setDisconnected("Connect failed: ${error.shortMessage()}", showToast = true)
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
            frameText.text = "Frame: -"
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
            ConnectionState.DISCONNECTED -> "Connect"
            ConnectionState.CONNECTING -> "Connecting..."
            ConnectionState.CONNECTED -> "Disconnect"
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
        configEditButton.text = "Auth"
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
            displaySpinner.adapter = compactDisplayAdapter(listOf("Default stream"))
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
            try {
                val input = client.openStream(displayId)
                streamInput = input
                MjpegStreamReader(input).readFrames { frame ->
                    if (generation != streamGeneration) return@readFrames
                    mainHandler.post { updateFrame(frame) }
                }
                if (generation == streamGeneration) {
                    mainHandler.post { setDisconnected("Disconnected: stream ended") }
                }
            } catch (error: Exception) {
                if (generation == streamGeneration) {
                    mainHandler.post { setDisconnected("Disconnected: stream error ${error.shortMessage()}") }
                }
            }
        }
    }

    private fun updateFrame(frame: Bitmap) {
        lastScreenWidth = frame.width
        lastScreenHeight = frame.height
        screenView.setFrame(frame)
        frameText.text = "Frame: ${frame.width}x${frame.height} ${System.currentTimeMillis()}"
    }

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
                mainHandler.post { showError("Actions load failed: ${error.shortMessage()}") }
            }
        }
    }

    private fun renderControlButtons(items: List<ControlButton>) {
        val buttons = items.ifEmpty { defaultControlButtons() }
        controlsLayout.removeAllViews()
        buttons.chunked(4).forEach { rowItems ->
            val controlsRow = row()
            rowItems.forEachIndexed { index, item ->
                controlsRow.addView(button(item.label) { runControlButton(item.type) }, weightWrap(isFirst = index == 0, isLast = index == rowItems.lastIndex))
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
        quickTextsLayout.removeAllViews()
        items.forEach { item ->
            quickTextsLayout.addView(button(item.name) {
                sendControl(JSONObject().put("type", "text").put("text", item.content).put("submit", submitCheck.isChecked))
            }, horizontalItemWrap())
        }
    }

    private fun renderCommands(items: List<QuickCommand>) {
        commandsLayout.removeAllViews()
        items.chunked(4).forEach { rowItems ->
            val commandsRow = row()
            rowItems.forEachIndexed { index, item ->
                commandsRow.addView(button(item.name) { runCommand(item.id) }, weightWrap(isFirst = index == 0, isLast = index == rowItems.lastIndex))
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
        shortcutsLayout.removeAllViews()
        items.forEach { item ->
            shortcutsLayout.addView(button(item.name) { runShortcut(item.id) }, horizontalItemWrap())
        }
        shortcutsLayout.addView(button("Add Shortcut", ButtonStyle.GHOST) { startShortcutCapture() }, horizontalItemWrap())
    }

    private fun sendText() {
        val text = textInput.text.toString()
        if (text.isBlank()) {
            showError("Text is empty")
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
        val client = apiClient ?: return showError("Not connected")
        outputText.text = "Starting command..."
        executor.execute {
            try {
                val runId = client.runCommand(id)
                if (runId.isBlank()) throw IllegalStateException("missing runId")
                pollCommand(client, runId)
            } catch (error: Exception) {
                mainHandler.post { showError("Command failed: ${error.shortMessage()}") }
            }
        }
    }

    private fun pollCommand(client: ApiClient, runId: String) {
        repeat(60) {
            val run = client.getCommandRun(runId)
            mainHandler.post {
                outputText.text = "Status: ${run.status} Exit: ${run.exitCode ?: "-"}\n${run.output}"
            }
            if (run.status != "queued" && run.status != "running") return
            Thread.sleep(1000)
        }
    }

    private fun runShortcut(id: String) {
        val client = apiClient ?: return showError("Not connected")
        executor.execute {
            try {
                client.runShortcutButton(id)
                mainHandler.post { showStatus("Shortcut sent") }
            } catch (error: Exception) {
                mainHandler.post { showError("Shortcut failed: ${error.shortMessage()}") }
            }
        }
    }

    private fun startShortcutCapture() {
        if (connectionState != ConnectionState.CONNECTED) {
            showError("Connect before adding shortcut")
            return
        }
        if (!screenView.startShortcutCapture()) {
            showError("No screen frame yet")
            return
        }
        showStatus("Drag the blue pointer to the target, then release")
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
            hint = "Shortcut name"
            setSingleLine()
            setText("Shortcut")
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Name shortcut")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    showError("Shortcut name is empty")
                } else {
                    saveShortcutPoint(name, x, y, imageWidth, imageHeight)
                }
            }
            .show()
    }

    private fun saveShortcutPoint(name: String, x: Int, y: Int, imageWidth: Int, imageHeight: Int) {
        val client = apiClient ?: return showError("Not connected")
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
                    showStatus("Shortcut saved")
                }
            } catch (error: Exception) {
                mainHandler.post { showError("Shortcut save failed: ${error.shortMessage()}") }
            }
        }
    }

    private fun sendScroll(deltaX: Int, deltaY: Int) {
        sendControl(JSONObject().put("type", "scroll").put("deltaX", deltaX).put("deltaY", deltaY))
    }

    private fun sendControl(payload: JSONObject, showSentStatus: Boolean = true) {
        val client = apiClient ?: return showError("Not connected")
        val decorated = payload.withDisplayContext()
        executor.execute {
            try {
                client.sendControl(decorated)
                if (showSentStatus) {
                    mainHandler.post { showStatus("Sent ${decorated.optString("type")}") }
                }
            } catch (error: Exception) {
                mainHandler.post { showError("Send failed: ${error.shortMessage()}") }
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
                modeButton.contentDescription = "Screen view mode"
            }
            InteractionMode.POINTER -> {
                modeButton.setImageResource(R.drawable.ic_mode_pointer)
                modeButton.contentDescription = "Pointer click mode"
            }
            InteractionMode.TRACKPAD -> {
                modeButton.setImageResource(R.drawable.ic_mode_trackpad)
                modeButton.contentDescription = "Trackpad mode"
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
        showStatus("Keyboard open")
    }

    private fun closeKeyboardInput() {
        keyboardActive = false
        resetKeyboardInput()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(keyboardInput.windowToken, 0)
        keyboardInput.clearFocus()
        showStatus("Keyboard closed")
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
}

private fun JSONObject.putScreen(width: Int, height: Int): JSONObject {
    put("screenWidth", width)
    put("screenHeight", height)
    return this
}

private fun Exception.shortMessage(): String = message?.take(140) ?: javaClass.simpleName
