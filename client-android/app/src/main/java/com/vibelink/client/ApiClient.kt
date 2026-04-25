package com.vibelink.client

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiClient(
    private val baseUrl: String,
    private val token: String
) {
    fun getHealth(): HealthInfo {
        val json = getJsonObject("/health", authenticated = false)
        val screen = json.optJSONObject("screen") ?: JSONObject()
        return HealthInfo(
            name = json.optString("name", "VibeLink Mac Server"),
            version = json.optString("version", "unknown"),
            screenWidth = screen.optInt("width", 0),
            screenHeight = screen.optInt("height", 0),
            scale = screen.optDouble("scale", 1.0),
            displays = json.optJSONArray("displays")?.parseDisplays().orEmpty()
        )
    }

    fun getDisplays(): List<RemoteDisplay> {
        return getJsonArray("/api/displays").parseDisplays()
    }

    fun openStream(displayId: String? = null): InputStream {
        val encoded = URLEncoder.encode(token, "UTF-8")
        val displayQuery = displayId
            ?.takeIf { it.isNotBlank() }
            ?.let { "&displayId=${URLEncoder.encode(it, "UTF-8")}" }
            .orEmpty()
        val connection = openConnection("/stream?token=$encoded$displayQuery", "GET", authenticated = false)
        val code = connection.responseCode
        if (code !in 200..299) throw IllegalStateException("stream HTTP $code")
        return BufferedInputStream(connection.inputStream)
    }

    fun sendControl(payload: JSONObject) {
        postJson("/api/control", payload)
    }

    fun getQuickTexts(): List<QuickText> {
        return getJsonArray("/api/quick-texts").mapObjects {
            it.parseQuickText()
        }
    }

    fun getClientConfig(): ClientConfig {
        val json = getJsonObject("/api/client-config")
        return ClientConfig(
            controlButtons = json.optJSONArray("controlButtons")?.mapObjects {
                ControlButton(
                    id = it.optString("id"),
                    label = it.optString("label", it.optString("id")),
                    type = it.optString("type")
                )
            }.orEmpty(),
            quickTexts = json.optJSONArray("quickTexts")?.mapObjects { it.parseQuickText() }.orEmpty(),
            commands = json.optJSONArray("commands")?.mapObjects { it.parseCommand() }.orEmpty(),
            shortcutButtons = json.optJSONArray("shortcutButtons")?.mapObjects { it.parseShortcutButton() }.orEmpty(),
            updatedAt = json.optString("updatedAt")
        )
    }

    fun getCommands(): List<QuickCommand> {
        return getJsonArray("/api/commands").mapObjects {
            it.parseCommand()
        }
    }

    fun runCommand(id: String): String {
        val json = postJson("/api/commands/run", JSONObject().put("id", id))
        return json.optString("runId")
    }

    fun getCommandRun(runId: String): CommandRun {
        val json = getJsonObject("/api/commands/runs/${urlPart(runId)}")
        return CommandRun(
            id = json.optString("id", runId),
            status = json.optString("status"),
            exitCode = if (json.has("exitCode") && !json.isNull("exitCode")) json.optInt("exitCode") else null,
            output = json.optString("output")
        )
    }

    fun getShortcutButtons(): List<ShortcutButton> {
        return getJsonArray("/api/shortcut-buttons").mapObjects {
            it.parseShortcutButton()
        }
    }

    fun runShortcutButton(id: String) {
        postJson("/api/shortcut-buttons/run", JSONObject().put("id", id))
    }

    fun saveShortcutButton(button: ShortcutButton): List<ShortcutButton> {
        return postJsonArray(
            "/api/shortcut-buttons",
            JSONObject()
                .put("id", button.id)
                .put("name", button.name)
                .put("x", button.x)
                .put("y", button.y)
                .put("screenWidth", button.screenWidth)
                .put("screenHeight", button.screenHeight)
                .put("requiresConfirmation", button.requiresConfirmation)
        ).mapObjects { it.parseShortcutButton() }
    }

    private fun getJsonObject(path: String, authenticated: Boolean = true): JSONObject {
        val connection = openConnection(path, "GET", authenticated)
        val body = readResponse(connection)
        return JSONObject(body)
    }

    private fun getJsonArray(path: String): JSONArray {
        val connection = openConnection(path, "GET", authenticated = true)
        val body = readResponse(connection)
        return JSONArray(body)
    }

    private fun postJson(path: String, payload: JSONObject): JSONObject {
        val connection = openConnection(path, "POST", authenticated = true)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
            it.write(payload.toString())
        }
        val body = readResponse(connection)
        return if (body.isBlank()) JSONObject() else JSONObject(body)
    }

    private fun postJsonArray(path: String, payload: JSONObject): JSONArray {
        val connection = openConnection(path, "POST", authenticated = true)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
            it.write(payload.toString())
        }
        val body = readResponse(connection)
        return JSONArray(body)
    }

    private fun openConnection(path: String, method: String, authenticated: Boolean): HttpURLConnection {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val connection = URL(normalizedBase + normalizedPath).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 5000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")
        if (authenticated) connection.setRequestProperty("Authorization", "Bearer $token")
        return connection
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.let { readText(it) }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("HTTP $code ${body.take(120)}")
        return body
    }

    private fun readText(input: InputStream): String {
        return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
    }

    private fun urlPart(value: String): String = URLEncoder.encode(value, "UTF-8")
}

private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> {
    val result = ArrayList<T>(length())
    for (index in 0 until length()) {
        result.add(block(optJSONObject(index) ?: JSONObject()))
    }
    return result
}

private fun JSONObject.parseQuickText(): QuickText {
    return QuickText(
        id = optString("id"),
        name = optString("name", optString("id")),
        group = optString("group"),
        content = optString("content")
    )
}

private fun JSONObject.parseCommand(): QuickCommand {
    return QuickCommand(
        id = optString("id"),
        name = optString("name", optString("id")),
        command = optString("command"),
        workingDirectory = optString("workingDirectory"),
        requiresConfirmation = optBoolean("requiresConfirmation", false)
    )
}

private fun JSONObject.parseShortcutButton(): ShortcutButton {
    return ShortcutButton(
        id = optString("id"),
        name = optString("name", optString("id")),
        x = optDouble("x"),
        y = optDouble("y"),
        screenWidth = optDouble("screenWidth"),
        screenHeight = optDouble("screenHeight"),
        requiresConfirmation = optBoolean("requiresConfirmation", false)
    )
}

private fun JSONArray.parseDisplays(): List<RemoteDisplay> {
    return mapObjects {
        RemoteDisplay(
            id = it.optString("id"),
            name = it.optString("name", it.optString("id", "Display")),
            x = it.optInt("x", 0),
            y = it.optInt("y", 0),
            width = it.optInt("width", 0),
            height = it.optInt("height", 0),
            scale = it.optDouble("scale", 1.0),
            isMain = it.optBoolean("isMain", false)
        )
    }.filter { it.id.isNotBlank() }
}
